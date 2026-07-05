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

* [ ] MEMBER: 소셜 로그인(OAuth2) 연동
* [ ] MEMBER: 프로필(악기/장르/자기소개) + 프로필 이미지 업로드
* [ ] VERIFIED-PERFORMER: 인증 연주자 신청 → 어드민 승인/거절, 공개 프로필 게시
* [ ] FEED: 게시글/댓글/좋아요, 피드 타임라인
* [ ] PERFORMANCE: 연주회 등록·홍보, 피드 카드 노출 연동
* [ ] RECRUITMENT: 구인/구직 공고 + 지원
* [ ] CHAT: WebSocket(STOMP)+Redis 기반 1:1 / 1:N 채팅

## 인프라 (추후)

* [ ] 배포 시 Nginx 리버스 프록시 + HTTPS + WebSocket 프록시 결정
