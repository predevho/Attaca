package com.back.domain.member.oauth;

import com.back.domain.member.entity.OAuthProvider;

/** 소셜 provider 연동 추상화. 인가코드 교환~유저정보 조회를 캡슐화한다. */
public interface OAuthClient {

    OAuthProvider provider();

    OAuthUserInfo fetch(String code, String redirectUri);
}
