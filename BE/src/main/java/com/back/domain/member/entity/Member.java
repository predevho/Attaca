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
 * 회원 엔티티. 자체 가입(email + password)과 소셜 로그인이 하나의 회원으로 수렴한다.
 * 비밀번호는 반드시 해시된 값으로 저장한다(평문 저장 금지).
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** 해시된 비밀번호. 평문 저장 금지. */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private Member(String email, String password, String nickname, Role role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
    }

    /**
     * 신규 회원 생성. 기본 권한은 {@link Role#USER}.
     *
     * @param encodedPassword 이미 해시된 비밀번호
     */
    public static Member create(String email, String encodedPassword, String nickname) {
        return new Member(email, encodedPassword, nickname, Role.USER);
    }
}
