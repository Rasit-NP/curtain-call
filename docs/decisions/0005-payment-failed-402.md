# 0005. 결제 거절은 402 PAYMENT_FAILED, 같은 키 재요청에는 같은 402

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 사람
- 관련: Issue [#3](https://github.com/Rasit-NP/curtain-call/issues/3), PR [#4](https://github.com/Rasit-NP/curtain-call/pull/4)

## 결정

모의 결제가 거절되면 `402 PAYMENT_FAILED`(오류 코드 표에 추가)를 돌려준다. 선점은 원래 만료 시각까지 남는다. 같은 멱등 키로 다시 요청하면 같은 402를 돌려주고, 다시 결제하려면 새 키를 쓴다.

## 근거

- 다른 오류처럼 `ProblemDetail` + `code`로 처리할 수 있다.
- R5의 '같은 결과를 돌려준다'를 거절에도 그대로 적용한다.

## 고려했지만 고르지 않은 것

- 거절도 200에 상태 필드로 표시: 오류 처리 경로가 둘이 된다.

## 적용과 남은 점

새 오류 코드는 루트 `CLAUDE.md` 표에 먼저 추가한다.
