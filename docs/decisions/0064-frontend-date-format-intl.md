# 0064. 프론트: 일시는 Intl.DateTimeFormat(Asia/Seoul)의 부분을 받아 직접 이어 붙인다

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트
- 관련: Issue [#36](https://github.com/Rasit-NP/curtain-call/issues/36), 결정 [0060](0060-frontend-query-screens.md), 명세 7.5

## 결정

- `formatDateTime`은 `Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', ... }).formatToParts`로 연·월·일·요일·시·분을 받아 `2026. 12. 20.(일) 19:00`으로 이어 붙인다. 시·분은 두 자리로 맞춘다.
- `formatPrice`는 `toLocaleString('ko-KR')` 뒤에 '원'을 붙이고, `formatSeats`는 `{row}-{number}`를 쉼표로 잇는다. 세 함수는 `src/format.ts`에 둔다.

## 근거

- 사용자 브라우저의 시간대와 상관없이 한국 시간으로 보여야 한다. 시간대 이름을 쓰면 브라우저의 시간대 자료가 한국 시간 규칙을 맡는다.
- `format()` 결과 문자열은 런타임(ICU 버전)마다 띄어쓰기·구두점이 다를 수 있어, 부분만 받아 정해진 형식으로 잇는다.
- 날짜가 바뀌는 경계(UTC 15:00 = 한국 자정)와 해가 바뀌는 경계를 테스트한다.

## 고려했지만 고르지 않은 것

- UTC에 9시간을 더해 `getUTC*`로 계산: 지금은 같은 결과지만 시간대 규칙을 코드에 박아 둔다.
- 날짜 라이브러리(date-fns 등): 함수 세 개에 의존성을 더할 이유가 없다.

## 적용과 남은 점

- 좌석 선택·결제 화면(3·4단계)도 같은 함수를 쓴다.
