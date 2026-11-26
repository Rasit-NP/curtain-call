# 0021. fetch는 src/api, localStorage는 src/auth에서만 쓰도록 ESLint로 막는다

- 상태: 제안(PR 병합 전)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#6](https://github.com/Rasit-NP/curtain-call/issues/6), PR [#7](https://github.com/Rasit-NP/curtain-call/pull/7)

## 결정

ESLint `no-restricted-globals`·`no-restricted-properties`로 `src/api/` 밖의 `fetch`, `src/auth/` 밖의 `localStorage`(`window.`·`globalThis.`·`self.` 포함)를 막는다. `src/test/`는 제외한다.

## 근거

- `frontend/CLAUDE.md`의 규칙을 문서로만 두지 않고 기계적으로 강제한다.

## 고려했지만 고르지 않은 것

- 리뷰로만 확인: 놓치기 쉽다.

## 적용과 남은 점

임시 파일로 실제로 오류가 나는 것을 확인했다.
