package com.kdn.ets.api_gateway.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.ToString;

@Entity
@Table(name = "api_list")
@Getter
@ToString
public class ApiRoute {

    @Id
    @Column(name = "api_id")
    private String apiId;

    @Column(name = "api_name", nullable = false)
    private String apiName;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String method;

    @Column(name = "use_yn", nullable = false)
    private String useYn;

    private String description;

    @Column(name = "flow_data")
    private String flowData;

    @Column(name = "write_id")
    private String writeId;

    @Column(name = "write_date")
    private LocalDateTime writeDate;

    @Column(name = "update_id")
    private String updateId;

    @Column(name = "update_date")
    private LocalDateTime updateDate;
}