# 0014. 오류 코드 enum은 루트 CLAUDE.md 표를 읽어 검사하고, Gradle 입력으로 선언한다

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 에이전트(PR 리뷰 반영)
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

`ErrorCodeTest`가 루트 `CLAUDE.md`의 오류 코드 표를 읽어 `ErrorCode`와 같은지 확인한다. `test` 작업은 `../CLAUDE.md`를 입력으로 선언한다. 프론트엔드도 같은 방식으로 `ERROR_CODES`를 표와 비교한다(#6).

## 근거

- 표가 백엔드·프론트엔드가 함께 쓰는 원본이라, 표와 코드가 어긋나면 바로 알아야 한다.
- 입력 선언이 없으면 표만 바꿨을 때 `:test UP-TO-DATE`로 검사가 돌지 않는 것을 재현했다.

## 고려했지만 고르지 않은 것

- 코드에 목록을 복사하고 사람이 맞춤: 어긋나도 모른다.

## 적용과 남은 점

프론트엔드 테스트는 `process.cwd()` 기준으로 표를 읽어 `frontend/`에서 실행해야 한다는 지적이 PR #7 리뷰에 있다(아직 처리 안 함).
