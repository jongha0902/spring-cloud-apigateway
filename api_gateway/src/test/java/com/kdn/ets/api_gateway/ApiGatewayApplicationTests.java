package com.kdn.ets.api_gateway;

import com.kdn.ets.api_gateway.entity.ApiRoute;
import com.kdn.ets.api_gateway.repository.ApiRouteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApiGatewayApplicationTests {

    // 1. ApiRouteRepository를 주입받습니다.
    @Autowired
    private ApiRouteRepository apiRouteRepository;

    // 2. 새로운 테스트 메소드를 추가합니다.
    @Test
    void findByApiId_테스트() {
        // 3. DB에 분명히 존재하는 api_id로 조회를 시도합니다.
        String existingApiId = "LLM_RAG";
        Optional<ApiRoute> result = apiRouteRepository.findByApiId(existingApiId);

        // 4. 조회 결과가 존재하는지 확인합니다.
        System.out.println("조회 결과: " + result);
        System.out.println("조회 결과: " + result.toString());
        assertTrue(result.isPresent(), "DB에서 " + existingApiId + " 를 찾을 수 없습니다.");
    }

    @Test
    void contextLoads() {
        // 기존 테스트는 그대로 둡니다.
    }
}