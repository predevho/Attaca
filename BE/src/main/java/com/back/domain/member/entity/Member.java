package com.back.domain.member.entity;

import com.back.global.common.BaseEntity;
import com.back.global.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 엔티티. 자체 로그인(loginId+password)과 소셜 로그인이 하나의 회원으로 수렴한다.
 * loginId/password 는 소셜 전용 회원에서는 null 이며, email/nickname 은 전원 필수.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 자체 로그인 아이디. 소셜 전용 회원은 null. */
    @Column(unique = true)
    private String loginId;

    /** 해시된 비밀번호. 소셜 전용 회원은 null. 평문 저장 금지. */
    private String password;

    /** 인증메일 발송·연락용 + 소셜 자동연결 매칭 키. 전원 필수. */
    @Column(nullable = false, unique = true)
    private String email;

    /** 웹 내 활동 표시명. */
    @Column(nullable = false, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private Member(String loginId, String password, String email, String nickname, Role role) {
        this.loginId = loginId;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    /** 자체 가입 회원 생성. encodedPassword = 이미 해시된 비밀번호. */
    public static Member createLocal(String loginId, String encodedPassword, String email, String nickname) {
        return new Member(loginId, encodedPassword, email, nickname, Role.USER);
    }

    /** 소셜 전용 회원 생성. loginId/password 없음. */
    public static Member createSocial(String email, String nickname) {
        return new Member(null, null, email, nickname, Role.USER);
    }
}
