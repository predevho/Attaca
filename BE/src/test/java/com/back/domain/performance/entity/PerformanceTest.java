package com.back.domain.performance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PerformanceTest {

    private Performance sample() {
        return Performance.create(1L, "봄 리사이틀", "소개",
                LocalDateTime.of(2026, 9, 1, 19, 30), "예술의전당", "베토벤 월광", "전석 3만원",
                "https://ticket.example/1");
    }

    @Test
    void create는_미삭제_공연을_만든다() {
        Performance performance = sample();

        assertThat(performance.getOrganizerId()).isEqualTo(1L);
        assertThat(performance.getTitle()).isEqualTo("봄 리사이틀");
        assertThat(performance.getVenue()).isEqualTo("예술의전당");
        assertThat(performance.getPerformedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 19, 30));
        assertThat(performance.getPosterImageKey()).isNull();
        assertThat(performance.isDeleted()).isFalse();
    }

    @Test
    void edit는_본문_필드를_전체_교체한다() {
        Performance performance = sample();

        performance.edit("가을 리사이틀", "새 소개", LocalDateTime.of(2026, 11, 1, 20, 0),
                "롯데콘서트홀", "쇼팽 발라드", "무료", null);

        assertThat(performance.getTitle()).isEqualTo("가을 리사이틀");
        assertThat(performance.getVenue()).isEqualTo("롯데콘서트홀");
        assertThat(performance.getTicketInfo()).isEqualTo("무료");
        assertThat(performance.getTicketUrl()).isNull();
    }

    @Test
    void changePoster는_key를_교체한다() {
        Performance performance = sample();

        performance.changePoster("performance/2026/09/01/a.png");

        assertThat(performance.getPosterImageKey()).isEqualTo("performance/2026/09/01/a.png");
    }

    @Test
    void delete는_deletedAt을_마킹한다() {
        Performance performance = sample();

        performance.delete();

        assertThat(performance.isDeleted()).isTrue();
        assertThat(performance.getDeletedAt()).isNotNull();
    }
}
