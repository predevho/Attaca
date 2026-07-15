package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    @Mock
    private S3Client s3Client;

    private S3FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3FileStorage(s3Client, new StorageProperties(
                "s3",
                null,
                new StorageProperties.S3("attaca-bucket", "ap-northeast-2", "AK", "SK",
                        "https://cdn.attaca.com")));
    }

    private InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 올바른_PutObjectRequest로_업로드한다() {
        String key = "profile/2026/07/14/abc.png";

        String returned = storage.upload(key, content("hello"), 5L, "image/png");

        assertThat(returned).isEqualTo(key);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("attaca-bucket");
        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo("image/png");
    }

    @Test
    void 올바른_DeleteObjectRequest로_삭제한다() {
        String key = "profile/2026/07/14/abc.png";

        storage.delete(key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertThat(captor.getValue().bucket()).isEqualTo("attaca-bucket");
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    void baseUrl과_key를_이어_URL을_만든다() {
        assertThat(storage.getUrl("profile/2026/07/14/abc.png"))
                .isEqualTo("https://cdn.attaca.com/profile/2026/07/14/abc.png");
    }

    @Test
    void SDK_예외를_FILE_UPLOAD_FAILED로_감싼다() {
        SdkException cause = SdkException.builder().message("s3 down").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(cause);

        assertThatThrownBy(() -> storage.upload("k.png", content("x"), 1L, "image/png"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
    }
}
