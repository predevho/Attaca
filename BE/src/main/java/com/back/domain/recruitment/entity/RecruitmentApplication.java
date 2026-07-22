package com.back.domain.recruitment.entity;

import com.back.global.common.BaseEntity;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
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
 * 구인 공고 지원. 공고·지원자는 원시 Long 으로 참조한다.
 * 상태 전이: PENDING → ACCEPTED/REJECTED(작성자) 또는 WITHDRAWN(지원자). PENDING 에서만 전이한다.
 */
@Entity
@Table(name = "recruitment_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitmentApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postingId;

    @Column(nullable = false)
    private Long applicantId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitmentApplicationStatus status;

    private RecruitmentApplication(Long postingId, Long applicantId, String message) {
        this.postingId = postingId;
        this.applicantId = applicantId;
        this.message = message;
        this.status = RecruitmentApplicationStatus.PENDING;
    }

    public static RecruitmentApplication apply(Long postingId, Long applicantId, String message) {
        return new RecruitmentApplication(postingId, applicantId, message);
    }

    public void accept() {
        transition(RecruitmentApplicationStatus.ACCEPTED);
    }

    public void reject() {
        transition(RecruitmentApplicationStatus.REJECTED);
    }

    public void withdraw() {
        transition(RecruitmentApplicationStatus.WITHDRAWN);
    }

    private void transition(RecruitmentApplicationStatus next) {
        if (this.status != RecruitmentApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RECRUITMENT_INVALID_APPLICATION_STATE);
        }
        this.status = next;
    }
}
