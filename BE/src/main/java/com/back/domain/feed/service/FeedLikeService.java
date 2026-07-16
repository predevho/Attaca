package com.back.domain.feed.service;

import com.back.domain.feed.entity.CommentLike;
import com.back.domain.feed.entity.PostLike;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostLikeRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글/댓글 좋아요·취소. 모두 멱등(중복 좋아요·중복 취소는 no-op). */
@Service
@RequiredArgsConstructor
public class FeedLikeService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;

    @Transactional
    public void likePost(Long memberId, Long postId) {
        requireActivePost(postId);
        if (postLikeRepository.existsByMemberIdAndPostId(memberId, postId)) {
            return; // 이미 좋아요 상태 — no-op
        }
        try {
            postLikeRepository.saveAndFlush(PostLike.create(memberId, postId));
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 요청 — 유니크 제약이 최종 방어선, 멱등하게 무시
        }
    }

    @Transactional
    public void unlikePost(Long memberId, Long postId) {
        postLikeRepository.deleteByMemberIdAndPostId(memberId, postId);
    }

    @Transactional
    public void likeComment(Long memberId, Long commentId) {
        requireActiveComment(commentId);
        if (commentLikeRepository.existsByMemberIdAndCommentId(memberId, commentId)) {
            return; // 이미 좋아요 상태 — no-op
        }
        try {
            commentLikeRepository.saveAndFlush(CommentLike.create(memberId, commentId));
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 요청 — 유니크 제약이 최종 방어선, 멱등하게 무시
        }
    }

    @Transactional
    public void unlikeComment(Long memberId, Long commentId) {
        commentLikeRepository.deleteByMemberIdAndCommentId(memberId, commentId);
    }

    private void requireActivePost(Long postId) {
        if (postRepository.findByIdAndDeletedAtIsNull(postId).isEmpty()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void requireActiveComment(Long commentId) {
        if (commentRepository.findByIdAndDeletedAtIsNull(commentId).isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }
}
