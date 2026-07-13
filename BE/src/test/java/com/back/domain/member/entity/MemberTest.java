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
