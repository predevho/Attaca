package com.back.domain.verifiedperformer.service;

import com.back.domain.verifiedperformer.dto.ApplicationResponse;
import com.back.domain.verifiedperformer.dto.ApplyRequest;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.entity.VerificationApplication;
import com.back.domain.verifiedperformer.entity.VerificationStatus;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 연주자 자격 상태의 소유·관리 서비스. 신청→심사→승인/거절→철회의 상태 워크플로를 담당한다.
 *
 * <p>인증 여부는 {@link #isVerified(Long)}로 다른 도메인(MEMBER 프로필 뱃지 등)에 파생 제공한다.
 * 회원은 원시 {@code memberId}로만 다루며 MEMBER 엔티티를 직접 참조하지 않는다
 * (DOMAIN-VERIFIED-PERFORMER-CONSTITUTION §2, §3).
 */
@Service
@RequiredArgsConstructor
public class VerifiedPerformerService {

    private final VerificationApplicationRepository repository;

    /** 회원의 인증 신청. 활성 신청(PENDING/APPROVED)이 있으면 거절한다. */
    @Transactional
    public ApplicationResponse apply(Long memberId, ApplyRequest request) {
        rejectIfActiveApplicationExists(memberId);
        VerificationApplication saved = repository.save(
                VerificationApplication.apply(memberId, request.statement(),
                        request.evidenceUrlsOrEmpty()));
        return ApplicationResponse.from(saved);
    }

    /** 어드민 직접지정. 활성 신청이 있으면 거절한다. */
    @Transactional
    public ApplicationResponse grant(GrantRequest request, Long adminId) {
        rejectIfActiveApplicationExists(request.memberId());
        VerificationApplication saved = repository.save(
                VerificationApplication.grantByAdmin(request.memberId(), adminId, request.reason()));
        return ApplicationResponse.from(saved);
    }

    @Transactional
    public ApplicationResponse approve(Long applicationId, Long adminId, String reason) {
        VerificationApplication application = findApplication(applicationId);
        application.approve(adminId, reason);
        return ApplicationResponse.from(application);
    }

    @Transactional
    public ApplicationResponse reject(Long applicationId, Long adminId, String reason) {
        VerificationApplication application = findApplication(applicationId);
        application.reject(adminId, reason);
        return ApplicationResponse.from(application);
    }

    @Transactional
    public ApplicationResponse revoke(Long applicationId, Long adminId, String reason) {
        VerificationApplication application = findApplication(applicationId);
        application.revoke(adminId, reason);
        return ApplicationResponse.from(application);
    }

    /** 내 최신 신청 상태. 이력이 없으면 null(호출부에서 200 + data:null 로 응답). */
    @Transactional(readOnly = true)
    public ApplicationResponse getMyLatestApplication(Long memberId) {
        return repository.findTopByMemberIdOrderByCreatedAtDescIdDesc(memberId)
                .map(ApplicationResponse::from)
                .orElse(null);
    }

    /** 어드민 상태별 신청 목록(페이징). */
    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getApplications(VerificationStatus status, Pageable pageable) {
        return repository.findByStatusOrderByCreatedAtDescIdDesc(status, pageable)
                .map(ApplicationResponse::from);
    }

    /** 뱃지 판정 근거. 회원에게 APPROVED 레코드가 있으면 인증된 것으로 본다. */
    @Transactional(readOnly = true)
    public boolean isVerified(Long memberId) {
        return repository.existsByMemberIdAndStatus(memberId, VerificationStatus.APPROVED);
    }

    /** 주어진 회원들 중 인증(APPROVED) 상태인 회원 id 집합. 피드 등 목록 뱃지 파생의 N+1 방지용. */
    @Transactional(readOnly = true)
    public Set<Long> findVerifiedMemberIds(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findApprovedMemberIds(memberIds));
    }

    private void rejectIfActiveApplicationExists(Long memberId) {
        if (repository.existsByMemberIdAndStatus(memberId, VerificationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_PENDING);
        }
        if (repository.existsByMemberIdAndStatus(memberId, VerificationStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_APPROVED);
        }
    }

    private VerificationApplication findApplication(Long applicationId) {
        return repository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }
}
