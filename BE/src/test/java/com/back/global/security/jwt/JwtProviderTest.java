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
