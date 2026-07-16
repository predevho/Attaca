package com.back.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.feed.dto.CommentResponse;
import com.back.domain.feed.dto.CreateCommentRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.entity.Post;
import com.back.domain.feed.repository.CommentLikeRepository;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.MemberQueryService;
import com.back.domain.verifiedperformer.repository.VerificationApplicationRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class FeedCommentServiceTest {

    @Autowired PostRepository postRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired CommentLikeRepository commentLikeRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private FeedCommentService service;
    private Long memberId;
    private Long postId;

    @BeforeEach
    void setUp() {
        VerifiedPerformerService vps = new VerifiedPerformerService(verificationApplicationRepository);
        MemberQueryService memberQueryService = new MemberQueryService(memberRepository, vps);
        service = new FeedCommentService(postRepository, commentRepository, commentLikeRepository,
                memberQueryService);
        Member member = memberRepository.save(Member.createLocal("c", "pw", "c@x.com", "댓쓴이"));
        memberId = member.getId();
        Post post = postRepository.save(Post.create(memberId, "글"));
        postId = post.getId();
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 댓글을_작성하면_작성자정보가_실린다() {
        CommentResponse response = service.createComment(memberId, postId,
                new CreateCommentRequest("좋은 글"));

        assertThat(response.content()).isEqualTo("좋은 글");
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.author().nickname()).isEqualTo("댓쓴이");
    }

    @Test
    void 없는_게시글에_댓글은_POST_NOT_FOUND() {
        assertThatThrownBy(() -> service.createComment(memberId, 999999L,
                new CreateCommentRequest("x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    void 댓글_목록은_오래된순_커서페이지다() {
        CommentResponse c1 = service.createComment(memberId, postId, new CreateCommentRequest("1"));
        CommentResponse c2 = service.createComment(memberId, postId, new CreateCommentRequest("2"));
        CommentResponse c3 = service.createComment(memberId, postId, new CreateCommentRequest("3"));

        CursorPage<CommentResponse> firstPage = service.getComments(memberId, postId, null, 2);
        assertThat(firstPage.items()).extracting(CommentResponse::id)
                .containsExactly(c1.id(), c2.id());
        assertThat(firstPage.nextCursor()).isEqualTo(c2.id());

        CursorPage<CommentResponse> secondPage =
                service.getComments(memberId, postId, firstPage.nextCursor(), 2);
        assertThat(secondPage.items()).extracting(CommentResponse::id).containsExactly(c3.id());
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void 타인_댓글삭제는_FORBIDDEN이고_본인은_가능하다() {
        CommentResponse comment = service.createComment(memberId, postId,
                new CreateCommentRequest("내 댓글"));
        Member other = memberRepository.save(Member.createLocal("o", "pw", "o@x.com", "남"));

        assertThatThrownBy(() -> service.deleteComment(other.getId(), false, comment.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        service.deleteComment(memberId, false, comment.id());
        assertThat(commentRepository.findByIdAndDeletedAtIsNull(comment.id())).isEmpty();
    }
}
