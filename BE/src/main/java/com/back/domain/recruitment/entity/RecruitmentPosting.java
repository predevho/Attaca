package com.back.domain.recruitment.entity;

import com.back.domain.member.entity.Instrument;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구인 공고. 작성자는 원시 authorId(Long)로만 참조한다. 삭제는 soft delete. */
@Entity
@Table(name = "recruitment_posting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitmentPosting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    /** 모집 파트(다중). 비어있지 않게 저장한다(검증은 요청 DTO에서). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recruitment_posting_instrument",
            joinColumns = @JoinColumn(name = "posting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument", length = 20)
    private Set<Instrument> instruments = new LinkedHashSet<>();

    /** 모집 인원. 미기재 허용. */
    private Integer recruitCount;

    @Column(length = 200)
    private String location;

    @Column(length = 200)
    private String fee;

    /** 모집 마감 일시. null 이면 상시 모집. */
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitmentStatus status;

    private LocalDateTime deletedAt;

    private RecruitmentPosting(Long authorId, String title, String description,
            Collection<Instrument> instruments, Integer recruitCount, String location, String fee,
            LocalDateTime deadline) {
        this.authorId = authorId;
        this.title = title;
        this.description = description;
        if (instruments != null) {
            this.instruments.addAll(instruments);
        }
        this.recruitCount = recruitCount;
        this.location = location;
        this.fee = fee;
        this.deadline = deadline;
        this.status = RecruitmentStatus.OPEN;
    }

    public static RecruitmentPosting create(Long authorId, String title, String description,
            Set<Instrument> instruments, Integer recruitCount, String location, String fee,
            LocalDateTime deadline) {
        return new RecruitmentPosting(authorId, title, description, instruments, recruitCount,
                location, fee, deadline);
    }

    /** 본문 필드를 전체 교체한다(PUT 시맨틱). 작성자·상태·삭제는 바꾸지 않는다. */
    public void edit(String title, String description, Set<Instrument> instruments,
            Integer recruitCount, String location, String fee, LocalDateTime deadline) {
        this.title = title;
        this.description = description;
        this.instruments.clear();
        if (instruments != null) {
            this.instruments.addAll(instruments);
        }
        this.recruitCount = recruitCount;
        this.location = location;
        this.fee = fee;
        this.deadline = deadline;
    }

    public void close() {
        this.status = RecruitmentStatus.CLOSED;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 마감 여부(파생): 수동 CLOSED 이거나 deadline 이 지났으면 마감. */
    public boolean isClosed(LocalDateTime now) {
        return status == RecruitmentStatus.CLOSED
                || (deadline != null && !now.isBefore(deadline));
    }
}
