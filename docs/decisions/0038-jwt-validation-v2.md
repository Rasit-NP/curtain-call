# 0038. 토큰 검증(개정): sub는 1 이상의 long이어야 한다

- 상태: 제안(PR 병합 전)
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(PR #32 리뷰 지적을 고치라는 사람의 지시에 따라)
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27), PR [#32](https://github.com/Rasit-NP/curtain-call/pull/32), 대체하는 결정 [0032](0032-jwt-validation.md)

## 결정

0032를 대체한다. 바뀐 것은 sub 검사다.

- `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`에 검증기를 직접 정하고 `JwtValidators.createDefault()`는 쓰지 않는다.
- `JwtTimestampValidator(Duration.ZERO)`에 주입한 `Clock`을 넣고, exp가 없는 토큰을 거부한다.
- `sub`는 앞자리 0이나 부호가 없는 숫자이고 `Long.parseLong`으로 바꿀 수 있는 1 이상의 값이어야 한다. 아니면 401 AUTH_REQUIRED.
- 서명 키 정책은 0032와 같다.

## 근거

- 리뷰 지적: 0032의 "19자리 이하" 검사는 `9223372036854775808`~`9999999999999999999`를 통과시켜, 컨트롤러의 `@CurrentUserId`에서 변환에 실패해 500이 됐다. 테스트로 재현했다(검증을 통과해 404). 경계값 `Long.MAX_VALUE`는 통과, 1 큰 값은 401인 테스트를 두었다.
- 나머지 근거는 0032와 같다(시스템 시계·60초 허용 오차를 쓰는 기본 검증기, exp 없는 토큰 거부).

## 고려했지만 고르지 않은 것

- 정규식으로만 범위를 표현: 읽기 어렵고 틀리기 쉽다.

## 적용과 남은 점

prod에서 개발 키를 그대로 넣는 경우는 막지 않는다(길이만 확인).
