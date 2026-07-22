package com.back.domain.recruitment.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.recruitment.dto.ApplyRecruitmentRequest;
import com.back.domain.recruitment.dto.RecruitmentApplicationResponse;
import com.back.domain.recruitment.entity.RecruitmentApplication;
import com.back.domain.recruitment.entity.RecruitmentApplicationStatus;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.repository.RecruitmentApplicationRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고 지원 생성/조회/상태전이. 활성 지원(PENDING/ACCEPTED) 유일.
 * 지원자 표시정보는 MEMBER 협력(배치)으로 파생한다.
 */
@Service
@RequiredArgsConstructor
public class RecruitmentApplicationService {

    private static final List<RecruitmentApplicationStatus> ACTIVE_STATUSES =
            List.of(RecruitmentApplicationStatus.PENDING, RecruitmentApplicationStatus.ACCEPTED);

    private final RecruitmentApplicationRepository applicationRepository;
    private final RecruitmentPostingService postingService;
    private final MemberQueryService memberQueryService;

    @Transactional
    public RecruitmentApplicationResponse apply(Long applicantId, Long postingId,
            ApplyRecruitmentRequest request) {
        RecruitmentPosting posting = postingService.findActive(postingId);
        if (posting.getAuthorId().equals(applicantId)) {
            throw new BusinessException(ErrorCode.CANNOT_APPLY_OWN_RECRUITMENT);
        }
        if (posting.isClosed(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RECRUITMENT_CLOSED);
        }
        if (applicationRepository.existsByPostingIdAndApplicantIdAndStatusIn(postingId, applicantId,
                ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }
        RecruitmentApplication saved = applicationRepository.save(
                RecruitmentApplication.apply(postingId, applicantId, request.message()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<RecruitmentApplicationResponse> getApplicationsForPosting(Long requesterId,
            Long postingId, Pageable pageable) {
        RecruitmentPosting posting = postingService.findActive(postingId);
        if (!posting.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toResponsePage(applicationRepository
                .findByPostingIdOrderByCreatedAtDescIdDesc(postingId, pageable));
    }

    @Transactional(readOnly = true)
    public Page<RecruitmentApplicationResponse> getMyApplications(Long applicantId,
            Pageable pageable) {
        return toResponsePage(applicationRepository
                .findByApplicantIdOrderByCreatedAtDescIdDesc(applicantId, pageable));
    }

    @Transactional
    public RecruitmentApplicationResponse accept(Long requesterId, Long applicationId) {
        RecruitmentApplication application = findApplicationOwnedByPostingAuthor(requesterId,
                applicationId);
        application.accept();
        return toResponse(application);
    }

    @Transactional
    public RecruitmentApplicationResponse reject(Long requesterId, Long applicationId) {
        RecruitmentApplication application = findApplicationOwnedByPostingAuthor(requesterId,
                applicationId);
        application.reject();
        return toResponse(application);
    }

    @Transactional
    public RecruitmentApplicationResponse withdraw(Long requesterId, Long applicationId) {
        RecruitmentApplication application = findApplication(applicationId);
        if (!application.getApplicantId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        application.withdraw();
        return toResponse(application);
    }

    /** 수락/거절은 공고 작성자만. 지원 → 공고 → 작성자 대조. */
    private RecruitmentApplication findApplicationOwnedByPostingAuthor(Long requesterId,
            Long applicationId) {
        RecruitmentApplication application = findApplication(applicationId);
        RecruitmentPosting posting = postingService.findActive(application.getPostingId());
        if (!posting.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return application;
    }

    private RecruitmentApplication findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECRUITMENT_APPLICATION_NOT_FOUND));
    }

    private RecruitmentApplicationResponse toResponse(RecruitmentApplication application) {
        MemberDisplay applicant = memberQueryService
                .findDisplaysByIds(Set.of(application.getApplicantId()))
                .get(application.getApplicantId());
        return RecruitmentApplicationResponse.of(application, applicant);
    }

    private Page<RecruitmentApplicationResponse> toResponsePage(Page<RecruitmentApplication> page) {
        Set<Long> applicantIds = page.getContent().stream()
                .map(RecruitmentApplication::getApplicantId).collect(Collectors.toSet());
        Map<Long, MemberDisplay> applicants = memberQueryService.findDisplaysByIds(applicantIds);
        return page.map(a -> RecruitmentApplicationResponse.of(a, applicants.get(a.getApplicantId())));
    }
}
