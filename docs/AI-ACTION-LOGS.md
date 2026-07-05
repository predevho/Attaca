# AI-ACTION-LOGS

최근 작업 로그. 최대 100개 유지 (오래된 항목부터 제거).

---

* 2026-07-05 — 프로젝트 초기 설계 브레인스토밍 진행. 서비스 정의(음악인 커뮤니티 SNS), 도메인 6개, 기술 스택 확정.
* 2026-07-05 — `docs/` 문서 체계 생성: ARCHITECTURE(CONSTITUTION/STATUTE), DOMAIN-COMMON(CONSTITUTION/STATUTE), DOMAIN-MEMBER(CONSTITUTION/STATUTE), TODO-*, CONTEXT, AI 기록 문서.
* 2026-07-05 — 플러그인 설치 검증(context7/jdtls-lsp/typescript-lsp/claude-md-management 적용 확인, supabase는 미인증). `BE/build.gradle.kts` Spring Boot 4.1.0→3.4.5 재조정 + 스타터명 수정, Gradle 래퍼 9.5.1→8.11.1, 테스트용 H2 추가. `./gradlew clean build` 통과.
* 2026-07-05 — BE 전역 기반 구성 TDD 완료: `ApiResponse`, `BaseEntity`(+JpaAuditingConfig), `ErrorCode`/`BusinessException`/`GlobalExceptionHandler`. 테스트 4종 + 전체 빌드 통과. VSCode cSpell 한글 오탐 → `.vscode/settings.json`에 Hangul ignore 규칙 추가.
