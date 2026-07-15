# DOMAIN-VERIFIED-PERFORMER-STATUTE

인증 연주자 도메인 구현 규칙.

> 작성일: 2026-07-15. 세부 필드·엔드포인트 시그니처는 구현 착수 시 확정하며, 변경 시 이 문서를 갱신한다.
> 상태 머신·도메인 경계·뱃지 협력 방식은 확정 규칙이다.

---

## 1. 패키지

```
com.back.domain.verifiedperformer
├── controller
├── service
├── repository
├── entity
└── dto
```

---

## 2. 상태 머신 (확정)

`VerificationStatus` (enum): `PENDING` / `APPROVED` / `REJECTED` / `REVOKED`.

```
              신청                     승인
  [없음] ───────────▶ PENDING ───────────▶ APPROVED ──철회──▶ REVOKED
                         │                                       │
                         └──거절──▶ REJECTED ──재신청(새 레코드)──┘
   어드민 직접지정 ─────────────────────────▶ APPROVED (신청서 없음)
```

* `PENDING` : 심사 대기.
* `APPROVED` : 인증됨. **뱃지 판정의 근거**.
* `REJECTED` : 거절됨. 뱃지 없음. 재신청 가능.
* `REVOKED` : 승인 후 철회됨. 뱃지 없음. 재신청 가능.

### 전이 규칙

* `PENDING → APPROVED` (어드민 승인) / `PENDING → REJECTED` (어드민 거절).
* `APPROVED → REVOKED` (어드민 철회).
* 어드민 직접지정: 신청 없이 `APPROVED` 레코드를 생성한다(`statement`/`evidenceUrls` 없음).
* **이미 종결된 상태(APPROVED·REJECTED·REVOKED)의 레코드는 재처리하지 않는다.** 위반 시 `INVALID_APPLICATION_STATE`.
* 재신청은 **새 레코드**를 만든다. 기존 레코드는 이력으로 보존한다.

### 활성 신청 유일성 (확정)

* 한 회원은 **활성 신청을 하나만** 가질 수 있다. `PENDING` 또는 `APPROVED` 레코드가 존재하면 새 신청을 거절한다.
  * `PENDING` 존재 → `VERIFICATION_ALREADY_PENDING`.
  * `APPROVED` 존재 → `VERIFICATION_ALREADY_APPROVED`.
* `REJECTED`·`REVOKED`만 있는 회원은 새 신청이 가능하다.

---

## 3. 엔티티 (초안)

### VerificationApplication

`BaseEntity` 상속(createdAt/updatedAt). 기존 엔티티 패턴 준수: `@NoArgsConstructor(access = PROTECTED)` + private 생성자 + static 팩토리, 단순 접근자는 Lombok `@Getter`, setter 금지(의도가 드러나는 변경 메서드 사용).

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | Long | PK |
| `memberId` | Long | 신청 회원. non-null |
| `statement` | String | 자기서술(지원 사유). 어드민 직접지정 시 nullable |
| `evidenceUrls` | `List<String>` | 증빙 링크. `@ElementCollection`. 직접지정 시 빈 목록 |
| `status` | VerificationStatus | `@Enumerated(STRING)`, non-null |
| `decisionReason` | String | 승인/거절/철회 사유(어드민 기록). nullable |
| `decidedBy` | Long | 처리한 어드민 memberId. nullable(PENDING이면 없음) |
| `decidedAt` | LocalDateTime | 처리 시각. nullable |

* `memberId`를 원시 Long으로 둘지 `Member` 연관으로 둘지는 **구현 착수 시 확정**한다.
  * domain→domain 참조는 허용되나(global→domain 금지와 다름), 느슨한 결합을 위해 Long을 우선 검토한다. 조회 시 회원 정보가 필요하면 서비스 계층에서 MEMBER 서비스로 협력한다.
* 상태 변경 메서드(초안): `approve(adminId, reason)` / `reject(adminId, reason)` / `revoke(adminId, reason)`. 팩토리: `apply(memberId, statement, evidenceUrls)` / `grantByAdmin(memberId, adminId, reason)`.

---

## 4. API (초안)

### 4.1 회원 API (인증 필요, principal = memberId)

* `POST /api/verified-performers/applications` : 인증 신청. body `{statement, evidenceUrls[]}`. 활성 신청 있으면 409.
* `GET /api/verified-performers/applications/me` : 내 최신 신청 상태 조회. 신청 이력 없으면 빈/none 응답(정책은 구현 시 확정).

### 4.2 어드민 API (`/api/admin/**`, `hasRole("ADMIN")`)

* `GET /api/admin/verified-performers/applications?status=PENDING` : 상태별 신청 목록(페이징).
* `POST /api/admin/verified-performers/applications/{id}/approve` : 승인. body `{reason?}`.
* `POST /api/admin/verified-performers/applications/{id}/reject` : 거절. body `{reason}`.
* `POST /api/admin/verified-performers/applications/{id}/revoke` : 철회. body `{reason}`.
* `POST /api/admin/verified-performers/grant` : 직접 지정. body `{memberId, reason?}`. 활성 신청 있으면 409.

### 4.3 도메인 간 협력 (내부)

* `VerifiedPerformerService.isVerified(Long memberId) → boolean` : 그 회원에게 `APPROVED` 레코드가 있으면 true.
* MEMBER의 `MemberProfileService`가 이 메서드를 호출해 `ProfileResponse`에 `verified` 필드를 채운다. (MEMBER 도메인 구현 시 `ProfileResponse`에 `verified` 추가)

---

## 5. 에러 코드 (전역 `ErrorCode`에 추가, 도메인 전용 enum 분리는 계속 보류)

| 코드 | resultCode | HTTP | 사유 |
|---|---|---|---|
| `VERIFICATION_ALREADY_PENDING` | 409-04 | 409 | 심사 대기 중인 신청이 이미 있음 |
| `VERIFICATION_ALREADY_APPROVED` | 409-05 | 409 | 이미 인증된 회원의 재신청/직접지정 |
| `INVALID_APPLICATION_STATE` | 409-06 | 409 | 이미 종결된 신청을 재처리 시도 |
| `APPLICATION_NOT_FOUND` | 404-04 | 404 | 신청 레코드 없음 |

* 번호는 현재 미사용값(404-04, 409-04~06)으로 확정. 구현 시 최종 대조.

---

## 6. 테스트 (구현 시)

* 신청 성공 / 활성 신청 중복 거절(PENDING·APPROVED)
* 승인·거절·철회 전이, 종결 상태 재처리 거절
* 어드민 직접지정(신청서 없이 APPROVED)
* 재신청(REJECTED·REVOKED → 새 레코드)
* `isVerified` 판정(APPROVED만 true)
* 권한별 접근 제어(회원 API vs 어드민 API)

---

## 7. 구현 착수 시 확정할 것

* `memberId` 원시 Long vs `Member` 연관.
* `GET .../applications/me`의 이력 없음 응답 형태(빈 객체 vs 404 vs null 필드).
* 어드민 목록 페이징·정렬 기준.
* MEMBER `ProfileResponse`에 `verified` 필드 추가(MEMBER 도메인 변경 동반).
