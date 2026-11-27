# #51 백엔드 선점 조회·해제(R2·R3·R6): 내 선점 조회 API, 내 선점 해제 API, 해제 동시성

- 기간: 2026-11-27
- Issue: [#51](https://github.com/Rasit-NP/curtain-call/issues/51), PR: (만들면 적는다)
- 관련 결정: 기존 [0070](../decisions/0070-hold-schema.md), [0071](../decisions/0071-missing-token-user-not-handled.md),
  [0074](../decisions/0074-valid-hold-single-place.md), [0090](../decisions/0090-path-id-ascii-decimal.md),
  [0091](../decisions/0091-strict-long-binding.md), [0100](../decisions/0100-hold-concurrency-v2.md)

## 배경

로드맵 [#13](https://github.com/Rasit-NP/curtain-call/issues/13)의 4단계 백엔드다. 결제 화면(프론트엔드 [#52](https://github.com/Rasit-NP/curtain-call/issues/52), 동시에 진행)은
새로고침할 때 `GET /api/holds/{holdId}`로 선점을 다시 불러오고, 결제 전에 앱 안의 다른 화면으로 갈 때 `DELETE /api/holds/{holdId}`를 부른다(명세 7.3).
3단계(#41)가 선점 생성, 선점 테이블과 좌석의 `hold_id`, 유효한 선점의 판정 `Hold.isValidAt`을 만들었고,
3단계 뒤 점검(#45·#46)이 경로 id의 엄격한 해석과 해제의 잠금 순서(선점 행 → 좌석 `id` 오름차순, 사용자 행은 잠그지 않음)를 정했다.
Issue에는 아직 정하지 않은 정책이 없다. 이 세션은 `backend/`와 `docs/`만 고치고, 결정 번호는 0120부터 쓴다.

## 진행 순서

1. Issue, 루트·백엔드 `CLAUDE.md`, 명세(2.2, 2.3, 2.4, 5, 6.5, 6.6), 대응표, 관련 결정(0070, 0071, 0074, 0090, 0091, 0100)과
   기존 선점 코드·테스트(`HoldService`, `HoldApiTest`, `HoldConcurrencyTest`)를 읽었다.
2. 브랜치를 `feat/51-hold-query-release`로 바꾸고 시작점이 최신 `origin/main`(c947d05)인지 확인했다.
