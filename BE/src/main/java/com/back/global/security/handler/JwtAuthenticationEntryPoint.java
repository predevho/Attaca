package com.back.global.security.handler;

import com.back.global.common.ApiResponse;
import com.back.global.exception.ErrorCode;
import com.back.global.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 인증 실패(401)를 ApiResponse JSON 으로 응답. 필터가 심은 ErrorCode 가 있으면 사유를 반영한다. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        if (errorCode == null) {
            errorCode = ErrorCode.UNAUTHORIZED;
        }
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }
}
