# 기본 원칙

- 모든 문서를 한 번에 읽지 말고, 필요한 문서만 선택해서 읽어라
- 문서를 기준으로 작업하고, 문서 없이 추측하지 마라
- 문서가 부족하거나 충돌하면 추측하지 말고 기록하거나 보고하라
- 가능하다면 TDD를 수행해라
- 모든 테스트/린트가 통과해야 작업 완료로 판단한다
- CONTEXT.md를 로그 저장소로 사용하지 마라
- 요청된 작업 범위를 벗어난 기능을 임의로 추가하지 마라
- 에이전트가 생성한 코드도 사람이 이해할 수 있는 구조로 작성하라
- 코드를 작성하게 되면 정리하여 어떤 로직을 구성했는지, 추가했는지 설명하라

---

# 문서

## 기본

- docs/CONTEXT.md : 현재 작업 수행에 필요한 최소한의 정보만 유지하는 캐시

CONTEXT.md 규칙

- 현재 작업에 직접 필요한 정보만 저장한다
- 항상 최신 상태로 유지한다
- 불필요한 내용은 즉시 제거한다
- 로그성 데이터나 중복 정보는 저장하지 않는다

---

## 작업 상태

- docs/TODO-READY.md : 바로 작업 가능한 작업 목록
- docs/TODO-DOING.md : 현재 진행 중인 작업
- docs/TODO-BACKLOG.md : 아직 시작하지 않은 예정 작업
- docs/TODO-DONE.md : 완료된 작업 기록

---

## 아키텍처

- docs/ARCHITECTURE-CONSTITUTION.md : 아키텍처 핵심 원칙
- docs/ARCHITECTURE-STATUTE.md : 아키텍처 구현 규칙

---

## 도메인

- docs/DOMAIN-COMMON-CONSTITUTION.md : 공통 도메인 원칙
- docs/DOMAIN-COMMON-STATUTE.md : 공통 도메인 규칙
- docs/DOMAIN-MEMBER-CONSTITUTION.md : 회원 도메인 원칙
- docs/DOMAIN-MEMBER-STATUTE.md : 회원 도메인 규칙

---

## 기록

- docs/AI-ACTION-LOGS.md : 최근 작업 로그, 최대 100개 유지
- docs/AI-MAJOR-EVENT.md : 주요 사건 및 의사결정
- docs/AI-MAJOR-EVENT-RECAP.md : 사람이 빠르게 읽기 위한 주요 사건 요약

---

# 문서 규칙

## 읽기

- 작업과 직접 관련 있는 문서만 읽어라
- 긴 문서는 바로 전체 읽지 말고, 먼저 grep/search로 관련 키워드를 찾은 뒤 필요한 범위만 읽어라

---

## 업데이트

- 작업 중 발생한 사실, 결정, 규칙, 문제, 다음 작업은 관련 문서에 반영하라
- 프로젝트 상태가 바뀌면 사용자 요청 없이도 관련 문서를 업데이트하라
- 구조/원칙 변경은 임의로 확정하지 말고 기록하거나 보고하라

---

# 작업 후 반영 위치

## TODO

- 완료 → docs/TODO-DONE.md
- 진행 중 → docs/TODO-DOING.md
- 신규 → docs/TODO-BACKLOG.md
- 즉시 가능 → docs/TODO-READY.md

---

## 설계

- 구조 변경 → docs/ARCHITECTURE-STATUTE.md
- 원칙 변경 → docs/ARCHITECTURE-CONSTITUTION.md

---

## 도메인

- 공통 규칙 → docs/DOMAIN-COMMON-STATUTE.md
- 특정 도메인 규칙 → docs/DOMAIN-XXX-STATUTE.md
- 특정 도메인 원칙 → docs/DOMAIN-XXX-CONSTITUTION.md

새로운 도메인이 생기면 반드시 다음 문서를 생성한다.

- docs/DOMAIN-XXX-CONSTITUTION.md
- docs/DOMAIN-XXX-STATUTE.md

도메인 문서 없이 해당 도메인의 기능을 구현하지 마라.

---

## 기록

- 작업 로그 → docs/AI-ACTION-LOGS.md
- 중요한 결정/사건 → docs/AI-MAJOR-EVENT.md
- 중요한 결정/사건 요약 → docs/AI-MAJOR-EVENT-RECAP.md

---

## 상태

- 현재 작업 수행에 필요한 최소 정보 → docs/CONTEXT.md

---

# Git / 커밋

- 커밋 메시지는 무조건 한글로, 알아보기 쉽게 작성한다.
- 제목은 `유형: 한글 요약` 형태로 쓴다. (예: `feat: 백엔드 전역 기반 구성`, `docs: 설계 문서 작성`)
- 본문에는 주요 변경 내역을 한글 불릿으로 정리한다.
- 커밋은 관심사(빌드/설정, 문서, 기능 등) 단위로 분리한다. 성격이 다른 변경을 한 커밋에 섞지 않는다.
- 커밋과 푸시는 사용자가 요청할 때만 수행한다.
- 빌드 산출물(build/, .gradle/), IDE 설정(.idea/, .vscode/), .DS_Store 등은 커밋하지 않는다. (.gitignore로 관리)

---

# 노션(Notion) 동기화

작업이 진행되면 아래 노션 허브도 최신 상태로 반영한다.
`docs/`가 정본(source of truth)이고, 노션은 요약·일정·학습 기록용 미러다.

## 대상

- 허브 페이지 "Attaca 프로젝트" (워크스페이스: `AIBE6 기록`)
  - page_id : `394dca37-4f2e-81ed-8fbf-c86c386714f6`
- 데이터베이스 (data_source_id)
  - ✅ TODO 보드 : `12c759a2-7e93-4614-aae3-1b82bfb7a857`
  - 📚 TIL : `1a4d054a-f131-438c-a59e-a1b5b6678220`
  - 📅 스케줄 : `f44bfdcb-358f-4c91-a903-b1567b6cbf89`

## 반영 규칙

- 작업 완료/상태 변경 → TODO 보드 항목의 `상태`(BACKLOG/READY/DOING/DONE)를 갱신한다.
- 새 학습 정리(TIL) → TIL DB에 항목 추가(날짜·카테고리·태그). 본문에 요약을 넣는다.
- 일정 발생/완료 → 스케줄 DB에 이벤트 추가(간단한 본문으로 무슨 일이 있었는지 기록).
- 날짜 속성은 확장 형식 `date:<속성>:start`, 멀티셀렉트는 JSON 배열 문자열로 넣는다.
- 노션 접근 불가(커넥터 미연결/다른 PC 등)면 건너뛰고 `docs/`에만 반영한 뒤 그 사실을 보고한다.
- docs/ 반영이 우선이며, 노션은 그 뒤에 맞춰 갱신한다(둘이 어긋나면 docs/ 기준).
