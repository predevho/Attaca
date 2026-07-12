package com.back.domain.member.dto;

/** 자체 회원가입 요청. */
public record SignupRequest(String email, String password, String nickname) {
}
