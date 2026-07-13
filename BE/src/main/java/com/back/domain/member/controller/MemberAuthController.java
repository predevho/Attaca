package com.back.domain.member.controller;

import com.back.domain.member.dto.LoginRequest;
import com.back.domain.member.dto.OAuthLoginRequest;
import com.back.domain.member.dto.SignupRequest;
import com.back.domain.member.dto.SignupResponse;
import com.back.domain.member.dto.TokenPairResponse;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.service.MemberOAuthService;
import com.back.domain.member.service.MemberService;
import com.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 인증 엔드포인트(자체 가입/로그인).
 * 인증 없이 접근 가능해야 하므로 {@code /api/auth/**} 경로(SecurityConfig permitAll) 아래 둔다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private final MemberService memberService;
    private final MemberOAuthService memberOAuthService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@RequestBody SignupRequest request) {
        return ApiResponse.success(memberService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenPairResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(memberService.login(request));
    }

    @PostMapping("/oauth/kakao")
    public ApiResponse<TokenPairResponse> kakaoLogin(@RequestBody OAuthLoginRequest request) {
        return ApiResponse.success(
                memberOAuthService.oauthLogin(OAuthProvider.KAKAO, request.code(), request.redirectUri()));
    }
}
