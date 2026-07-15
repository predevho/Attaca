package com.back.global.config;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.storage.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * LocalFileStorage 가 저장한 파일을 {@code /files/**} 로 서빙한다.
 * {@code storage.type=s3}일 때는 파일이 서버를 거치지 않으므로 등록하지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileServingConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path rootDir = Paths.get(properties.local().rootDir()).toAbsolutePath().normalize();

        // rootDir(기본값 ./uploads)은 .gitignore 대상이라 첫 업로드 전까지 실제로 존재하지 않는다.
        // Path.toUri()는 대상이 "존재하는 디렉터리"일 때만 끝에 '/'를 붙이는데, 이 슬래시가 없으면
        // Spring이 캐시해둔 리소스 위치를 기준으로 한 상대 경로 해석(RFC-3986)에서 마지막 세그먼트
        // (uploads)가 통째로 날아가 버려 첫 업로드 파일이 재시작 전까지 404가 된다.
        // 따라서 toUri() 호출 전에 디렉터리 존재를 보장한다. createDirectories는 이미 있으면 no-op이다.
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }

        registry.addResourceHandler("/files/**")
                .addResourceLocations(rootDir.toUri().toString());
    }
}
