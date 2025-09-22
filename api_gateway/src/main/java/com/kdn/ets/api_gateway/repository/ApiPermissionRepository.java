package com.kdn.ets.api_gateway.repository;

import com.kdn.ets.api_gateway.entity.ApiPermission;
import com.kdn.ets.api_gateway.entity.ApiPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiPermissionRepository extends JpaRepository<ApiPermission, ApiPermissionId> {

    /**
     * userId와 apiId를 기준으로 권한이 존재하는지 확인합니다.
     */
    boolean existsById_UserIdAndId_ApiId(String userId, String apiId);
}