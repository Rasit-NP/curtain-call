# 0072. 요청 본문의 숫자 필드는 JSON 숫자 그대로만 받는다(정수 자리의 소수와 숫자 문자열은 400 INVALID_INPUT)

- 상태: 채택
- 날짜: 2026-11-27
- 결정한 사람: 사람(2026-11-27, Issue #41 세션. 에이전트가 낸 선택지 중 추천인 '소수·문자열 모두 400'을 골랐다)
- 관련: Issue [#41](https://github.com/Rasit-NP/curtain-call/issues/41), 결정 [0012](0012-backend-error-mapping.md), [0050](0050-performance-detail-id-errors.md), spec 3, 3.1, 6.4

## 결정

- 요청 본문(JSON)의 정수 필드(선점의 `performanceId`·`seatIds`, 앞으로 결제의 `holdId`)에 소수(`1.5`, `1.0`, `1e3`)나 숫자 문자열(`"1"`)이 오면
  400 INVALID_INPUT이다.
- 모든 프로필의 Jackson 설정 두 개를 끈다(`application.yml`): `spring.jackson.deserialization.accept-float-as-int: false`,
  `spring.jackson.mapper.allow-coercion-of-scalars: false`. 본문을 읽지 못한 오류는 공통 처리기가 400 INVALID_INPUT으로 바꾼다(결정 0012).

## 근거

- Jackson 3 기본값은 정수 필드에 온 `1.5`를 잘라 `1`로, `1e3`을 `1000`으로, `"1"`을 `1`로 받는다. 잘린 값으로 요청하지 않은 공연·좌석을 선점할 수 있다.
- spec 3은 ID를 정수로 정했고, 결정 0050은 경로의 `1.5`·`1e3`을 400으로 정했다. 본문도 같게 둔다.
- 전역 설정이라 이후 결제 요청에도 같은 규칙이 적용된다. 문자열 필드(로그인의 아이디·비밀번호)는 바뀌지 않는다.

## 고려했지만 고르지 않은 것

- 소수만 400, 숫자 문자열 `"1"`은 받음: 값이 바뀌는 경우만 막는다. 사람이 고르지 않았다.
- 그대로 둠: spec 3의 '정수'와 결정 0050과 어긋난다.
- 필드마다 역직렬화를 따로 정함: 같은 규칙을 필드마다 되풀이한다.

## 적용과 남은 점

- spec 3.1의 400 목록에 더했다.
- 테스트: `HoldApiTest`의 400 매개변수 테스트 중 소수, 지수 표기, 숫자 문자열, 소수점이 있는 수. 설정을 빼면 이 5개가 201로 실패하는 것을 확인했다.
