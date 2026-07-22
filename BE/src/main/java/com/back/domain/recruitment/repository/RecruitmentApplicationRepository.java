package com.back.domain.recruitment.repository;

import com.back.domain.recruitment.entity.RecruitmentApplication;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentApplicationRepository
        extends JpaRepository<RecruitmentApplication, Long> {

    /** 활성 지원(PENDING/ACCEPTED) 유일성 판정용. */
    boolean existsByPostingIdAndApplicantIdAndStatusIn(Long postingId, Long applicantId,
            Collection<RecruitmentApplicationStatus> statuses);

    /** 공고별 지원자 목록(작성자용). 최신순, id 타이브레이크. */
    Page<RecruitmentApplication> findByPostingIdOrderByCreatedAtDescIdDesc(Long postingId,
            Pageable pageable);

    /** 내 지원 목록(지원자용). 최신순, id 타이브레이크. */
    Page<RecruitmentApplication> findByApplicantIdOrderByCreatedAtDescIdDesc(Long applicantId,
            Pageable pageable);
}
