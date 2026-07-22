package com.back.domain.recruitment.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Instrument;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.recruitment.dto.RecruitmentPostingRequest;
import com.back.domain.recruitment.dto.RecruitmentPostingResponse;
import com.back.domain.recruitment.dto.RecruitmentScope;
import com.back.domain.recruitment.entity.RecruitmentPosting;
import com.back.domain.recruitment.entity.RecruitmentStatus;
import com.back.domain.recruitment.repository.RecruitmentPostingRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구인 공고 등록/조회/목록/수정/마감/삭제. 등록 자격은 인증 회원 누구나.
 * 작성자 표시정보는 MEMBER 협력(배치)으로 파생한다.
 */
@Service
@RequiredArgsConstructor
public class RecruitmentPostingService {

    private final RecruitmentPostingRepository postingRepository;
    private final MemberQueryService memberQueryService;

    @Transactional
    public RecruitmentPostingResponse register(Long authorId, RecruitmentPostingRequest request) {
        RecruitmentPosting saved = postingRepository.save(RecruitmentPosting.create(authorId,
                request.title(), request.description(), request.instruments(),
                request.recruitCount(), request.location(), request.fee(), request.deadline()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecruitmentPostingResponse getPosting(Long id) {
        return toResponse(findActive(id));
    }

    @Transactional(readOnly = true)
    public Page<RecruitmentPostingResponse> getPostings(RecruitmentScope scope,
            Instrument instrument, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<RecruitmentPosting> page = switch (scope) {
            case OPEN -> postingRepository.findOpen(RecruitmentStatus.OPEN, now, instrument, pageable);
            case CLOSED -> postingRepository.findClosed(RecruitmentStatus.CLOSED, now, instrument,
                    pageable);
            case ALL -> postingRepository.findAllActive(instrument, pageable);
        };
        Set<Long> authorIds = page.getContent().stream()
                .map(RecruitmentPosting::getAuthorId).collect(Collectors.toSet());
        Map<Long, MemberDisplay> authors = memberQueryService.findDisplaysByIds(authorIds);
        return page.map(p -> RecruitmentPostingResponse.of(p, authors.get(p.getAuthorId()), now));
    }

    @Transactional
    public RecruitmentPostingResponse editPosting(Long editorId, Long id,
            RecruitmentPostingRequest request) {
        RecruitmentPosting posting = findActive(id);
        requireAuthor(posting, editorId);
        posting.edit(request.title(), request.description(), request.instruments(),
                request.recruitCount(), request.location(), request.fee(), request.deadline());
        return toResponse(posting);
    }

    @Transactional
    public RecruitmentPostingResponse closePosting(Long requesterId, Long id) {
        RecruitmentPosting posting = findActive(id);
        requireAuthor(posting, requesterId);
        posting.close();
        return toResponse(posting);
    }

    @Transactional
    public void deletePosting(Long requesterId, boolean requesterIsAdmin, Long id) {
        RecruitmentPosting posting = findActive(id);
        if (!posting.getAuthorId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        posting.delete();
    }

    /** 미삭제 공고 조회. 지원 서비스(Task 6)에서 재사용한다. 없으면 RECRUITMENT_NOT_FOUND. */
    @Transactional(readOnly = true)
    public RecruitmentPosting findActive(Long id) {
        return postingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
    }

    private void requireAuthor(RecruitmentPosting posting, Long requesterId) {
        if (!posting.getAuthorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private RecruitmentPostingResponse toResponse(RecruitmentPosting posting) {
        MemberDisplay author = memberQueryService
                .findDisplaysByIds(Set.of(posting.getAuthorId()))
                .get(posting.getAuthorId());
        return RecruitmentPostingResponse.of(posting, author, LocalDateTime.now());
    }
}
