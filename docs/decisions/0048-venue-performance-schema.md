# 0048. 공연장·배치·공연·공연 좌석 테이블: 공연 좌석은 공연장 배치를 복사하고, 제약은 공연 좌석에 직접 건다

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(Issue #35가 테이블 구성과 이름을 에이전트에게 맡김)
- 관련: Issue [#35](https://github.com/Rasit-NP/curtain-call/issues/35), 결정 [0046](0046-venue-seat-layout.md), [0047](0047-seat-grid-position.md), spec 2.1

## 결정

`V2__create_venues_and_performances.sql`:

- `venues`(공연장, 이름 고유), `venue_seats`(공연장 배치), `performances`(공연, `venue_id`), `seats`(공연 좌석, `performance_id`).
- 공연 좌석은 공연을 만들 때 그 공연장 배치의 `seat_row`·`seat_number`·`grid_row`·`grid_column`을 복사해 가진다(스냅샷). 배치 행을 참조하지 않는다.
- 제약: 같은 공연에서 (`seat_row`, `seat_number`) 고유, (`grid_row`, `grid_column`) 고유. 공연장 배치도 같은 공연장 안에서 같다.
  번호와 위치는 1 이상, `seat_row`는 빈 문자열이 아니고, `status`는 `AVAILABLE`·`HELD`·`SOLD`만.
- `row`는 SQL 키워드라 컬럼 이름에 `seat_` 접두어를 붙인다. 엔티티 필드와 응답은 `row`·`number`다.
- `seat_row`는 `COLLATE "C"`로 둔다. 좌석 목록의 row 오름차순(spec 5)이 DB 로캘과 상관없이 코드 포인트 순서가 된다.
- 엔티티: `Venue`, `VenueSeat`, `Performance`, `Seat`(`performance.domain`). 공연 조회는 공연장을 함께 읽는다(open-in-view를 끔).
- 선점 컬럼(`hold_id`)은 선점 생성 Issue(3단계)에서 더한다.

## 근거

- 공연 좌석은 공연마다 선점·판매 상태를 가지므로 공연마다 있어야 한다(Issue #35).
- 이미 판매한 공연의 좌석은 공연장 배치가 나중에 바뀌어도 그대로여야 한다. 참조하면 배치를 고칠 때 판매된 좌석의 이름·위치가 함께 바뀐다.
- 완료 조건 "같은 공연에서 (row, number)가 고유하고 (gridRow, gridColumn)도 겹치지 않는다"를 공연 좌석 테이블의 유니크 제약 두 개로 바로 보장한다.
  참조 구조에서는 좌석이 다른 공연장의 배치를 가리키지 못하게 복합 외래 키가 더 필요하다.
- 선점(3단계)은 좌석을 `id` 오름차순으로 잠그고 `row`·`number`를 응답한다. 값이 좌석 행에 있으면 조인 없이 된다.

## 고려했지만 고르지 않은 것

- 공연 좌석이 `venue_seat_id`로 배치를 참조: 위 근거로 기각. 같은 공연장의 공연이 같은 배치라는 것은 DB가 보장하지만, 배치 변경이 판매된 좌석에 번진다.
- 공연장 배치 없이 공연 좌석만 둠: Issue가 공연장과 배치를 DB에 두라고 정했다.

## 적용과 남은 점

- 공연 좌석과 공연장 배치가 같은지는 DB가 보장하지 않는다. 지금은 시드만 공연 좌석을 만들고, 시드 테스트(`PerformanceSeedTest`)가 공연마다 좌석이 배치와 같은지 확인한다.
  공연을 만드는 기능은 범위 밖(관리자 화면)이다.
