# BE 보안 기반(Security + JWT) 골격 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `com.back.global.security`에 JWT 기반 인증/인가 인프라 골격(발급·검증·필터·핸들러·재발급)을 TDD로 구축한다.

**Architecture:** 커스텀 `OncePerRequestFilter`가 Bearer access 토큰을 `JwtProvider`로 검증해 `SecurityContext`에 인증을 세팅하고, `SecurityConfig`가 STATELESS 정책과 인가 규칙을 정의한다. 필터 체인의 인증/인가 실패는 두 핸들러가 `ApiResponse` JSON으로 직접 응답한다. 무상태 `/api/auth/reissue`가 refresh로 access를 재발급한다.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Security 6, jjwt 0.12.6, JUnit 5 + Spring Security Test.

## Global Constraints

- Spring Boot 3.4.5 / Gradle 8.11.1 / JDK 21 toolchain 유지 (Gradle 9로 올리지 말 것).
- 코드 스타일: 단순 필드 접근자는 Lombok `@Getter`, 생성자는 상황에 맞는 Lombok 애노테이션 사용(수동 boilerplate 금지).
- 응답은 항상 `com.back.global.common.ApiResponse<T>`로 감싼다. 에러 본문은 `ErrorBody(resultCode:String, code:String, message:String)`.
- `resultCode`는 `HTTP상태-일련번호` 문자열(예: `401-01`).
- 이 골격은 **DB를 사용하지 않는다**(무상태). 테스트에서 JPA/DB 컨텍스트를 띄우지 않는다.
- 시크릿 등 민감값은 환경변수로 주입하고 저장소에 커밋하지 않는다.
- 각 Task 완료 시 `./gradlew -p BE clean build` 또는 해당 테스트가 통과해야 한다.

---

## File Structure

```
BE/src/main/java/com/back/global/
├── security/
│   ├── SecurityConfig.java                    (Task 4)
│   ├── Role.java                              (Task 1)
│   ├── jwt/
│   │   ├── JwtProperties.java                 (Task 2)
│   │   ├── JwtProvider.java                   (Task 2)
│   │   └── JwtAuthenticationFilter.java       (Task 3)
│   ├── handler/
│   │   ├── JwtAuthenticationEntryPoint.java   (Task 4)
│   │   └── JwtAccessDeniedHandler.java        (Task 4)
│   └── auth/
│       ├── controller/AuthController.java     (Task 5)
│       └── dto/
│           ├── ReissueRequest.java            (Task 5)
│           └── TokenResponse.java             (Task 5)
└── exception/ErrorCode.java                   (Task 1, 수정)

BE/build.gradle.kts                            (Task 2, 수정)
BE/src/main/resources/application.yaml         (Task 2, 수정)
```

---

## Task 1: Role enum + ErrorCode 인증 코드 확장

**Files:**
- Create: `BE/src/main/java/com/back/global/security/Role.java`
- Modify: `BE/src/main/java/com/back/global/exception/ErrorCode.java`
- Test: `BE/src/test/java/com/back/global/security/RoleTest.java`
- Test: `BE/src/test/java/com/back/global/exception/ErrorCodeTest.java`

**Interfaces:**
- Produces: `enum Role { USER, ADMIN }` with `String authority()` → `"ROLE_USER"` / `"ROLE_ADMIN"`.
- Produces: `ErrorCode.{UNAUTHORIZED, MALFORMED_TOKEN, INVALID_SIGNATURE, EXPIRED_TOKEN, UNSUPPORTED_TOKEN, INVALID_TOKEN_TYPE, FORBIDDEN}` with resultCode `401-01`~`401-06`, `403-01`.

- [ ] **Step 1: Write the failing test for Role**

`BE/src/test/java/com/back/global/security/RoleTest.java`
```java
package com.back.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void authority_prefixesRoleName() {
        assertThat(Role.USER.authority()).isEqualTo("ROLE_USER");
        assertThat(Role.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.RoleTest"`
Expected: FAIL — `Role` 심볼 없음(컴파일 에러).

- [ ] **Step 3: Create Role enum**

`BE/src/main/java/com/back/global/security/Role.java`
```java
package com.back.global.security;

/**
 * 회원 권한. 권한 문자열은 Spring Security 관례를 따라 "ROLE_" 접두사를 붙인다.
 * MEMBER 도메인의 Member.role 이 이 enum 을 재사용한다.
 */
public enum Role {

    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
```

- [ ] **Step 4: Run Role test to verify it passes**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.RoleTest"`
Expected: PASS

- [ ] **Step 5: Write the failing test for new ErrorCodes**

`BE/src/test/java/com/back/global/exception/ErrorCodeTest.java`
```java
package com.back.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void authErrorCodes_haveExpectedResultCodeAndStatus() {
        assertThat(ErrorCode.UNAUTHORIZED.getResultCode()).isEqualTo("401-01");
        assertThat(ErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(ErrorCode.MALFORMED_TOKEN.getResultCode()).isEqualTo("401-02");
        assertThat(ErrorCode.INVALID_SIGNATURE.getResultCode()).isEqualTo("401-03");
        assertThat(ErrorCode.EXPIRED_TOKEN.getResultCode()).isEqualTo("401-04");
        assertThat(ErrorCode.UNSUPPORTED_TOKEN.getResultCode()).isEqualTo("401-05");
        assertThat(ErrorCode.INVALID_TOKEN_TYPE.getResultCode()).isEqualTo("401-06");

        assertThat(ErrorCode.FORBIDDEN.getResultCode()).isEqualTo("403-01");
        assertThat(ErrorCode.FORBIDDEN.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.exception.ErrorCodeTest"`
Expected: FAIL — 해당 enum 상수 없음(컴파일 에러).

- [ ] **Step 7: Add auth ErrorCodes**

`BE/src/main/java/com/back/global/exception/ErrorCode.java` 의 enum 상수 목록에 아래를 추가(기존 3개 뒤에 이어서):
```java
    METHOD_NOT_ALLOWED("405-01", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // --- 인증/인가 ---
    UNAUTHORIZED("401-01", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    MALFORMED_TOKEN("401-02", HttpStatus.UNAUTHORIZED, "토큰 형식이 올바르지 않습니다."),
    INVALID_SIGNATURE("401-03", HttpStatus.UNAUTHORIZED, "토큰 서명이 유효하지 않습니다."),
    EXPIRED_TOKEN("401-04", HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    UNSUPPORTED_TOKEN("401-05", HttpStatus.UNAUTHORIZED, "지원하지 않는 토큰입니다."),
    INVALID_TOKEN_TYPE("401-06", HttpStatus.UNAUTHORIZED, "토큰 종류가 올바르지 않습니다."),
    FORBIDDEN("403-01", HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
```
> 주의: 기존 마지막 상수 `METHOD_NOT_ALLOWED(...)` 끝의 세미콜론을 콤마로 바꾸고, 마지막 상수 `FORBIDDEN(...)` 뒤에 세미콜론을 둔다.

- [ ] **Step 8: Run both tests to verify they pass**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.RoleTest" --tests "com.back.global.exception.ErrorCodeTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add BE/src/main/java/com/back/global/security/Role.java BE/src/main/java/com/back/global/exception/ErrorCode.java BE/src/test/java/com/back/global/security/RoleTest.java BE/src/test/java/com/back/global/exception/ErrorCodeTest.java
git commit -m "feat: Role enum 및 인증 계열 ErrorCode 추가"
```

---

## Task 2: jjwt 의존성 + JwtProperties + JwtProvider

**Files:**
- Modify: `BE/build.gradle.kts`
- Modify: `BE/src/main/resources/application.yaml`
- Create: `BE/src/main/java/com/back/global/security/jwt/JwtProperties.java`
- Create: `BE/src/main/java/com/back/global/security/jwt/JwtProvider.java`
- Modify: `BE/src/main/java/com/back/AttacaApplication.java` (`@ConfigurationPropertiesScan`)
- Test: `BE/src/test/java/com/back/global/security/jwt/JwtProviderTest.java`

**Interfaces:**
- Consumes: `Role` (Task 1).
- Produces:
  - `JwtProperties(String secret, long accessTokenExpiry, long refreshTokenExpiry)` (record, `@ConfigurationProperties("jwt")`).
  - `JwtProvider`:
    - `String createAccessToken(Long userId, Role role)`
    - `String createRefreshToken(Long userId, Role role)`
    - `io.jsonwebtoken.Claims parse(String token)` — 검증 실패 시 jjwt 예외 전파
    - `org.springframework.security.core.Authentication getAuthentication(io.jsonwebtoken.Claims claims)` — principal=userId(Long), authorities=role claim

- [ ] **Step 1: Add jjwt dependencies**

`BE/build.gradle.kts` 의 `dependencies { ... }` 블록에 추가:
```kotlin
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

- [ ] **Step 2: Add jwt config to application.yaml**

`BE/src/main/resources/application.yaml`:
```yaml
spring:
  application:
    name: attaca

jwt:
  secret: ${JWT_SECRET:local-dev-secret-please-change-in-real-environment-0123456789}
  access-token-expiry: 1800000       # 30분 (ms)
  refresh-token-expiry: 1209600000   # 14일 (ms)
```

- [ ] **Step 3: Create JwtProperties**

`BE/src/main/java/com/back/global/security/jwt/JwtProperties.java`
```java
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
```

- [ ] **Step 4: Enable configuration properties scanning**

`BE/src/main/java/com/back/AttacaApplication.java` 의 클래스 애노테이션에 `@ConfigurationPropertiesScan` 추가:
```java
package com.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AttacaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttacaApplication.class, args);
    }
}
```

- [ ] **Step 5: Write the failing test for JwtProvider**

`BE/src/test/java/com/back/global/security/jwt/JwtProviderTest.java`
```java
package com.back.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.global.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class JwtProviderTest {

    private final JwtProperties props = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256-0123456789",
            1800000L,
            1209600000L);
    private final JwtProvider jwtProvider = new JwtProvider(props);

    @Test
    void accessToken_roundTrip_carriesSubjectAndRole() {
        String token = jwtProvider.createAccessToken(1L, Role.USER);

        Claims claims = jwtProvider.parse(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }

    @Test
    void refreshToken_roundTrip_carriesTypeRefresh() {
        String token = jwtProvider.createRefreshToken(1L, Role.USER);

        Claims claims = jwtProvider.parse(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void getAuthentication_buildsPrincipalAndAuthority() {
        String token = jwtProvider.createAccessToken(42L, Role.ADMIN);
        Claims claims = jwtProvider.parse(token);

        Authentication auth = jwtProvider.getAuthentication(claims);

        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void parse_expiredToken_throwsExpired() {
        JwtProperties expiredProps = new JwtProperties(props.secret(), -1000L, -1000L);
        JwtProvider expiredProvider = new JwtProvider(expiredProps);
        String token = expiredProvider.createAccessToken(1L, Role.USER);

        assertThatThrownBy(() -> expiredProvider.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parse_tamperedToken_throwsSignature() {
        JwtProvider other = new JwtProvider(new JwtProperties(
                "another-different-secret-key-long-enough-hs256-987654321", 1800000L, 1209600000L));
        String tokenFromOther = other.createAccessToken(1L, Role.USER);

        assertThatThrownBy(() -> jwtProvider.parse(tokenFromOther))
                .isInstanceOf(SignatureException.class);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.jwt.JwtProviderTest"`
Expected: FAIL — `JwtProvider` 심볼 없음(컴파일 에러).

- [ ] **Step 7: Create JwtProvider**

`BE/src/main/java/com/back/global/security/jwt/JwtProvider.java`
```java
package com.back.global.security.jwt;

import com.back.global.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·파싱·인증 변환. HS256, self-issued.
 * 무상태 재발급을 위해 refresh 에도 role claim 을 담는다.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = properties.accessTokenExpiry();
        this.refreshTokenExpiry = properties.refreshTokenExpiry();
    }

    public String createAccessToken(Long userId, Role role) {
        return createToken(userId, role, "access", accessTokenExpiry);
    }

    public String createRefreshToken(Long userId, Role role) {
        return createToken(userId, role, "refresh", refreshTokenExpiry);
    }

    private String createToken(Long userId, Role role, String type, long expiryMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.authority())
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMillis))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Authentication getAuthentication(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.jwt.JwtProviderTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add BE/build.gradle.kts BE/src/main/resources/application.yaml BE/src/main/java/com/back/AttacaApplication.java BE/src/main/java/com/back/global/security/jwt/JwtProperties.java BE/src/main/java/com/back/global/security/jwt/JwtProvider.java BE/src/test/java/com/back/global/security/jwt/JwtProviderTest.java
git commit -m "feat: JwtProvider 및 JwtProperties, jjwt 의존성 추가"
```

---

## Task 3: JwtAuthenticationFilter

**Files:**
- Create: `BE/src/main/java/com/back/global/security/jwt/JwtAuthenticationFilter.java`
- Test: `BE/src/test/java/com/back/global/security/jwt/JwtAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `JwtProvider` (Task 2), `ErrorCode` (Task 1).
- Produces: `JwtAuthenticationFilter extends OncePerRequestFilter`. 유효 access → `SecurityContext` 에 Authentication 세팅. 검증 실패 → `request.setAttribute("errorCode", ErrorCode.X)` 후 컨텍스트 비운 채 통과. 요청 속성 키는 상수 `JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE = "errorCode"`.

- [ ] **Step 1: Write the failing test**

`BE/src/test/java/com/back/global/security/jwt/JwtAuthenticationFilterTest.java`
```java
package com.back.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.global.exception.ErrorCode;
import com.back.global.security.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private final JwtProperties props = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256-0123456789", 1800000L, 1209600000L);
    private final JwtProvider jwtProvider = new JwtProvider(props);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validAccessToken_setsAuthentication() throws Exception {
        String token = jwtProvider.createAccessToken(7L, Role.USER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
    }

    @Test
    void noToken_leavesContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredToken_setsErrorCodeAttribute() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(props.secret(), -1000L, -1000L));
        String token = expiredProvider.createAccessToken(1L, Role.USER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE))
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    // MockFilterChain 은 spring-test 의 org.springframework.mock.web.MockFilterChain 사용
    static class MockFilterChain extends org.springframework.mock.web.MockFilterChain {
    }
}
```
> `FilterChain` import 는 실제로는 위 MockFilterChain 으로 대체되므로 필요 없으면 제거해도 된다.

- [ ] **Step 2: Run test to verify it fails**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.jwt.JwtAuthenticationFilterTest"`
Expected: FAIL — `JwtAuthenticationFilter` 심볼 없음.

- [ ] **Step 3: Create JwtAuthenticationFilter**

`BE/src/main/java/com/back/global/security/jwt/JwtAuthenticationFilter.java`
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.jwt.JwtAuthenticationFilterTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add BE/src/main/java/com/back/global/security/jwt/JwtAuthenticationFilter.java BE/src/test/java/com/back/global/security/jwt/JwtAuthenticationFilterTest.java
git commit -m "feat: JwtAuthenticationFilter 추가"
```

---

## Task 4: 핸들러 2종 + SecurityConfig (통합 테스트)

**Files:**
- Create: `BE/src/main/java/com/back/global/security/handler/JwtAuthenticationEntryPoint.java`
- Create: `BE/src/main/java/com/back/global/security/handler/JwtAccessDeniedHandler.java`
- Create: `BE/src/main/java/com/back/global/security/SecurityConfig.java`
- Test: `BE/src/test/java/com/back/global/security/SecurityConfigTest.java`

**Interfaces:**
- Consumes: `JwtProvider`, `JwtAuthenticationFilter`, `ErrorCode`, `ApiResponse`.
- Produces: `SecurityFilterChain` 빈, `PasswordEncoder` 빈, 두 핸들러 빈.

- [ ] **Step 1: Create JwtAuthenticationEntryPoint**

`BE/src/main/java/com/back/global/security/handler/JwtAuthenticationEntryPoint.java`
```java
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
```

- [ ] **Step 2: Create JwtAccessDeniedHandler**

`BE/src/main/java/com/back/global/security/handler/JwtAccessDeniedHandler.java`
```java
package com.back.global.security.handler;

import com.back.global.common.ApiResponse;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 인가 실패(403)를 ApiResponse JSON 으로 응답. */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.FORBIDDEN));
    }
}
```

- [ ] **Step 3: Create SecurityConfig**

`BE/src/main/java/com/back/global/security/SecurityConfig.java`
```java
package com.back.global.security;

import com.back.global.security.handler.JwtAccessDeniedHandler;
import com.back.global.security.handler.JwtAuthenticationEntryPoint;
import com.back.global.security.jwt.JwtAuthenticationFilter;
import com.back.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 4: Write the failing integration test**

`BE/src/test/java/com/back/global/security/SecurityConfigTest.java`
```java
package com.back.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.global.security.jwt.JwtProperties;
import com.back.global.security.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import({SecurityConfig.class,
        com.back.global.security.handler.JwtAuthenticationEntryPoint.class,
        com.back.global.security.handler.JwtAccessDeniedHandler.class,
        SecurityConfigTest.TestBeans.class,
        SecurityConfigTest.TestController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.resultCode").value("401-01"));
    }

    @Test
    void validUserToken_returns200() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.USER);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userToken_onAdminPath_returns403() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.USER);
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.resultCode").value("403-01"));
    }

    @Test
    void adminToken_onAdminPath_returns200() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.ADMIN);
        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tamperedToken_returns401WithSignatureCode() throws Exception {
        JwtProvider other = new JwtProvider(new JwtProperties(
                "another-secret-key-long-enough-for-hs256-9876543210", 1800000L, 1209600000L));
        String token = other.createAccessToken(1L, Role.USER);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-03"));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-secret-key-that-is-long-enough-for-hs256-0123456789",
                    1800000L, 1209600000L);
        }

        @Bean
        JwtProvider jwtProvider(JwtProperties props) {
            return new JwtProvider(props);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/api/me")
        public String me() {
            return "me";
        }

        @GetMapping("/api/admin/ping")
        public String adminPing() {
            return "pong";
        }
    }
}
```
> 참고: `@WebMvcTest` 는 JPA/DB 를 로드하지 않는다. `JwtProvider`/`JwtProperties`/`ObjectMapper` 는 `TestBeans` 로 공급한다. `@Component` 로 선언한 두 핸들러와 `SecurityConfig` 는 `@Import` 로 가져온다. (구현 중 컨텍스트 로딩 문제가 있으면 `@WebMvcTest(controllers = ...)` 범위 지정 또는 `@AutoConfigureMockMvc` + `@SpringBootTest(webEnvironment = MOCK)` 로 조정.)

- [ ] **Step 5: Run test to verify it fails, then passes**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.SecurityConfigTest"`
Expected: 처음엔 컴파일/컨텍스트 실패 → 위 프로덕션 클래스 완성 후 PASS.

- [ ] **Step 6: Commit**

```bash
git add BE/src/main/java/com/back/global/security/handler/ BE/src/main/java/com/back/global/security/SecurityConfig.java BE/src/test/java/com/back/global/security/SecurityConfigTest.java
git commit -m "feat: SecurityConfig 및 인증/인가 실패 핸들러 추가"
```

---

## Task 5: AuthController /reissue + DTO

**Files:**
- Create: `BE/src/main/java/com/back/global/security/auth/dto/ReissueRequest.java`
- Create: `BE/src/main/java/com/back/global/security/auth/dto/TokenResponse.java`
- Create: `BE/src/main/java/com/back/global/security/auth/controller/AuthController.java`
- Test: `BE/src/test/java/com/back/global/security/auth/AuthControllerTest.java`

**Interfaces:**
- Consumes: `JwtProvider`, `Role`, `ErrorCode`, `BusinessException`, `ApiResponse`.
- Produces: `POST /api/auth/reissue` → `ApiResponse<TokenResponse>`.
  - `ReissueRequest(String refreshToken)`
  - `TokenResponse(String accessToken)`

- [ ] **Step 1: Create DTOs**

`BE/src/main/java/com/back/global/security/auth/dto/ReissueRequest.java`
```java
package com.back.global.security.auth.dto;

public record ReissueRequest(String refreshToken) {
}
```

`BE/src/main/java/com/back/global/security/auth/dto/TokenResponse.java`
```java
package com.back.global.security.auth.dto;

public record TokenResponse(String accessToken) {
}
```

- [ ] **Step 2: Write the failing test**

`BE/src/test/java/com/back/global/security/auth/AuthControllerTest.java`
```java
package com.back.global.security.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.security.Role;
import com.back.global.security.auth.controller.AuthController;
import com.back.global.security.jwt.JwtProperties;
import com.back.global.security.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private final JwtProperties props = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256-0123456789", 1800000L, 1209600000L);
    private final JwtProvider jwtProvider = new JwtProvider(props);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(jwtProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validRefresh_returnsNewAccessToken() throws Exception {
        String refresh = jwtProvider.createRefreshToken(1L, Role.USER);
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("refreshToken", refresh);
        }});

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void accessTokenInsteadOfRefresh_returns401TypeError() throws Exception {
        String access = jwtProvider.createAccessToken(1L, Role.USER);
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("refreshToken", access);
        }});

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-06"));
    }

    @Test
    void expiredRefresh_returns401Expired() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(props.secret(), -1000L, -1000L));
        String refresh = expiredProvider.createRefreshToken(1L, Role.USER);
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("refreshToken", refresh);
        }});

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.resultCode").value("401-04"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.auth.AuthControllerTest"`
Expected: FAIL — `AuthController` 심볼 없음.

- [ ] **Step 4: Create AuthController**

`BE/src/main/java/com/back/global/security/auth/controller/AuthController.java`
```java
package com.back.global.security.auth.controller;

import com.back.global.common.ApiResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
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
        com.back.global.security.Role role =
                com.back.global.security.Role.valueOf(
                        claims.get("role", String.class).replace("ROLE_", ""));
        String newAccessToken = jwtProvider.createAccessToken(userId, role);
        return ApiResponse.success(new TokenResponse(newAccessToken));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `BE/gradlew.bat -p BE test --tests "com.back.global.security.auth.AuthControllerTest"`
Expected: PASS

- [ ] **Step 6: Full build**

Run: `BE/gradlew.bat -p BE clean build`
Expected: BUILD SUCCESSFUL (전체 테스트 통과)

- [ ] **Step 7: Commit**

```bash
git add BE/src/main/java/com/back/global/security/auth/ BE/src/test/java/com/back/global/security/auth/AuthControllerTest.java
git commit -m "feat: 토큰 재발급 엔드포인트 /api/auth/reissue 추가"
```

---

## Task 6: 문서 갱신 및 상태 반영

**Files:**
- Modify: `docs/DOMAIN-COMMON-STATUTE.md` (§4 인증/인가)
- Modify: `docs/CONTEXT.md`
- Modify: `docs/AI-ACTION-LOGS.md`
- Modify: `docs/TODO-READY.md`, `docs/TODO-DONE.md`
- (노션) TODO 보드 항목 상태 갱신, 스케줄 이벤트 추가

- [ ] **Step 1: DOMAIN-COMMON-STATUTE §4 갱신**

인증/인가 절을 구현에 맞게 구체화: `JwtProvider`/`JwtAuthenticationFilter`/`SecurityConfig`/핸들러 2종/`Role`/`/api/auth/reissue`, 인가 규칙(`/api/auth/**` permit, `/api/admin/**` ADMIN, 그 외 authenticated), 무상태 refresh 정책, 인증 ErrorCode 표(`401-01`~`401-06`, `403-01`).

- [ ] **Step 2: ErrorCode 표 반영**

DOMAIN-COMMON-STATUTE 의 ErrorCode 관련 절에 신규 인증 코드 7종을 표로 추가.

- [ ] **Step 3: CONTEXT.md 갱신**

"현재 상태"를 `보안 기반(Security+JWT) 골격 완료 → 다음은 MEMBER 도메인(자체 회원가입/로그인)` 으로. "주의"에 `jwt.secret 은 env(JWT_SECRET) 주입, 커밋 금지` 추가.

- [ ] **Step 4: TODO 이동**

`TODO-READY.md` 에서 "BE 보안 기반 구성" 제거, `TODO-DONE.md` 에 완료 기록 추가(날짜, 구성 요약).

- [ ] **Step 5: AI-ACTION-LOGS 기록 + 노션 동기화**

`AI-ACTION-LOGS.md` 에 한 줄 로그 추가. 노션 TODO 보드의 "BE 보안 기반 구성 (Security + JWT)" 항목을 DONE 으로, 스케줄 DB 에 구현 완료 이벤트 추가.

- [ ] **Step 6: Commit**

```bash
git add docs/ CLAUDE.md
git commit -m "docs: 보안 기반 구현 반영 및 상태 갱신"
```

---

## Self-Review 결과

- **Spec 커버리지**: §4 컴포넌트(모두 Task 1~5), §5 데이터 흐름(Task 3·4·5), §6 ErrorCode(Task 1), §7 설정/의존성(Task 2), §8 테스트(각 Task 내), §9 문서(Task 6) — 누락 없음.
- **Placeholder**: jjwt 버전은 `0.12.6`으로 확정. 플레이스홀더 없음.
- **타입 일관성**: `createAccessToken(Long, Role)`, `createRefreshToken(Long, Role)`, `parse(String)→Claims`, `getAuthentication(Claims)→Authentication`, `ERROR_CODE_ATTRIBUTE` 상수 — Task 간 시그니처 일치 확인.
- **주의(구현 시 검증 필요)**: Task 4의 `@WebMvcTest` 컨텍스트 로딩은 스프링 시큐리티 슬라이스 구성에 따라 조정이 필요할 수 있음(대안 명시). `SignatureException` 은 `io.jsonwebtoken.security.SignatureException` 임에 유의(0.12.x).
