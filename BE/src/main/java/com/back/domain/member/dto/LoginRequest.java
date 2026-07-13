package com.back.domain.member.dto;

/** 자체 로그인 요청. */
public record LoginRequest(String loginId, String password) {
}
