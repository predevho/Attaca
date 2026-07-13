package com.back.domain.member.dto;

/** 소셜 로그인 요청. 프론트가 provider 로부터 받은 1회용 인가코드를 전달한다. */
public record OAuthLoginRequest(String code, String redirectUri) {
}
