package com.back.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.global.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LocalFileServingConfig가 등록하는 {@code /files/**} 리소스 핸들러가 "첫 업로드가 일어나기 전"에도
 * 실제로 파일을 서빙하는지 end-to-end로 검증한다.
 *
 * <p>버그 재현 조건은 storage.local.root-dir이 애플리케이션 컨텍스트 기동 시점에 아직 디스크에
 * 존재하지 않는 것이다(실제 운영에서는 .gitignore된 ./uploads가 배포 직후 그렇다). 그래서 이미
 * 존재하는 임시 디렉터리를 그대로 root-dir로 쓰지 않고, 그 아래 아직 만들어지지 않은 하위
 * 디렉터리를 root-dir로 지정해 "존재하지 않는 디렉터리에서 컨텍스트가 뜬다"는 상황을 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalFileServingConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileStorage fileStorage;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) throws IOException {
        Path parent = Files.createTempDirectory("file-serving-test-");
        Path rootDir = parent.resolve("uploads-not-created-yet");
        registry.add("storage.local.root-dir", rootDir::toString);
    }

    @Test
    void 첫_업로드_파일도_재시작_없이_바로_서빙된다() throws Exception {
        String key = "profile/2026/07/15/regression.txt";
        String body = "hello-file-serving";

        fileStorage.upload(key,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                body.length(), "text/plain");

        mockMvc.perform(get("/files/" + key))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .isEqualTo(body));
    }
}
