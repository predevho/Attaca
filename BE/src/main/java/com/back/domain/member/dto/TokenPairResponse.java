package com.back.domain.member.dto;

/** 로그인 성공 시 발급하는 토큰 쌍(access + refresh). */
public record TokenPairResponse(String accessToken, String refreshToken) {
}
