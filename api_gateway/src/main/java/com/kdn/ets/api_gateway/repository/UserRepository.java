package com.kdn.ets.api_gateway.repository;

import com.kdn.ets.api_gateway.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
    // 기본 CRUD 외에 특별히 필요한 조회 메소드가 있다면 여기에 추가합니다.
    // 예: Optional<User> findByUserName(String userName);
}