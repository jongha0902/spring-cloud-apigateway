package com.kdn.ets.api_gateway.filter;

import java.net.URI;
import java.nio.charset.StandardCharsets;

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
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import com.kdn.ets.api_gateway.exception.ApiException;
import com.kdn.ets.api_gateway.repository.ApiRouteRepository;
import com.kdn.ets.api_gateway.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalAuthRoutingFilter implements GlobalFilter, Ordered {

    private final ApiRouteRepository apiRouteRepository;
    private final AuthService authService;

    // 공유 attribute 키
    private static final String ATTR_REQ   = "captured_request_body";
    private static final String ATTR_START = "logging_start_ms";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 요청 시작 시각(지연시간 계산용) 없으면 세팅
        if (exchange.getAttribute(ATTR_START) == null) {
            exchange.getAttributes().put(ATTR_START, System.currentTimeMillis());
        }

        final String path = exchange.getRequest().getURI().getPath();
        final String apiId = path.startsWith("/") ? path.substring(1) : path;
        final String requestMethod = exchange.getRequest().getMethod().name();

        log.info("Request received for apiId: {}, Method: {}", apiId, requestMethod);

        // 라우트 조회 (블로킹 가능성 → boundedElastic)
        return Mono.fromCallable(() ->
                    apiRouteRepository.findByApiId(apiId)
                        .filter(route -> "Y".equalsIgnoreCase(route.getUseYn()))
                        .orElseThrow(() -> new ApiException(404, "API를 찾을 수 없거나 비활성화되었습니다: " + apiId))
               )
               .subscribeOn(Schedulers.boundedElastic())
               .flatMap(apiInfo -> {
                   // 메서드 검증
                   if (!apiInfo.getMethod().equalsIgnoreCase(requestMethod)) {
                       return Mono.error(new ApiException(405, "허용되지 않은 메서드입니다: " + requestMethod));
                   }
                   exchange.getAttributes().put("api_info", apiInfo);

                   // 인증/인가
                   return authService.verifyAndGetUserId(exchange.getRequest(), apiId)
                       .flatMap(userId -> {
                           exchange.getAttributes().put("user_id", userId);

                           // 목적지 URI 재작성
                           final URI downstreamUri = URI.create(apiInfo.getPath());
                           ServerWebExchangeUtils.addOriginalRequestUrl(exchange, exchange.getRequest().getURI());
                           final URI newRequestUri = UriComponentsBuilder.fromUri(downstreamUri).build(true).toUri();

                           // 요청 바디 캡처 후 재주입
                           final DataBufferFactory bf = exchange.getResponse().bufferFactory();
                           return DataBufferUtils.join(exchange.getRequest().getBody())
                                   .defaultIfEmpty(bf.wrap(new byte[0]))
                                   .flatMap(joined -> {
                                       byte[] reqBytes = new byte[joined.readableByteCount()];
                                       joined.read(reqBytes);
                                       DataBufferUtils.release(joined);

                                       // 요청 바디(텍스트 기준) 저장 — 로깅 서비스가 참고
                                       final String reqBodyStr = new String(reqBytes, StandardCharsets.UTF_8);
                                       exchange.getAttributes().put(ATTR_REQ, reqBodyStr);

                                       // 요청 데코레이터로 바디 재공급 + URI 교체
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

                                       ServerWebExchange newExchange = exchange.mutate().request(decoratedRequest).build();
                                       newExchange.getAttributes().put(
                                               ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, newRequestUri);
                                       log.info(">>> Rewriting path to: {}", newRequestUri);

                                       // 응답 캡처/로깅은 ResponseCaptureFilter & GlobalExceptionHandler가 담당
                                       return chain.filter(newExchange);
                                   });
                       });
               });
    }

    @Override
    public int getOrder() {
        // RouteToRequestUrlFilter(10000) 이후에 동작하여 최종 목적지를 덮어쓰도록 설정
        return 20000;
    }
}
