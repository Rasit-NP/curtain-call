# 0074. 유효한 선점의 판정은 Hold.isValidAt 한 곳에 둔다: 보유 좌석 수와 좌석의 API 상태도 이 판정을 쓴다

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(Issue #41이 클래스·패키지 구조를 에이전트에게 맡김)
- 관련: Issue [#41](https://github.com/Rasit-NP/curtain-call/issues/41), 결정 [0070](0070-hold-schema.md), [0073](0073-hold-concurrency.md), spec 2.2, 5, 6.3

## 결정

- spec 2.2의 '유효한 선점'(status가 ACTIVE이고 현재 시각 < expiresAt)은 `Hold.isValidAt(now)` 한 곳에만 둔다.
  R1 좌석 판정, R2 보유 좌석 수, 공연 상세의 좌석 API 상태가 모두 이것을 쓴다.
- R2: DB에서 같은 사용자·같은 공연의 ACTIVE 선점(만료된 것 포함)과 좌석 목록을 읽고, `HoldLimit.heldSeatCount`가 유효한 선점의 좌석만 센다.
  SQL에는 `expires_at` 비교를 두지 않는다.
- 좌석의 API 상태: `SeatAvailability.apiStatus(저장 상태, 좌석을 잡은 선점, 현재 시각)`. HELD인데 좌석을 잡은 선점을 넘기지 않으면 예외를 던진다.
  조용히 AVAILABLE로 보면 선점된 좌석을 다시 선점할 수 있게 되기 때문이다(R1).
- 패키지 의존: `hold.domain` → `performance.domain`(Seat, SeatStatus), `performance.application` → `hold.domain`(HoldRepository, SeatAvailability).
  도메인 패키지끼리는 한 방향뿐이다(Seat는 선점과 id로만 잇는다).
- 선점 응답의 좌석은 잠근 좌석을 자바에서 정렬한다(`SeatOrder`). row는 코드 포인트 순서로 비교해, 공연 상세의 DB 정렬(`COLLATE "C"`)과 같은 순서다.

## 근거

- 만료 경계(`<`와 `<=`)가 한 곳에 있으면 되돌림 확인 한 번으로 모든 쓰임을 확인한다. 경계를 바꾸자 단위·통합 테스트 7개가 함께 실패했다.
  SQL과 자바에 나눠 두면 한쪽만 바뀌어도 다른 쪽 테스트가 통과해 어긋남을 놓칠 수 있다.
- R2 단위 테스트('만료·해제된 선점은 보유 좌석 수에 넣지 않는다')가 실제로 쓰이는 코드를 검사한다.
- HELD인데 좌석을 잡은 선점이 없는 상태는 DB 제약과 외래 키([0070](0070-hold-schema.md))로 생기지 않는다. 생기면 호출하는 쪽이 선점을 빠뜨린 것이므로 크게 실패하게 했다.
- `String.compareTo`는 UTF-16 단위로 비교해, 보충 문자가 있으면 spec 5의 코드 포인트 순서와 달라진다.

## 고려했지만 고르지 않은 것

- R2를 SQL 집계(`expires_at > now` 조건)로: 쿼리 한 번이지만 유효 판정이 두 곳이 된다.
- 사용자의 모든 공연의 선점을 읽어 자바에서 공연까지 거름: 읽는 양이 는다. '다른 공연의 선점은 세지 않는다'는 통합 테스트로 확인한다.
- 좌석 정렬을 DB에 다시 맡김(잠근 뒤 row, number 순으로 다시 조회): 쿼리가 하나 는다.

## 적용과 남은 점

- 만료는 저장하지 않으므로(R3) 만료된 ACTIVE 선점이 쌓인다. 보유 좌석 수를 셀 때 같은 사용자·같은 공연의 만료된 선점까지 읽는다.
  한 사용자가 한 공연에서 만료시킨 선점 수만큼이라 지금 규모에서는 문제가 없다.
- 공연 상세는 좌석과 좌석을 잡은 선점을 두 문장으로 읽는다(READ COMMITTED). 그 사이에 바뀐 값은 다음 조회에 반영된다.
- 선점 조회(4단계)의 `Hold.status`(ACTIVE·EXPIRED)도 `isValidAt`으로 정한다(`HoldResponse.Status`).
