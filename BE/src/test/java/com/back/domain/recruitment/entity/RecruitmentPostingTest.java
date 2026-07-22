package com.back.domain.recruitment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.member.entity.Instrument;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecruitmentPostingTest {

    private RecruitmentPosting posting(LocalDateTime deadline) {
        return RecruitmentPosting.create(1L, "첼로 구합니다", "정기연주회 단원 모집",
                Set.of(Instrument.CELLO, Instrument.VIOLA), 2, "서울 강남", "회당 5만원", deadline);
    }

    @Test
    void 생성되면_OPEN이고_필드가_담긴다() {
        RecruitmentPosting p = posting(LocalDateTime.now().plusDays(7));

        assertThat(p.getStatus()).isEqualTo(RecruitmentStatus.OPEN);
        assertThat(p.getAuthorId()).isEqualTo(1L);
        assertThat(p.getInstruments()).containsExactlyInAnyOrder(Instrument.CELLO, Instrument.VIOLA);
        assertThat(p.getRecruitCount()).isEqualTo(2);
        assertThat(p.isDeleted()).isFalse();
    }

    @Test
    void edit는_본문을_전체교체한다() {
        RecruitmentPosting p = posting(LocalDateTime.now().plusDays(7));

        p.edit("바이올린 구함", "바뀐 설명", Set.of(Instrument.VIOLIN), 1, "부산", "무보수", null);

        assertThat(p.getTitle()).isEqualTo("바이올린 구함");
        assertThat(p.getInstruments()).containsExactly(Instrument.VIOLIN);
        assertThat(p.getRecruitCount()).isEqualTo(1);
        assertThat(p.getDeadline()).isNull();
    }

    @Test
    void close하면_CLOSED이고_isClosed는_true() {
        RecruitmentPosting p = posting(LocalDateTime.now().plusDays(7));

        p.close();

        assertThat(p.getStatus()).isEqualTo(RecruitmentStatus.CLOSED);
        assertThat(p.isClosed(LocalDateTime.now())).isTrue();
    }

    @Test
    void deadline이_지나면_OPEN이어도_isClosed는_true() {
        RecruitmentPosting past = posting(LocalDateTime.now().minusDays(1));
        RecruitmentPosting future = posting(LocalDateTime.now().plusDays(1));
        RecruitmentPosting always = posting(null);

        assertThat(past.isClosed(LocalDateTime.now())).isTrue();
        assertThat(future.isClosed(LocalDateTime.now())).isFalse();
        assertThat(always.isClosed(LocalDateTime.now())).isFalse();
    }

    @Test
    void delete하면_isDeleted가_true() {
        RecruitmentPosting p = posting(null);

        p.delete();

        assertThat(p.isDeleted()).isTrue();
    }
}
