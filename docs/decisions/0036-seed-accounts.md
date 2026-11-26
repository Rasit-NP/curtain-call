# 0036. 계정 시드: 시연용 demo, E2E용 e2e-user1·e2e-user2, 테스트에서는 시드 파일을 직접 실행해 검증

- 상태: 에이전트 결정
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27), spec 2.5

## 결정

- `db/seed/V1000__seed_users.sql`: `demo`/`demo1234`(시연용, README 안내), `e2e-user1`/`e2e-pass1`(E2E 로그인 사용자), `e2e-user2`/`e2e-pass2`(시나리오 2의 '다른 사용자'). 해시는 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`로 만든 `{bcrypt}` 값이다.
- 사용자 테이블은 `V1__create_users.sql`(`users`, `username` 고유). `user`는 PostgreSQL 예약어라 복수형을 쓴다.
- `E2eProfileAuthTest`는 test 프로필에 e2e를 더해 띄우고, 테스트마다 테이블을 비운 뒤 `db/seed/*.sql`을 직접 실행해 시드 계정으로 로그인되는지 확인한다.

## 근거

- 시연·E2E용 테스트 계정은 저장소에 둘 수 있다(루트 `CLAUDE.md` '금지 사항'의 예외).
- 시드는 test 프로필 Flyway 위치에 없으므로, 시드 파일이 실제 스키마에서 실행되고 해시가 비밀번호와 맞는지는 이렇게 확인해야 한다. 프로필을 둘 다 켜도 Flyway 위치는 파일 뒤쪽의 test 문서가 덮어써 시드가 이력에 남지 않는다.

## 고려했지만 고르지 않은 것

- 해시가 비밀번호와 맞는지만 보는 단위 테스트: SQL이 스키마에서 실행되는지는 모른다.

## 적용과 남은 점

E2E 첫 설정 Issue의 reset 컨트롤러는 같은 시드를 다시 넣는다. 공연·좌석 시드는 공연 Issue가 `V1001__`부터 더한다.
