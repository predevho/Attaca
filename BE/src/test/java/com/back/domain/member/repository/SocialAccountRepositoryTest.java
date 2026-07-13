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
