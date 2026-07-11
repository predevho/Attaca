package com.back.global.security;

/**
 * 회원 권한. 권한 문자열은 Spring Security 관례를 따라 "ROLE_" 접두사를 붙인다.
 * MEMBER 도메인의 Member.role 이 이 enum 을 재사용한다.
 */
public enum Role {

    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
