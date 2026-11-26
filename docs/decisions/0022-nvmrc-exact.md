# 0022. .nvmrc는 정확한 버전으로 고정

- 상태: 제안(PR 병합 전)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#6](https://github.com/Rasit-NP/curtain-call/issues/6), PR [#7](https://github.com/Rasit-NP/curtain-call/pull/7)

## 결정

루트 `.nvmrc`는 `24`가 아니라 `24.16.0`이다.

## 근거

- `frontend/CLAUDE.md`의 '고정한다'를 문자 그대로 따랐다. 로컬에서 확인한 버전이고 모든 의존성의 engines를 만족한다.

## 고려했지만 고르지 않은 것

- 메이저만(`24`): 기계마다 패치 버전이 달라질 수 있다.

## 적용과 남은 점

E2E도 이 파일을 따른다.
