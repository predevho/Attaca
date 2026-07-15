package com.back.domain.member.service;

import com.back.domain.member.dto.ProfileImageResponse;
import com.back.domain.member.dto.ProfileResponse;
import com.back.domain.member.dto.UpdateProfileRequest;
import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.MemberProfile;
import com.back.domain.member.repository.MemberProfileRepository;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileService;
import com.back.global.storage.StoredFile;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 프로필 서비스. 프로필은 가입 시 만들지 않고 첫 수정/업로드 때 생성한다(lazy upsert).
 * 파일은 FileService만 경유한다(FileStorage 직접 호출 금지).
 */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private static final String PROFILE_IMAGE_DIRECTORY = "profile";

    private final MemberRepository memberRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(Long memberId) {
        return memberProfileRepository.findByMemberId(memberId)
                .map(this::toResponse)
                .orElseGet(ProfileResponse::empty);
    }

    @Transactional
    public ProfileResponse updateMyProfile(Long memberId, UpdateProfileRequest request) {
        MemberProfile profile = getOrCreate(memberId);
        Set<Instrument> instruments = request.instruments() == null
                ? Set.of()
                : Set.copyOf(request.instruments());
        profile.updateInfo(instruments, request.bio());
        return toResponse(profile);
    }

    @Transactional
    public ProfileImageResponse updateProfileImage(Long memberId, MultipartFile file) {
        validateImage(file);
        MemberProfile profile = getOrCreate(memberId);
        String oldKey = profile.getProfileImageKey();

        StoredFile stored = fileService.upload(file, PROFILE_IMAGE_DIRECTORY, memberId);
        profile.changeImage(stored.storageKey());

        // 새 이미지 저장이 확정된 뒤에만 옛 파일을 제거한다 — 실패해도 이미지 유실 없음.
        if (oldKey != null) {
            fileService.delete(oldKey);
        }
        return new ProfileImageResponse(stored.url());
    }

    /** 이미지 타입 정책은 MEMBER 도메인의 책임(DOMAIN-COMMON-STATUTE §7). 크기 상한은 전역 multipart 설정이 담당. */
    private void validateImage(MultipartFile file) {
        if (file == null || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private MemberProfile getOrCreate(Long memberId) {
        return memberProfileRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                    return memberProfileRepository.save(MemberProfile.create(member));
                });
    }

    private ProfileResponse toResponse(MemberProfile profile) {
        String url = profile.getProfileImageKey() == null
                ? null
                : fileService.getUrl(profile.getProfileImageKey());
        return ProfileResponse.from(profile, url);
    }
}
