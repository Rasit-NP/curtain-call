# 0140. 예매·결제 테이블: reservations와 reservation_seats, payments. 한 선점에 예매 하나, 한 좌석에 예매 하나, 승인된 결제에만 예매가 있다는 DB 제약. 예매 시각은 판정 시각

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #55 '에이전트가 정하는 것': 예매·결제 테이블과 클래스 구조, `reservedAt`을 읽는 때)
- 관련: Issue [#55](https://github.com/Rasit-NP/curtain-call/issues/55), 결정 [0003](0003-hold-is-seat-bundle.md), [0006](0006-idempotency-record.md), [0070](0070-hold-schema.md), [0100](0100-hold-concurrency-v2.md), spec 2.1, 2.2, 2.3, 5, 6.7, 6.8

## 결정

`V4__create_reservations_and_payments.sql`:

- `reservations`: `id`, `user_id`, `performance_id`, `hold_id`(모두 외래 키), `amount`(BIGINT), `reserved_at`.
  - `hold_id` 유니크: 한 선점으로 예매는 하나다.
  - 인덱스 `(user_id, performance_id)`: 보유 좌석 수(R2)의 예매 몫. `(user_id, reserved_at DESC, id DESC)`: 내 예매(6.8)의 순서.
- `reservation_seats`(`reservation_id`, `seat_id`): 예매의 좌석(선점의 묶음을 옮김). 기본 키 (`reservation_id`, `seat_id`)와 `seat_id` 유니크:
  취소가 없으므로(spec 8) 한 좌석은 예매 하나에만 들어간다.
- `payments`: `id`, `user_id`, `hold_id`(외래 키), `amount`, `status`(`SUCCEEDED`·`FAILED`, CHECK), `reservation_id`(외래 키, 유니크), `created_at`.
  CHECK `(status = 'SUCCEEDED') = (reservation_id IS NOT NULL)`: 승인된 결제에만 예매가 있다.
  모의 결제를 부를 때마다 한 행이고, 404·409로 거부한 요청은 모의 결제를 부르지 않으므로 행이 없다(spec 2.1).
- 멱등 기록 테이블은 만들지 않는다(#56).

엔티티:

- `Reservation`(`reservation.domain`): 사용자·공연·선점을 id로만 가진다(선점과 같은 방식, 결정 0070). 좌석 목록은 `@ElementCollection Set<Long> seatIds`.
  `Reservation.of(paidHold, amount, reservedAt)`로만 만들고, 선점이 PAID가 아니면 예외를 던진다.
- `Payment`(`payment.domain`): `Payment.succeeded(..., reservationId, ...)`·`Payment.failed(...)`로만 만들어 위 CHECK와 같은 모양을 지킨다.
- 예매 시각(`reservedAt`)과 결제의 `created_at`은 결제가 만료를 판정한 시각이다. 모든 잠금 뒤에 주입한 `Clock`으로 한 번 읽은 값(결정 0100)을
  만료 판정, `Hold.pay`, 예매 시각에 함께 쓴다. 모의 결제가 끝난 뒤 시계를 다시 읽지 않는다.

## 근거

- 같은 선점을 두 번 결제하거나 같은 좌석을 두 번 파는 일은 잠금(결정 0100)이 막지만, 잠금을 실수로 되돌려도 DB가 한 번 더 막도록 유니크 제약을 둔다.
  되돌림 확인에서 결제의 사용자 행 잠금과 선점 행 잠금을 함께 빼자, 같은 선점의 두 번째 결제가 그보다 앞의 `Seat.sell` 확인(이미 SOLD인 좌석)에 걸려
  500으로 끝났다(1회 실행, 첫 반복). 예매는 둘 생기지 않았고, 유니크 제약까지는 가지 않았다. 제약은 응용 코드의 확인도 빠졌을 때를 위한 마지막 방어다.
- 예매에 금액을 둔다(spec 2.1). 공연 가격이 나중에 바뀌어도 예매 금액은 결제한 금액이다.
- 예매 시각을 판정 시각으로 두면, 판정을 통과했으면 늦게 끝나도 결제를 마친다는 spec 2.4와 맞고(만료 전 시각에 예매된 것으로 남는다), 통합 테스트가 시각을
  고정해 응답의 `reservedAt`을 정확히 확인할 수 있다. 테스트가 아닌 프로필의 모의 결제는 바로 끝나므로 두 시각의 차이는 없다.
- 금액은 BIGINT다. 가격(INTEGER) × 4석이 int 범위를 넘을 수 있다(`Hold.amount`도 long으로 계산한다).

## 고려했지만 고르지 않은 것

- 예매 좌석을 따로 두지 않고 선점의 `hold_seats`를 다시 씀: 예매 조회와 보유 좌석 수가 선점 테이블에 기대고, 좌석 하나를 두 번 파는 것을 유니크 제약으로 막을 수 없다.
- 결제 행에 예매를 합침(결제 = 예매): spec 2.1은 거절된 결제도 남기므로(FAILED) 둘을 나눈다.
- 승인 뒤 시계를 다시 읽어 예매 시각으로: 판정 뒤 만료 시각을 넘긴 시각이 예매 시각이 될 수 있고, 같은 결제 안에서 시각이 둘이 된다.

## 적용과 남은 점

- 코드: `V4__create_reservations_and_payments.sql`, `Reservation`, `ReservationRepository`, `Payment`, `PaymentStatus`, `PaymentRepository`.
- 예매 취소가 생기면(spec 8) `reservation_seats.seat_id` 유니크를 다시 정해야 한다. 지금은 범위 밖이다.
- `payments.hold_id`에는 인덱스를 두지 않았다. 지금 선점으로 결제를 찾는 운영 조회가 없다(테스트만 찾는다).
