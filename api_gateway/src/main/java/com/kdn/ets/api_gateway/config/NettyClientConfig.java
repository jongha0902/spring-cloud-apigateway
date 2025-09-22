package com.kdn.ets.api_gateway.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Configuration
public class NettyClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.create()
                // 연결 타임아웃 10초
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                // 응답 타임아웃 15초
                .responseTimeout(Duration.ofSeconds(120))
                // 시스템/환경 프록시 무시
                .noProxy();
    }
}
