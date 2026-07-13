package com.back.domain.member.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 카카오 OAuth 설정. client-secret 은 env 주입(커밋 금지). */
@ConfigurationProperties(prefix = "oauth.kakao")
public record OAuthProperties(String clientId, String clientSecret, String tokenUri, String userInfoUri) {
}
