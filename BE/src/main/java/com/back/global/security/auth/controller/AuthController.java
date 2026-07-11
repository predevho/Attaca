package com.back.global.security.auth.controller;

import com.back.global.common.ApiResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.Role;
import com.back.global.security.auth.dto.ReissueRequest;
import com.back.global.security.auth.dto.TokenResponse;
import com.back.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 토큰 재발급 등 인증 인프라 엔드포인트. 무상태(서버 저장 없음). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtProvider jwtProvider;

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@RequestBody ReissueRequest request) {
        Claims claims;
        try {
            claims = jwtProvider.parse(request.refreshToken());
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.MALFORMED_TOKEN);
        }

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN_TYPE);
        }

        Long userId = Long.valueOf(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class).replace("ROLE_", ""));
        String newAccessToken = jwtProvider.createAccessToken(userId, role);
        return ApiResponse.success(new TokenResponse(newAccessToken));
    }
}
