# MEMBER 카카오 소셜 로그인 + loginId 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 자체 로그인을 `loginId` 기반으로 전환하고 카카오 소셜 로그인(프론트 인가코드 + 백엔드 코드교환)을 추가한다. 모든 인증은 동일한 `JwtProvider` access+refresh 발급으로 수렴한다.

**Architecture:** `com.back.domain.member` 도메인에 `Member`(loginId/email/nickname 역할 분리) + `SocialAccount`를 두고, 자체 인증은 `MemberService`, 소셜 인증은 `MemberOAuthService`가 담당한다. provider 호출은 `OAuthClient` 인터페이스로 추상화하여 테스트는 Fake로 대체하고 실제 카카오 HTTP는 `KakaoOAuthClient`가 캡슐화한다.

**Tech Stack:** Spring Boot 3.4.5 / Java 21 / Spring Security(무상태 JWT) / Spring Data JPA / RestClient / JUnit5 · H2(test)

## Global Constraints

- Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21 toolchain — 래퍼·버전 올리지 말 것
- 빌드/테스트: `./gradlew clean build` (Windows: `.\gradlew.bat`) — 전체 통과가 완료 기준
- 단순 접근자는 Lombok `@Getter` 사용(수동 getter 금지)
- 응답은 `ApiResponse<T>` 래퍼, 에러는 전역 `ErrorCode` enum에 추가(도메인 전용 enum 분리 보류 유지)
- `resultCode` 형식: `HTTP상태-일련번호` (예 409-03, 401-08, 502-01)
- 비밀번호는 BCrypt 해시로만 저장(평문 금지). `jwt.secret`·카카오 `client-secret`은 env 주입, 커밋 금지
- 커밋 메시지는 한글. 제목 `유형: 요약`. 커밋은 관심사 단위로 분리
- TDD: 프로덕션 코드 전에 실패 테스트부터. mock은 외부 HTTP 경계에서만(내부 로직은 Fake/실물)
- 인증 엔드포인트는 모두 `/api/auth/**`(SecurityConfig permitAll) 아래 → SecurityConfig 변경 없음

---

## 파일 구조

**신규(main)**
- `domain/member/entity/SocialAccount.java` — 소셜 계정 연결 엔티티
- `domain/member/entity/OAuthProvider.java` — provider enum(KAKAO)
- `domain/member/repository/SocialAccountRepository.java`
- `domain/member/dto/OAuthLoginRequest.java` — `{code, redirectUri}`
- `domain/member/oauth/OAuthClient.java` — provider 추상화 인터페이스
- `domain/member/oauth/OAuthUserInfo.java` — provider 유저정보 표준형(record)
- `domain/member/oauth/KakaoOAuthClient.java` — 카카오 HTTP 구현
- `domain/member/oauth/OAuthProperties.java` — 카카오 설정(@ConfigurationProperties)
- `domain/member/service/MemberOAuthService.java` — 소셜 로그인/자동가입/자동연결

**수정(main)**
- `domain/member/entity/Member.java` — loginId 추가, password/loginId nullable, createLocal/createSocial
- `domain/member/repository/MemberRepository.java` — existsByLoginId/findByLoginId 추가
- `domain/member/dto/SignupRequest.java` — `{loginId, password, email, nickname}`
- `domain/member/dto/SignupResponse.java` — loginId 포함
- `domain/member/dto/LoginRequest.java` — `{loginId, password}`
- `domain/member/service/MemberService.java` — loginId 기반 signup/login
- `domain/member/controller/MemberAuthController.java` — `/oauth/kakao` 추가
- `global/exception/ErrorCode.java` — 코드 3종 추가
- `src/main/resources/application.yaml` — oauth.kakao 설정

**테스트**: 각 대응 테스트 + `MemberAuthControllerOAuthTest`, `KakaoOAuthClientTest`, `MemberOAuthServiceTest`

---

## Task 1: ErrorCode 3종 추가

**Files:**
- Modify: `BE/src/main/java/com/back/global/exception/ErrorCode.java`
- Test: `BE/src/test/java/com/back/global/exception/ErrorCodeTest.java`

**Interfaces:**
- Produces: `ErrorCode.LOGIN_ID_ALREADY_EXISTS`(409-03/CONFLICT), `ErrorCode.OAUTH_EMAIL_UNVERIFIED`(401-08/UNAUTHORIZED), `ErrorCode.OAUTH_PROVIDER_ERROR`(502-01/BAD_GATEWAY)

- [ ] **Step 1: 실패 테스트 추가** — `ErrorCodeTest.java`에 메서드 추가

```java
    @Test
    void oauthAndLoginIdErrorCodes_haveExpectedResultCodeAndStatus() {
        assertThat(ErrorCode.LOGIN_ID_ALREADY_EXISTS.getResultCode()).isEqualTo("409-03");
        assertThat(ErrorCode.LOGIN_ID_ALREADY_EXISTS.getStatus()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(ErrorCode.OAUTH_EMAIL_UNVERIFIED.getResultCode()).isEqualTo("401-08");
        assertThat(ErrorCode.OAUTH_EMAIL_UNVERIFIED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(ErrorCode.OAUTH_PROVIDER_ERROR.getResultCode()).isEqualTo("502-01");
        assertThat(ErrorCode.OAUTH_PROVIDER_ERROR.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `.\gradlew.bat test --tests "com.back.global.exception.ErrorCodeTest" --console=plain`
Expected: FAIL — `cannot find symbol LOGIN_ID_ALREADY_EXISTS`

- [ ] **Step 3: 상수 추가** — `ErrorCode.java`의 MEMBER 섹션에 추가(기존 EMAIL/NICKNAME 뒤)

```java
    LOGIN_ID_ALREADY_EXISTS("409-03", HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),

    // --- OAuth(소셜) ---
    OAUTH_EMAIL_UNVERIFIED("401-08", HttpStatus.UNAUTHORIZED, "소셜 계정의 이메일이 확인되지 않았습니다."),
    OAUTH_PROVIDER_ERROR("502-01", HttpStatus.BAD_GATEWAY, "소셜 로그인 제공자 연동에 실패했습니다.");
```
(직전 상수 `NICKNAME_ALREADY_EXISTS(...)`의 끝 세미콜론을 콤마로 바꾸고 위 항목 추가)

- [ ] **Step 4: 통과 확인**

Run: `.\gradlew.bat test --tests "com.back.global.exception.ErrorCodeTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add BE/src/main/java/com/back/global/exception/ErrorCode.java BE/src/test/java/com/back/global/exception/ErrorCodeTest.java
git commit -m "feat: MEMBER/OAuth 에러코드 추가(409-03, 401-08, 502-01)"
```

---

## Task 2: 자체 인증 loginId 전환

기존 email 기반 자체 회원가입/로그인을 loginId 기반으로 개정한다. 한 태스크로 묶어 빌드 그린을 유지한다.

**Files:**
- Modify: `domain/member/entity/Member.java`, `domain/member/repository/MemberRepository.java`,
  `domain/member/dto/SignupRequest.java`, `domain/member/dto/SignupResponse.java`,
  `domain/member/dto/LoginRequest.java`, `domain/member/service/MemberService.java`
- Test: `domain/member/entity/MemberTest.java`, `domain/member/repository/MemberRepositoryTest.java`,
  `domain/member/service/MemberServiceTest.java`, `domain/member/controller/MemberAuthControllerTest.java`

**Interfaces:**
- Produces:
  - `Member.createLocal(String loginId, String encodedPassword, String email, String nickname)` → USER
  - `Member.createSocial(String email, String nickname)` → loginId=null, password=null, USER
  - `Member.getLoginId()/getPassword()/getEmail()/getNickname()/getRole()/getId()`
  - `MemberRepository`: `existsByLoginId(String)`, `findByLoginId(String)`, `existsByEmail(String)`, `findByEmail(String)`, `existsByNickname(String)`
  - `SignupRequest(String loginId, String password, String email, String nickname)`
  - `SignupResponse(Long id, String loginId, String email, String nickname, Role role)` + `from(Member)`
  - `LoginRequest(String loginId, String password)`
  - `MemberService.signup(SignupRequest) → SignupResponse`, `MemberService.login(LoginRequest) → TokenPairResponse`

- [ ] **Step 1: Member 테스트 개정** — `MemberTest.java` 전체 교체

```java
package com.back.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.global.config.JpaAuditingConfig;
import com.back.global.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class MemberTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void createLocal_populatesFieldsWithRoleUser() {
        Member saved = entityManager.persistFlushFind(
                Member.createLocal("jazzman", "encoded-pw", "user@attaca.com", "재즈맨"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLoginId()).isEqualTo("jazzman");
        assertThat(saved.getPassword()).isEqualTo("encoded-pw");
        assertThat(saved.getEmail()).isEqualTo("user@attaca.com");
        assertThat(saved.getNickname()).isEqualTo("재즈맨");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createSocial_hasNullLoginIdAndPassword() {
        Member saved = entityManager.persistFlushFind(
                Member.createSocial("social@attaca.com", "소셜러"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLoginId()).isNull();
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getEmail()).isEqualTo("social@attaca.com");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.entity.MemberTest" --console=plain`
Expected: FAIL — `cannot find symbol createLocal/createSocial`

- [ ] **Step 3: Member 엔티티 개정** — `Member.java` 전체 교체

```java
package com.back.domain.member.entity;

import com.back.global.common.BaseEntity;
import com.back.global.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 엔티티. 자체 로그인(loginId+password)과 소셜 로그인이 하나의 회원으로 수렴한다.
 * loginId/password 는 소셜 전용 회원에서는 null 이며, email/nickname 은 전원 필수.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 자체 로그인 아이디. 소셜 전용 회원은 null. */
    @Column(unique = true)
    private String loginId;

    /** 해시된 비밀번호. 소셜 전용 회원은 null. 평문 저장 금지. */
    private String password;

    /** 인증메일 발송·연락용 + 소셜 자동연결 매칭 키. 전원 필수. */
    @Column(nullable = false, unique = true)
    private String email;

    /** 웹 내 활동 표시명. */
    @Column(nullable = false, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private Member(String loginId, String password, String email, String nickname, Role role) {
        this.loginId = loginId;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    /** 자체 가입 회원 생성. encodedPassword = 이미 해시된 비밀번호. */
    public static Member createLocal(String loginId, String encodedPassword, String email, String nickname) {
        return new Member(loginId, encodedPassword, email, nickname, Role.USER);
    }

    /** 소셜 전용 회원 생성. loginId/password 없음. */
    public static Member createSocial(String email, String nickname) {
        return new Member(null, null, email, nickname, Role.USER);
    }
}
```

- [ ] **Step 4: MemberTest 통과 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.entity.MemberTest" --console=plain`
Expected: PASS

- [ ] **Step 5: Repository 테스트 개정** — `MemberRepositoryTest.java` 전체 교체

```java
package com.back.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.save(Member.createLocal("jazzman", "pw", "user@attaca.com", "재즈맨"));
    }

    @Test
    void existsByLoginId() {
        assertThat(memberRepository.existsByLoginId("jazzman")).isTrue();
        assertThat(memberRepository.existsByLoginId("none")).isFalse();
    }

    @Test
    void findByLoginId() {
        assertThat(memberRepository.findByLoginId("jazzman"))
                .get().extracting(Member::getEmail).isEqualTo("user@attaca.com");
        assertThat(memberRepository.findByLoginId("none")).isEmpty();
    }

    @Test
    void existsByEmailAndNickname() {
        assertThat(memberRepository.existsByEmail("user@attaca.com")).isTrue();
        assertThat(memberRepository.existsByNickname("재즈맨")).isTrue();
        assertThat(memberRepository.findByEmail("user@attaca.com")).isPresent();
    }
}
```

- [ ] **Step 6: Repository 개정** — `MemberRepository.java` 전체 교체

```java
package com.back.domain.member.repository;

import com.back.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByEmail(String email);
}
```

- [ ] **Step 7: DTO 3종 개정**

`SignupRequest.java`:
```java
package com.back.domain.member.dto;

/** 자체 회원가입 요청. */
public record SignupRequest(String loginId, String password, String email, String nickname) {
}
```

`SignupResponse.java`:
```java
package com.back.domain.member.dto;

import com.back.domain.member.entity.Member;
import com.back.global.security.Role;

/** 회원가입 결과. 비밀번호 등 민감 정보는 노출하지 않는다. */
public record SignupResponse(Long id, String loginId, String email, String nickname, Role role) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getLoginId(),
                member.getEmail(), member.getNickname(), member.getRole());
    }
}
```

`LoginRequest.java`:
```java
package com.back.domain.member.dto;

/** 자체 로그인 요청. */
public record LoginRequest(String loginId, String password) {
}
```

- [ ] **Step 8: MemberService 테스트 개정** — `MemberServiceTest.java` 전체 교체

```java
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
```

- [ ] **Step 9: MemberService 개정** — `MemberService.java` 전체 교체

```java
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
```

- [ ] **Step 10: 컨트롤러 테스트 개정** — `MemberAuthControllerTest.java`의 요청 DTO 사용부를 loginId 기반으로 교체

기존 각 `new SignupRequest("...email...", "raw-password", "닉")` → `new SignupRequest("<loginId>", "raw-password", "<email>", "<nickname>")`,
`new LoginRequest("...email...", "...")` → `new LoginRequest("<loginId>", "...")`, jsonPath `$.data.email` 검증에 `$.data.loginId` 추가. 예시:

```java
    @Test
    void signup_returns200WithMemberData() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("newbie", "raw-password", "new@attaca.com", "새회원"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loginId").value("newbie"))
                .andExpect(jsonPath("$.data.nickname").value("새회원"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void signup_duplicateLoginId_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("dup", "raw-password", "a@attaca.com", "닉A"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("dup", "raw-password", "b@attaca.com", "닉B"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.resultCode").value("409-03"));
    }

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("loginuser", "raw-password", "login@attaca.com", "로그인유저"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("loginuser", "raw-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("pwuser", "correct-password", "pw@attaca.com", "비번유저"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("pwuser", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-07"));
    }
```

- [ ] **Step 11: 관련 테스트 전체 통과 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.*" --console=plain`
Expected: PASS (Member/Repository/Service/Controller 전부)

- [ ] **Step 12: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/ BE/src/test/java/com/back/domain/member/
git commit -m "refactor: 자체 인증을 loginId 기반으로 전환 (email→loginId)"
```

---

## Task 3: SocialAccount 엔티티 + OAuthProvider + Repository

**Files:**
- Create: `domain/member/entity/OAuthProvider.java`, `domain/member/entity/SocialAccount.java`,
  `domain/member/repository/SocialAccountRepository.java`
- Test: `domain/member/repository/SocialAccountRepositoryTest.java`

**Interfaces:**
- Produces:
  - `enum OAuthProvider { KAKAO }`
  - `SocialAccount.create(Member member, OAuthProvider provider, String providerUserId)`; getters `getId/getMember/getProvider/getProviderUserId`
  - `SocialAccountRepository.findByProviderAndProviderUserId(OAuthProvider, String) → Optional<SocialAccount>`

- [ ] **Step 1: Repository 실패 테스트 작성** — `SocialAccountRepositoryTest.java`

```java
package com.back.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.entity.SocialAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class SocialAccountRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Test
    void findByProviderAndProviderUserId() {
        Member member = memberRepository.save(Member.createSocial("s@attaca.com", "소셜러"));
        socialAccountRepository.save(SocialAccount.create(member, OAuthProvider.KAKAO, "kakao-123"));

        assertThat(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "kakao-123"))
                .get().extracting(sa -> sa.getMember().getId()).isEqualTo(member.getId());
        assertThat(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "none"))
                .isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.repository.SocialAccountRepositoryTest" --console=plain`
Expected: FAIL — `OAuthProvider`/`SocialAccount`/`SocialAccountRepository` 없음

- [ ] **Step 3: OAuthProvider enum 생성**

```java
package com.back.domain.member.entity;

/** 소셜 로그인 제공자. 우선 카카오만 지원하며 이후 GOOGLE 등 추가한다. */
public enum OAuthProvider {
    KAKAO
}
```

- [ ] **Step 4: SocialAccount 엔티티 생성**

```java
package com.back.domain.member.entity;

import com.back.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원에 연결된 소셜 계정. (provider, providerUserId) 조합이 유일하다. */
@Entity
@Table(name = "social_account",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    private SocialAccount(Member member, OAuthProvider provider, String providerUserId) {
        this.member = member;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static SocialAccount create(Member member, OAuthProvider provider, String providerUserId) {
        return new SocialAccount(member, provider, providerUserId);
    }
}
```

- [ ] **Step 5: SocialAccountRepository 생성**

```java
package com.back.domain.member.repository;

import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.entity.SocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
```

- [ ] **Step 6: 통과 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.repository.SocialAccountRepositoryTest" --console=plain`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/entity/OAuthProvider.java BE/src/main/java/com/back/domain/member/entity/SocialAccount.java BE/src/main/java/com/back/domain/member/repository/SocialAccountRepository.java BE/src/test/java/com/back/domain/member/repository/SocialAccountRepositoryTest.java
git commit -m "feat: SocialAccount 엔티티/리포지토리 및 OAuthProvider 추가"
```

---

## Task 4: OAuthClient 추상화 + MemberOAuthService (소셜 로그인/자동가입/자동연결)

**Files:**
- Create: `domain/member/oauth/OAuthUserInfo.java`, `domain/member/oauth/OAuthClient.java`,
  `domain/member/service/MemberOAuthService.java`
- Test: `domain/member/service/MemberOAuthServiceTest.java` (내부에 FakeOAuthClient 정의)

**Interfaces:**
- Consumes: `Member.createSocial`, `MemberRepository.findByEmail/existsByNickname`,
  `SocialAccountRepository`, `JwtProvider`, `TokenPairResponse`, `OAuthProvider`,
  `ErrorCode.OAUTH_EMAIL_UNVERIFIED/OAUTH_PROVIDER_ERROR`
- Produces:
  - `record OAuthUserInfo(String providerUserId, String email, boolean emailVerified, String nickname)`
  - `interface OAuthClient { OAuthProvider provider(); OAuthUserInfo fetch(String code, String redirectUri); }`
  - `MemberOAuthService(MemberRepository, SocialAccountRepository, JwtProvider, List<OAuthClient>)`
  - `MemberOAuthService.oauthLogin(OAuthProvider provider, String code, String redirectUri) → TokenPairResponse`

- [ ] **Step 1: OAuthUserInfo / OAuthClient 인터페이스 생성** (테스트 컴파일에 필요)

`OAuthUserInfo.java`:
```java
package com.back.domain.member.oauth;

/** provider 별 유저정보를 통일한 표준형. */
public record OAuthUserInfo(String providerUserId, String email, boolean emailVerified, String nickname) {
}
```

`OAuthClient.java`:
```java
package com.back.domain.member.oauth;

import com.back.domain.member.entity.OAuthProvider;

/** 소셜 provider 연동 추상화. 인가코드 교환~유저정보 조회를 캡슐화한다. */
public interface OAuthClient {

    OAuthProvider provider();

    OAuthUserInfo fetch(String code, String redirectUri);
}
```

- [ ] **Step 2: 실패 테스트 작성** — `MemberOAuthServiceTest.java`

```java
package com.back.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.dto.TokenPairResponse;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.entity.SocialAccount;
import com.back.domain.member.oauth.OAuthClient;
import com.back.domain.member.oauth.OAuthUserInfo;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.repository.SocialAccountRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.jwt.JwtProperties;
import com.back.global.security.jwt.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class MemberOAuthServiceTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;

    private final JwtProvider jwtProvider = new JwtProvider(new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256-0123456789", 1800000L, 1209600000L));
    private final FakeOAuthClient fakeClient = new FakeOAuthClient();
    private MemberOAuthService service;

    @BeforeEach
    void setUp() {
        service = new MemberOAuthService(memberRepository, socialAccountRepository, jwtProvider,
                List.of(fakeClient));
    }

    @Test
    void newSocialUser_isCreatedAndLoggedIn() {
        fakeClient.next = new OAuthUserInfo("kakao-1", "new@attaca.com", true, "카카오유저");

        TokenPairResponse tokens = service.oauthLogin(OAuthProvider.KAKAO, "code", "https://app/cb");

        assertThat(tokens.accessToken()).isNotBlank();
        Member created = memberRepository.findByEmail("new@attaca.com").orElseThrow();
        assertThat(created.getLoginId()).isNull();
        assertThat(created.getPassword()).isNull();
        assertThat(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "kakao-1"))
                .isPresent();
    }

    @Test
    void existingSocialAccount_logsInSameMember() {
        Member member = memberRepository.save(Member.createSocial("s@attaca.com", "기존소셜"));
        socialAccountRepository.save(SocialAccount.create(member, OAuthProvider.KAKAO, "kakao-1"));
        fakeClient.next = new OAuthUserInfo("kakao-1", "s@attaca.com", true, "기존소셜");

        service.oauthLogin(OAuthProvider.KAKAO, "code", "https://app/cb");

        assertThat(socialAccountRepository.findAll()).hasSize(1);
        assertThat(memberRepository.findAll()).hasSize(1);
    }

    @Test
    void verifiedEmailMatchingExistingMember_autoLinks() {
        Member local = memberRepository.save(
                Member.createLocal("jazzman", "pw", "same@attaca.com", "재즈맨"));
        fakeClient.next = new OAuthUserInfo("kakao-9", "same@attaca.com", true, "재즈맨");

        service.oauthLogin(OAuthProvider.KAKAO, "code", "https://app/cb");

        assertThat(memberRepository.findAll()).hasSize(1);
        SocialAccount linked = socialAccountRepository
                .findByProviderAndProviderUserId(OAuthProvider.KAKAO, "kakao-9").orElseThrow();
        assertThat(linked.getMember().getId()).isEqualTo(local.getId());
    }

    @Test
    void unverifiedEmail_throwsOauthEmailUnverified() {
        fakeClient.next = new OAuthUserInfo("kakao-2", "x@attaca.com", false, "미검증");

        assertThatThrownBy(() -> service.oauthLogin(OAuthProvider.KAKAO, "code", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OAUTH_EMAIL_UNVERIFIED);
    }

    @Test
    void newSocialUser_nicknameCollision_generatesUnique() {
        memberRepository.save(Member.createLocal("id1", "pw", "a@attaca.com", "중복닉"));
        fakeClient.next = new OAuthUserInfo("kakao-3", "b@attaca.com", true, "중복닉");

        service.oauthLogin(OAuthProvider.KAKAO, "code", "https://app/cb");

        Member created = memberRepository.findByEmail("b@attaca.com").orElseThrow();
        assertThat(created.getNickname()).isNotEqualTo("중복닉");
        assertThat(created.getNickname()).startsWith("중복닉");
    }

    /** 외부 HTTP 없이 정해진 OAuthUserInfo 를 반환하는 테스트 더블. */
    static class FakeOAuthClient implements OAuthClient {
        OAuthUserInfo next;

        @Override
        public OAuthProvider provider() {
            return OAuthProvider.KAKAO;
        }

        @Override
        public OAuthUserInfo fetch(String code, String redirectUri) {
            return next;
        }
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.service.MemberOAuthServiceTest" --console=plain`
Expected: FAIL — `MemberOAuthService` 없음

- [ ] **Step 4: MemberOAuthService 구현**

```java
package com.back.domain.member.service;

import com.back.domain.member.dto.TokenPairResponse;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.entity.SocialAccount;
import com.back.domain.member.oauth.OAuthClient;
import com.back.domain.member.oauth.OAuthUserInfo;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.repository.SocialAccountRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.jwt.JwtProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜(OAuth2) 로그인. provider 인가코드를 검증(OAuthClient)해 얻은 유저정보로
 * 기존 로그인 / 이메일 자동연결 / 신규 자동가입을 수행하고 우리 JWT 를 발급한다.
 */
@Service
public class MemberOAuthService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final JwtProvider jwtProvider;
    private final List<OAuthClient> oauthClients;

    public MemberOAuthService(MemberRepository memberRepository,
                              SocialAccountRepository socialAccountRepository,
                              JwtProvider jwtProvider,
                              List<OAuthClient> oauthClients) {
        this.memberRepository = memberRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.jwtProvider = jwtProvider;
        this.oauthClients = oauthClients;
    }

    @Transactional
    public TokenPairResponse oauthLogin(OAuthProvider provider, String code, String redirectUri) {
        OAuthUserInfo info = resolveClient(provider).fetch(code, redirectUri);

        Member member = socialAccountRepository
                .findByProviderAndProviderUserId(provider, info.providerUserId())
                .map(SocialAccount::getMember)
                .orElseGet(() -> linkOrCreate(provider, info));

        String access = jwtProvider.createAccessToken(member.getId(), member.getRole());
        String refresh = jwtProvider.createRefreshToken(member.getId(), member.getRole());
        return new TokenPairResponse(access, refresh);
    }

    private OAuthClient resolveClient(OAuthProvider provider) {
        return oauthClients.stream()
                .filter(c -> c.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR));
    }

    /** SocialAccount 미존재 시: 검증된 이메일로 기존 회원 연결, 없으면 신규 소셜 회원 생성. */
    private Member linkOrCreate(OAuthProvider provider, OAuthUserInfo info) {
        if (!info.emailVerified() || info.email() == null || info.email().isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_EMAIL_UNVERIFIED);
        }

        Member member = memberRepository.findByEmail(info.email())
                .orElseGet(() -> memberRepository.save(
                        Member.createSocial(info.email(), uniqueNickname(info.nickname()))));

        socialAccountRepository.save(SocialAccount.create(member, provider, info.providerUserId()));
        return member;
    }

    /** 닉네임 충돌 시 짧은 랜덤 접미사로 유니크 값을 만든다. */
    private String uniqueNickname(String base) {
        String seed = (base == null || base.isBlank()) ? "user" : base;
        String candidate = seed;
        while (memberRepository.existsByNickname(candidate)) {
            candidate = seed + "_" + UUID.randomUUID().toString().substring(0, 4);
        }
        return candidate;
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.service.MemberOAuthServiceTest" --console=plain`
Expected: PASS (5개)

- [ ] **Step 6: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/oauth/ BE/src/main/java/com/back/domain/member/service/MemberOAuthService.java BE/src/test/java/com/back/domain/member/service/MemberOAuthServiceTest.java
git commit -m "feat: 소셜 로그인 서비스(자동가입/자동연결) 및 OAuthClient 추상화"
```

---

## Task 5: KakaoOAuthClient + OAuthProperties + 설정

카카오 실제 HTTP 연동. 네트워크 없는 응답 매핑(`toUserInfo`)만 단위 테스트하고, 실제 호출은 운영에서 env 키로 수동 검증한다.

**Files:**
- Create: `domain/member/oauth/OAuthProperties.java`, `domain/member/oauth/KakaoOAuthClient.java`
- Modify: `src/main/resources/application.yaml`
- Test: `domain/member/oauth/KakaoOAuthClientTest.java`

**Interfaces:**
- Consumes: `OAuthClient`, `OAuthUserInfo`, `OAuthProvider`, `ErrorCode.OAUTH_PROVIDER_ERROR`
- Produces: `KakaoOAuthClient implements OAuthClient`(provider=KAKAO), 내부 `static OAuthUserInfo toUserInfo(KakaoUserResponse)`; `OAuthProperties(clientId, clientSecret, tokenUri, userInfoUri)`

- [ ] **Step 1: 응답 매핑 실패 테스트 작성** — `KakaoOAuthClientTest.java`

```java
package com.back.domain.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.oauth.KakaoOAuthClient.KakaoAccount;
import com.back.domain.member.oauth.KakaoOAuthClient.KakaoProfile;
import com.back.domain.member.oauth.KakaoOAuthClient.KakaoUserResponse;
import org.junit.jupiter.api.Test;

class KakaoOAuthClientTest {

    @Test
    void toUserInfo_mapsVerifiedEmailAndNickname() {
        KakaoUserResponse res = new KakaoUserResponse(1234L,
                new KakaoAccount("user@kakao.com", true, new KakaoProfile("카카오닉")));

        OAuthUserInfo info = KakaoOAuthClient.toUserInfo(res);

        assertThat(info.providerUserId()).isEqualTo("1234");
        assertThat(info.email()).isEqualTo("user@kakao.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.nickname()).isEqualTo("카카오닉");
    }

    @Test
    void toUserInfo_handlesMissingAccount() {
        OAuthUserInfo info = KakaoOAuthClient.toUserInfo(new KakaoUserResponse(9L, null));

        assertThat(info.providerUserId()).isEqualTo("9");
        assertThat(info.email()).isNull();
        assertThat(info.emailVerified()).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.oauth.KakaoOAuthClientTest" --console=plain`
Expected: FAIL — `KakaoOAuthClient` 없음

- [ ] **Step 3: OAuthProperties 생성**

```java
package com.back.domain.member.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 카카오 OAuth 설정. client-secret 은 env 주입(커밋 금지). */
@ConfigurationProperties(prefix = "oauth.kakao")
public record OAuthProperties(String clientId, String clientSecret, String tokenUri, String userInfoUri) {
}
```

- [ ] **Step 4: KakaoOAuthClient 생성**

```java
package com.back.domain.member.oauth;

import com.back.domain.member.entity.OAuthProvider;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 인가코드 → 토큰 → 유저정보 조회를 캡슐화한다.
 * 네트워크 호출 실패는 OAUTH_PROVIDER_ERROR 로 변환한다.
 */
@Component
public class KakaoOAuthClient implements OAuthClient {

    private final OAuthProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo fetch(String code, String redirectUri) {
        try {
            String accessToken = requestAccessToken(code, redirectUri);
            KakaoUserResponse user = requestUser(accessToken);
            return toUserInfo(user);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        KakaoTokenResponse token = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);

        if (token == null || token.accessToken() == null) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        return token.accessToken();
    }

    private KakaoUserResponse requestUser(String accessToken) {
        return restClient.get()
                .uri(properties.userInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserResponse.class);
    }

    /** 카카오 유저 응답을 표준 OAuthUserInfo 로 변환. (단위 테스트 대상) */
    static OAuthUserInfo toUserInfo(KakaoUserResponse res) {
        KakaoAccount account = res.kakaoAccount();
        String email = account == null ? null : account.email();
        boolean verified = account != null && Boolean.TRUE.equals(account.isEmailVerified());
        String nickname = (account != null && account.profile() != null)
                ? account.profile().nickname() : null;
        return new OAuthUserInfo(String.valueOf(res.id()), email, verified, nickname);
    }

    // --- 카카오 응답 매핑 DTO (package-private) ---

    record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    record KakaoUserResponse(@JsonProperty("id") Long id,
                             @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    record KakaoAccount(@JsonProperty("email") String email,
                        @JsonProperty("is_email_verified") Boolean isEmailVerified,
                        @JsonProperty("profile") KakaoProfile profile) {
    }

    record KakaoProfile(@JsonProperty("nickname") String nickname) {
    }
}
```

- [ ] **Step 5: application.yaml 에 카카오 설정 추가** — `jwt:` 블록 아래에 추가

```yaml
oauth:
  kakao:
    client-id: ${KAKAO_CLIENT_ID:}
    client-secret: ${KAKAO_CLIENT_SECRET:}
    token-uri: https://kauth.kakao.com/oauth/token
    user-info-uri: https://kapi.kakao.com/v2/user/me
```

- [ ] **Step 6: 통과 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.oauth.KakaoOAuthClientTest" --console=plain`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/oauth/OAuthProperties.java BE/src/main/java/com/back/domain/member/oauth/KakaoOAuthClient.java BE/src/main/resources/application.yaml BE/src/test/java/com/back/domain/member/oauth/KakaoOAuthClientTest.java
git commit -m "feat: KakaoOAuthClient(HTTP) 및 oauth.kakao 설정 추가"
```

---

## Task 6: 소셜 로그인 엔드포인트 + 통합 테스트

**Files:**
- Create: `domain/member/dto/OAuthLoginRequest.java`,
  `domain/member/controller/MemberAuthControllerOAuthTest.java`
- Modify: `domain/member/controller/MemberAuthController.java`

**Interfaces:**
- Consumes: `MemberOAuthService.oauthLogin`, `OAuthProvider.KAKAO`, `TokenPairResponse`
- Produces: `OAuthLoginRequest(String code, String redirectUri)`; `POST /api/auth/oauth/kakao`

- [ ] **Step 1: OAuthLoginRequest 생성**

```java
package com.back.domain.member.dto;

/** 소셜 로그인 요청. 프론트가 provider 로부터 받은 1회용 인가코드를 전달한다. */
public record OAuthLoginRequest(String code, String redirectUri) {
}
```

- [ ] **Step 2: 통합 실패 테스트 작성** — `MemberAuthControllerOAuthTest.java` (외부 HTTP 경계인 KakaoOAuthClient 를 @MockitoBean 으로 대체)

```java
package com.back.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.member.dto.OAuthLoginRequest;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.oauth.KakaoOAuthClient;
import com.back.domain.member.oauth.OAuthUserInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberAuthControllerOAuthTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        when(kakaoOAuthClient.provider()).thenReturn(OAuthProvider.KAKAO);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void kakaoLogin_newVerifiedUser_returns200WithTokens() throws Exception {
        when(kakaoOAuthClient.fetch(any(), any()))
                .thenReturn(new OAuthUserInfo("kakao-1", "new@attaca.com", true, "카카오유저"));

        mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OAuthLoginRequest("auth-code", "https://app/cb"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void kakaoLogin_unverifiedEmail_returns401() throws Exception {
        when(kakaoOAuthClient.fetch(any(), any()))
                .thenReturn(new OAuthUserInfo("kakao-2", "x@attaca.com", false, "미검증"));

        mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OAuthLoginRequest("auth-code", "https://app/cb"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-08"));
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.controller.MemberAuthControllerOAuthTest" --console=plain`
Expected: FAIL — `/api/auth/oauth/kakao` 매핑 없음 (404)

- [ ] **Step 4: 컨트롤러에 소셜 엔드포인트 추가** — `MemberAuthController.java` 수정

import 추가:
```java
import com.back.domain.member.dto.OAuthLoginRequest;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.service.MemberOAuthService;
```

필드 추가(`MemberService` 옆):
```java
    private final MemberOAuthService memberOAuthService;
```

메서드 추가(`login` 아래):
```java
    @PostMapping("/oauth/kakao")
    public ApiResponse<TokenPairResponse> kakaoLogin(@RequestBody OAuthLoginRequest request) {
        return ApiResponse.success(
                memberOAuthService.oauthLogin(OAuthProvider.KAKAO, request.code(), request.redirectUri()));
    }
```

- [ ] **Step 5: 통과 확인**

Run: `.\gradlew.bat test --tests "com.back.domain.member.controller.MemberAuthControllerOAuthTest" --console=plain`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add BE/src/main/java/com/back/domain/member/dto/OAuthLoginRequest.java BE/src/main/java/com/back/domain/member/controller/MemberAuthController.java BE/src/test/java/com/back/domain/member/controller/MemberAuthControllerOAuthTest.java
git commit -m "feat: 카카오 소셜 로그인 엔드포인트 POST /api/auth/oauth/kakao 추가"
```

---

## Task 7: 전체 빌드 검증 + 문서/노션 반영

**Files:**
- Modify: `docs/DOMAIN-MEMBER-STATUTE.md`, `docs/CONTEXT.md`, `docs/TODO-DONE.md`,
  `docs/TODO-BACKLOG.md`, `docs/AI-ACTION-LOGS.md`

- [ ] **Step 1: 전체 clean build**

Run: `.\gradlew.bat clean build --console=plain`
Expected: `BUILD SUCCESSFUL`, 실패 테스트 0

- [ ] **Step 2: 문서 반영**
  - `DOMAIN-MEMBER-STATUTE.md` §2 엔티티 초안을 실제 구현(loginId 추가, password/loginId nullable, SocialAccount)으로 갱신하고, §3.1에 `POST /api/auth/oauth/kakao`·자동연결 규칙·검증 이메일 전제 추가
  - `CONTEXT.md` 현재 상태에 "MEMBER 카카오 소셜 로그인 완료(loginId 전환 포함)", email/loginId 역할, env 키(`KAKAO_CLIENT_ID/SECRET`) 주입 필요 명시
  - `TODO-DONE.md` 에 (2026-07-13) 소셜 로그인 항목 추가, `TODO-BACKLOG.md` 에서 "MEMBER: 소셜 로그인(OAuth2) 연동" 제거(구글 등 확장은 남김)
  - `AI-ACTION-LOGS.md` 에 작업 로그 1줄 추가

- [ ] **Step 3: 문서 커밋**

```bash
git add docs/
git commit -m "docs: MEMBER 카카오 소셜 로그인 완료 반영 및 상태 갱신"
```

- [ ] **Step 4: 노션 반영** — TODO 보드의 "BE 보안 기반..." 인접 소셜 항목이 없으면 새 항목 추가하거나, 기존 MEMBER 항목에 소셜 완료 메모 갱신. 노션 접근 불가 시 건너뛰고 보고.

---

## Self-Review 결과

- **스펙 커버리지**: 식별자 역할(T2) / loginId 전환(T2) / SocialAccount(T3) / provider 추상화·자동가입·자동연결·검증가드(T4) / 카카오 HTTP·설정(T5) / 엔드포인트(T6) / 에러코드(T1) / 문서(T7) — 스펙 §2~9 모두 태스크 대응됨.
- **범위 밖 준수**: 실제 인증메일 발송·구글·수동 연결 UI·FE 없음.
- **타입 일관성**: `Member.createLocal/createSocial`, `SocialAccount.create`, `OAuthUserInfo(providerUserId,email,emailVerified,nickname)`, `OAuthClient.fetch(code,redirectUri)`, `MemberOAuthService.oauthLogin(provider,code,redirectUri)` — 태스크 간 시그니처 일치 확인.
- **플레이스홀더**: 없음(모든 코드 스텝에 실제 코드 포함).
- **주의**: T2는 커밋된 email 기반 코드를 개정하므로 파일 전체 교체 방식으로 제시함(빌드 그린 유지). `@MockitoBean`은 Spring Boot 3.4 정식 API.
