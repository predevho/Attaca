package com.back.domain.verifiedperformer.entity;

/**
 * 인증 연주자 신청 상태 머신.
 *
 * <pre>
 *   [없음] ──신청──▶ PENDING ──승인──▶ APPROVED ──철회──▶ REVOKED
 *                       └──거절──▶ REJECTED
 *   어드민 직접지정 ─────────────▶ APPROVED (신청서 없음)
 * </pre>
 *
 * APPROVED 만이 뱃지 판정의 근거다. REJECTED/REVOKED 회원은 재신청(새 레코드)이 가능하다.
 */
public enum VerificationStatus {

    PENDING,
    APPROVED,
    REJECTED,
    REVOKED
}
