package com.back.domain.verifiedperformer.repository;

import com.back.domain.verifiedperformer.entity.VerificationApplication;
import com.back.domain.verifiedperformer.entity.VerificationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationApplicationRepository
        extends JpaRepository<VerificationApplication, Long> {

    /** 활성 신청 유일성/뱃지 판정용. 특정 회원에게 해당 상태 레코드가 있는지. */
    boolean existsByMemberIdAndStatus(Long memberId, VerificationStatus status);

    /**
     * 내 최신 신청 상태 조회용. 재신청으로 여러 이력이 있으면 가장 최근 것.
     * createdAt 이 동일한 경우(같은 순간 저장)까지 결정적으로 정렬하도록 id 로 타이브레이크한다.
     */
    Optional<VerificationApplication> findTopByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

    /** 어드민 상태별 신청 목록(페이징). 안정적 페이지네이션을 위해 id 로 타이브레이크한다. */
    Page<VerificationApplication> findByStatusOrderByCreatedAtDescIdDesc(VerificationStatus status,
            Pageable pageable);

    /** 주어진 회원 id 중 APPROVED 레코드가 있는 회원 id만 배치로 조회한다. 피드 등 목록 뱃지 파생의 N+1 방지용. */
    @Query(
            "select distinct a.memberId from VerificationApplication a "
            + "where a.status = com.back.domain.verifiedperformer.entity.VerificationStatus.APPROVED "
            + "and a.memberId in :ids")
    List<Long> findApprovedMemberIds(@Param("ids") Collection<Long> ids);
}
