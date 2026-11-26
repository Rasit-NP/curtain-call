# 0001. 검증은 verify.sh 하나로, 영역은 표식 파일로 판단

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 에이전트 제안, 사람 병합
- 관련: Issue [#1](https://github.com/Rasit-NP/curtain-call/issues/1), PR [#2](https://github.com/Rasit-NP/curtain-call/pull/2)

## 결정

모든 검증은 `scripts/verify.sh`로 한다. 영역(backend·frontend·e2e)을 만들었는지는 폴더가 아니라 표식 파일(`backend/gradlew`, `frontend/package.json`, `e2e/package.json`)로 판단하고, 없으면 SKIP한다. 실패하면 로그 끝부분과 함께 첫 오류로 보이는 줄을 줄 번호와 함께 보여 준다.

## 근거

- 영역 폴더에는 코드보다 `CLAUDE.md`가 먼저 생긴다. 폴더로 판단하면 코드가 없는데도 실행해 `verify.sh quick`이 항상 실패했다(재현).
- `npm ci` 실패 로그는 원인이 맨 앞에 있고 끝 40줄에는 도움말만 있었다. 끝부분만 보여 주면 에이전트가 원인을 못 본다.

## 고려했지만 고르지 않은 것

- 폴더 존재로 판단: 위 이유로 기각.
- 영역별 스크립트를 따로 둠: 진입점이 여러 개면 에이전트와 사람이 다른 검증을 돌리게 된다.

## 적용과 남은 점

새 영역을 만들면 표식 파일이 생기는 순간 검증에 들어간다. 검증 스크립트를 고치면 `verify.sh harness`를 돌린다.
