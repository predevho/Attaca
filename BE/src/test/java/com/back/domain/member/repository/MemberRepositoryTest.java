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
        memberRepository.save(Member.create("user@attaca.com", "encoded-pw", "재즈맨"));
    }

    @Test
    void existsByEmail_true_whenPresent() {
        assertThat(memberRepository.existsByEmail("user@attaca.com")).isTrue();
        assertThat(memberRepository.existsByEmail("none@attaca.com")).isFalse();
    }

    @Test
    void existsByNickname_true_whenPresent() {
        assertThat(memberRepository.existsByNickname("재즈맨")).isTrue();
        assertThat(memberRepository.existsByNickname("없는이름")).isFalse();
    }

    @Test
    void findByEmail_returnsMember_whenPresent() {
        assertThat(memberRepository.findByEmail("user@attaca.com"))
                .get()
                .extracting(Member::getNickname)
                .isEqualTo("재즈맨");
        assertThat(memberRepository.findByEmail("none@attaca.com")).isEmpty();
    }
}
