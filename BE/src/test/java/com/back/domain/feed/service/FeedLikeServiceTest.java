package com.back.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.feed.entity.Comment;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostLikeRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class FeedLikeServiceTest {

    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired CommentLikeRepository commentLikeRepository;

    private FeedLikeService service;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        service = new FeedLikeService(postRepository, commentRepository, postLikeRepository,
                commentLikeRepository);
        Post post = postRepository.save(Post.create(1L, "글"));
        postId = post.getId();
        Comment comment = commentRepository.save(Comment.create(postId, 1L, "댓글"));
        commentId = comment.getId();
    }

    @Test
    void 게시글_좋아요는_멱등이다() {
        service.likePost(2L, postId);
        service.likePost(2L, postId); // 두 번째는 no-op

        assertThat(postLikeRepository.countByPostId(postId)).isEqualTo(1);
    }

    @Test
    void 게시글_좋아요_취소는_멱등이다() {
        service.likePost(2L, postId);

        service.unlikePost(2L, postId);
        service.unlikePost(2L, postId); // 없어도 no-op

        assertThat(postLikeRepository.countByPostId(postId)).isZero();
    }

    @Test
    void 없는_게시글_좋아요는_POST_NOT_FOUND() {
        assertThatThrownBy(() -> service.likePost(2L, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(((BusinessException) t).getErrorCode())
                        .isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    void 댓글_좋아요는_멱등이다() {
        service.likeComment(2L, commentId);
        service.likeComment(2L, commentId);

        assertThat(commentLikeRepository.countByCommentId(commentId)).isEqualTo(1);
    }

    @Test
    void 없는_댓글_좋아요는_COMMENT_NOT_FOUND() {
        assertThatThrownBy(() -> service.likeComment(2L, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(((BusinessException) t).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }
}
