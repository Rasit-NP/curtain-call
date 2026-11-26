# 0047. 좌석 응답 Seat에 gridRow·gridColumn(1부터)을 더한다

- 상태: 채택
- 날짜: 2026-11-27
- 결정한 사람: 사람(메인 세션 대화, Issue #35 '이미 정한 정책')
- 관련: Issue [#35](https://github.com/Rasit-NP/curtain-call/issues/35), 결정 [0046](0046-venue-seat-layout.md), spec 5, 6.3

## 결정

- `Seat { id, row, number, gridRow, gridColumn, status }`. `gridRow`·`gridColumn`은 둘 다 1부터 세는 정수이고, 좌석이 배치 격자의 몇 번째 줄·칸에 있는지를 나타낸다.
- 통로와 빈자리는 좌석이 없는 칸이다.
- `row`·`number`는 표시 이름(`A-3`)으로 그대로 둔다.
- 예: `{ "id": 101, "row": "A", "number": 1, "gridRow": 1, "gridColumn": 2, "status": "AVAILABLE" }`

## 근거

- 프론트엔드가 이 값을 CSS Grid의 `grid-row`·`grid-column`에 그대로 넣어 그릴 수 있다(좌석 선택 화면, 3단계).
- 표시 이름과 위치를 나누면, 통로 오른쪽 좌석처럼 번호와 칸이 다른 경우도 표현된다.

## 고려했지만 고르지 않은 것

- `row`·`number`로 위치를 계산: 통로와 빈자리를 표현할 수 없다.

## 적용과 남은 점

- 백엔드 #35가 공연 상세(6.3) 응답에 넣었다. 좌석 선택 화면(3단계)이 쓴다.
