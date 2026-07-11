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
