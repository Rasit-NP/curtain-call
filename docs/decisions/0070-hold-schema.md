# 0070. 선점 테이블: holds와 hold_seats, 좌석의 hold_id, HELD일 때만 hold_id가 있다는 DB 제약

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(Issue #41이 선점 테이블, 좌석의 `hold_id` 컬럼과 제약의 구성과 이름을 에이전트에게 맡김)
- 관련: Issue [#41](https://github.com/Rasit-NP/curtain-call/issues/41), 결정 [0003](0003-hold-is-seat-bundle.md), [0009](0009-seat-hold-id.md), [0048](0048-venue-performance-schema.md), spec 2.1, 2.3

## 결정

`V3__create_holds.sql`:

- `holds`: `id`, `user_id`(users 외래 키), `performance_id`(performances 외래 키), `status`, `created_at`, `expires_at`.
  `status`는 `ACTIVE`·`RELEASED`·`PAID`만(CHECK). 만료는 저장하지 않으므로 `EXPIRED` 상태가 없다(R3 지연 만료).
- `hold_seats`(`hold_id`, `seat_id`): 선점의 좌석 목록(만든 때의 묶음). 기본 키는 (`hold_id`, `seat_id`).
- `seats.hold_id`(holds 외래 키)와 CHECK `(status = 'HELD') = (hold_id IS NOT NULL)`: 저장 상태가 HELD인 좌석에만 holdId가 있다(spec 2.1).
- 인덱스: `holds (user_id, performance_id, status)`. 보유 좌석 수(R2)를 셀 선점을 찾는다.
- 엔티티: `Hold`(`hold.domain`)는 사용자와 공연을 id로만 가진다(연관 엔티티 없음). 좌석 목록은 `@ElementCollection Set<Long> seatIds`,
  좌석의 `holdId`는 `Long`이다. 좌석과 선점은 서로 id로만 잇는다.

## 근거

- 결정 0009: 좌석을 지금 잡은 선점은 좌석의 holdId다. 만료 뒤 새 선점이 좌석을 덮어쓰면 한 좌석이 두 선점의 목록에 들어가므로,
  선점의 좌석 목록(`hold_seats`)과 좌석을 지금 잡은 선점(`seats.hold_id`)을 나눈다. 선점 조회(4단계)는 만료된 선점의 좌석도 보여 주고,
  결제(5단계)는 선점의 묶음을 받는다.
- 좌석 행에 `hold_id`가 하나뿐이라 한 좌석을 두 선점이 함께 잡은 상태는 구조상 생기지 않는다. 동시에 판정하는 문제는 잠금이 맡는다([0073](0073-hold-concurrency.md)).
- spec 2.1의 'HELD일 때만 holdId'를 응용 코드가 아니라 DB 제약으로 보장한다. 해제·결제가 holdId를 비우지 않고 상태만 바꾸는 실수도 막는다.
- 선점이 사용자·공연을 id로만 가지면 `hold.domain`이 인증·공연 엔티티에 묶이지 않고, 단위 테스트가 선점을 바로 만들 수 있다. 응답의 공연 정보는
  서비스가 공연 조회로 읽는다.

## 고려했지만 고르지 않은 것

- `hold_seats` 없이 `seats.hold_id`만: 만료 뒤 덮어쓴 좌석을 원래 선점이 잃는다. 선점 조회와 결제에 묶음이 필요하다.
- 선점·좌석의 공연이 같다는 것을 복합 외래 키로 보장: `holds (id, performance_id)` 유니크와 복합 외래 키가 더 필요하다. 선점을 만드는 곳이 하나이고,
  그 공연의 좌석인지는 404 검사로 확인한다.
- `expires_at = created_at + 5분`을 CHECK로: R3 값이 스키마에 들어가 정책이 바뀌면 마이그레이션이 필요하다. 도메인 단위 테스트가 확인한다.
- `EXPIRED` 저장 상태: 만료를 저장하려면 스케줄러나 쓰기가 필요하다. R3은 지연 만료다.

## 적용과 남은 점

- 해제(4단계)는 holdId가 그 선점인 좌석만 holdId를 비우며 AVAILABLE로, 결제(5단계)는 holdId를 비우며 SOLD로 바꾼다. CHECK가 이를 강제한다.
- `seats.hold_id`와 `hold_seats.seat_id`에는 인덱스를 두지 않았다. 선점과 좌석을 지우지 않고, 지금은 holdId로 좌석을 찾는 조회가 없다. 해제가 그렇게 찾게 되면 그때 더한다.
- 만료된 선점도 저장 상태는 ACTIVE로 남는다. 보유 좌석 수를 셀 때는 같은 사용자·같은 공연의 ACTIVE 선점을 모두 읽는다([0074](0074-valid-hold-single-place.md)).
