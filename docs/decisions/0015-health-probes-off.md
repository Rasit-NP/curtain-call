# 0015. health 응답을 {"status":"UP"}로 두려고 프로브 그룹을 끈다

- 상태: 에이전트 결정
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

`management.endpoint.health.probes.enabled=false`.

## 근거

- Spring Boot 4는 liveness·readiness 그룹을 기본으로 켜서 응답에 `"groups"`가 붙고, spec 6.10의 `{"status":"UP"}`와 달라진다(`bootRun` 뒤 `curl`과 엄격 비교 테스트로 재현).

## 고려했지만 고르지 않은 것

- 명세를 바꿈: E2E 준비 확인에는 status만 필요해 명세를 바꿀 이유가 없다.

## 적용과 남은 점

쿠버네티스 프로브는 쓰지 않는다.
