package com.back.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정값. application.yaml 의 jwt.* 에 바인딩된다.
 * secret 은 환경변수(JWT_SECRET)로 주입하며 저장소에 실제 값을 커밋하지 않는다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiry,
        long refreshTokenExpiry
) {
}
