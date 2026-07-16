package com.back.domain.feed.dto;

import java.util.List;

/** 커서 기반 목록 응답. nextCursor 가 null 이면 마지막 페이지. */
public record CursorPage<T>(List<T> items, Long nextCursor) {
}
