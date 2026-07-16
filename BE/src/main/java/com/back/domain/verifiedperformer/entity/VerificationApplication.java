package com.back.domain.verifiedperformer.entity;

import com.back.global.common.BaseEntity;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증 연주자 신청. 인증 자격 상태의 소유·관리 책임은 이 도메인에 있으며,
 * Member 엔티티에는 인증 여부를 두지 않는다(DOMAIN-VERIFIED-PERFORMER-CONSTITUTION §2).
 *
 * <p>회원은 원시 {@code memberId}(Long)로 참조한다 — 도메인 간 느슨한 결합을 위해 연관을 두지 않는다.
 * 재신청은 기존 레코드를 덮어쓰지 않고 새 레코드로 남겨 이력을 보존한다.
 */
@Entity
@Table(name = "verification_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 자기서술(지원 사유). 어드민 직접지정 시 null. */
    @Column(length = 1000)
    private String statement;

    /** 증빙 링크. 어드민 직접지정 시 빈 목록. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "verification_application_evidence",
            joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "evidence_url", length = 500)
    private List<String> evidenceUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    /** 승인/거절/철회 사유(어드민 기록). PENDING 이면 null. */
    @Column(length = 500)
    private String decisionReason;

    /** 처리한 어드민 memberId. PENDING 이면 null. */
    private Long decidedBy;

    /** 처리 시각. PENDING 이면 null. */
    private LocalDateTime decidedAt;

    private VerificationApplication(Long memberId, String statement, List<String> evidenceUrls,
            VerificationStatus status) {
        this.memberId = memberId;
        this.statement = statement;
        if (evidenceUrls != null) {
            this.evidenceUrls.addAll(evidenceUrls);
        }
        this.status = status;
    }

    /** 회원의 인증 신청. PENDING 으로 시작한다. */
    public static VerificationApplication apply(Long memberId, String statement,
            List<String> evidenceUrls) {
        return new VerificationApplication(memberId, statement, evidenceUrls,
                VerificationStatus.PENDING);
    }

    /** 어드민 직접지정. 신청서(statement/evidence) 없이 곧바로 APPROVED 레코드를 만든다. */
    public static VerificationApplication grantByAdmin(Long memberId, Long adminId, String reason) {
        VerificationApplication application =
                new VerificationApplication(memberId, null, null, VerificationStatus.APPROVED);
        application.decide(adminId, reason);
        return application;
    }

    /** 어드민 승인. PENDING 에서만 가능. */
    public void approve(Long adminId, String reason) {
        transitionFrom(VerificationStatus.PENDING, VerificationStatus.APPROVED, adminId, reason);
    }

    /** 어드민 거절. PENDING 에서만 가능. */
    public void reject(Long adminId, String reason) {
        transitionFrom(VerificationStatus.PENDING, VerificationStatus.REJECTED, adminId, reason);
    }

    /** 어드민 철회. APPROVED 에서만 가능. */
    public void revoke(Long adminId, String reason) {
        transitionFrom(VerificationStatus.APPROVED, VerificationStatus.REVOKED, adminId, reason);
    }

    private void transitionFrom(VerificationStatus required, VerificationStatus next,
            Long adminId, String reason) {
        if (this.status != required) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_STATE);
        }
        this.status = next;
        decide(adminId, reason);
    }

    private void decide(Long adminId, String reason) {
        this.decidedBy = adminId;
        this.decisionReason = reason;
        this.decidedAt = LocalDateTime.now();
    }
}
