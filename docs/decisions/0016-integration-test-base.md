# 0016. 통합 테스트는 컨테이너 하나 공유, 테스트 시계, 테이블 비우기

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

`IntegrationTest`를 상속한다. `postgres:17` 컨테이너를 static 필드로 JVM에서 한 번 띄워 `@ServiceConnection`으로 연결하고 `@Testcontainers`/`@Container`는 쓰지 않는다. `@Primary MutableClock`을 테스트마다 `2026-12-01T10:00:00Z`로 되돌리고, Flyway 이력을 뺀 모든 테이블을 `TRUNCATE ... RESTART IDENTITY CASCADE`한다.

## 근거

- `backend/CLAUDE.md`가 컨테이너 공유와 `@Transactional` 롤백 금지를 요구한다.
- `@Container`는 테스트 클래스마다 컨테이너를 멈춰, 컨텍스트 캐시와 어긋난다.
- R3·R4 만료 테스트는 시각을 옮길 수 있어야 한다.

## 고려했지만 고르지 않은 것

- `@Transactional` 롤백: 다른 스레드의 트랜잭션과 실제 커밋을 가린다(동시성 테스트에 치명적).

## 적용과 남은 점

남은 점(PR #8 리뷰 지적, 아직 처리 안 함): 운영 `Clock` 빈이 실제로 등록됐는지는 스프링 컨텍스트에서 검사하지 않는다.
