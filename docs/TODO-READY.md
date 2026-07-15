# TODO-READY

바로 작업 가능한 작업 목록.

---

* [ ] BE 런타임 DB 구성 — `application.yaml`에 MySQL 데이터소스 추가(+드라이버 의존성). 현재 H2는 `testRuntimeOnly`뿐이라 `bootRun`으로 서버 기동이 불가 → **FE 연동·수동 검증의 선행 조건**
* [ ] MEMBER: 프로필(악기/장르/자기소개) + 프로필 이미지 업로드 — 선행 작업(FileStorage, 2026-07-14) 완료로 착수 가능. HTTP 업로드 엔드포인트 포함
* [ ] FE `FE/`에 Next.js 프로젝트 초기화 — BE 연동 시 CORS 설정 필요(BACKLOG 참조)
