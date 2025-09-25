package com.kdn.ets.api_gateway.helper;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.kdn.ets.api_gateway.service.LoggingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayLogHelper {

    // 모든 필터/핸들러에서 같은 키를 사용해야 공용 컨텍스트로 공유됩니다.
    public static final String ATTR_REQ    = "captured_request_body";
    public static final String ATTR_RES    = "captured_response_body";
    public static final String ATTR_LOGGED = "logging_done_once";
    public static final String ATTR_START  = "logging_start_ms";

    private final LoggingService loggingService;

    /**
     * 이 메서드만 호출하면 됨.
     * - 한번만 실행(dedupe)
     * - 지연시간 자동 계산(ATTR_START 없으면 지금 시각으로 초기화)
     * - 비동기(boundElastic)로 DB 로깅
     */
    public void asyncLogOnce(ServerWebExchange exchange, int statusCode, String responseBody, Throwable error) {
        // 시작시각 보장 + 지연시간 계산
        Long start = exchange.getAttribute(ATTR_START);
        if (start == null) {
            start = System.currentTimeMillis();
            exchange.getAttributes().put(ATTR_START, start);
        }
        final int latencyMs = (int) (System.currentTimeMillis() - start);

        // dedupe 플래그 보장
        AtomicBoolean once = exchange.getAttribute(ATTR_LOGGED);
        if (once == null) {
            once = new AtomicBoolean(false);
            exchange.getAttributes().put(ATTR_LOGGED, once);
        }
        if (!once.compareAndSet(false, true)) {
            return; // 이미 다른 곳에서 기록함
        }

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
}
