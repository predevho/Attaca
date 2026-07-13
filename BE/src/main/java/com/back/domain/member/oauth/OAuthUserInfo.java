package com.back.domain.member.oauth;

/** provider 별 유저정보를 통일한 표준형. */
public record OAuthUserInfo(String providerUserId, String email, boolean emailVerified, String nickname) {
}
