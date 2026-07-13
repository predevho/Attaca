package com.back.domain.member.service;

import com.back.domain.member.dto.LoginRequest;
import com.back.domain.member.dto.SignupRequest;
import com.back.domain.member.dto.SignupResponse;
import com.back.domain.member.dto.TokenPairResponse;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자체(loginId+password) 회원가입·로그인 서비스. 로그인 성공 시 JWT(access+refresh)를 발급한다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = memberRepository.save(
                Member.createLocal(request.loginId(), encodedPassword, request.email(), request.nickname()));
        return SignupResponse.from(member);
    }

    /**
     * loginId+password 로그인. 아이디 부재·비밀번호 없음(소셜 전용)·불일치는 모두 LOGIN_FAILED 로 통일.
     */
    @Transactional(readOnly = true)
    public TokenPairResponse login(LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (member.getPassword() == null
                || !passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String access = jwtProvider.createAccessToken(member.getId(), member.getRole());
        String refresh = jwtProvider.createRefreshToken(member.getId(), member.getRole());
        return new TokenPairResponse(access, refresh);
    }
}
