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
