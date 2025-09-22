package com.kdn.ets.api_gateway.filter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import com.kdn.ets.api_gateway.exception.ApiException;
import com.kdn.ets.api_gateway.repository.ApiRouteRepository;
import com.kdn.ets.api_gateway.service.AuthService;
import com.kdn.ets.api_gateway.service.LoggingService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class GlobalAuthRoutingFilter implements GlobalFilter, Ordered {

	// 라우트 조회(DB/캐시), 인증/인가, 로깅을 담당하는 애플리케이션 서비스들 의존성 주입.
    @Autowired private ApiRouteRepository apiRouteRepository;
    @Autowired private AuthService authService;
    @Autowired private LoggingService loggingService;
    
    // exchange attribute(요청 컨텍스트 저장소)에 쓸 키들. 요청/응답 바디와 로깅 플래그.
    private static final String ATTR_REQ = "captured_request_body";
    private static final String ATTR_RES = "captured_response_body";
    private static final String ATTR_LOGGED = "logging_done_once"; // 교차 경로 중복 로그 방지 키

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    	// 시작 시각을 찍어 지연시간(ms) 계산용으로 쓰고, 요청 URI에서 선두 '/' 제거해 apiId로 사용.
        // 예: '/health' -> 'health'. HTTP 메서드도 문자열로 확보.
        final long startTime = System.currentTimeMillis();
        final String path = exchange.getRequest().getURI().getPath();
        final String apiId = path.startsWith("/") ? path.substring(1) : path;
        final String requestMethod = exchange.getRequest().getMethod().name();

        log.info("Request received for apiId: {}, Method: {}", apiId, requestMethod);

        // 공용 플래그 준비 (exchange attribute 저장)
        // 이번 요청 컨텍스트에서 "로그는 딱 1번"만 하도록 하는 공유 플래그를 준비.
        // 성공/에러/완료 훅 등 여러 지점에서 호출돼도 compareAndSet으로 1회만 허용.)
        AtomicBoolean loggedOnceGlobal = exchange.getAttribute(ATTR_LOGGED);
        if (loggedOnceGlobal == null) {
            loggedOnceGlobal = new AtomicBoolean(false);
            exchange.getAttributes().put(ATTR_LOGGED, loggedOnceGlobal);
        }
        AtomicBoolean finalLoggedOnceGlobal = loggedOnceGlobal;
        
        // (블로킹일 수 있는) 라우트 조회를 fromCallable로 감싸고 boundedElastic 스케줄러에서 실행.
        // apiId로 라우트 찾기 → useYn이 'Y'인지 확인 → 없거나 비활성화면 404 ApiException.
        return Mono.fromCallable(() ->
                    apiRouteRepository.findByApiId(apiId)
                        .filter(route -> "Y".equalsIgnoreCase(route.getUseYn()))
                        .orElseThrow(() -> new ApiException(404, "API를 찾을 수 없거나 비활성화되었습니다: " + apiId))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(apiInfo -> {
                    if (!apiInfo.getMethod().equalsIgnoreCase(requestMethod)) {
                        return Mono.error(new ApiException(405, "허용되지 않은 메서드입니다: " + requestMethod));
                    }
                    exchange.getAttributes().put("api_info", apiInfo);

                    return authService.verifyAndGetUserId(exchange.getRequest(), apiId)
                        .flatMap(userId -> {
                        	// 인증/인가: 요청 헤더/키 등을 검사해 userId 획득. 실패 시 downsteam 으로 가지 않음.
                            // 인증 통과하면 user_id를 컨텍스트에 저장(로그에 활용).
                            exchange.getAttributes().put("user_id", userId);
                            
                            // 라우트가 가리키는 "다운스트림 실제 엔드포인트" URI 생성.
                            // 원래 요청 URL은 보존(추적/필요 시 사용), 실제로 보낼 URL은 newRequestUri 로 교체.
                            final URI downstreamUri = URI.create(apiInfo.getPath());
                            ServerWebExchangeUtils.addOriginalRequestUrl(exchange, exchange.getRequest().getURI());
                            final URI newRequestUri = UriComponentsBuilder.fromUri(downstreamUri).build(true).toUri();
                            
                            // 리액티브 스트림으로 흘러오는 요청 바디를 "한 번" 모두 모음(join).
                            // 바디는 1회 소모 가능이므로, 바이트 배열로 복제해 다시 공급할 준비를 함.
                            final DataBufferFactory bf = exchange.getResponse().bufferFactory();
                            return DataBufferUtils.join(exchange.getRequest().getBody())
                                    .defaultIfEmpty(bf.wrap(new byte[0]))
                                    .flatMap(joined -> {
                                        byte[] reqBytes = new byte[joined.readableByteCount()];
                                        joined.read(reqBytes);
                                        DataBufferUtils.release(joined);
                                        
                                        // 요청 바디 문자열을 컨텍스트에 저장(로깅/감사 목적).
                                        // (바이너리 요청은 깨질 수 있으므로 실제 운영에선 MIME 체크/마스킹/크기 제한 권장)
                                        final String reqBodyStr = new String(reqBytes, StandardCharsets.UTF_8);
                                        exchange.getAttributes().put(ATTR_REQ, reqBodyStr);
                                        
                                        // 교체된 URI를 가진 요청으로 래핑 + 바디 재주입.
                                        // TRANSFER_ENCODING 제거, Content-Length를 정확한 길이로 설정.
                                        // getBody()에서 방금 저장한 reqBytes를 다시 흘려보내도록 구현.
                                        ServerHttpRequest base = exchange.getRequest().mutate().uri(newRequestUri).build();
                                        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(base) {
                                            @Override
                                            public HttpHeaders getHeaders() {
                                                HttpHeaders headers = new HttpHeaders();
                                                headers.putAll(super.getHeaders());
                                                headers.remove(HttpHeaders.TRANSFER_ENCODING);
                                                headers.setContentLength(reqBytes.length);
                                                return headers;
                                            }
                                            @Override
                                            public Flux<DataBuffer> getBody() {
                                                return Flux.defer(() -> Flux.just(bf.wrap(reqBytes)));
                                            }
                                        };
                                        
                                        // 원래 exchange 를 새 요청(데코레이터)로 교체.
                                        // Gateway 내부에서 사용하는 라우팅 URL 속성도 newRequestUri로 설정.
                                        ServerWebExchange newExchange = exchange.mutate().request(decoratedRequest).build();
                                        newExchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, newRequestUri);
                                        log.info(">>> Rewriting path to: {}", newRequestUri);
                                        
                                        // 응답 바디를 누적 저장할 버퍼 준비(문자열 기반).
                                        final ServerHttpResponse originalResponse = newExchange.getResponse();
                                        final StringBuilder respBody = new StringBuilder();

                                        // ★ 지역 loggedOnce 제거하고, 공용(finalLoggedOnceGlobal)만 사용
                                        // 응답을 가로채어 바디를 캡처하고, 완료 지점에서 1회만 로깅하는 데코레이터.
                                        // compareAndSet(false, true)로 중복 로그 방지.
                                        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                                            private void tryLogOnce() {
                                                if (finalLoggedOnceGlobal.compareAndSet(false, true)) {
                                                    long latency = System.currentTimeMillis() - startTime;
                                                    int status = getStatusCode() != null ? getStatusCode().value() : 500;
                                                    String bodyStr = respBody.toString();
                                                    newExchange.getAttributes().put(ATTR_RES, bodyStr);
                                                    asyncLog(newExchange, (int) latency, status, bodyStr, null);
                                                }
                                            }
                                            
                                            // 응답 전송 전에 Content-Length를 제거 → 청크드 전송 유도.
                                            // (바디를 가로채 가공하므로 원래 길이를 보장하기 어려움)
                                            @Override
                                            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                                                // ★★★ 가장 중요: 응답 시작 전에 Content-Length 제거해서 chunked로 전송되게 함
                                                HttpHeaders headers = getDelegate().getHeaders();
                                                headers.remove(HttpHeaders.CONTENT_LENGTH);
                                                
                                                // 흘러오는 응답 데이터버퍼를 읽어 문자열로 누적 저장(respBody).
                                                // 원본 버퍼는 즉시 release하고, 같은 바이트로 새 버퍼를 만들어 그대로 다운스트림에 씀.
                                                // (바이너리 응답은 문자열 변환 시 깨질 수 있으므로 운영 시 주의/MIME 필터링 권장)
                                                Flux<? extends DataBuffer> flux = Flux.from(body)
                                                    .map(buf -> {
                                                        byte[] bytes = new byte[buf.readableByteCount()];
                                                        buf.read(bytes);
                                                        DataBufferUtils.release(buf);
                                                        respBody.append(new String(bytes, StandardCharsets.UTF_8));
                                                        // 원본을 반환하지 말고 새 버퍼로 감싸서 그대로 흘려보낸다
                                                        return originalResponse.bufferFactory().wrap(bytes);
                                                    })
                                                    .doOnError(t -> log.warn("[Gateway] writeWith error", t))
                                                    .doFinally(st -> tryLogOnce());

                                                return super.writeWith(flux);
                                            }
                                            
                                            @Override
                                            public Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                                            	// 청크가 중첩된 경우(writeAndFlushWith)에도 Content-Length 제거.
                                                HttpHeaders headers = getDelegate().getHeaders();
                                                headers.remove(HttpHeaders.CONTENT_LENGTH);

                                                return writeWith(Flux.from(body).flatMap(p -> p));
                                            }

                                            @Override
                                            public Mono<Void> setComplete() {
                                                return super.setComplete().doFinally(st -> tryLogOnce());
                                            }
                                        };
                                        
                                        // 체인 다운스트림으로 필터를 진행하되, 응답을 데코레이터로 교체한 exchange를 전달.
                                        Mono<Void> routed = chain.filter(newExchange.mutate().response(decoratedResponse).build());

                                        // (안전망) 체인 완료 시점에도 마지막으로 1회 로깅 시도.
                                        // 앞에서 이미 기록됐다면 compareAndSet에서 걸러짐.
                                        return routed.doFinally(st -> {
                                            if (finalLoggedOnceGlobal.compareAndSet(false, true)) {
                                                long latency = System.currentTimeMillis() - startTime;
                                                int status = originalResponse.getStatusCode() != null ? originalResponse.getStatusCode().value() : 500;
                                                String bodyStr = respBody.toString();
                                                newExchange.getAttributes().put(ATTR_RES, bodyStr);
                                                asyncLog(newExchange, (int) latency, status, bodyStr, null);
                                            }
                                        });
                                    });
                        });
                })
                .onErrorResume(e -> { 
                	// 위의 어느 단계에서든 예외가 나면 여기로 옴. 상태코드/지연시간 산출.
                	// 실패 경로에서도 단 한 번만 로깅. 응답 바디 대신 에러 메시지를 JSON 형태로 기록.
                    int statusCode = (e instanceof ApiException) ? ((ApiException) e).getStatusCode() : 500;
                    long latency = System.currentTimeMillis() - startTime;

                    // ★ 실패 경로에서도 공용 플래그 사용 (중복 방지)
                    AtomicBoolean loggedFlag = exchange.getAttribute(ATTR_LOGGED);
                    if (loggedFlag == null) {
                        loggedFlag = new AtomicBoolean(false);
                        exchange.getAttributes().put(ATTR_LOGGED, loggedFlag);
                    }
                    if (loggedFlag.compareAndSet(false, true)) {
                        asyncLog(exchange, (int) latency, statusCode, "{\"detail\":\"" + e.getMessage() + "\"}", e);
                    }

                    return Mono.error(e);
                });
    }

    private void asyncLog(ServerWebExchange exchange, int latencyMs, int statusCode, String responseBody, Throwable error) {
    	// 로깅은 비동기로 실행(논블로킹). 로깅 중 예외가 나도 게이트웨이 흐름을 막지 않도록 try/catch.
        // boundedElastic에 위임(파일/DB I/O 가능성 고려).
        final String resBody = responseBody;
        Mono.fromRunnable(() -> {
            try {
                exchange.getAttributes().put(ATTR_RES, resBody);
                loggingService.logRequest(exchange, latencyMs, statusCode, resBody, error);
            } catch (Throwable t) {
                log.warn("loggingService.logRequest failed (async)", t);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    @Override
    public int getOrder() {
        // NettyRoutingFilter 바로 앞에서 동작
        return 20000;
    }
}
