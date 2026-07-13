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
    void unverifiedEmailMatchingExistingMember_isRejectedAndNotLinked() {
        memberRepository.save(Member.createLocal("victim", "pw", "victim@attaca.com", "피해자"));
        fakeClient.next = new OAuthUserInfo("kakao-attacker", "victim@attaca.com", false, "공격자");

        assertThatThrownBy(() -> service.oauthLogin(OAuthProvider.KAKAO, "code", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OAUTH_EMAIL_UNVERIFIED);

        assertThat(memberRepository.findAll()).hasSize(1);
        assertThat(socialAccountRepository.findAll()).isEmpty();
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
