package com.back.domain.feed.controller;

import com.back.domain.feed.dto.CreatePostRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.dto.PostResponse;
import com.back.domain.feed.dto.UpdatePostRequest;
import com.back.domain.feed.service.FeedPostService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 게시글 API. 모두 인증 필요(principal = memberId). */
@RestController
@RequestMapping("/api/feed/posts")
@RequiredArgsConstructor
public class FeedPostController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FeedPostService feedPostService;

    @PostMapping
    public ApiResponse<PostResponse> create(@AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(feedPostService.createPost(memberId, request));
    }

    @GetMapping
    public ApiResponse<CursorPage<PostResponse>> timeline(@AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(feedPostService.getTimeline(memberId, cursor, clamp(size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PostResponse> get(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(feedPostService.getPost(memberId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<PostResponse> edit(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id, @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.success(feedPostService.editPost(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @PathVariable Long id) {
        feedPostService.deletePost(memberId, isAdmin(authentication), id);
        return ApiResponse.success();
    }

    private int clamp(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
