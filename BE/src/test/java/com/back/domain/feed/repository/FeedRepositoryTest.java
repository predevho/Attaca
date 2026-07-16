package com.back.domain.feed.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.feed.entity.Comment;
import com.back.domain.feed.entity.CommentLike;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.entity.PostLike;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class FeedRepositoryTest {

    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired CommentLikeRepository commentLikeRepository;

    @Test
    void 타임라인은_미삭제글을_id_내림차순_커서로_가져온다() {
        Post p1 = postRepository.save(Post.create(1L, "1"));
        Post p2 = postRepository.save(Post.create(1L, "2"));
        Post p3 = postRepository.save(Post.create(1L, "3"));
        p2.delete();
        postRepository.saveAndFlush(p2);

        List<Post> firstPage = postRepository.findTimeline(null, PageRequest.of(0, 10));
        assertThat(firstPage).extracting(Post::getId).containsExactly(p3.getId(), p1.getId());

        List<Post> afterCursor = postRepository.findTimeline(p3.getId(), PageRequest.of(0, 10));
        assertThat(afterCursor).extracting(Post::getId).containsExactly(p1.getId());
    }

    @Test
    void 미삭제_단건만_조회된다() {
        Post post = postRepository.save(Post.create(1L, "글"));
        assertThat(postRepository.findByIdAndDeletedAtIsNull(post.getId())).isPresent();

        post.delete();
        postRepository.saveAndFlush(post);
        assertThat(postRepository.findByIdAndDeletedAtIsNull(post.getId())).isEmpty();
    }

    @Test
    void 댓글은_미삭제만_오래된순_커서로_가져온다() {
        Comment c1 = commentRepository.save(Comment.create(10L, 1L, "a"));
        Comment c2 = commentRepository.save(Comment.create(10L, 1L, "b"));
        Comment other = commentRepository.save(Comment.create(99L, 1L, "다른글"));

        List<Comment> page = commentRepository.findCommentPage(10L, null, PageRequest.of(0, 10));
        assertThat(page).extracting(Comment::getId).containsExactly(c1.getId(), c2.getId());
    }

    @Test
    void 좋아요_수를_게시글별로_배치_집계한다() {
        postLikeRepository.save(PostLike.create(1L, 10L));
        postLikeRepository.save(PostLike.create(2L, 10L));
        postLikeRepository.save(PostLike.create(1L, 11L));

        Map<Long, Long> counts = postLikeRepository.countByPostIds(List.of(10L, 11L)).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCount));

        assertThat(counts).containsEntry(10L, 2L).containsEntry(11L, 1L);
    }

    @Test
    void 내가_좋아요한_게시글_id를_배치로_가져온다() {
        postLikeRepository.save(PostLike.create(1L, 10L));
        postLikeRepository.save(PostLike.create(2L, 11L));

        List<Long> liked = postLikeRepository.findLikedPostIds(1L, List.of(10L, 11L));
        assertThat(liked).containsExactly(10L);
    }

    @Test
    void 좋아요_수를_댓글별로_배치_집계한다() {
        commentLikeRepository.save(CommentLike.create(1L, 20L));
        commentLikeRepository.save(CommentLike.create(2L, 20L));
        commentLikeRepository.save(CommentLike.create(1L, 21L));

        Map<Long, Long> counts = commentLikeRepository.countByCommentIds(List.of(20L, 21L)).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCount));

        assertThat(counts).containsEntry(20L, 2L).containsEntry(21L, 1L);
    }

    @Test
    void 내가_좋아요한_댓글_id를_배치로_가져온다() {
        commentLikeRepository.save(CommentLike.create(1L, 20L));
        commentLikeRepository.save(CommentLike.create(2L, 21L));

        List<Long> liked = commentLikeRepository.findLikedCommentIds(1L, List.of(20L, 21L));
        assertThat(liked).containsExactly(20L);
    }

    @Test
    void 댓글_커서는_다음_페이지를_오래된순으로_이어서_가져온다() {
        Comment c1 = commentRepository.save(Comment.create(10L, 1L, "a"));
        Comment c2 = commentRepository.save(Comment.create(10L, 1L, "b"));
        Comment c3 = commentRepository.save(Comment.create(10L, 1L, "c"));

        List<Comment> firstPage = commentRepository.findCommentPage(10L, null, PageRequest.of(0, 2));
        assertThat(firstPage).extracting(Comment::getId).containsExactly(c1.getId(), c2.getId());

        List<Comment> secondPage =
                commentRepository.findCommentPage(10L, c2.getId(), PageRequest.of(0, 2));
        assertThat(secondPage).extracting(Comment::getId).containsExactly(c3.getId());
    }
}
