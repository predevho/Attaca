package com.back.domain.recruitment.entity;

/** 지원 상태머신. PENDING → ACCEPTED/REJECTED(작성자) 또는 WITHDRAWN(지원자, PENDING에서만). */
public enum RecruitmentApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
