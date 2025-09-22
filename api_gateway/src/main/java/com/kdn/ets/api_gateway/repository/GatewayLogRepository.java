package com.kdn.ets.api_gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.kdn.ets.api_gateway.entity.GatewayLog;

public interface GatewayLogRepository extends JpaRepository<GatewayLog, Long> {
}
