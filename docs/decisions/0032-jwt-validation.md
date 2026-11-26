# 0032. 토큰 검증: 기본 검증기 대신 주입한 시계·허용 오차 0·exp 필수·sub 숫자 필수

- 상태: 대체됨(→0038)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27)

## 결정

`NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`에 검증기를 직접 정한다.

- `JwtTimestampValidator(Duration.ZERO)`에 주입한 `Clock`을 넣고, `setAllowEmptyExpiryClaim(false)`로 exp가 없는 토큰을 거부한다.
- `sub`가 1 이상의 정수 문자열(19자리 이하)이 아니면 거부한다.
- `JwtValidators.createDefault()`는 쓰지 않는다.
- 서명 키는 `curtaincall.jwt.secret`. local·e2e·test는 application.yml의 개발 전용 키(이름에 `dev`), prod는 `${JWT_SECRET}`(기본값 없음). 비었거나 32바이트보다 짧으면 `JwtConfig`가 실패해 앱이 시작되지 않는다.

## 근거

- spec 3.2: 만료는 주입한 Clock으로, 허용 오차 0, exp와 같은 시각은 유효. 기본 검증기는 시스템 시계와 60초 허용 오차를 쓴다. 허용 오차를 60초로, 또는 시계를 시스템 시계로 되돌리면 R6 경계 테스트가 실패하는 것을 확인했다.
- 기본 `JwtTimestampValidator`는 exp가 없으면 통과시킨다. 그러면 만료 없는 토큰이 생길 수 있다.
- 컨트롤러는 sub를 그대로 사용자 id로 쓴다(0033). 숫자가 아닌 sub를 필터에서 401로 막아야 변환 오류가 500이 되지 않는다.
- HS256 키는 256비트 이상이어야 한다.

## 고려했지만 고르지 않은 것

- `JwtValidators.createDefaultWithValidators(...)`: 기본 검증기(인증서 바인딩 등)가 함께 붙는데 직접 서명한 토큰에는 필요 없고, 무엇이 붙는지 버전마다 바뀔 수 있다.

## 적용과 남은 점

- prod에서 개발 키를 그대로 넣는 경우는 막지 않는다(길이만 확인).
