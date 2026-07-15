# TODO-BACKLOG

아직 시작하지 않은 예정 작업.

---

## 도메인 문서화 (구현 전 필수)

* [x] ~~DOMAIN-VERIFIED-PERFORMER-CONSTITUTION.md / STATUTE.md~~ (2026-07-15 작성 완료)
* [ ] DOMAIN-FEED-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-PERFORMANCE-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-RECRUITMENT-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-CHAT-CONSTITUTION.md / STATUTE.md

## 기능

* [ ] MEMBER: 소셜 로그인 provider 확장(구글 등 — `OAuthClient` 어댑터 추가) *(카카오는 2026-07-13 완료)*
* [ ] MEMBER: 실제 인증메일 발송(가입 이메일 검증) — 메일 인프라 도입 시
* [ ] VERIFIED-PERFORMER: 인증 연주자 신청 → 어드민 승인/거절/철회, 뱃지 (구현 — 문서는 2026-07-15 완료)
  * 설계 확정(2026-07-15, DOMAIN-VERIFIED-PERFORMER-*): 별도 엔티티 `VerificationApplication`, 상태 4종(PENDING/APPROVED/REJECTED/REVOKED), 재신청=새 레코드, 어드민 직접지정. 뱃지는 MEMBER `ProfileResponse.verified`로 파생 노출(`isVerified` 서비스 협력). 에러코드 409-04~06/404-04. Member에 boolean 미추가.
  * 공개 인증자 목록/신청 첨부파일/이력 테이블은 범위 밖(추후).
* [ ] FEED: 게시글/댓글/좋아요, 피드 타임라인
* [ ] PERFORMANCE: 연주회 등록·홍보, 피드 카드 노출 연동
* [ ] RECRUITMENT: 구인/구직 공고 + 지원
* [ ] CHAT: WebSocket(STOMP)+Redis 기반 1:1 / 1:N 채팅
* [ ] FE: MEMBER 프로필 화면(조회/수정/이미지) — BE API·BFF 패턴 준비됨
* [ ] FE: 카카오 소셜 로그인 연동(인가코드 리다이렉트 → BFF 교환)

## BE 공통 (도메인 확장 전후로 필요)

* [x] ~~CORS 설정~~ — BFF 채택으로 브라우저 경로는 same-origin이라 불필요. 모바일 앱 등 BE 직접 호출 소비처가 생기면 재도입 검토. (2026-07-15 결정)
* [ ] Swagger/OpenAPI 문서화 — `resultCode`의 문서화 용도가 COMMON-STATUTE §1에 명시돼 있으나 도입은 미착수
* [ ] refresh 토큰 로테이션·철회 — Redis 도입 시(COMMON-STATUTE §4에 예정 명시). CHAT의 Redis 도입과 시기 조율 가능

## 인프라 (추후)

* [ ] 배포 시 Nginx 리버스 프록시 + HTTPS + WebSocket 프록시 결정
* [ ] 실제 S3 연동 검증 — AWS 자격증명 발급 후 `STORAGE_TYPE=s3`로 업로드/삭제/조회 수동 확인 (자동 테스트 범위 밖)
* [ ] 고아 파일 정리(GC) — 메타데이터 없는 물리 파일, 업로드 실패로 남은 파일 정리 배치
* [ ] CloudFront/R2 등 CDN 전환으로 파일 접근 요금 최적화 (`base-url` 교체만으로 가능하도록 설계됨)
* [ ] FE: middleware → proxy 마이그레이션 검토 — Next 16.2에서 'middleware' 파일 규약이 deprecated(경고만, 현재 정상 동작). proxy는 edge가 아닌 nodejs 런타임이라 'server-only' import 제약이 사라질 수 있어 쿠키 이름 하드코딩 재검토 대상. (2026-07-15 발견)
