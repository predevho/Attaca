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
