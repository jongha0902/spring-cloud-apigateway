package com.kdn.ets.api_gateway.exception;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdn.ets.api_gateway.helper.GatewayLogHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-2) // 기본 핸들러보다 먼저
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final GatewayLogHelper logHelper;
    private final ObjectMapper objectMapper;
    private final Environment env;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 최종 상태/메시지 확정 (final)
        final HttpStatus httpStatus;
        final String userMessage;
        if (ex instanceof ApiException) {
            ApiException api = (ApiException) ex;
            httpStatus = HttpStatus.valueOf(api.getStatusCode());
            userMessage = api.getMessage();
        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            userMessage = "서버 내부 오류가 발생했습니다.";
        }
        final int statusCode = httpStatus.value();

        // 에러 바디 구성 요소
        final String traceId = ensureTraceId(exchange);
        final String causeMsg = rootCauseMessage(ex);
        final boolean includeStack = isDebug(exchange);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", statusCode);
        body.put("error", httpStatus.getReasonPhrase());
        body.put("message", userMessage);
        body.put("exception", ex.getClass().getName());
        body.put("cause", causeMsg);
        body.put("path", exchange.getRequest().getPath().value());
        body.put("method", exchange.getRequest().getMethodValue());
        body.put("traceId", traceId);
        if (includeStack) {
            String stack = Arrays.stream(ex.getStackTrace())
                                 .limit(100)
                                 .map(StackTraceElement::toString)
                                 .collect(Collectors.joining("\n"));
            body.put("stackTrace", stack);
        }

        // 직렬화
        byte[] jsonBytes;
        try {
            jsonBytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception jacksonError) {
            log.warn("Error serializing error body, fallback to plain string", jacksonError);
            String fallback = "{\"status\":" + statusCode + ",\"message\":\"" + safe(userMessage) + "\",\"cause\":\"" + safe(causeMsg) + "\"}";
            jsonBytes = fallback.getBytes(StandardCharsets.UTF_8);
        }

        // 응답 쓰기
        exchange.getResponse().setStatusCode(httpStatus);
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", traceId);

        final String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(jsonBytes);

        // 실제 바디 쓰기 완료 시점에 단 한 번 로깅
        return exchange.getResponse().writeWith(Mono.just(buffer))
                .doFinally(sig -> logHelper.asyncLogOnce(exchange, statusCode, jsonStr, ex));
    }

    // ===== Helpers =====

    private String ensureTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().set("X-Request-Id", traceId);
        return traceId;
    }

    private boolean isDebug(ServerWebExchange exchange) {
        String xDebug = exchange.getRequest().getHeaders().getFirst("X-Debug");
        if ("true".equalsIgnoreCase(xDebug)) return true;

        String[] profiles = env.getActiveProfiles();
        for (String p : profiles) {
            if ("dev".equalsIgnoreCase(p) || "local".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private String rootCauseMessage(Throwable ex) {
        Throwable cause = ex;
        Throwable next;
        while ((next = cause.getCause()) != null && next != cause) {
            cause = next;
        }
        String msg = cause.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = cause.getClass().getSimpleName();
        }
        return msg;
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
