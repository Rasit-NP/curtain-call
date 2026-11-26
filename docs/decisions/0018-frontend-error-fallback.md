# 0018. 프론트: code가 없는 오류 응답은 INTERNAL_ERROR

- 상태: 제안(사람 확인 대기)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#6](https://github.com/Rasit-NP/curtain-call/issues/6) '참고', PR [#7](https://github.com/Rasit-NP/curtain-call/pull/7)

## 결정

오류 응답 본문에 오류 코드 표의 `code`가 없거나 JSON이 아니면 `INTERNAL_ERROR`로 본다. 2xx인데 본문이 JSON이 아닌 경우도 같다.

## 근거

- 명세가 정하지 않은 경우다. 클라이언트는 `code`만 보고 판단하므로(spec 3.1) 모르는 응답은 '예상하지 못한 오류'로 묶는 것이 가장 보수적이다.

## 고려했지만 고르지 않은 것

- 상태 코드로 추정: 표에 없는 상태가 섞인다.

## 적용과 남은 점

PR #7 리뷰 지적: 백엔드가 꺼졌을 때 Vite 프록시가 빈 502를 주면 '서버에 연결할 수 없습니다'가 아니라 이 문구가 나오고, 결제 화면은 새 멱등 키를 만든다.
