# 0004. 결제 전 다른 화면으로 가면 선점 해제, 창을 닫으면 만료까지 유지

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 사람
- 관련: Issue [#3](https://github.com/Rasit-NP/curtain-call/issues/3), PR [#4](https://github.com/Rasit-NP/curtain-call/pull/4)

## 결정

`DELETE /api/holds/{holdId}`를 둔다. 결제가 승인되기 전에 앱 안의 다른 화면으로 가면(거절 뒤 포함) 해제한다. 창을 닫거나 새로고침하면 해제하지 않고 5분 뒤 만료된다.

## 근거

- 사람이 CGV에서 직접 확인한 동작(다른 페이지로 가면 풀리고 창을 닫으면 풀리지 않음).
- 다른 예매 시스템(Wix Ticket Reservations, seats.io)도 자동 만료와 즉시 해제를 함께 둔다.

## 고려했지만 고르지 않은 것

- 만료만 둠: 사용자가 포기한 좌석이 5분간 묶인다.
- 창 닫기에도 해제(beforeunload): 새로고침과 구분하기 어렵고 신뢰할 수 없다.

## 적용과 남은 점

화면을 떠나는지는 라우터 이동으로 판단한다. `useEffect` 정리 함수로 판단하면 StrictMode에서 들어오자마자 해제된다(spec 7.3).
