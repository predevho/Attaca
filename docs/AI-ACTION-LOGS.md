# AI-ACTION-LOGS

최근 작업 로그. 최대 100개 유지 (오래된 항목부터 제거).

---

* 2026-07-05 — 프로젝트 초기 설계 브레인스토밍 진행. 서비스 정의(음악인 커뮤니티 SNS), 도메인 6개, 기술 스택 확정.
* 2026-07-05 — `docs/` 문서 체계 생성: ARCHITECTURE(CONSTITUTION/STATUTE), DOMAIN-COMMON(CONSTITUTION/STATUTE), DOMAIN-MEMBER(CONSTITUTION/STATUTE), TODO-*, CONTEXT, AI 기록 문서.
* 2026-07-05 — 플러그인 설치 검증(context7/jdtls-lsp/typescript-lsp/claude-md-management 적용 확인, supabase는 미인증). `BE/build.gradle.kts` Spring Boot 4.1.0→3.4.5 재조정 + 스타터명 수정, Gradle 래퍼 9.5.1→8.11.1, 테스트용 H2 추가. `./gradlew clean build` 통과.
* 2026-07-05 — BE 전역 기반 구성 TDD 완료: `ApiResponse`, `BaseEntity`(+JpaAuditingConfig), `ErrorCode`/`BusinessException`/`GlobalExceptionHandler`. 테스트 4종 + 전체 빌드 통과. VSCode cSpell 한글 오탐 → `.vscode/settings.json`에 Hangul ignore 규칙 추가.
* 2026-07-05 — 기존 전역 코드 상세 리뷰(기능/근거/대안) 후 두 가지 반영: (1) `ErrorCode`에 `resultCode`(int, HTTP 기반 40001/40501/50001) 추가하고 `ApiResponse.ErrorBody`를 `(resultCode, code, message)`로 확장(Swagger/Postman 문서화 대비, 응답 본문 노출). (2) 손으로 쓴 필드 getter를 Lombok `@Getter`로 전환(ApiResponse/BaseEntity/BusinessException/ErrorCode). TDD(RED: `resultCode()` 심볼 없음 → GREEN: `clean build` 통과). `ErrorCode` 인터페이스화는 개발 진행하며 판단하기로 보류. (커밋 `d459be0`)
* 2026-07-05 — `ErrorCode` 수동 생성자를 Lombok `@RequiredArgsConstructor`로 대체(수동 생성자와 중복 정의 충돌 확인 후 제거). 동작/응답 포맷 변경 없음, `clean build` 통과. (커밋 `4a03d3a`)
* 2026-07-05 — 설계 유지 결정 논의: (1) `JpaAuditingConfig` 분리 구조 유지 — `@DataJpaTest` 슬라이스 테스트에서 `@Import`로 감사 설정을 재사용하기 위함(진입점 부착은 슬라이스에서 감사 비활성 문제). (2) `ApiResponse` 현재 설계 유지 — 타 프로젝트의 평면 record(`resultCode` 문자열 `"200-1"` 방식)와 비교했으나, 도메인 다수·에러 중앙관리(`ErrorCode` enum) 이점으로 현행(success 불리언 + data/error 분리) 유지.
* 2026-07-05 — TIL 문서 체계 시작: `docs/TIL/` 신설, `docs/TIL/2026-07-05-generics.md`(Java 제네릭 상세) 작성. 학습 기록용이며 코드/설계에 영향 없음.
* 2026-07-05 — 노션 프로젝트 허브 구성(`AIBE6 기록` 워크스페이스): "Attaca 프로젝트" 페이지 + TODO/TIL/스케줄 DB(보드·캘린더 뷰) + 프로젝트 문서 4종. `CLAUDE.md`에 노션 동기화 규칙 추가.
* 2026-07-05 — `resultCode` 포맷 변경: `int 40001` → `String "400-01"`(HTTP상태-일련번호, 가독성). `ErrorCode`/`ApiResponse.ErrorBody`/테스트/문서 반영, TDD(RED→GREEN) 후 `clean build` 통과.
* 2026-07-07 — BE 보안 기반(Security+JWT) 골격 구현 완료(계획 Task1~5, TDD). `Role`/인증 ErrorCode 7종, `JwtProvider`/`JwtProperties`(jjwt 0.12.6), `JwtAuthenticationFilter`, `SecurityConfig`(STATELESS)+핸들러 2종, `AuthController(/api/auth/reissue)`. access+refresh 무상태(refresh에도 role claim). 테스트 5종 + `clean build` 통과. 커밋 5개(`5a76833`~`6da73cf`).
