package com.kdn.ets.api_gateway.repository;

import com.kdn.ets.api_gateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    /**
     * API 키(문자열)를 기준으로 ApiKey 엔티티를 조회합니다.
     * @param apiKey 조회할 해싱된 API 키
     * @return Optional<ApiKey> 조회 결과
     */
    Optional<ApiKey> findByApiKey(String apiKey);
}