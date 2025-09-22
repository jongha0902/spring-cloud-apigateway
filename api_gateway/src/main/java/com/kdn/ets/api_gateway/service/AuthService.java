package com.kdn.ets.api_gateway.service;

import com.kdn.ets.api_gateway.entity.ApiKey;
import com.kdn.ets.api_gateway.entity.User;
import com.kdn.ets.api_gateway.exception.ApiException;
import com.kdn.ets.api_gateway.repository.ApiKeyRepository;
import com.kdn.ets.api_gateway.repository.ApiPermissionRepository; // 수정
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class AuthService {

    @Value("${app.api.salt}")
    private String apiSalt;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiPermissionRepository permissionRepository; // 수정

    public Mono<String> verifyAndGetUserId(ServerHttpRequest request, String apiId) {
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || authHeader.isEmpty()) {
            return Mono.error(new ApiException(401, "API Key 인증이 필요합니다. (Authorization 헤더 누락)"));
        }

        String hashedKey = hashWithSalt(authHeader);

        ApiKey apiKey = apiKeyRepository.findByApiKey(hashedKey)
                .orElseThrow(() -> new ApiException(401, "유효하지 않은 API 키입니다."));
        
        User user = apiKey.getUser();
        if (user == null) {
            throw new ApiException(403, "API 키에 연결된 사용자가 없습니다.");
        }

        if (!"Y".equalsIgnoreCase(user.getUseYn())) {
            throw new ApiException(403, "비활성화된 사용자 계정입니다.");
        }

        // --- 권한 확인 로직 변경 ---
        String userId = user.getUserId();
        
        if (!permissionRepository.existsById_UserIdAndId_ApiId(userId, apiId)) {
            throw new ApiException(403, "해당 API에 접근 권한이 없습니다.");
        }

        return Mono.just(userId);
    }

    private String hashWithSalt(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((apiSalt + key).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 Hashing Error", e);
        }
    }
}