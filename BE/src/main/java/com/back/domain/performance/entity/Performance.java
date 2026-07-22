package com.back.domain.performance.entity;

import com.back.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 연주회/공연. 주최자는 원시 organizerId(Long)로만 참조한다. 삭제는 soft delete. */
@Entity
@Table(name = "performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime performedAt;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(length = 2000)
    private String program;

    @Column(length = 200)
    private String ticketInfo;

    @Column(length = 500)
    private String ticketUrl;

    @Column(name = "poster_image_key")
    private String posterImageKey;

    private LocalDateTime deletedAt;

    private Performance(Long organizerId, String title, String description, LocalDateTime performedAt,
            String venue, String program, String ticketInfo, String ticketUrl) {
        this.organizerId = organizerId;
        this.title = title;
        this.description = description;
        this.performedAt = performedAt;
        this.venue = venue;
        this.program = program;
        this.ticketInfo = ticketInfo;
        this.ticketUrl = ticketUrl;
    }

    public static Performance create(Long organizerId, String title, String description,
            LocalDateTime performedAt, String venue, String program, String ticketInfo,
            String ticketUrl) {
        return new Performance(organizerId, title, description, performedAt, venue, program,
                ticketInfo, ticketUrl);
    }

    /** 본문 필드를 전체 교체한다(PUT 시맨틱). 주최자·포스터·삭제상태는 바꾸지 않는다. */
    public void edit(String title, String description, LocalDateTime performedAt, String venue,
            String program, String ticketInfo, String ticketUrl) {
        this.title = title;
        this.description = description;
        this.performedAt = performedAt;
        this.venue = venue;
        this.program = program;
        this.ticketInfo = ticketInfo;
        this.ticketUrl = ticketUrl;
    }

    public void changePoster(String newKey) {
        this.posterImageKey = newKey;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
