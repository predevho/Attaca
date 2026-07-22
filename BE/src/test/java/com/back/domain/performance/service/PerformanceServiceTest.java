package com.back.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.performance.dto.PerformanceRequest;
import com.back.domain.performance.dto.PerformanceResponse;
import com.back.domain.performance.dto.PerformanceScope;
import com.back.domain.performance.repository.PerformanceRepository;
import com.back.domain.verifiedperformer.dto.GrantRequest;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileMetadataRepository;
import com.back.global.storage.FileService;
import com.back.global.storage.FileStorage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

@DataJpaTest
class PerformanceServiceTest {

    @Autowired PerformanceRepository performanceRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;
    @Autowired FileMetadataRepository fileMetadataRepository;

    private VerifiedPerformerService verifiedPerformerService;
    private PerformanceService service;
    private Long verifiedId;
    private Long normalId;

    @BeforeEach
    void setUp() {
        verifiedPerformerService = new VerifiedPerformerService(verificationApplicationRepository);
        MemberQueryService memberQueryService =
                new MemberQueryService(memberRepository, verifiedPerformerService);
        FileService fileService = new FileService(new FakeFileStorage(), fileMetadataRepository);
        service = new PerformanceService(performanceRepository, memberQueryService,
                verifiedPerformerService, fileService);

        Member verified = memberRepository.save(
                Member.createLocal("perf", "pw", "perf@x.com", "연주자"));
        verifiedId = verified.getId();
        verifiedPerformerService.grant(new GrantRequest(verifiedId, "지정"), 99L); // 인증 처리
        Member normal = memberRepository.save(Member.createLocal("norm", "pw", "norm@x.com", "일반"));
        normalId = normal.getId();
    }

    private PerformanceRequest request(LocalDateTime when) {
        return new PerformanceRequest("리사이틀", "소개", when, "예술의전당", "베토벤", "3만원",
                "https://t.example/1");
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 인증연주자는_등록할_수_있고_주최자정보가_실린다() {
        PerformanceResponse response = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThat(response.title()).isEqualTo("리사이틀");
        assertThat(response.organizer().nickname()).isEqualTo("연주자");
        assertThat(response.organizer().verified()).isTrue();
        assertThat(response.posterImageUrl()).isNull();
    }

    @Test
    void 인증연주자가_아니면_등록은_NOT_VERIFIED_PERFORMER() {
        assertThatThrownBy(() -> service.register(normalId, false,
                request(LocalDateTime.now().plusDays(3))))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.NOT_VERIFIED_PERFORMER));
    }

    @Test
    void 어드민은_인증연주자가_아니어도_등록할_수_있다() {
        PerformanceResponse response = service.register(normalId, true,
                request(LocalDateTime.now().plusDays(3)));

        assertThat(response.title()).isEqualTo("리사이틀");
    }

    @Test
    void 단건_조회는_삭제되면_PERFORMANCE_NOT_FOUND() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));
        service.deletePerformance(verifiedId, false, created.id());

        assertThatThrownBy(() -> service.getPerformance(created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND));
    }

    @Test
    void 목록은_scope로_구분되고_주최자정보를_배치로_채운다() {
        LocalDateTime now = LocalDateTime.now();
        service.register(verifiedId, false, request(now.plusDays(1)));
        service.register(verifiedId, false, request(now.minusDays(1)));

        assertThat(service.getPerformances(PerformanceScope.UPCOMING, PageRequest.of(0, 10))
                .getContent()).hasSize(1);
        assertThat(service.getPerformances(PerformanceScope.PAST, PageRequest.of(0, 10))
                .getContent()).hasSize(1);
        assertThat(service.getPerformances(PerformanceScope.ALL, PageRequest.of(0, 10))
                .getContent()).hasSize(2);
        assertThat(service.getPerformances(PerformanceScope.ALL, PageRequest.of(0, 10))
                .getContent().get(0).organizer().nickname()).isEqualTo("연주자");
    }

    @Test
    void 주최자가_아니면_수정은_FORBIDDEN() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThatThrownBy(() -> service.editPerformance(normalId, created.id(),
                request(LocalDateTime.now().plusDays(5))))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 주최자는_수정할_수_있다() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        PerformanceResponse updated = service.editPerformance(verifiedId, created.id(),
                new PerformanceRequest("바뀐공연", "소개2", LocalDateTime.now().plusDays(5), "롯데홀",
                        null, null, null));

        assertThat(updated.title()).isEqualTo("바뀐공연");
        assertThat(updated.venue()).isEqualTo("롯데홀");
    }

    @Test
    void 타인_삭제는_FORBIDDEN이지만_어드민은_가능하다() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThatThrownBy(() -> service.deletePerformance(normalId, false, created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        service.deletePerformance(normalId, true, created.id()); // 어드민 모더레이션
        assertThat(performanceRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "poster.png", "image/png",
                "img".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 주최자는_포스터를_올릴_수_있고_url이_실린다() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        PerformanceResponse withPoster = service.updatePoster(verifiedId, created.id(), png());

        assertThat(withPoster.posterImageUrl()).isNotBlank();
        assertThat(performanceRepository.findByIdAndDeletedAtIsNull(created.id())
                .orElseThrow().getPosterImageKey()).startsWith("performance/").endsWith(".png");
    }

    @Test
    void 주최자가_아니면_포스터_업로드는_FORBIDDEN() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));

        assertThatThrownBy(() -> service.updatePoster(normalId, created.id(), png()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 이미지가_아닌_포스터는_INVALID_FILE() {
        PerformanceResponse created = service.register(verifiedId, false,
                request(LocalDateTime.now().plusDays(3)));
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf",
                "x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.updatePoster(verifiedId, created.id(), pdf))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.INVALID_FILE));
    }

    /** 디스크/AWS 없이 검증하기 위한 인메모리 저장소(FileServiceTest 패턴). */
    static class FakeFileStorage implements FileStorage {
        final Map<String, byte[]> stored = new HashMap<>();

        @Override
        public String upload(String key, InputStream content, long size, String contentType) {
            try {
                stored.put(key, content.readAllBytes());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return key;
        }

        @Override
        public void delete(String key) {
            stored.remove(key);
        }

        @Override
        public String getUrl(String key) {
            return "http://fake/" + key;
        }
    }
}
