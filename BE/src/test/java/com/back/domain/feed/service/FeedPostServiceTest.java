package com.back.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.feed.dto.CreatePostRequest;
import com.back.domain.feed.dto.CursorPage;
import com.back.domain.feed.dto.PostResponse;
import com.back.domain.feed.dto.UpdatePostRequest;
import com.back.domain.feed.entity.PostLike;
import com.back.domain.feed.repository.CommentRepository;
import com.back.domain.feed.repository.PostLikeRepository;
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
class FeedPostServiceTest {

    @Autowired PostRepository postRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired VerificationApplicationRepository verificationApplicationRepository;

    private FeedPostService service;
    private Long authorId;

    @BeforeEach
    void setUp() {
        VerifiedPerformerService vps = new VerifiedPerformerService(verificationApplicationRepository);
        MemberQueryService memberQueryService = new MemberQueryService(memberRepository, vps);
        service = new FeedPostService(postRepository, postLikeRepository, commentRepository,
                memberQueryService);
        Member author = memberRepository.save(Member.createLocal("w", "pw", "w@x.com", "글쓴이"));
        authorId = author.getId();
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 게시글을_작성하면_작성자_표시정보가_실린다() {
        PostResponse response = service.createPost(authorId, new CreatePostRequest("안녕"));

        assertThat(response.content()).isEqualTo("안녕");
        assertThat(response.author().nickname()).isEqualTo("글쓴이");
        assertThat(response.likeCount()).isZero();
        assertThat(response.commentCount()).isZero();
        assertThat(response.likedByMe()).isFalse();
    }

    @Test
    void 단건_조회는_좋아요수와_내좋아요를_반영한다() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        postLikeRepository.save(PostLike.create(authorId, created.id()));

        PostResponse response = service.getPost(authorId, created.id());

        assertThat(response.likeCount()).isEqualTo(1);
        assertThat(response.likedByMe()).isTrue();
    }

    @Test
    void 삭제된_게시글_조회는_POST_NOT_FOUND() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        service.deletePost(authorId, false, created.id());

        assertThatThrownBy(() -> service.getPost(authorId, created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    void 타임라인은_최신순_커서페이지를_돌려준다() {
        PostResponse p1 = service.createPost(authorId, new CreatePostRequest("1"));
        PostResponse p2 = service.createPost(authorId, new CreatePostRequest("2"));
        PostResponse p3 = service.createPost(authorId, new CreatePostRequest("3"));

        CursorPage<PostResponse> firstPage = service.getTimeline(authorId, null, 2);
        assertThat(firstPage.items()).extracting(PostResponse::id)
                .containsExactly(p3.id(), p2.id());
        assertThat(firstPage.nextCursor()).isEqualTo(p2.id());

        CursorPage<PostResponse> secondPage = service.getTimeline(authorId, firstPage.nextCursor(), 2);
        assertThat(secondPage.items()).extracting(PostResponse::id).containsExactly(p1.id());
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void 작성자가_아니면_수정은_FORBIDDEN() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        Member other = memberRepository.save(Member.createLocal("o", "pw", "o@x.com", "남"));

        assertThatThrownBy(() -> service.editPost(other.getId(), created.id(),
                new UpdatePostRequest("바꿈")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void 작성자는_수정할_수_있다() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("이전"));

        PostResponse updated = service.editPost(authorId, created.id(), new UpdatePostRequest("이후"));

        assertThat(updated.content()).isEqualTo("이후");
    }

    @Test
    void 타인_삭제는_FORBIDDEN이지만_어드민은_가능하다() {
        PostResponse created = service.createPost(authorId, new CreatePostRequest("글"));
        Member other = memberRepository.save(Member.createLocal("o2", "pw", "o2@x.com", "남2"));

        assertThatThrownBy(() -> service.deletePost(other.getId(), false, created.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(errorOf(t)).isEqualTo(ErrorCode.FORBIDDEN));

        service.deletePost(other.getId(), true, created.id()); // 어드민 모더레이션
        assertThat(postRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
    }
}
