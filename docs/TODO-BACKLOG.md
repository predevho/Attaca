# TODO-BACKLOG

아직 시작하지 않은 예정 작업.

---

## 도메인 문서화 (구현 전 필수)

* [x] ~~DOMAIN-VERIFIED-PERFORMER-CONSTITUTION.md / STATUTE.md~~ (2026-07-15 작성 완료)
* [x] ~~DOMAIN-FEED-CONSTITUTION.md / STATUTE.md~~ (2026-07-17 작성 완료)
* [ ] DOMAIN-PERFORMANCE-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-RECRUITMENT-CONSTITUTION.md / STATUTE.md
* [ ] DOMAIN-CHAT-CONSTITUTION.md / STATUTE.md

## 기능

* [ ] MEMBER: 소셜 로그인 provider 확장(구글 등 — `OAuthClient` 어댑터 추가) *(카카오는 2026-07-13 완료)*
* [ ] MEMBER: 실제 인증메일 발송(가입 이메일 검증) — 메일 인프라 도입 시
* [x] ~~VERIFIED-PERFORMER: 인증 연주자 신청 → 어드민 승인/거절/철회, 뱃지~~ — 2026-07-16 BE 구현 완료(TDD). 엔티티/상태머신 + 회원 API 2종 + 어드민 API 5종 + `isVerified` 협력 + MEMBER `ProfileResponse.verified` 통합. 에러코드 409-04~06/404-04 추가.
  * 확정한 결정: `memberId`=원시 Long, `GET .../applications/me` 이력없음=200+data:null, 어드민 목록=`?status`+Pageable(createdAt desc, id 타이브레이크), 뱃지는 프로필 미생성 회원도 파생.
  * 남은 범위 밖: 공개 인증자 목록/신청 첨부파일/이력 테이블, 어드민 grant 시 회원 존재 검증(느슨한 결합 유지로 미도입), 실FE 화면.
* [x] ~~FEED: 게시글/댓글/좋아요, 피드 타임라인~~ — 2026-07-17 BE 구현 완료(TDD, 서브에이전트 주도). 엔티티 4종 + 리포지토리(커서/배치) + 서비스 3종 + 컨트롤러 3종 + MEMBER 배치 협력(MemberQueryService). 에러코드 404-05/06. 전 계층 테스트 + 전체 회귀 통과, 최종 전체-브랜치 리뷰 MERGEABLE.
  * 남은 범위 밖: 이미지첨부·대댓글·댓글수정·팔로우타임라인·신고·PERFORMANCE 카드.
* [ ] PERFORMANCE: 연주회 등록·홍보, 피드 카드 노출 연동
* [ ] RECRUITMENT: 구인/구직 공고 + 지원
* [ ] CHAT: WebSocket(STOMP)+Redis 기반 1:1 / 1:N 채팅
* [x] ~~FE: MEMBER 프로필 화면(조회/수정/이미지)~~ — 2026-07-16 완료(조회/수정 모드, 이미지 즉시 업로드, beFetch 멀티파트 지원). 라이브 수동 검증까지 성공(멀티파트 실체인: 브라우저→BFF→Spring @RequestPart→디스크→/files/** 서빙→DB key 저장·새로고침 유지). 실이미지 S3 검증만 별개 BACKLOG.
* [x] ~~FE: 카카오 실제 로그인 왕복 수동 검증~~ — 2026-07-15 완료. 실제 카카오 앱 키/Client Secret/Redirect URI/이메일 동의로 브라우저 왕복 성공(로그인→/dashboard). 개인 개발자 비즈 앱 전환, redirect_uri는 `/config/callback`에 등록.

## FE 공통 (정리)

* [ ] FE: BFF 라우트 status 폴백 일괄 수정 — 모든 BFF 라우트가 `{ status: res.status || 200 }`을 써서 BE 연결 실패(`beFetch` status 0)를 HTTP 200으로 응답한다. 현재 클라이언트는 바디의 `ok`로 판단해 무해하나, status 기준 소비처가 생기면 오작동. `res.status || 502`(또는 `=== 0 ? 502`)로 login/signup/logout/me/oauth·프로필 등 전체를 한 번에 정리. (2026-07-16 프로필 리뷰에서 식별)

## FEED 정리 (최종 리뷰에서 이연된 Minor)

* [ ] FEED: `clamp(size)`가 `FeedPostController`/`FeedCommentController`에 중복 — 공용 헬퍼로 추출(예: `FeedPostController.isAdmin`처럼 정적 헬퍼 재사용). (2026-07-17 최종 리뷰 식별)
* [ ] FEED: `VerificationApplicationRepository.findApprovedMemberIds`의 JPQL이 enum을 FQN 리터럴로 사용 → `@Param`으로 파라미터 바인딩 정리(리네임 취약). 해당 테스트의 인라인 `java.util.Set`도 import로. (2026-07-17)

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
