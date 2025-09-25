package com.kdn.ets.api_gateway.helper;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;

public final class ClientIpHelper {
    // 신뢰하는 프록시 개수에 맞춰 설정 (nginx 1개 있으면 1)
    private static final XForwardedRemoteAddressResolver XFF_RESOLVER = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    private ClientIpHelper() {}

    public static String resolve(ServerWebExchange exchange) {
        HttpHeaders h = exchange.getRequest().getHeaders();

        // 1) Spring Cloud Gateway의 안전한 XFF 해석기 (권장)
        try {
            InetSocketAddress addr = XFF_RESOLVER.resolve(exchange);
            if (addr != null && addr.getAddress() != null) {
                return normalize(addr.getAddress().getHostAddress());
            }
        } catch (Exception ignore) { /* fall back */ }

        // 2) X-Forwarded-For 직접 파싱 (프록시들이 제대로 넣어주는 경우)
        String xff = h.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // 첫 번째가 클라이언트 IP
            String ip = xff.split(",")[0].trim();
            if (!ip.isEmpty()) return normalize(ip);
        }

        // 3) X-Real-IP, Forwarded: for=... 도 시도
        String xReal = h.getFirst("X-Real-IP");
        if (xReal != null && !xReal.isEmpty()) return normalize(xReal);

        String forwarded = h.getFirst("Forwarded"); // RFC 7239
        if (forwarded != null) {
            // 간단 파서: for= 값만 추출
            for (String part : forwarded.split(";|,")) {
                String p = part.trim().toLowerCase();
                if (p.startsWith("for=")) {
                    String ip = p.substring(4).replace("\"", "").trim();
                    // for="[2001:db8::1234]" 형태 처리
                    if (ip.startsWith("[") && ip.endsWith("]")) ip = ip.substring(1, ip.length()-1);
                    if (!ip.isEmpty()) return normalize(ip);
                }
            }
        }

        // 4) 최후: 소켓 원격 주소
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(a -> normalize(a.getHostAddress()))
                .orElse("unknown");
    }

    private static String normalize(String ip) {
        if (ip == null) return "unknown";
        // IPv6 루프백 → IPv4 루프백으로 정규화(선호한다면)
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        // IPv6 zone id 제거 (fe80::1%eth0)
        int zone = ip.indexOf('%');
        if (zone > -1) ip = ip.substring(0, zone);
        // IPv4-mapped IPv6 (::ffff:192.0.2.1) → IPv4
        if (ip.startsWith("::ffff:")) return ip.substring(7);
        return ip;
    }
}
