# FE MEMBER 프로필 화면(조회/수정/이미지) 설계

> 작성일: 2026-07-16
> 상태: 설계 승인 완료 → 구현 계획(writing-plans) 전환
> 관련 문서: `docs/DOMAIN-MEMBER-STATUTE.md §2·§3.2`, `docs/superpowers/specs/2026-07-15-fe-init-auth-bff-design.md`
> 선행 완료: MEMBER 프로필 BE API(2026-07-15), FE 인증 플로우 BFF(2026-07-15), 카카오 소셜 FE(2026-07-15)

---

## 1. 목표와 범위

### 목표

FE에 **내 프로필 화면**(`/profile`)을 만든다. 조회 모드가 기본이고 "수정" 버튼으로 편집
모드로 전환한다. 악기·자기소개는 편집 폼 저장으로, 프로필 이미지는 파일 선택 즉시 업로드로
반영한다. 모든 BE 호출은 기존 BFF 인증(httpOnly 쿠키 + reissue)을 경유한다.

### 범위 (In)

- 라우트 `/profile`(인증 필요, 미들웨어 보호에 추가)
- 조회 모드(이미지·악기·자기소개) + 편집 모드(악기 칩 토글·자기소개 textarea·저장/취소)
- 프로필 이미지 즉시 업로드(파일 선택 → 업로드 → 화면 갱신)
- BFF 라우트 3개: `PUT /api/bff/me/profile`, `PUT /api/bff/me/profile/image`, `GET /api/bff/profile-options`
- `beFetch` 멀티파트 지원(FormData면 content-type 미설정) + 단위 테스트
- 대시보드에 "내 프로필" 진입 링크
- Vitest 단위/스모크 테스트

### 범위 밖 (Out)

- **다른 회원 프로필 조회** — BE에도 없음. 소비처 생길 때.
- 이미지 크롭·미리보기 편집·드래그앤드롭
- 악기 검색/그룹 접기, 프로필 완성도 표시 등 고도화
- 프로필 캐싱·낙관적 업데이트(현 규모엔 과함, 네이티브 fetch 유지)
- 실제 이미지가 S3로 가는 검증(현재 storage.type=local, 기존 BACKLOG)

---

## 2. 화면 (`/profile`)

클라이언트 컴포넌트. 진입 시 프로필과 악기 선택지를 병렬 로드한다.

### 2-1. 조회 모드 (기본)

- 프로필 이미지(`profileImageUrl`, 없으면 플레이스홀더 원형)
- 악기 목록: 저장된 `instruments`(코드)를 선택지의 한글 label로 변환해 칩으로 표시. 없으면 "등록된 악기가 없습니다".
- 자기소개(`bio`), 없으면 "자기소개가 없습니다".
- 버튼: **[수정]**(편집 모드 전환), **[이미지 변경]**(파일 선택), 그리고 대시보드로 돌아가는 링크.

### 2-2. 편집 모드

- 악기: 선택지 21종을 **칩 토글**로 표시(선택된 것 강조). 최대 10개(초과 시 클라이언트에서 막고 안내).
- 자기소개: textarea, 최대 500자(글자 수 표시).
- 버튼: **[저장]** → `PUT /api/bff/me/profile` → 성공 시 조회 모드로 + 최신값 반영. **[취소]** → 편집 내용 버리고 조회 모드로.
- 실패 시 폼 상단에 BE `message` 표시.

### 2-3. 이미지 (모드 무관)

- **[이미지 변경]** → 파일 선택 → **즉시** `PUT /api/bff/me/profile/image`(multipart) → 성공 시 새 `profileImageUrl`로 화면 갱신.
- 클라이언트 사전 검증: `image/*`만, 그 외는 업로드 전 안내. (BE도 `INVALID_FILE` 400-02로 재검증)
- 업로드 중 버튼 비활성/로딩 표시.

---

## 3. BFF 라우트 (기존 `me` 외 3개 추가)

모두 `authedBeFetch`(Bearer + 401 시 reissue 1회 재시도) 경유. UI엔 `{ok, data?, message}`만 노출.

| BFF 경로 | 메서드 | BE 위임 | 바디 |
|---|---|---|---|
| `/api/bff/me/profile` | PUT | `PUT /api/members/me/profile` | JSON `{instruments[], bio}` 통과 |
| `/api/bff/me/profile/image` | PUT | `PUT /api/members/me/profile/image` | multipart(part `file`) 재구성 전달 |
| `/api/bff/profile-options` | GET | `GET /api/members/profile-options` | 없음 |

- 기존 `/api/bff/me`(GET → `/api/members/me/profile`)는 그대로 재사용(조회).
- 이미지 라우트: `await request.formData()`로 `file` 파트를 꺼내 **새 FormData**에 `file`로 담아 `authedBeFetch(store, '/api/members/me/profile/image', { method: 'PUT', body: fd })`. 파일 누락 시 400 + 메시지.

---

## 4. 멀티파트 지원 — `beFetch` 수정 (핵심)

현재 `beFetch`는 `headers: { 'content-type': 'application/json', ... }`를 강제한다. multipart는
브라우저/undici가 `boundary`를 포함한 content-type을 자동 설정해야 하므로, **body가 `FormData`면
기본 JSON content-type을 붙이지 않는다.**

- 변경: `beFetch`에서 `body instanceof FormData`면 기본 content-type을 생략(호출자가 명시하지 않는 한).
- JSON 경로(기존 login/signup/profile PUT 등)는 동작 불변.
- `authedBeFetch`는 `beFetch`를 감싸므로 자동으로 멀티파트도 지원(Bearer + reissue 유지).
- 단위 테스트: FormData body → content-type 미설정 / JSON body → `application/json` 유지.

---

## 5. 파일 구조

```
FE/
├── app/
│   ├── profile/page.tsx                         # 서버: 진입점(클라이언트 컴포넌트 렌더). 필요 시 얇게
│   ├── profile/ProfileView.tsx                  # 'use client' 조회/편집 통합 화면
│   ├── api/bff/me/profile/route.ts              # PUT → BE 프로필 수정
│   ├── api/bff/me/profile/image/route.ts        # PUT → BE 이미지(멀티파트)
│   └── api/bff/profile-options/route.ts         # GET → BE 선택지
├── lib/
│   ├── server/beClient.ts                       # (수정) FormData 시 content-type 생략
│   └── api.ts                                    # (추가) putBff / putBffForm 헬퍼
├── middleware.ts                                 # (수정) matcher에 /profile 추가
└── app/dashboard/page.tsx                        # (수정) 내 프로필 링크
```

### 클라이언트 헬퍼(`lib/api.ts` 추가)

- `putBff<T>(path, body?)` — JSON PUT(프로필 수정).
- `putBffForm<T>(path, form: FormData)` — multipart PUT(이미지). content-type 지정 안 함(브라우저가 boundary 설정).
- 기존 `postBff`/`getBff` 유지.

---

## 6. 데이터 흐름

```
/profile 진입(클라이언트)
   │  병렬: getBff('/api/bff/me')  +  getBff('/api/bff/profile-options')
   │  me 실패(401 등) → /login
   ▼
조회 모드 렌더(이미지/악기 label/자기소개)
   ├─ [수정] → 편집 모드(현재값으로 폼 초기화)
   │     └─ [저장] → putBff('/api/bff/me/profile', {instruments, bio}) → 조회 모드 + 갱신
   └─ [이미지 변경] → 파일 선택 → putBffForm('/api/bff/me/profile/image', fd) → profileImageUrl 갱신
```

- 악기 코드↔label 변환은 `profile-options` 응답(`{code, label}[]`)으로 수행.

---

## 7. 에러 처리

- me 조회 실패(401) → `/login`. 그 외 로드 실패 → 화면에 "프로필을 불러오지 못했습니다".
- 저장/업로드 실패 → BE `message`를 해당 영역에 표시(악기 10개 초과 400-01, 비이미지 400-02 등).
- 이미지 비이미지 타입은 업로드 전 클라이언트에서 1차 차단 + BE 2차 검증.

---

## 8. 테스트 전략

| 대상 | 방식 |
|---|---|
| `beFetch` 멀티파트 분기 | FormData body → content-type 헤더 미포함 / JSON body → `application/json` (fetch 목) |
| BFF `me/profile` PUT | next/headers·fetch 목: BE로 JSON 통과, 결과 반환 |
| BFF `me/profile/image` PUT | formData 목: 파일 파트 재구성 전달, 파일 누락 시 400 |
| BFF `profile-options` GET | 위임 + 결과 반환 |
| `ProfileView` 스모크 | 조회 렌더(악기 label·자기소개), [수정]→편집 전환, 칩 토글, textarea 글자수 |
| `api.ts` `putBff`/`putBffForm` | 상대경로·메서드·body 형태 검증(fetch 목) |

- `npm --prefix FE run build` + `npm --prefix FE test` 통과가 완료 기준.
- 실연동(BE 기동 후 프로필 수정·이미지 업로드)은 수동 확인. 인증/BFF 왕복은 이미 검증됨.

---

## 9. 문서 반영(구현 완료 시)

- `ARCHITECTURE-STATUTE` FE 절 — 프로필 화면·이미지 멀티파트 BFF 한 줄
- `CONTEXT.md` — 프로필 화면 흐름·`beFetch` FormData 지원 주의
- `DOMAIN-MEMBER-STATUTE §5` — FE 프로필 화면 완료 표시
- `TODO-BACKLOG` — "FE: MEMBER 프로필 화면" 항목 닫기
- `AI-ACTION-LOGS`, `AI-MAJOR-EVENT`(멀티파트 BFF 결정), 노션

---

## 10. 후속 작업

- 다른 회원 프로필 공개 조회(BE 확장 동반)
- 이미지 미리보기·크롭
- VERIFIED-PERFORMER 뱃지 노출(그 도메인 구현 시 `ProfileResponse.verified` 추가와 함께)
