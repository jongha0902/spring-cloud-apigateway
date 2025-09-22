package com.kdn.ets.api_gateway.entity;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "gateway_logs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "user_id") // DB 컬럼명 매핑
    private String userId;

    @Column(name = "api_id") // DB 컬럼명 매핑
    private String apiId;

    @Column(nullable = false) // NOT NULL 제약조건 추가
    private String method;

    @Column(nullable = false) // NOT NULL 제약조건 추가
    private String path;

    @Column(name = "query_param", length = 1000) // DB 컬럼명 매핑
    private String queryParam;

    @Column(length = 1500)
    private String headers;

    @Column(length = 2000)
    private String body;

    @Column(name = "status_code") // DB 컬럼명 매핑
    private Integer statusCode;

    @Column(length = 4000)
    private String response;

    @Column(name = "requested_at", nullable = false) // DB 컬럼명 매핑 및 NOT NULL
    private LocalDateTime requestedAt;

    @Column(name = "responded_at", nullable = false) // DB 컬럼명 매핑 및 NOT NULL
    private LocalDateTime respondedAt;

    @Column(name = "latency_ms") // DB 컬럼명 매핑
    private Integer latencyMs;

    @Column(name = "client_ip") // DB 컬럼명 매핑
    private String clientIp;

    @Column(name = "user_agent", length = 500) // DB 컬럼명 매핑
    private String userAgent;

    @Column(name = "is_success", nullable = false) // DB 컬럼명 매핑 및 NOT NULL
    private String isSuccess;

    @Column(name = "error_message", length = 500) // DB 컬럼명 매핑
    private String errorMessage;
}