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
        if (!postLikeRepository.existsByMemberIdAndPostId(memberId, postId)) {
            postLikeRepository.save(PostLike.create(memberId, postId));
        }
    }

    @Transactional
    public void unlikePost(Long memberId, Long postId) {
        postLikeRepository.deleteByMemberIdAndPostId(memberId, postId);
    }

    @Transactional
    public void likeComment(Long memberId, Long commentId) {
        requireActiveComment(commentId);
        if (!commentLikeRepository.existsByMemberIdAndCommentId(memberId, commentId)) {
            commentLikeRepository.save(CommentLike.create(memberId, commentId));
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
