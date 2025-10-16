package com.kdn.ets.api_gateway.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kdn.ets.api_gateway.entity.ApiRoute;
import com.kdn.ets.api_gateway.entity.GatewayLog;
import com.kdn.ets.api_gateway.helper.ClientIpHelper;
import com.kdn.ets.api_gateway.helper.GatewayLogHelper;
import com.kdn.ets.api_gateway.repository.GatewayLogRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoggingService {

    @Autowired
    private GatewayLogRepository logRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final List<String> SENSITIVE_KEYS = Collections.unmodifiableList(
        Arrays.asList(
            "authorization", "cookie", "x-api-key", "set-cookie",
            "password", "passwd", "new_password", "confirm_password",
            "access_token", "refresh_token", "token", "secret", "client_secret"
        )
    );

    /**
     * @param responseBody 필터에서 캡처한 "응답 바디"
     * 요청 바디는 exchange attribute("captured_request_body")에서 읽어옵니다.
     */
    @Async
    public void logRequest(ServerWebExchange exchange, int latency, Integer statusCode, String responseBody, Throwable ex) {

        ApiRoute apiInfo = exchange.getAttribute("api_info");
        String userId = exchange.getAttribute("user_id");

        // ── 요청 바디 꺼내기 (필터에서 저장됨)
        String rawRequestBody = exchange.getAttribute("captured_request_body");
        String contentType = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        String safeRequestBody = maskBodyIfPossible(rawRequestBody, contentType);
        
        final String path = exchange.getRequest().getURI().getPath();
        final String apiId = path.startsWith("/") ? path.substring(1) : path;

        GatewayLog.GatewayLogBuilder logBuilder = GatewayLog.builder()
											                .userId(userId)
											                .apiId(apiId != null ? apiId : "unknown")
											                .method(exchange.getRequest().getMethod().name())
											                .path(apiInfo != null ? apiInfo.getPath() : "unknown")
											                .queryParam(exchange.getRequest().getQueryParams().toString())
											                .headers(maskSensitiveHeaders(exchange.getRequest().getHeaders()))
											                .requestedAt(LocalDateTime.now().minusNanos(latency * 1_000_000L))
											                .respondedAt(LocalDateTime.now())
											                .latencyMs(latency)
											                .clientIp(ClientIpHelper.resolve(exchange))
											                .userAgent(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT))
											                .statusCode(statusCode)
											                .body(safeRequestBody);
        
        if (ex == null) {
            logBuilder.isSuccess("Y")
                      .response(truncate(responseBody, 4000));
        } else {
            logBuilder.isSuccess("N")
                      .errorMessage(ex.getClass().getName() + ": " + ex.getMessage())
                      .response(truncate(responseBody, 4000));
        }

        logRepository.save(logBuilder.build());
    }

    /** 헤더 값 마스킹 */
    private String maskSensitiveHeaders(HttpHeaders headers) {
        Map<String, String> masked = headers.toSingleValueMap()
        									.entrySet()
        									.stream()
							                .collect(Collectors.toMap(
							                        Map.Entry::getKey,
							                        e -> SENSITIVE_KEYS.contains(e.getKey().toLowerCase()) ? "**********" : e.getValue()
							                ));
        try {
            return truncate(objectMapper.writeValueAsString(masked), 1500);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Header parsing failed\"}";
        }
    }

    /** 요청 바디 마스킹 (가능할 때만) */
    private String maskBodyIfPossible(String body, String contentType) {
        if (body == null || body.isEmpty()) return body;
        if (contentType == null) return body;

        try {
            // JSON이면 키 기반 마스킹
            if (contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
                JsonNode node = objectMapper.readTree(body);
                maskJsonNode(node);
                return objectMapper.writeValueAsString(node);
            }

            // x-www-form-urlencoded의 경우 키=값&... 단순 마스킹
            if (contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
                String[] pairs = body.split("&");
                for (int i = 0; i < pairs.length; i++) {
                    String[] kv = pairs[i].split("=", 2);
                    if (kv.length == 2 && SENSITIVE_KEYS.contains(kv[0].toLowerCase())) {
                        pairs[i] = kv[0] + "=**********";
                    }
                }
                return String.join("&", pairs);
            }
        } catch (Exception ignore) {
            // 파싱 실패 시 원문 그대로 저장
        	log.warn("loggingService.logRequest failed (async)", ignore);
        }
        return body;
    }

    /** JSON 트리에서 민감키 재귀 마스킹 */
    private void maskJsonNode(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fieldNames().forEachRemaining(field -> {
                JsonNode child = obj.get(field);
                if (SENSITIVE_KEYS.contains(field.toLowerCase())) {
                    obj.put(field, "**********");
                } else {
                    maskJsonNode(child);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) maskJsonNode(child);
        }
    }

    private String truncate(String value, int length) {
        if (value == null || value.length() <= length) return value;
        return value.substring(0, length);
    }
}
