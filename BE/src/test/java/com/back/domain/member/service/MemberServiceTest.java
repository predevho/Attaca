package com.back.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.dto.LoginRequest;
import com.back.domain.member.dto.SignupRequest;
import com.back.domain.member.dto.SignupResponse;
import com.back.domain.member.dto.TokenPairResponse;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.Role;
import com.back.global.security.jwt.JwtProperties;
import com.back.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DataJpaTest
class MemberServiceTest {

    @Autowired
    private MemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtProvider jwtProvider = new JwtProvider(new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256-0123456789", 1800000L, 1209600000L));
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void signup_persistsWithEncodedPassword() {
        SignupResponse res = memberService.signup(
                new SignupRequest("jazzman", "raw-pw", "user@attaca.com", "재즈맨"));

        assertThat(res.loginId()).isEqualTo("jazzman");
        assertThat(res.role()).isEqualTo(Role.USER);
        Member stored = memberRepository.findByLoginId("jazzman").orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo("raw-pw");
        assertThat(passwordEncoder.matches("raw-pw", stored.getPassword())).isTrue();
    }

    @Test
    void signup_duplicateLoginId_throws() {
        memberRepository.save(Member.createLocal("dup", "x", "a@attaca.com", "닉A"));
        assertThatThrownBy(() -> memberService.signup(
                new SignupRequest("dup", "raw-pw", "b@attaca.com", "닉B")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
    }

    @Test
    void signup_duplicateEmail_throws() {
        memberRepository.save(Member.createLocal("id1", "x", "dup@attaca.com", "닉A"));
        assertThatThrownBy(() -> memberService.signup(
                new SignupRequest("id2", "raw-pw", "dup@attaca.com", "닉B")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void signup_duplicateNickname_throws() {
        memberRepository.save(Member.createLocal("id1", "x", "a@attaca.com", "중복닉"));
        assertThatThrownBy(() -> memberService.signup(
                new SignupRequest("id2", "raw-pw", "b@attaca.com", "중복닉")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    @Test
    void login_validCredentials_returnsTokens() {
        memberService.signup(new SignupRequest("jazzman", "raw-pw", "user@attaca.com", "재즈맨"));

        TokenPairResponse tokens = memberService.login(new LoginRequest("jazzman", "raw-pw"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(jwtProvider.parse(tokens.accessToken()).get("type", String.class)).isEqualTo("access");
    }

    @Test
    void login_unknownLoginId_throwsLoginFailed() {
        assertThatThrownBy(() -> memberService.login(new LoginRequest("nobody", "raw-pw")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void login_socialOnlyMemberWithNullPassword_throwsLoginFailed() {
        memberRepository.save(Member.createSocial("social@attaca.com", "소셜러"));
        // 소셜 전용 회원은 loginId 가 없으므로 loginId 로 로그인 시도 자체가 실패
        assertThatThrownBy(() -> memberService.login(new LoginRequest("social@attaca.com", "raw-pw")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void login_wrongPassword_throwsLoginFailed() {
        memberService.signup(new SignupRequest("jazzman", "correct-pw", "user@attaca.com", "재즈맨"));
        assertThatThrownBy(() -> memberService.login(new LoginRequest("jazzman", "wrong-pw")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
    }
}
