# 0019. 프론트: '응답 없음'은 프론트 전용 NO_RESPONSE, 본문 수신 중 끊김도 포함

- 상태: 제안(사람 확인 대기)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#6](https://github.com/Rasit-NP/curtain-call/issues/6), PR [#7](https://github.com/Rasit-NP/curtain-call/pull/7)

## 결정

네트워크 오류, 10초 시간 초과, 본문을 받는 중 끊김은 `ApiError { status: null, code: 'NO_RESPONSE' }`다. 서버 오류 코드 표에는 넣지 않는다. 시간 제한 타이머는 본문을 다 읽은 뒤 정리한다.

## 근거

- 결제 화면은 응답을 못 받았으면 같은 멱등 키를 다시 쓴다(spec 7.3). 본문이 끊기면 결과를 알 수 없으므로 같은 키로 다시 보내는 편이 안전하다(R5).
- 서버가 보내는 값이 아니라 표에 넣으면 백엔드와 어긋난다.

## 고려했지만 고르지 않은 것

- 본문 끊김을 INTERNAL_ERROR로: 결제가 됐는지 모르는데 새 키로 다시 결제하게 된다.

## 적용과 남은 점

PR #7 리뷰 지적: `JSON.stringify` 실패나 GET에 본문을 넣는 프로그래밍 오류도 지금은 `NO_RESPONSE`가 된다.
