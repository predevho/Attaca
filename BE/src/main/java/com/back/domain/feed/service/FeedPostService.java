package com.back.domain.feed.service;

import com.back.domain.feed.dto.CreatePostRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.dto.PostResponse;
import com.back.domain.feed.dto.UpdatePostRequest;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.IdCount;
import com.back.domain.feed.repository.PostLikeRepository;
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

/** 게시글 작성/조회/타임라인/수정/삭제. 작성자 표시정보는 MEMBER 협력으로 파생한다. */
@Service
@RequiredArgsConstructor
public class FeedPostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final MemberQueryService memberQueryService;

    @Transactional
    public PostResponse createPost(Long authorId, CreatePostRequest request) {
        Post saved = postRepository.save(Post.create(authorId, request.content()));
        return toSingleResponse(authorId, saved);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(Long viewerId, Long postId) {
        Post post = findActivePost(postId);
        return toSingleResponse(viewerId, post);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getTimeline(Long viewerId, Long cursor, int size) {
        List<Post> posts = postRepository.findTimeline(cursor, PageRequest.of(0, size + 1));
        boolean hasNext = posts.size() > size;
        List<Post> page = hasNext ? posts.subList(0, size) : posts;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Set<Long> authorIds = page.stream().map(Post::getAuthorId).collect(Collectors.toSet());

        Map<Long, MemberDisplay> authors = memberQueryService.findDisplaysByIds(authorIds);
        Map<Long, Long> likeCounts = toCountMap(postLikeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByPostIds(postIds));
        Set<Long> likedIds = Set.copyOf(postLikeRepository.findLikedPostIds(viewerId, postIds));

        List<PostResponse> items = page.stream()
                .map(p -> toResponse(p, authors.get(p.getAuthorId()),
                        likeCounts.getOrDefault(p.getId(), 0L),
                        commentCounts.getOrDefault(p.getId(), 0L),
                        likedIds.contains(p.getId())))
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public PostResponse editPost(Long editorId, Long postId, UpdatePostRequest request) {
        Post post = findActivePost(postId);
        if (!post.getAuthorId().equals(editorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.edit(request.content());
        return toSingleResponse(editorId, post);
    }

    @Transactional
    public void deletePost(Long requesterId, boolean requesterIsAdmin, Long postId) {
        Post post = findActivePost(postId);
        if (!post.getAuthorId().equals(requesterId) && !requesterIsAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.delete();
    }

    private Post findActivePost(Long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private PostResponse toSingleResponse(Long viewerId, Post post) {
        MemberDisplay author = memberQueryService.findDisplaysByIds(Set.of(post.getAuthorId()))
                .get(post.getAuthorId());
        long likeCount = postLikeRepository.countByPostId(post.getId());
        long commentCount = commentRepository.countByPostIdAndDeletedAtIsNull(post.getId());
        boolean likedByMe = postLikeRepository.existsByMemberIdAndPostId(viewerId, post.getId());
        return toResponse(post, author, likeCount, commentCount, likedByMe);
    }

    private PostResponse toResponse(Post post, MemberDisplay author, long likeCount,
            long commentCount, boolean likedByMe) {
        return new PostResponse(post.getId(), author, post.getContent(), likeCount, commentCount,
                likedByMe, post.getCreatedAt(), post.getUpdatedAt());
    }

    private Map<Long, Long> toCountMap(List<IdCount> counts) {
        return counts.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getCount));
    }
}
