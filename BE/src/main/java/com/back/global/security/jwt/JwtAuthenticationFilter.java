package com.back.global.security.jwt;

import com.back.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer access 토큰을 검증해 SecurityContext 에 인증을 세팅한다.
 * 검증 실패 시 사유 ErrorCode 를 request 속성에 심고(엔트리포인트가 사용) 컨텍스트는 비운 채 통과한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "errorCode";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = jwtProvider.parse(token);
                Authentication authentication = jwtProvider.getAuthentication(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.EXPIRED_TOKEN);
            } catch (SignatureException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.INVALID_SIGNATURE);
            } catch (MalformedJwtException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.MALFORMED_TOKEN);
            } catch (UnsupportedJwtException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.UNSUPPORTED_TOKEN);
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.MALFORMED_TOKEN);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith(BEARER_PREFIX)) {
            return bearer.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
