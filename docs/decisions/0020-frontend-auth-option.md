# 0020. 프론트 API 클라이언트의 auth는 기본값 없는 필수 옵션

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#6](https://github.com/Rasit-NP/curtain-call/issues/6), PR [#7](https://github.com/Rasit-NP/curtain-call/pull/7)

## 결정

`apiRequest(path, { auth })`에서 `auth`는 필수다. `true`일 때만 저장된 토큰을 Bearer로 붙인다. 인증 요청인데 토큰이 없으면 막지 않고 헤더 없이 보낸다.

## 근거

- 공개 API에 토큰을 붙이면 만료 토큰 때문에 401이 날 수 있다(spec 3장). 기본값이 있으면 호출하는 쪽이 실수로 붙이거나 빼먹는다.
- 토큰이 없을 때 막지 않으면 서버의 `401 AUTH_REQUIRED`로 공통 처리(spec 3.3)를 탄다.

## 고려했지만 고르지 않은 것

- 기본값 `true`나 `false`: 위 이유로 기각.

## 적용과 남은 점

남은 점(PR #7 리뷰 지적, 아직 처리 안 함): 공개 엔드포인트 호출이 `auth: false`를 쓰는지는 대응표에서 더 이상 추적되지 않는다.
