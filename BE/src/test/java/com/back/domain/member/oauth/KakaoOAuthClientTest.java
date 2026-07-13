package com.back.domain.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.oauth.KakaoOAuthClient.KakaoAccount;
import com.back.domain.member.oauth.KakaoOAuthClient.KakaoProfile;
import com.back.domain.member.oauth.KakaoOAuthClient.KakaoUserResponse;
import org.junit.jupiter.api.Test;

class KakaoOAuthClientTest {

    @Test
    void toUserInfo_mapsVerifiedEmailAndNickname() {
        KakaoUserResponse res = new KakaoUserResponse(1234L,
                new KakaoAccount("user@kakao.com", true, new KakaoProfile("카카오닉")));

        OAuthUserInfo info = KakaoOAuthClient.toUserInfo(res);

        assertThat(info.providerUserId()).isEqualTo("1234");
        assertThat(info.email()).isEqualTo("user@kakao.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.nickname()).isEqualTo("카카오닉");
    }

    @Test
    void toUserInfo_handlesMissingAccount() {
        OAuthUserInfo info = KakaoOAuthClient.toUserInfo(new KakaoUserResponse(9L, null));

        assertThat(info.providerUserId()).isEqualTo("9");
        assertThat(info.email()).isNull();
        assertThat(info.emailVerified()).isFalse();
    }
}
