package com.back.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.global.exception.ErrorCode;
import com.back.global.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
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

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
    }

    @Test
    void noToken_leavesContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredToken_setsErrorCodeAttribute() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(props.secret(), -1000L, -1000L));
        String token = expiredProvider.createAccessToken(1L, Role.USER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE))
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }
}
