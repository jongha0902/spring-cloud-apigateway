package com.kdn.ets.api_gateway.filter;

import java.nio.charset.StandardCharsets;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.kdn.ets.api_gateway.helper.GatewayLogHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseCaptureFilter implements GlobalFilter, Ordered {

    private final GatewayLogHelper logHelper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {

        final ServerHttpResponse original = exchange.getResponse();
        final StringBuilder respBody = new StringBuilder();

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            private void logOnce(Throwable error) {
                int status = getStatusCode() != null ? getStatusCode().value() : 500;
                logHelper.asyncLogOnce(exchange, status, respBody.toString(), error);
            }

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // Content-Length 제거 → 청크 전송
                getDelegate().getHeaders().remove(HttpHeaders.CONTENT_LENGTH);

                Flux<? extends DataBuffer> flux = Flux.from(body)
                    .map(buf -> {
                        byte[] bytes = new byte[buf.readableByteCount()];
                        buf.read(bytes);
                        DataBufferUtils.release(buf);
                        // 텍스트 기반 로깅(필요시 마스킹/길이 제한은 GatewayLogHelper에 추가)
                        respBody.append(new String(bytes, StandardCharsets.UTF_8));
                        return original.bufferFactory().wrap(bytes);
                    })
                    .doFinally(sig -> logOnce(null));

                return super.writeWith(flux);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                getDelegate().getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                return writeWith(Flux.from(body).flatMap(p -> p));
            }

            @Override
            public Mono<Void> setComplete() {
                return super.setComplete().doFinally(sig -> logOnce(null));
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        // NettyWriteResponseFilter(-1) 이전에 데코레이터 설치 필요
        return -2;
    }
}
