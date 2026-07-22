package com.back.domain.recruitment.entity;

/** 구인 공고 상태. 마감은 이 상태(수동) 또는 deadline 경과(파생)로 판정한다. */
public enum RecruitmentStatus {
    OPEN,
    CLOSED
}
