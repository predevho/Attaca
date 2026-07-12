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
    void create_defaultsToRoleUserAndPopulatesAuditing() {
        Member member = Member.create("user@attaca.com", "encoded-pw", "재즈맨");

        Member saved = entityManager.persistFlushFind(member);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("user@attaca.com");
        assertThat(saved.getPassword()).isEqualTo("encoded-pw");
        assertThat(saved.getNickname()).isEqualTo("재즈맨");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
