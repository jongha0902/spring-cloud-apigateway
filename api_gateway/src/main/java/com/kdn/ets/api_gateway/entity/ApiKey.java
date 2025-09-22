package com.kdn.ets.api_gateway.entity;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne; // OneToOne 임포트
import javax.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "api_keys")
@Getter
public class ApiKey {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "api_key", unique = true, nullable = false)
    private String apiKey;

    // --- User와의 관계 매핑 추가 ---
    @OneToOne // 일대일 관계 설정
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    private String comment;

    @Column(name = "generate_date")
    private LocalDateTime generateDate;

    @Column(name = "generate_id")
    private String generateId;

    @Column(name = "regenerate_date")
    private LocalDateTime regenerateDate;

    @Column(name = "regenerate_id")
    private String regenerateId;
}