package com.back.domain.feed.service;

import com.back.domain.feed.dto.CommentResponse;
import com.back.domain.feed.dto.CreateCommentRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.entity.Comment;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.IdCount;
import com.back.domain.feed.repository.PostRepository;
import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.service.MemberQueryService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평면 댓글 작성/목록/삭제. 대상 게시글 존재(미삭제)를 서비스에서 확인한다. */
@Service
@RequiredArgsConstructor
public class FeedCommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final MemberQueryService memberQueryService;

    @Transactional
    public CommentResponse createComment(Long authorId, Long postId, CreateCommentRequest request) {
        requireActivePost(postId);
        Comment saved = commentRepository.save(Comment.create(postId, authorId, request.content()));
        MemberDisplay author = memberQueryService.findDisplaysByIds(Set.of(authorId)).get(authorId);
        return toResponse(saved, author, 0L, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> getComments(Long viewerId, Long postId, Long cursor,
            int size) {
        requireActivePost(postId);
        List<Comment> comments =
                commentRepository.findCommentPage(postId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = comments.size() > size;
        List<Comment> page = hasNext ? comments.subList(0, size) : comments;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }
        List<Long> commentIds = page.stream().map(Comment::getId).toList();
        Set<Long> authorIds = page.stream().map(Comment::getAuthorId).collect(Collectors.toSet());

        Map<Long, MemberDisplay> authors = memberQueryService.findDisplaysByIds(authorIds);
        Map<Long, Long> likeCounts = commentLikeRepository.countByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCount));
        Set<Long> likedIds =
                Set.copyOf(commentLikeRepository.findLikedCommentIds(viewerId, commentIds));

        List<CommentResponse> items = page.stream()
                .map(c -> toResponse(c, authors.get(c.getAuthorId()),
                        likeCounts.getOrDefault(c.getId(), 0L), likedIds.contains(c.getId())))
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public void deleteComment(Long requesterId, boolean requesterIsAdmin, Long commentId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getAuthorId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.delete();
    }

    private void requireActivePost(Long postId) {
        if (postRepository.findByIdAndDeletedAtIsNull(postId).isEmpty()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private CommentResponse toResponse(Comment comment, MemberDisplay author, long likeCount,
            boolean likedByMe) {
        return new CommentResponse(comment.getId(), comment.getPostId(), author,
                comment.getContent(), likeCount, likedByMe, comment.getCreatedAt());
    }
}
