package com.kdn.ets.api_gateway.config;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
class ProxyKiller {

    @PostConstruct
    void clearProxy() {
        for (String k : new String[]{
                "http.proxyHost","http.proxyPort",
                "https.proxyHost","https.proxyPort",
                "socksProxyHost","socksProxyPort"}) {
            if (System.getProperty(k) != null) {
                log.warn("Clearing system property {}", k);
                System.clearProperty(k);
            }
        }
        // 진단용 출력
        log.info("HTTP_PROXY={}", System.getenv("HTTP_PROXY"));
        log.info("HTTPS_PROXY={}", System.getenv("HTTPS_PROXY"));
        log.info("NO_PROXY={}", System.getenv("NO_PROXY"));
        log.info("http.proxyHost={}", System.getProperty("http.proxyHost"));
        log.info("https.proxyHost={}", System.getProperty("https.proxyHost"));
    }
}
