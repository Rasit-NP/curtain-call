# 0120. 선점 조회·해제 구현: 해제는 선점 행을 잠근 뒤 주인·상태를 보고, 선점의 좌석 묶음을 id 오름차순으로 잠가 holdId가 이 선점인 좌석만 되돌린다. 조회는 잠그지 않는다

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(Issue #51 '에이전트가 정하는 것': 조회·해제 쿼리, 클래스 구조, 동시성 구현 방법. 명세 2.4와 결정 0100의 결과를 지키고 DB로 보장한다)
- 관련: Issue [#51](https://github.com/Rasit-NP/curtain-call/issues/51), 결정 [0009](0009-seat-hold-id.md), [0070](0070-hold-schema.md), [0074](0074-valid-hold-single-place.md),
  [0090](0090-path-id-ascii-decimal.md), [0091](0091-strict-long-binding.md), [0100](0100-hold-concurrency-v2.md), spec 2.2, 2.3, 2.4, 5, 6.5, 6.6

## 결정

구조:

- `HoldController`에 `GET /api/holds/{holdId}`와 `DELETE /api/holds/{holdId}`를 더한다. `holdId`는 `@PathVariable long`으로 받아, 결정 0091의 바인더가
  결정 0090의 규칙으로 해석한다(정수가 아니면 400 INVALID_INPUT). 컨트롤러에 따로 검사를 두지 않는다.
- `HoldService.get`·`release`가 처리한다. 404 판정은 한 곳(`isOpenHoldOf`)이다: 요청한 사용자의 선점이고 저장 상태가 `ACTIVE`(만료된 선점 포함)일 때만
  조회·해제할 수 있다. 다른 사용자의 선점, 해제·결제된 선점은 없는 선점과 같은 404 NOT_FOUND다.
- 상태 전이는 엔티티 메서드로 둔다. `Hold.release()`는 `ACTIVE` → `RELEASED`이고 현재 시각을 받지 않는다. `Seat.release()`는 `HELD` → `AVAILABLE`이고 holdId를 비운다.
  둘 다 앞 상태가 아니면 `IllegalStateException`을 던진다(서비스가 앞에서 걸러 정상 흐름에서는 생기지 않는다).

조회(spec 6.5):

- 선점과 좌석 목록을 한 번에 읽고(`findWithSeatIdsById`, 잠그지 않음), 공연(공연장 포함)과 좌석을 읽어 선점 생성과 같은 `HoldDetail`로 응답한다.
- `status`는 주입한 `Clock`의 현재 시각으로 `Hold.isValidAt`이 정한다(결정 0074). `seats`와 `amount`는 만든 때의 묶음(`hold_seats`)이다.
  만료 뒤 다른 사용자가 덮어쓴 좌석도 들어간다(결정 0070 '선점 조회는 만료된 선점의 좌석도 보여 준다').

해제(spec 6.6), 한 트랜잭션(READ COMMITTED)에서:

1. 선점 행을 `FOR NO KEY UPDATE`로 잠그고 읽는다(`HoldRepository.lockById`, JPA `PESSIMISTIC_WRITE`). 이 트랜잭션의 첫 조회다.
2. 잠근 뒤 읽은 값으로 404를 판정한다. 같은 선점의 해제가 앞에 있었으면 그 요청이 커밋한 `RELEASED`를 읽어 404다.
3. 선점의 좌석 묶음을 선점 생성과 같은 쿼리(`SeatRepository.lockByPerformanceIdAndIdInOrderById`, `ORDER BY id FOR NO KEY UPDATE`)로 잠그고 읽는다.
4. 잠근 뒤 holdId가 이 선점인 좌석만 `Seat.release()`로 되돌린다. 만료 뒤 다른 선점이 덮어쓴 좌석은 건드리지 않는다.
5. `Hold.release()`로 `RELEASED`를 저장한다. 사용자 행은 잠그지 않고 현재 시각을 읽지 않는다(결정 0100).

## 근거

- 선점 행을 먼저 잠그면 같은 선점의 해제가 한 줄로 서서 하나만 204다. 잠그지 않고 읽으면 여러 요청이 모두 ACTIVE를 읽고 좌석을 기다렸다가 저마다 204가 된다
  (되돌림 확인: `lockById`를 일반 조회로 바꾸자 동시 해제 5개가 모두 204였다).
- 좌석을 잠근 뒤 holdId를 보면, 같은 좌석을 덮어쓰는 다른 사용자의 선점이 커밋 전이어도 기다렸다가 커밋한 holdId를 읽는다. 잠그지 않고 읽은 값으로 좌석을 저장하면
  새 선점의 좌석을 AVAILABLE로 덮어쓴다(되돌림 확인: 좌석을 일반 조회로 읽자 동시성 테스트에서 새 선점의 4석이 모두 풀렸다).
- 좌석을 선점 생성과 같은 쿼리로 잠그면 좌석의 잠금 순서(id 오름차순)가 한 곳에서 정해진다. 해제가 다른 순서로 잠그면 같은 좌석을 덮어쓰는 선점과 교착 상태가 된다
  (되돌림 확인: 해제가 좌석을 id 내림차순으로 하나씩 잠그자 `deadlock detected`로 500이 났다).
- 잠그는 좌석은 holdId가 아니라 선점의 좌석 묶음(`hold_seats`)으로 찾는다. 기본 키로 찾으므로 `seats.hold_id`에 인덱스가 필요 없고(결정 0070 '적용과 남은 점'),
  덮어쓴 좌석까지 같은 순서로 잠가 잠금 순서 규칙이 단순하다.
- 404 판정을 잠근 뒤 자바에서 하면 다른 사용자의 선점도 잠깐 잠그지만, 그 요청은 바로 404로 끝나 아무것도 바꾸지 않는다. 판정을 SQL 조건에 넣는 것보다 조회와
  해제가 같은 판정 한 곳을 쓴다.
- 해제는 현재 시각을 쓰지 않는다. 만료된 선점도 저장 상태가 ACTIVE라 해제하고(spec 6.6), 만료 여부와 관계없이 하는 일이 같다.
- `FOR NO KEY UPDATE`는 외래 키 확인이 거는 `FOR KEY SHARE`와 부딪히지 않는다(결정 0100). 임시 테스트로 확인했다: 해제와 같은 잠금을 쥔 동안 다른 연결의
  `FOR KEY SHARE NOWAIT`는 통과하고 `FOR NO KEY UPDATE NOWAIT`는 `55P03`으로 막혔다(선점 행, 좌석 행 모두. 테스트는 지웠다).

## 고려했지만 고르지 않은 것

- 조건부 UPDATE 한 문장(`UPDATE seats SET status = 'AVAILABLE', hold_id = NULL WHERE hold_id = ?`): 덮어쓴 좌석은 READ COMMITTED의 재확인으로 건너뛰지만,
  UPDATE가 행을 잠그는 순서를 정할 수 없어 같은 좌석을 id 순서로 잠그는 선점과 교착 상태가 될 수 있다. `seats.hold_id` 인덱스도 필요하다.
- 선점 행을 `WHERE id = ? AND user_id = ? AND status = 'ACTIVE'`로 잠금: 다른 사용자의 선점을 잠그지 않지만, 404 판정이 SQL과 자바(조회)의 두 곳이 된다.
- 해제의 좌석 거르기를 `Hold`의 메서드로(`hold.release(seats)`): 좌석을 넘겨받는 도메인 메서드는 단위 테스트에서 저장된 좌석·선점 id가 필요하다.
  거르는 조건이 한 줄이고 통합 테스트(R3)가 확인한다.
- 조회를 별도 서비스(`HoldQueryService`)로: 선점 생성·해제와 같은 404 판정과 응답 값을 쓰므로 한 서비스에 둔다.

## 적용과 남은 점

- 코드: `HoldController`, `HoldService.get`·`release`, `HoldRepository.findWithSeatIdsById`·`lockById`, `Hold.release`, `Seat.release`.
- 결제(5단계)도 선점 행을 같은 `lockById`로 잠근다(결정 0100: 사용자 행 → 멱등 기록 → 선점 행 → 좌석). 결제된 선점의 조회·해제 404는 `isOpenHoldOf`가 이미 다룬다(PAID는 ACTIVE가 아님).
  테스트는 결제 API가 생기는 5단계에서 실제 결제로 만든다.
- 해제는 이 선점의 좌석 중 holdId가 이 선점인 것만 `Seat.release()`를 부른다. 그런 좌석은 DB 제약(`(status = 'HELD') = (hold_id IS NOT NULL)`)상 언제나 HELD라 예외가 나지 않는다.
