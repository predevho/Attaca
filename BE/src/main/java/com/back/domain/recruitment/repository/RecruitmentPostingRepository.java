package com.back.domain.recruitment.repository;

import com.back.domain.member.entity.Instrument;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.entity.RecruitmentStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruitmentPostingRepository extends JpaRepository<RecruitmentPosting, Long> {

    Optional<RecruitmentPosting> findByIdAndDeletedAtIsNull(Long id);

    /** 모집중: 미삭제 + status=OPEN + (마감일 없음 or 아직 안 지남) + (악기 필터 없음 or 해당 악기 모집). 최신순. */
    @Query("select p from RecruitmentPosting p "
            + "where p.deletedAt is null and p.status = :openStatus "
            + "and (p.deadline is null or p.deadline > :now) "
            + "and (:instrument is null or :instrument member of p.instruments) "
            + "order by p.createdAt desc, p.id desc")
    Page<RecruitmentPosting> findOpen(@Param("openStatus") RecruitmentStatus openStatus,
            @Param("now") LocalDateTime now, @Param("instrument") Instrument instrument,
            Pageable pageable);

    /** 마감: 미삭제 + (status=CLOSED or 마감일 지남) + 악기 필터. 최신순. */
    @Query("select p from RecruitmentPosting p "
            + "where p.deletedAt is null "
            + "and (p.status = :closedStatus or (p.deadline is not null and p.deadline <= :now)) "
            + "and (:instrument is null or :instrument member of p.instruments) "
            + "order by p.createdAt desc, p.id desc")
    Page<RecruitmentPosting> findClosed(@Param("closedStatus") RecruitmentStatus closedStatus,
            @Param("now") LocalDateTime now, @Param("instrument") Instrument instrument,
            Pageable pageable);

    /** 전체(미삭제) + 악기 필터. 최신순. */
    @Query("select p from RecruitmentPosting p "
            + "where p.deletedAt is null "
            + "and (:instrument is null or :instrument member of p.instruments) "
            + "order by p.createdAt desc, p.id desc")
    Page<RecruitmentPosting> findAllActive(@Param("instrument") Instrument instrument,
            Pageable pageable);
}
