package com.back.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.domain.member.dto.ProfileImageResponse;
import com.back.domain.member.dto.ProfileResponse;
import com.back.domain.member.dto.UpdateProfileRequest;
import com.back.domain.member.entity.Instrument;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberProfileRepository;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.FileMetadataRepository;
import com.back.global.storage.FileService;
import com.back.global.storage.FileStorage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

// FileStorage 를 Fake 로 대체해 디스크/AWS 없이 서비스 로직을 검증한다(FileServiceTest 패턴).
@DataJpaTest
class MemberProfileServiceTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberProfileRepository memberProfileRepository;
    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private FakeFileStorage fileStorage;
    private MemberProfileService service;

    @BeforeEach
    void setUp() {
        fileStorage = new FakeFileStorage();
        FileService fileService = new FileService(fileStorage, fileMetadataRepository);
        service = new MemberProfileService(memberRepository, memberProfileRepository, fileService);
    }

    private Member savedMember(String suffix) {
        return memberRepository.save(
                Member.createLocal("user" + suffix, "pw", suffix + "@attaca.com", "닉" + suffix));
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "얼굴.png", "image/png",
                "img-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 프로필이_없으면_빈_기본값을_돌려준다() {
        Member member = savedMember("a");

        ProfileResponse response = service.getMyProfile(member.getId());

        assertThat(response.instruments()).isEmpty();
        assertThat(response.bio()).isNull();
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void 첫_수정_호출이_프로필을_생성한다() {
        Member member = savedMember("b");

        ProfileResponse response = service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(List.of(Instrument.VIOLIN, Instrument.PIANO), "소개"));

        assertThat(response.instruments())
                .containsExactly(Instrument.PIANO, Instrument.VIOLIN); // 이름순 정렬
        assertThat(response.bio()).isEqualTo("소개");
        assertThat(memberProfileRepository.findByMemberId(member.getId())).isPresent();
    }

    @Test
    void 재수정은_전체_교체다() {
        Member member = savedMember("c");
        service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(List.of(Instrument.VIOLIN), "이전"));

        ProfileResponse response = service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(List.of(Instrument.CELLO), "이후"));

        assertThat(response.instruments()).containsExactly(Instrument.CELLO);
        assertThat(response.bio()).isEqualTo("이후");
    }

    @Test
    void null_목록은_빈_목록으로_간주한다() {
        Member member = savedMember("d");

        ProfileResponse response = service.updateMyProfile(member.getId(),
                new UpdateProfileRequest(null, "소개만"));

        assertThat(response.instruments()).isEmpty();
    }

    @Test
    void 없는_회원의_수정은_MEMBER_NOT_FOUND() {
        assertThatThrownBy(() -> service.updateMyProfile(999999L,
                new UpdateProfileRequest(List.of(), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 이미지를_업로드하면_key가_저장되고_url을_돌려준다() {
        Member member = savedMember("e");

        ProfileImageResponse response = service.updateProfileImage(member.getId(), pngFile());

        String savedKey = memberProfileRepository.findByMemberId(member.getId())
                .orElseThrow().getProfileImageKey();
        assertThat(savedKey).startsWith("profile/").endsWith(".png");
        assertThat(response.profileImageUrl()).isEqualTo("http://fake/" + savedKey);
        assertThat(fileStorage.stored).containsKey(savedKey);
        assertThat(fileMetadataRepository.findByStorageKey(savedKey)).isPresent();
    }

    @Test
    void 이미지_교체시_옛_파일과_메타데이터가_삭제된다() {
        Member member = savedMember("f");
        service.updateProfileImage(member.getId(), pngFile());
        String oldKey = memberProfileRepository.findByMemberId(member.getId())
                .orElseThrow().getProfileImageKey();

        service.updateProfileImage(member.getId(), pngFile());

        String newKey = memberProfileRepository.findByMemberId(member.getId())
                .orElseThrow().getProfileImageKey();
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(fileStorage.stored).doesNotContainKey(oldKey).containsKey(newKey);
        assertThat(fileMetadataRepository.findByStorageKey(oldKey)).isEmpty();
    }

    @Test
    void 이미지가_아닌_파일은_거절한다() {
        Member member = savedMember("g");
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.updateProfileImage(member.getId(), pdf))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE);
    }

    /** 디스크/AWS 없이 검증하기 위한 인메모리 저장소. */
    static class FakeFileStorage implements FileStorage {

        final Map<String, byte[]> stored = new HashMap<>();

        @Override
        public String upload(String key, InputStream content, long size, String contentType) {
            try {
                stored.put(key, content.readAllBytes());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return key;
        }

        @Override
        public void delete(String key) {
            stored.remove(key);
        }

        @Override
        public String getUrl(String key) {
            return "http://fake/" + key;
        }
    }
}
