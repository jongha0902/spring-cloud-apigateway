package com.kdn.ets.api_gateway.entity;

import java.io.Serializable;
import javax.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
@EqualsAndHashCode
public class ApiPermissionId implements Serializable {

    private String apiId;
    private String method;
    private String userId;
}