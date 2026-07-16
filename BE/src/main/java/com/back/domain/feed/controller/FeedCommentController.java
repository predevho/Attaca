package com.back.domain.feed.controller;

import com.back.domain.feed.dto.CommentResponse;
import com.back.domain.feed.dto.CreateCommentRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.service.FeedCommentService;
import com.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 댓글 API. 모두 인증 필요. */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedCommentController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FeedCommentService feedCommentService;

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentResponse> create(@AuthenticationPrincipal Long memberId,
            @PathVariable Long postId, @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(feedCommentService.createComment(memberId, postId, request));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<CursorPage<CommentResponse>> list(@AuthenticationPrincipal Long memberId,
            @PathVariable Long postId, @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(
                feedCommentService.getComments(memberId, postId, cursor, clamp(size)));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
            Authentication authentication, @PathVariable Long id) {
        feedCommentService.deleteComment(memberId, FeedPostController.isAdmin(authentication), id);
        return ApiResponse.success();
    }

    private int clamp(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
