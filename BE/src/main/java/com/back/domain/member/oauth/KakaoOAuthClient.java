package com.back.domain.member.oauth;

import com.back.domain.member.entity.OAuthProvider;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 인가코드 → 토큰 → 유저정보 조회를 캡슐화한다.
 * 네트워크 호출 실패는 OAUTH_PROVIDER_ERROR 로 변환한다.
 */
@Component
public class KakaoOAuthClient implements OAuthClient {

    private final OAuthProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo fetch(String code, String redirectUri) {
        try {
            String accessToken = requestAccessToken(code, redirectUri);
            KakaoUserResponse user = requestUser(accessToken);
            return toUserInfo(user);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        KakaoTokenResponse token = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);

        if (token == null || token.accessToken() == null) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        return token.accessToken();
    }

    private KakaoUserResponse requestUser(String accessToken) {
        KakaoUserResponse user = restClient.get()
                .uri(properties.userInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserResponse.class);

        if (user == null) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        return user;
    }

    /** 카카오 유저 응답을 표준 OAuthUserInfo 로 변환. (단위 테스트 대상) */
    static OAuthUserInfo toUserInfo(KakaoUserResponse res) {
        KakaoAccount account = res.kakaoAccount();
        String email = account == null ? null : account.email();
        boolean verified = account != null && Boolean.TRUE.equals(account.isEmailVerified());
        String nickname = (account != null && account.profile() != null)
                ? account.profile().nickname() : null;
        return new OAuthUserInfo(String.valueOf(res.id()), email, verified, nickname);
    }

    // --- 카카오 응답 매핑 DTO (package-private) ---

    record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    record KakaoUserResponse(@JsonProperty("id") Long id,
                             @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    record KakaoAccount(@JsonProperty("email") String email,
                        @JsonProperty("is_email_verified") Boolean isEmailVerified,
                        @JsonProperty("profile") KakaoProfile profile) {
    }

    record KakaoProfile(@JsonProperty("nickname") String nickname) {
    }
}
