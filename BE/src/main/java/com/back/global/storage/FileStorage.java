package com.back.global.storage;

import java.io.InputStream;

/**
 * 파일 바이트 저장소. 논리 key 단위로 저장/삭제/URL 생성만 담당하며 DB를 알지 못한다.
 * key 생성 규칙과 메타데이터 영속화는 {@link FileService}의 책임이다.
 */
public interface FileStorage {

    /** 주어진 key 위치에 내용을 저장하고 저장된 key를 반환한다. */
    String upload(String key, InputStream content, long size, String contentType);

    /** key에 해당하는 파일을 삭제한다. 물리 파일이 이미 없어도 예외를 던지지 않는다(멱등). */
    void delete(String key);

    /** key의 공개 접근 URL을 만든다. */
    String getUrl(String key);

    /** base-url과 key를 이어 붙인다. base-url 끝의 '/' 유무에 관계없이 결과가 같다. */
    static String joinUrl(String baseUrl, String key) {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return base + "/" + key;
    }
}
