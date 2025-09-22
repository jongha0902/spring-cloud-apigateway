package com.kdn.ets.api_gateway.entity;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "api_permissions")
@Getter
public class ApiPermission {

    @EmbeddedId
    private ApiPermissionId id;

    @Column(name = "create_id")
    private String createId;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    @Column(name = "update_id")
    private String updateId;

    @Column(name = "update_date")
    private LocalDateTime updateDate;
}