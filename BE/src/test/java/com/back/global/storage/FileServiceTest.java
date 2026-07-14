package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

// FileStorage 를 Fake 로 대체해 디스크/AWS 를 건드리지 않고, 메타데이터 영속화는 실제 H2 로 검증한다.
@DataJpaTest
class FileServiceTest {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private FakeFileStorage fileStorage;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileStorage = new FakeFileStorage();
        fileService = new FileService(fileStorage, fileMetadataRepository);
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile(
                "file", "내 사진.png", "image/png", "hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 업로드하면_key와_url을_반환하고_메타데이터를_저장한다() {
        StoredFile stored = fileService.upload(pngFile(), "profile", 7L);

        LocalDate today = LocalDate.now();
        String datePrefix = "profile/%04d/%02d/%02d/"
                .formatted(today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        assertThat(stored.storageKey()).startsWith(datePrefix).endsWith(".png");
        assertThat(stored.url()).isEqualTo("http://fake/" + stored.storageKey());
        assertThat(stored.id()).isNotNull();

        // 원본 파일명(한글)은 key 에 들어가지 않는다
        assertThat(stored.storageKey()).doesNotContain("내 사진");

        // 실제 스토리지에 내용이 전달되었다
        assertThat(fileStorage.stored).containsKey(stored.storageKey());

        // 메타데이터가 저장되었다
        assertThat(fileMetadataRepository.findByStorageKey(stored.storageKey()))
                .get()
                .satisfies(meta -> {
                    assertThat(meta.getOriginalName()).isEqualTo("내 사진.png");
                    assertThat(meta.getContentType()).isEqualTo("image/png");
                    assertThat(meta.getSize()).isEqualTo(5L);
                    assertThat(meta.getUploaderId()).isEqualTo(7L);
                });
    }

    @Test
    void 빈_파일은_거절한다() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileService.upload(empty, "profile", 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE);
    }

    @Test
    void 확장자가_없으면_확장자_없는_key를_만든다() {
        MockMultipartFile noExt = new MockMultipartFile(
                "file", "noext", "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8));

        StoredFile stored = fileService.upload(noExt, "misc", null);

        assertThat(stored.storageKey()).doesNotContain(".");
    }

    @Test
    void 삭제하면_스토리지와_메타데이터에서_모두_사라진다() {
        StoredFile stored = fileService.upload(pngFile(), "profile", 7L);

        fileService.delete(stored.storageKey());

        assertThat(fileStorage.stored).doesNotContainKey(stored.storageKey());
        assertThat(fileMetadataRepository.findByStorageKey(stored.storageKey())).isEmpty();
    }

    @Test
    void 모르는_key를_삭제하면_FILE_NOT_FOUND() {
        assertThatThrownBy(() -> fileService.delete("profile/2026/07/14/unknown.png"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    /** 디스크/AWS 없이 FileService 를 검증하기 위한 인메모리 저장소. */
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
