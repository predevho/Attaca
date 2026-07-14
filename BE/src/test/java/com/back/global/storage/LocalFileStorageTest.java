package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(new StorageProperties(
                "local",
                new StorageProperties.Local(tempDir.toString(), "http://localhost:8080/files"),
                null));
    }

    private InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 파일을_저장하고_key를_반환한다() throws Exception {
        String key = "profile/2026/07/14/abc.png";

        String returned = storage.upload(key, content("hello"), 5L, "image/png");

        assertThat(returned).isEqualTo(key);
        Path saved = tempDir.resolve(key);
        assertThat(saved).exists();
        assertThat(Files.readString(saved)).isEqualTo("hello");
    }

    @Test
    void 저장한_파일을_삭제한다() throws Exception {
        String key = "profile/2026/07/14/abc.png";
        storage.upload(key, content("hello"), 5L, "image/png");

        storage.delete(key);

        assertThat(tempDir.resolve(key)).doesNotExist();
    }

    @Test
    void 없는_파일을_삭제해도_예외를_던지지_않는다() {
        String key = "profile/2026/07/14/does-not-exist.png";

        assertThatCode(() -> storage.delete(key)).doesNotThrowAnyException();

        assertThat(tempDir.resolve(key)).doesNotExist();
    }

    @Test
    void baseUrl과_key를_이어_URL을_만든다() {
        assertThat(storage.getUrl("profile/2026/07/14/abc.png"))
                .isEqualTo("http://localhost:8080/files/profile/2026/07/14/abc.png");
    }

    @Test
    void 루트를_벗어나는_key는_거절한다() {
        assertThatThrownBy(() -> storage.upload("../evil.png", content("bad"), 3L, "image/png"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE);
    }
}
