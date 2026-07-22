package com.back.domain.performance.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.performance.dto.PerformanceRequest;
import com.back.domain.performance.dto.PerformanceResponse;
import com.back.domain.performance.dto.PerformanceScope;
import com.back.domain.performance.entity.Performance;
import com.back.domain.performance.repository.PerformanceRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileService;
import com.back.global.storage.StoredFile;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 연주회 등록/조회/목록/수정/삭제. 등록 자격은 인증 연주자 또는 어드민.
 * 주최자 표시정보는 MEMBER 협력(배치)으로 파생한다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private static final String POSTER_DIRECTORY = "performance";

    private final PerformanceRepository performanceRepository;
    private final MemberQueryService memberQueryService;
    private final VerifiedPerformerService verifiedPerformerService;
    private final FileService fileService;

    @Transactional
    public PerformanceResponse register(Long organizerId, boolean isAdmin, PerformanceRequest request) {
        if (!isAdmin && !verifiedPerformerService.isVerified(organizerId)) {
            throw new BusinessException(ErrorCode.NOT_VERIFIED_PERFORMER);
        }
        Performance saved = performanceRepository.save(Performance.create(organizerId,
                request.title(), request.description(), request.performedAt(), request.venue(),
                request.program(), request.ticketInfo(), request.ticketUrl()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PerformanceResponse getPerformance(Long id) {
        return toResponse(findActive(id));
    }

    @Transactional(readOnly = true)
    public Page<PerformanceResponse> getPerformances(PerformanceScope scope, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<Performance> page = switch (scope) {
            case UPCOMING -> performanceRepository
                    .findByDeletedAtIsNullAndPerformedAtGreaterThanEqualOrderByPerformedAtAsc(now, pageable);
            case PAST -> performanceRepository
                    .findByDeletedAtIsNullAndPerformedAtLessThanOrderByPerformedAtDesc(now, pageable);
            case ALL -> performanceRepository.findByDeletedAtIsNullOrderByPerformedAtDesc(pageable);
        };
        Set<Long> organizerIds = page.getContent().stream()
                .map(Performance::getOrganizerId).collect(Collectors.toSet());
        Map<Long, MemberDisplay> organizers = memberQueryService.findDisplaysByIds(organizerIds);
        return page.map(p -> toResponse(p, organizers.get(p.getOrganizerId())));
    }

    @Transactional
    public PerformanceResponse editPerformance(Long editorId, Long id, PerformanceRequest request) {
        Performance performance = findActive(id);
        if (!performance.getOrganizerId().equals(editorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        performance.edit(request.title(), request.description(), request.performedAt(),
                request.venue(), request.program(), request.ticketInfo(), request.ticketUrl());
        return toResponse(performance);
    }

    @Transactional
    public void deletePerformance(Long requesterId, boolean requesterIsAdmin, Long id) {
        Performance performance = findActive(id);
        if (!performance.getOrganizerId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        performance.delete();
    }

    @Transactional
    public PerformanceResponse updatePoster(Long organizerId, Long id, MultipartFile file) {
        validateImage(file);
        Performance performance = findActive(id);
        if (!performance.getOrganizerId().equals(organizerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String oldKey = performance.getPosterImageKey();
        StoredFile stored = fileService.upload(file, POSTER_DIRECTORY, organizerId);
        performance.changePoster(stored.storageKey());
        // 새 이미지 저장이 확정된 뒤에만 옛 파일을 제거한다 — 실패해도 이미지 유실 없음(프로필 이미지 패턴).
        if (oldKey != null) {
            fileService.delete(oldKey);
        }
        return toResponse(performance);
    }

    /** 이미지 타입만 허용. 크기 상한은 전역 multipart 설정이 담당. */
    private void validateImage(MultipartFile file) {
        if (file == null || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private Performance findActive(Long id) {
        return performanceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    }

    private PerformanceResponse toResponse(Performance performance) {
        MemberDisplay organizer = memberQueryService
                .findDisplaysByIds(Set.of(performance.getOrganizerId()))
                .get(performance.getOrganizerId());
        return toResponse(performance, organizer);
    }

    private PerformanceResponse toResponse(Performance p, MemberDisplay organizer) {
        String posterUrl = p.getPosterImageKey() == null
                ? null
                : fileService.getUrl(p.getPosterImageKey());
        return new PerformanceResponse(p.getId(), organizer, p.getTitle(), p.getDescription(),
                p.getPerformedAt(), p.getVenue(), p.getProgram(), p.getTicketInfo(),
                p.getTicketUrl(), posterUrl, p.getCreatedAt(), p.getUpdatedAt());
    }
}
