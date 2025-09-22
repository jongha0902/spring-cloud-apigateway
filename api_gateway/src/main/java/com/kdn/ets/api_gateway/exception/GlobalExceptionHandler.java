package com.kdn.ets.api_gateway.exception;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-2) // 기본 핸들러보다 먼저 실행되도록 순서 지정
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String detail = "서버 내부 오류가 발생했습니다.";
        log.error(">>> GlobalExceptionHandler caught exception: ", ex);

        if (ex instanceof ApiException) {
            ApiException apiException = (ApiException) ex;
            status = HttpStatus.valueOf(apiException.getStatusCode());
            detail = apiException.getMessage();
        } else {
            // 다른 예상치 못한 예외들에 대한 처리
            // 로깅을 추가할 수 있음
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String responseBody = "{\"detail\":\"" + detail + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBody.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
