# 0183. GitHub Actions를 도입한다

- 상태: 채택
- 날짜: 2026-11-28
- 결정한 사람: 사람(2026-11-28 메인 세션)
- 관련: 로드맵 2 [#64](https://github.com/Rasit-NP/curtain-call/issues/64)(1단계), Issue [#65](https://github.com/Rasit-NP/curtain-call/issues/65), 결정 [0180](0180-roadmap2-transition.md), [0186](0186-handoff-roadmap-issue-scope.md), [0162](0162-e2e-setup-impl.md)

## 결정

GitHub Actions를 도입한다. 로드맵 2의 1단계 CI Issue에서 PR과 main push마다 `scripts/verify.sh` 전체를 실행하게 만든다.

## 근거

- 사람이 정했다(#64 '정한 것').
- 지금 검증은 각 세션이 로컬에서 돌린 `verify.sh`와 Stop 훅뿐이다. 검증 결과는 PR 본문에만 남는다.

## 고려했지만 고르지 않은 것

없음.

## 적용과 남은 점

- 워크플로 구성(캐시, 같은 PR의 이전 실행 취소, 저장소가 비공개라 실행 시간에 한도가 있는 것)은 1단계 CI Issue에서 정한다.
- 결정 [0162](0162-e2e-setup-impl.md)의 'CI(GitHub Actions)로 돌리는 것은 범위 밖'은 1단계 CI Issue에서 새 결정으로 대체한다. 이 Issue(#65)에서는 0162를 고치지 않는다.
- 로드맵에 있는 CI Issue가 CI 설정을 고쳐도 사람에게 넘기지 않는다([0186](0186-handoff-roadmap-issue-scope.md)).
- main 브랜치 보호 규칙(CI를 통과해야 병합)은 1단계 뒤에 사람이 설정한다.
