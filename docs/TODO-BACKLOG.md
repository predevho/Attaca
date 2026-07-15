# TODO-BACKLOG

아직 시작하지 않은 예정 작업.

---

## 도메인 문서화 (구현 전 필수)

* [ ] DOMAIN-VERIFIED-PERFORMER-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-FEED-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-PERFORMANCE-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-RECRUITMENT-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-CHAT-CONSTITUTION.md / STATUTE.md

## 기능

* [ ] MEMBER: 소셜 로그인 provider 확장(구글 등 — `OAuthClient` 어댑터 추가) *(카카오는 2026-07-13 완료)*
* [ ] MEMBER: 실제 인증메일 발송(가입 이메일 검증) — 메일 인프라 도입 시
* [ ] VERIFIED-PERFORMER: 인증 연주자 신청 → 어드민 승인/거절, 공개 프로필 게시
  * 설계 메모(2026-07-15): 프로필의 "인증 음악가" 뱃지는 Member에 boolean 컬럼을 추가하지 않는다. 인증 상태는 이 도메인 소유의 별도 엔티티(상태 워크플로: 신청/승인/거절 + 이력)로 관리하고, 뱃지는 조회 시 서비스 계층 협력으로 `ProfileResponse`에 파생 노출한다. (도메인 경계 원칙 — ARCHITECTURE-CONSTITUTION §4)
* [ ] FEED: 게시글/댓글/좋아요, 피드 타임라인
* [ ] PERFORMANCE: 연주회 등록·홍보, 피드 카드 노출 연동
* [ ] RECRUITMENT: 구인/구직 공고 + 지원
* [ ] CHAT: WebSocket(STOMP)+Redis 기반 1:1 / 1:N 채팅

## BE 공통 (도메인 확장 전후로 필요)

* [ ] CORS 설정 — FE dev 서버(:3000)↔BE(:8080) 연동 시 필수. FE 초기화와 같은 시기에 진행
* [ ] Swagger/OpenAPI 문서화 — `resultCode`의 문서화 용도가 COMMON-STATUTE §1에 명시돼 있으나 도입은 미착수
* [ ] refresh 토큰 로테이션·철회 — Redis 도입 시(COMMON-STATUTE §4에 예정 명시). CHAT의 Redis 도입과 시기 조율 가능

## 인프라 (추후)

* [ ] 배포 시 Nginx 리버스 프록시 + HTTPS + WebSocket 프록시 결정
* [ ] 실제 S3 연동 검증 — AWS 자격증명 발급 후 `STORAGE_TYPE=s3`로 업로드/삭제/조회 수동 확인 (자동 테스트 범위 밖)
* [ ] 고아 파일 정리(GC) — 메타데이터 없는 물리 파일, 업로드 실패로 남은 파일 정리 배치
* [ ] CloudFront/R2 등 CDN 전환으로 파일 접근 요금 최적화 (`base-url` 교체만으로 가능하도록 설계됨)
