package com.back.domain.member.dto;

import com.back.domain.member.entity.Member;
import com.back.global.security.Role;

/** 회원가입 결과. 비밀번호 등 민감 정보는 노출하지 않는다. */
public record SignupResponse(Long id, String email, String nickname, Role role) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname(), member.getRole());
    }
}
