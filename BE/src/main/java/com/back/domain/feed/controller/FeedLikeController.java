package com.back.domain.feed.controller;

import com.back.domain.feed.service.FeedLikeService;
import com.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 좋아요 API(멱등). 응답은 빈 성공. */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedLikeController {

    private final FeedLikeService feedLikeService;

    @PostMapping("/posts/{id}/like")
    public ApiResponse<Void> likePost(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.likePost(memberId, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<Void> unlikePost(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.unlikePost(memberId, id);
        return ApiResponse.success();
    }

    @PostMapping("/comments/{id}/like")
    public ApiResponse<Void> likeComment(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.likeComment(memberId, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/comments/{id}/like")
    public ApiResponse<Void> unlikeComment(@AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        feedLikeService.unlikeComment(memberId, id);
        return ApiResponse.success();
    }
}
