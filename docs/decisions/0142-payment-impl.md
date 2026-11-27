# 0142. 결제 구현: 잠금 뒤 판정하고 결과를 값으로 돌려 커밋한다(402도 결제 FAILED가 남는다). 선점은 판정 시각으로 PAID, 모의 결제는 한 구현에 테스트 조작을 둔다

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #55 '에이전트가 정하는 것': 거절의 결제 기록을 커밋하는 방법(트랜잭션 경계), 동시성 구현 방법, 클래스 구조, 쿼리)
- 관련: Issue [#55](https://github.com/Rasit-NP/curtain-call/issues/55), 결정 [0012](0012-backend-error-mapping.md), [0070](0070-hold-schema.md), [0074](0074-valid-hold-single-place.md),
  [0100](0100-hold-concurrency-v2.md), [0120](0120-hold-query-release-impl.md), [0140](0140-reservation-payment-schema.md), [0141](0141-idempotency-key-format.md), spec 2.2, 2.3, 2.4, 6.7, 6.7.2, 6.8

## 결정

결제 흐름(`PaymentService.pay`, 한 트랜잭션, READ COMMITTED):

1. 요청한 사용자 행을 잠근다(`UserRepository.lockById`, `FOR NO KEY UPDATE`). 트랜잭션의 첫 조회다.
2. (#56) 같은 사용자·같은 키의 멱등 기록을 여기서 잡는다. 지금은 자리만 주석으로 둔다.
3. 선점 행을 잠근다(`HoldRepository.lockById`, 해제와 같은 쿼리). 잠근 뒤 읽은 값으로 `Hold.isOpenFor(userId)`(내 선점이고 ACTIVE)를 보고, 아니면 404다.
4. 선점의 좌석 묶음을 선점 생성·해제와 같은 쿼리(`lockByPerformanceIdAndIdInOrderById`, `ORDER BY id FOR NO KEY UPDATE`)로 잠근다.
5. 현재 시각을 주입한 `Clock`으로 한 번 읽는다. `Hold.isValidAt(now)`가 거짓이면 409 HOLD_EXPIRED이고 모의 결제를 부르지 않는다.
6. 금액(`Hold.amount(price)`, spec 2.2)으로 모의 결제(`PaymentGateway`)를 부른다. 거절이면 결제 FAILED만 저장한다.
7. 승인이면 좌석마다 `Seat.sell(holdId)`(HELD이고 이 선점이 잡은 좌석 → SOLD, holdId 비움), `Hold.pay(now)`(판정 시각에 유효해야 PAID), 예매와 결제 SUCCEEDED를 저장한다.

결과와 트랜잭션 경계:

- 서비스는 결과를 값(`PaymentOutcome`: `Approved` 또는 `Rejected(code)`)으로 돌려주고 트랜잭션을 정상 커밋한다. 404·409·402를 예외로 던지지 않는다.
  그래서 거절(402)의 결제 FAILED가 커밋되고, 404·409는 바꾼 것이 없어 커밋해도 같다. 예상하지 못한 예외(500)만 트랜잭션을 되돌린다.
- 컨트롤러가 커밋 뒤에 `Rejected`를 `ApiException`으로 바꿔 공통 처리기가 응답을 만든다(결정 0012). 승인은 201 `{ paymentId, amount, reservation }`이다.
- #56은 같은 트랜잭션에서 이 결과(201·402·404·409)를 멱등 기록에 저장하면 된다(spec 6.7.1).

도메인:

- `Hold.isOpenFor(userId)`: 조회·해제·결제가 함께 쓰는 404 판정. #51의 `HoldService.isOpenHoldOf`를 옮겼다(동작은 같다).
- `Hold.pay(judgedAt)`: ACTIVE → PAID. 판정 시각(5번의 `now`)에 유효하지 않으면 예외를 던진다. 판정을 통과했으면 모의 결제가 늦게 끝나도 결제를 마치도록(spec 2.4)
  승인된 때가 아니라 판정 시각을 받는다. 서비스가 5번에서 걸러 정상 흐름에서는 예외가 나지 않는다.
- `Hold.amount(price)`: 결제 금액. 선점 응답의 금액(`HoldDetail.amount`)도 이것을 쓴다.
- `HoldLimit.ownedSeatCount(holds, reservedSeats, now)`: 보유 좌석 수 = 유효한 선점의 좌석 + 예매 좌석. 선점 생성(`HoldService.create`)이
  사용자 행을 잠근 뒤 ACTIVE 선점과 예매 좌석 수(`ReservationRepository.countSeatsByUserIdAndPerformanceId`, 네이티브 집계)를 읽어 이것으로 센다.
  결제된 선점(PAID)은 ACTIVE 조회에 들어오지 않으므로 예매로만 센다.

모의 결제(spec 6.7.2):

- `FakePaymentGateway`(`payment.infra`) 하나를 모든 프로필에서 쓴다. 아무것도 지정하지 않으면 늘 승인한다. 테스트가 아닌 프로필에는 조작을 부르는 코드가 없다.
- 테스트 조작은 같은 클래스의 메서드다: `declineNext()`, `callCount()`, `pauseNext()`(돌려준 `Pause`로 들어온 것을 기다리고 풀어 준다), `reset()`.
  "다음 호출"은 지정한 뒤 처음 들어오는 호출 하나다. 통합 테스트 기반(`IntegrationTest`)이 매 테스트 전에 `reset()`한다.
- 상태는 원자적 값(`AtomicInteger` 등)과 `CountDownLatch`다. 여러 요청 스레드가 함께 부르기 때문이고, 결제의 동시성을 보장하는 것은 아니다(그것은 DB 잠금).
  멈춘 호출은 30초 안에 풀리지 않으면 예외로 끝난다(테스트가 풀어 주지 않고 끝나도 요청 스레드가 남지 않게).

`Idempotency-Key` 헤더(결정 0141):

- `IdempotencyKeyHeader.read(request)`가 헤더 값 목록을 읽어 `parse`로 검증한다. 컨트롤러가 본문 검증 뒤, 서비스 전에 부른다. 키는 아직 서비스에 넘기지 않는다(#56).
- Spring Security 방화벽은 헤더 값을 읽는 순간 제어 문자가 든 값을 거부한다(`RequestRejectedException`). 이 헤더를 읽다 난 거부는 결정 0141에 걸리는 값이므로
  같은 400 INVALID_INPUT으로 바꾼다. 보안 설정(방화벽 규칙)은 바꾸지 않는다.

내 예매(spec 6.8): `ReservationQueryService.findMine`이 예매(좌석 목록 포함, `reservedAt` 내림차순·`id` 내림차순), 공연(공연장 포함), 좌석을 각각 한 번에 읽고,
좌석은 `SeatOrder`로 row·number 순서로 정렬한다. 잠그지 않는다.

## 근거

- 결과를 값으로 돌려 커밋하면 거절의 결제 기록이 남고(spec 2.1 '모의 결제를 부를 때마다 하나'), 예외와 롤백 규칙(`noRollbackFor`)을 따로 두지 않는다.
  되돌림 확인: 거절을 `ApiException`으로 던지게 바꾸자 결제 FAILED가 남지 않아 테스트 3개가 실패했다.
- 잠금 순서와 시각을 읽는 때는 결정 0100 그대로다. 되돌림 확인에서 사용자 행 잠금, 선점 행 잠금, 좌석 잠금, 좌석 잠금 순서, 잠그기 전에 시각 읽기를 각각 되돌리면
  해당 동시성 테스트가 실패했다(`docs/history/0055-backend-payment.md`).
- 404 판정을 좌석을 잠그기 전에 하면 다른 사용자의 선점이나 결제된 선점의 좌석을 잠그지 않는다. 해제(결정 0120)와 같은 순서다.
- `Hold.pay`가 판정 시각을 받아 다시 확인하면, 서비스의 만료 판정을 실수로 빼도 만료된 선점이 PAID가 되지 않는다(500으로 끝나고 되돌려진다).
  되돌림 확인: 서비스의 만료 판정을 빼자 409 대신 500이 되어 R4 테스트가 실패했다.
- 모의 결제를 한 구현에 두는 것은 spec 6.7.2와 `backend/CLAUDE.md`('`FakePaymentGateway`가 성공과 실패를 흉내 내고, 테스트에서 결과를 지정할 수 있어야 한다')를
  그대로 따른 것이다. 통합 테스트가 운영과 같은 빈을 쓴다.
- 방화벽 거부를 헤더를 읽는 곳에서만 바꾸는 것은 범위를 이 헤더로 좁히기 위해서다. 공통 처리기에서 `RequestRejectedException`을 모두 400으로 바꾸면 다른 경로의 동작도
  바뀌므로 이 Issue에서 정할 일이 아니다.

## 고려했지만 고르지 않은 것

- 거절을 예외로 던지고 `@Transactional(noRollbackFor = …)`: 거절 말고도 결과마다 롤백 여부를 따로 정해야 하고, #56에서 404·409도 저장하려면 다시 바꿔야 한다.
- 거절 기록을 별도 트랜잭션(`REQUIRES_NEW`)으로: 잠금을 쥔 바깥 트랜잭션과 연결이 둘이 되고, #56의 멱등 기록과 결제 기록이 다른 트랜잭션에 놓인다.
- 결제 전에 보유 좌석 수를 다시 세어 거부: 결정 0100 '고려했지만 고르지 않은 것'(판정을 통과한 결제를 거부하는 것은 정책 결정).
- 테스트 전용 `PaymentGateway` 구현을 테스트 설정에서 `@Primary`로: 운영의 `FakePaymentGateway`가 통합 테스트에서 쓰이지 않고, spec 6.7.2의 '테스트에서는 … 할 수 있어야 한다'와 어긋난다.
- 공통 처리기에서 `RequestRejectedException`을 400으로: 위 '근거'.

## 적용과 남은 점

- 코드: `PaymentService`, `PaymentOutcome`, `PaymentController`, `PayRequest`, `PaymentResponse`, `IdempotencyKeyHeader`, `FakePaymentGateway`, `PaymentGateway`,
  `ReservationQueryService`, `ReservationController`, `ReservationResponse`, `ReservationDetail`, `Hold.isOpenFor`·`pay`·`amount`, `Seat.sell`, `HoldLimit.ownedSeatCount`, `HoldService.create`.
- 결제는 모의 결제를 기다리는 동안 사용자 행·선점 행·좌석 잠금을 쥔다(결정 0100 '적용과 남은 점'). 테스트가 아닌 프로필에서는 모의 결제가 바로 끝난다.
- 토큰의 사용자 행이 없으면(결정 0071) 잠글 행이 없고, 그 사용자의 선점도 없으므로 404다.
- 다른 헤더(예: `Authorization`)에 방화벽이 거부하는 값이 오면 어떻게 되는지는 이 Issue에서 확인하지 않았다. 앱이 읽지 않는 헤더(`X-Note: 키`)는 영향이 없었다(임시 테스트).
