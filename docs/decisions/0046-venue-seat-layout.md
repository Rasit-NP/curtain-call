# 0046. 좌석 배치는 공연장마다 다르고, 통로와 빈자리까지 표현한다

- 상태: 채택
- 날짜: 2026-11-27
- 결정한 사람: 사람(메인 세션 대화, Issue #35 '이미 정한 정책')
- 관련: Issue [#35](https://github.com/Rasit-NP/curtain-call/issues/35), Issue [#36](https://github.com/Rasit-NP/curtain-call/issues/36), 결정 [0047](0047-seat-grid-position.md)

## 결정

좌석 배치는 공연장마다 다르다. 배치는 격자로 표현하고, 좌석이 없는 칸으로 통로와 빈자리를 나타낸다.

## 근거

- 소극장마다 좌석 수와 줄 모양이 다르다. 모든 공연을 같은 직사각형 배치로 그리면 좌석 선택 화면이 실제 공연장과 달라진다.

## 고려했지만 고르지 않은 것

- 모든 공연장에 같은 직사각형 배치: 단순하지만 통로·빈자리를 표현할 수 없다.

## 적용과 남은 점

- DB 구조는 [0048](0048-venue-performance-schema.md), 응답의 위치 필드는 [0047](0047-seat-grid-position.md)이다.
- 격자의 방향(1번째 줄이 무대 쪽)과 크기(좌석 위치의 최댓값)는 [0051](0051-seat-grid-orientation-and-size.md)이다.
