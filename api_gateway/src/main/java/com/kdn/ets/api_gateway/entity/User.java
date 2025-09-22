package com.kdn.ets.api_gateway.entity;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String password;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "permission_code")
    private String permissionCode;

    @Column(name = "use_yn")
    private String useYn;

    // ... createId, createDate 등 나머지 필드 ...
    @Column(name = "create_id")
    private String createId;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    @Column(name = "update_id")
    private String updateId;

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    @Column(name = "refresh_token")
    private String refreshToken;
}