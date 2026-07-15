package com.back.domain.member.entity;

import com.back.global.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 프로필(악기/자기소개/프로필 이미지). Member와 1:1 단방향 — Member 쪽에는 참조를 두지 않아
 * 인증 등 프로필 무관 조회에 컬렉션이 딸려오지 않게 한다.
 * 가입 시 만들지 않고 첫 수정/이미지 업로드 때 생성한다(lazy upsert).
 * 이미지는 storageKey만 보관하고 URL은 저장하지 않는다(DOMAIN-COMMON-STATUTE §7).
 */
@Entity
@Table(name = "member_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_profile_instrument",
            joinColumns = @JoinColumn(name = "member_profile_id"))
    @Column(name = "instrument", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Instrument> instruments = new HashSet<>();

    @Column(length = 500)
    private String bio;

    @Column(name = "profile_image_key")
    private String profileImageKey;

    private MemberProfile(Member member) {
        this.member = member;
    }

    public static MemberProfile create(Member member) {
        return new MemberProfile(member);
    }

    /** 악기·소개를 전체 교체한다(부분 수정 아님 — PUT 시맨틱). */
    public void updateInfo(Set<Instrument> instruments, String bio) {
        this.instruments.clear();
        this.instruments.addAll(instruments);
        this.bio = bio;
    }

    public void changeImage(String newKey) {
        this.profileImageKey = newKey;
    }
}
