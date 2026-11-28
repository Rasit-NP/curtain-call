# 0213. 운영 시연 데이터 구현: prod 전용 Flyway 위치 db/demo에 반복 마이그레이션(R__)으로 두고 없는 행만 넣는다. 공연장은 local 시드와 같은 두 곳

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #71 '에이전트가 정하는 것': 운영 시연 데이터를 넣는 방식(Flyway 위치 등)과 공연장 값)
- 관련: Issue [#71](https://github.com/Rasit-NP/curtain-call/issues/71), [#73](https://github.com/Rasit-NP/curtain-call/issues/73), 결정 [0013](0013-flyway-out-of-order-local.md),
  [0036](0036-seed-accounts.md), [0048](0048-venue-performance-schema.md), [0049](0049-performance-seed.md), [0211](0211-prod-demo-data.md)(정책)

## 결정

- **위치**: `src/main/resources/db/demo/`에 둔다. `prod` 프로필의 Flyway 위치는 `classpath:db/migration, classpath:db/demo`다. local·e2e·test는 이 위치를 읽지 않고,
  prod는 local·e2e 시드(`db/seed`)를 읽지 않는다.
- **반복 마이그레이션**: `R__demo_users.sql`(시연 계정), `R__demo_venues.sql`(공연장·배치) 두 파일이다. 버전 마이그레이션(`V1000__` 같은)으로 두지 않는다.
- **없는 행만 넣는다**: 모든 `INSERT`에 `ON CONFLICT DO NOTHING`을 붙인다(계정은 `username`, 공연장은 `name`, 배치는 두 유니크 제약). 있는 행은 바꾸거나 지우지 않는다.
  있는 계정의 비밀번호나 있는 공연장의 배치를 바꾸려면 새 버전 마이그레이션으로 한다.
- **prod의 out-of-order는 그대로 끈다**(결정 0013).
- **값**: 시연 계정 `demo1`~`demo5`, 비밀번호 `demo1234`(결정 0211). 해시는 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`로 계정마다 따로 만든 `{bcrypt}` 값이다.
  공연장은 local·e2e 시드(`V1001__seed_venues.sql`)와 같은 두 곳(대학로 커튼홀 40석·세로 통로, 혜화 다락극장 30석·빈자리와 가로 통로)이다. 공연·공연 좌석은 넣지 않는다.

## 근거

- **배포 서버는 초기화하지 않고 배포마다 이어서 마이그레이션한다**(결정 0211). #73은 공연장·시작 시각 유니크 제약 같은 스키마 마이그레이션(`V6__` 등)을 더할 예정이다.
  시연 데이터를 `V1000__`으로 넣으면, 그 뒤에 더한 `V6__`은 이미 적용된 가장 큰 버전(1000)보다 작아 out-of-order를 끈 prod에서
  `Detected resolved migration not applied to database`로 검증에 걸리고 애플리케이션이 뜨지 않는다(결정 0013에서 local로 재현한 것과 같다).
  시연 데이터를 `V1000__`·`V1001__`로 바꾼 변형으로 `ProdMigrationOrderTest`를 돌려 이 실패를 재현했다.
- 반복 마이그레이션은 버전 순서에 끼지 않고 모든 버전 마이그레이션 뒤에 돈다. 처음 배포에서는 빈 DB에 넣고, 파일이 바뀌면 다음 배포에서 다시 돈다.
  그래서 다시 돌아도 운영 데이터를 덮어쓰지 않도록 없는 행만 넣는다(`ProdProfileTest`가 다시 실행해도 행이 그대로인지 본다).
  `ON CONFLICT`를 뺀 변형은 다시 실행할 때 유니크 위반으로 실패했다.
- out-of-order를 켜는 방법도 동작하지만, 빈 DB(시연 데이터가 V6보다 뒤)와 운영 DB(V6이 시연 데이터보다 뒤)의 적용 순서가 달라진다. 순서를 지키는 지금 설정을 바꿀 이유가 없다.
- 공연장: 명세 2.5의 성질(배치가 서로 다른 공연장, 통로나 빈자리가 있는 배치)을 이미 만족하는 값이다. 시드 값은 샘플 데이터라 같은 값을 쓴다.

## 고려했지만 고르지 않은 것

- 시연 데이터를 버전 마이그레이션(`V1000__`)으로 두고 prod에서 out-of-order를 켜기: 위 근거.
- `db/seed`를 prod에도 쓰고 E2E 계정과 공연만 빼기: 같은 위치를 쓰면 E2E 계정·공연을 넣지 않는 조건을 파일마다 나눠야 하고, local의 Flyway 이력과 얽힌다.
  local 시드의 공연장 파일(`V1001__`)을 옮기거나 고치면 이미 적용한 개발 DB의 검증이 깨진다.
- 애플리케이션이 시작할 때 코드로 넣기(`ApplicationRunner`): Flyway 밖에서 데이터를 바꾸면 무엇이 언제 들어갔는지 이력에 남지 않는다.
- 같은 비밀번호라 local의 `demo` 해시를 그대로 쓰기: 쓸 수는 있지만 계정마다 따로 만드는 편이 다른 계정의 해시를 옮겨 쓴 것으로 보이지 않는다.

## 적용과 남은 점

- 코드: `backend/src/main/resources/db/demo/`, `application.yml`(prod 문서), 테스트 `ProdProfileTest`, `ProdMigrationOrderTest`, `ApplicationProfilesTest`(prod의 Flyway 위치).
- #73: 운영 공연장은 이 파일의 두 곳이다. 공연 생성기는 공연장 이름이나 id를 가정하지 않고 `venues`를 읽는 것이 안전하다.
  스키마 마이그레이션은 지금처럼 `db/migration`에 다음 버전으로 더하면 운영 DB에도 적용된다(`ProdMigrationOrderTest`가 확인한다).
- 결정 0013의 근거 'test·prod에는 시드가 없어 필요 없다'는 이제 'prod 시연 데이터는 반복 마이그레이션이라 필요 없다'로 읽는다. 0013의 결정(test·prod는 끈다)은 그대로다.
