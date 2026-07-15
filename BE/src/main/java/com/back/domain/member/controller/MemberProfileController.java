package com.back.domain.member.controller;

import com.back.domain.member.dto.ProfileImageResponse;
import com.back.domain.member.dto.ProfileOptionsResponse;
import com.back.domain.member.dto.ProfileResponse;
import com.back.domain.member.dto.UpdateProfileRequest;
import com.back.domain.member.service.MemberProfileService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 프로필 API. 모두 인증 필요(기본 인가 규칙) — principal은 JWT의 회원 id(Long).
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    @GetMapping("/me/profile")
    public ApiResponse<ProfileResponse> getMyProfile(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(memberProfileService.getMyProfile(memberId));
    }

    @PutMapping("/me/profile")
    public ApiResponse<ProfileResponse> updateMyProfile(@AuthenticationPrincipal Long memberId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(memberProfileService.updateMyProfile(memberId, request));
    }

    @PutMapping("/me/profile/image")
    public ApiResponse<ProfileImageResponse> updateProfileImage(@AuthenticationPrincipal Long memberId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(memberProfileService.updateProfileImage(memberId, file));
    }

    @GetMapping("/profile-options")
    public ApiResponse<ProfileOptionsResponse> profileOptions() {
        return ApiResponse.success(ProfileOptionsResponse.create());
    }
}
