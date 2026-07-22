# DOMAIN-RECRUITMENT-CONSTITUTION

구인(연주자 모집) 도메인 원칙.

> 작성일: 2026-07-22. 이 문서는 원칙을 정의한다. 구현 규칙은 STATUTE에 둔다.

---

## 1. 책임

* 음악인이 **연주자를 구하는 구인(求人) 공고를 등록·홍보**한다. 구인만 다룬다(구직 방향은 범위 밖).
* 공고의 생성·수정·마감·삭제 및 조회(목록·단건)를 제공한다.
* 공고에 대한 **지원(application)**의 생성·상태전이(수락/거절/철회) 및 조회(공고별 지원자 목록·내 지원 목록)를 제공한다.

---

## 2. 원칙

* **공고·지원 데이터의 소유·관리는 이 도메인에 있다.** 등록·수정·마감·삭제·지원·상태전이·조회 책임을 가진다.
* **작성자·지원자는 원시 `Long`(authorId/applicantId)으로만 참조한다.** MEMBER 엔티티를 직접 참조하지 않는다(ARCHITECTURE-CONSTITUTION §4).
  * 응답에 필요한 표시정보(닉네임·인증 뱃지)는 **읽기 시점에 MEMBER 서비스로 협력**해 파생한다. 닉네임을 복제(비정규화)해 소유하지 않는다.
* **등록 자격은 인증 회원 누구나다.** PERFORMANCE와 달리 인증 연주자 게이팅을 두지 않는다. 구인 주체는 연주자가 아닐 수 있기 때문이다(매니저·기획자·음악감독 등).
* **공고 삭제는 soft delete다.** 공고는 논리 삭제(`deletedAt` 마킹)하며 모든 조회에서 제외한다.
* **지원은 soft delete가 없다.** 철회는 삭제가 아니라 **상태(`WITHDRAWN`)로 표현**한다. 상태머신: `PENDING` → `ACCEPTED`/`REJECTED`(작성자) 또는 `WITHDRAWN`(지원자, PENDING에서만).
* **활성 지원(PENDING/ACCEPTED)은 공고당 지원자마다 유일하다.** 같은 공고에 활성 지원이 있으면 재지원할 수 없다. `REJECTED`/`WITHDRAWN` 이후에는 재지원(새 레코드)을 허용한다.
* **목록 조회는 페이징을 기본으로 한다**(DOMAIN-COMMON-CONSTITUTION §5, Spring `Pageable`).

---

## 3. 관계

* **MEMBER**: 공고 작성자·지원자는 회원이다. 표시정보(닉네임·인증뱃지)는 MEMBER가 소유하며, 이 도메인은 서비스 계층으로 협력해 조회만 한다. 어드민 역시 MEMBER의 `ROLE_ADMIN`이며, 공고 삭제 모더레이션에 쓰인다.
* **VERIFIED-PERFORMER**: 등록 게이팅에는 쓰지 않는다(PERFORMANCE와의 차이). 다만 응답의 인증 뱃지는 `MemberDisplay.verified`(MEMBER가 VERIFIED-PERFORMER와 협력해 채우는 값)를 통해 파생된다.
* **PERFORMANCE**: 현재 연결 없음(독립). 구인은 대개 공연 확정 전에 이뤄지므로 nullable FK도 두지 않는다. 실수요가 생기면 `performanceId` 추가로 확장한다.

---

## 4. 범위 밖 (현재)

* 구직(연주자가 자리 찾는) 공고 — MEMBER 프로필/VERIFIED-PERFORMER로 커버, 실수요 시 확장.
* PERFORMANCE 연결(`performanceId`) — 실수요 시 nullable FK 추가.
* 지원 첨부파일(포트폴리오/음원) — 필요 시 FileService 협력으로 확장.
* 지원자↔작성자 메시징/채팅 — CHAT 도메인 도입 시 연계.
* 공고 검색(제목/지역 키워드, 태그) — 현재는 scope + instrument 필터만.
* 알림(지원 접수/수락 푸시) — 알림 인프라 도입 시.
* 마감 자동화 배치(deadline 지난 공고 status 일괄 CLOSED 전환) — 현재는 조회/지원 시 파생 판정으로 처리, 상태 필드는 수동 전환만.
