package com.back.domain.feed.repository;

/** 배치 카운트 projection. 대상 id → 개수. */
public interface IdCount {
    Long getId();
    long getCount();
}
