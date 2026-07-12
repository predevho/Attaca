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
    void signup_persistsMemberWithEncodedPasswordAndDefaultRole() {
        SignupResponse response = memberService.signup(
                new SignupRequest("user@attaca.com", "raw-password", "재즈맨"));

        assertThat(response.email()).isEqualTo("user@attaca.com");
        assertThat(response.nickname()).isEqualTo("재즈맨");
        assertThat(response.role()).isEqualTo(Role.USER);

        Member stored = memberRepository.findByEmail("user@attaca.com").orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo("raw-password");
        assertThat(passwordEncoder.matches("raw-password", stored.getPassword())).isTrue();
    }

    @Test
    void signup_duplicateEmail_throwsEmailAlreadyExists() {
        memberRepository.save(Member.create("dup@attaca.com", "x", "기존이름"));

        assertThatThrownBy(() -> memberService.signup(
                new SignupRequest("dup@attaca.com", "raw-password", "새이름")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void signup_duplicateNickname_throwsNicknameAlreadyExists() {
        memberRepository.save(Member.create("existing@attaca.com", "x", "중복닉네임"));

        assertThatThrownBy(() -> memberService.signup(
                new SignupRequest("new@attaca.com", "raw-password", "중복닉네임")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    @Test
    void login_validCredentials_returnsAccessAndRefreshTokens() {
        memberService.signup(new SignupRequest("login@attaca.com", "raw-password", "로그인유저"));

        TokenPairResponse tokens = memberService.login(
                new LoginRequest("login@attaca.com", "raw-password"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        Long memberId = memberRepository.findByEmail("login@attaca.com").orElseThrow().getId();
        assertThat(jwtProvider.parse(tokens.accessToken()).getSubject())
                .isEqualTo(String.valueOf(memberId));
        assertThat(jwtProvider.parse(tokens.accessToken()).get("type", String.class))
                .isEqualTo("access");
    }

    @Test
    void login_unknownEmail_throwsLoginFailed() {
        assertThatThrownBy(() -> memberService.login(
                new LoginRequest("nobody@attaca.com", "raw-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void login_wrongPassword_throwsLoginFailed() {
        memberService.signup(new SignupRequest("pw@attaca.com", "correct-password", "비번유저"));

        assertThatThrownBy(() -> memberService.login(
                new LoginRequest("pw@attaca.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }
}
