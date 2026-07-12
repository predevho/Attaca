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
 * 회원 가입·로그인 애플리케이션 서비스.
 * 자체 가입은 email + password(해시 저장), 로그인 성공 시 JWT(access+refresh)를 발급한다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** 자체 회원가입. 이메일/닉네임 중복을 검증하고 비밀번호를 해시해 저장한다. */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = memberRepository.save(
                Member.create(request.email(), encodedPassword, request.nickname()));
        return SignupResponse.from(member);
    }

    /**
     * 자체 로그인. 이메일로 회원을 찾아 비밀번호를 검증하고 access+refresh 토큰을 발급한다.
     * 이메일 부재와 비밀번호 불일치는 정보 노출을 막기 위해 동일한 {@link ErrorCode#LOGIN_FAILED}로 처리한다.
     */
    @Transactional(readOnly = true)
    public TokenPairResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtProvider.createRefreshToken(member.getId(), member.getRole());
        return new TokenPairResponse(accessToken, refreshToken);
    }
}
