package com.back.domain.verifiedperformer.dto;

/** 승인 요청. 사유는 선택(본문 자체를 생략할 수 있다). */
public record DecisionRequest(String reason) {
}
