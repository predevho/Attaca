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
