package com.back.global.storage;

/** 업로드 결과. 도메인 엔티티는 {@code storageKey}를 보관하고, {@code url}은 응답에 쓴다. */
public record StoredFile(Long id, String storageKey, String url) {
}
