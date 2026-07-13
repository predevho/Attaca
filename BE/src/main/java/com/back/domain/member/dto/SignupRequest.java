package com.back.domain.member.dto;

/** 자체 회원가입 요청. */
public record SignupRequest(String loginId, String password, String email, String nickname) {
}
