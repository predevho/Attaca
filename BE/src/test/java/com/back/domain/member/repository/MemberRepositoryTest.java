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
