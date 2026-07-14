package com.back.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장 설정. {@code type}이 {@code local}이면 로컬 디스크, {@code s3}면 AWS S3를 사용한다.
 * S3 자격증명은 환경변수로 주입한다(커밋 금지).
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String type, Local local, S3 s3) {

    /** 로컬 디스크 저장 설정. */
    public record Local(String rootDir, String baseUrl) {
    }

    /** S3 저장 설정. {@code baseUrl}은 나중에 CDN 도메인으로 교체하는 지점이다. */
    public record S3(String bucket, String region, String accessKey, String secretKey, String baseUrl) {
    }
}
