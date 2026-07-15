# AI-MAJOR-EVENT-RECAP

사람이 빠르게 읽기 위한 주요 사건 요약.

---

* **2026-07-05 프로젝트 시작**: Attaca(음악인 커뮤니티 SNS) 설계 확정.
  * 도메인 6개: 회원 / 인증 연주자 / 피드 / 연주회 / 구인·구직 / 채팅
  * BE: Spring Boot 3.4.x · Java 21 · MySQL · JWT+OAuth2 · WebSocket+Redis · S3
  * FE: Next.js
  * 어드민 = 회원의 ROLE_ADMIN, 별도 도메인 아님
* **2026-07-13 소셜 로그인·식별자 설계 확정**: MEMBER 카카오 소셜 로그인 구현.
  * 로그인 열쇠를 email→loginId로 분리 (email=인증·소셜연결 키, nickname=활동명, id=내부 신원)
  * 소셜은 프론트 인가코드→백엔드 교환(무상태 유지). 검증된 이메일만 자동연결(탈취 방지)
  * OAuthClient 추상화(카카오 먼저), 실제 카카오 HTTP는 env 키 수동 검증
* **2026-07-14 파일 저장 기반(FileStorage) 구성**: 문서 충돌 해소 + 버그 2건 발견.
  * 파일 메타데이터는 공용 `FileMetadata` 테이블 하나로 통일(도메인별 중복 정의 안 함), `FileStorage`(바이트)/`FileService`(key+메타데이터) 책임 분리
  * 앱 전역 500→404 라우팅 버그 발견: 매칭 안 되는 URL이 500으로 응답되던 것을 `RESOURCE_NOT_FOUND`(404-02)로 정정
  * key 생성 시 dotfile(`.내파일`)에서 원본 파일명이 그대로 새어나가던 취약점 발견·수정
  * **결정 번복**: 파일 저장 기본값을 S3→로컬로 변경(S3 자격증명 미발급, 개발/FE 연동 지속 위함). S3는 opt-in으로 유지, 자격증명 확보 시 운영 목표로 전환
* **2026-07-15 런타임 DB + MEMBER 프로필**: docker-compose MySQL로 `bootRun` 가능해짐(테스트는 H2 프로파일 분리).
  * 프로필: 악기 enum 21종(VOICE=성악/VOCAL=보컬 분리, 장르 제외), lazy upsert, 이미지는 FileService 경유(교체 시 옛 파일 삭제)
  * Bean Validation 도입, 클라이언트 실수 3종이 500으로 응답되던 결함을 400으로 정정
* **2026-07-15 FE 초기화 + 인증(BFF)**: Next.js 16으로 FE 시작. BFF+httpOnly 쿠키로 토큰을 UI에서 격리.
  * 회원가입/로그인/대시보드 + 미들웨어 보호 + reissue 1회 재시도
  * CORS는 BFF라 미도입(브라우저 same-origin), 통신은 네이티브 fetch(라이브러리 미도입)
  * 브라우저 E2E 6단계 검증(토큰 JS 미노출 확인). PC env DB_PASSWORD=1234가 bootRun 막던 문제 발견·해결
* **2026-07-15 VERIFIED-PERFORMER 도메인 문서화**: 인증 연주자 도메인 CONSTITUTION/STATUTE 작성(코드는 후속).
  * 인증 상태를 Member boolean이 아닌 별도 엔티티로 소유. 상태 4종(PENDING/APPROVED/REJECTED/REVOKED), 재신청=새 레코드, 어드민 직접지정·철회 가능
  * 뱃지는 MEMBER ProfileResponse.verified로 파생(서비스 협력). 심사는 ROLE_ADMIN. 공개목록은 범위 밖
* **2026-07-15 FE 카카오 소셜 로그인**: 서버 start/callback BFF 라우트 + CSRF state(httpOnly 쿠키, 단일사용).
  * 콜백을 서버 라우트로 둬 토큰이 UI에 안 닿음. client_id는 서버 env(NEXT_PUBLIC_ 아님)
  * 실제 카카오 왕복은 앱 키 확보 후 수동 검증(코드·배선은 완료)
* **2026-07-16 FE 프로필 화면**: `/profile` 조회/수정 모드 + 이미지 즉시 업로드.
  * 악기 칩 토글·자기소개, PUT 전체 교체 유지(폼이 전체를 쥠). BFF 라우트 3종 authedBeFetch 경유
  * beFetch를 FormData면 content-type 미설정으로 고쳐 멀티파트 지원(JSON 경로 불변)
