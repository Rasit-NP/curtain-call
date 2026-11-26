# 0013. local·e2e에서 Flyway out-of-order를 켠다

- 상태: 제안(PR 병합 전)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트(PR 리뷰 반영)
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

`local | e2e` 프로필은 `spring.flyway.out-of-order: true`. test·prod는 끈다.

## 근거

- 시드는 `V1000__`부터 쓰고(`backend/CLAUDE.md`) 스키마 마이그레이션과 같은 이력을 쓴다. compose 볼륨이 이력을 유지하므로, 시드가 적용된 뒤 추가된 `V2__`가 무시돼 기동이 실패한다.
- compose DB에 임시 시드와 마이그레이션으로 `Detected resolved migration not applied to database`를 재현하고, out-of-order로 해결되는 것을 확인했다.

## 고려했지만 고르지 않은 것

- 시드를 반복 마이그레이션(`R__`)으로: 시드 순서·재실행 규칙을 새로 정해야 한다.
- 볼륨을 매번 지우기: 로컬 데이터가 사라진다.

## 적용과 남은 점

test·prod에는 시드가 없어 필요 없다.
