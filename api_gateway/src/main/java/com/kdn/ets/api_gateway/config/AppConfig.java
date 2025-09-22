package com.kdn.ets.api_gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // LocalDateTime 등 Java 8 시간 타입 직렬화를 위함
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
}
