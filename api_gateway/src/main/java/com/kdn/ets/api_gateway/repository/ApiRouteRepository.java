package com.kdn.ets.api_gateway.repository;

import com.kdn.ets.api_gateway.entity.ApiRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiRouteRepository extends JpaRepository<ApiRoute, String> {

    /**
     * apiId를 기준으로 ApiRoute 엔티티를 조회합니다.
     */
    Optional<ApiRoute> findByApiId(String apiId);
}