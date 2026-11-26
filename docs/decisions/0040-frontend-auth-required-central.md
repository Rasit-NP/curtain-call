# 0040. 프론트: 401 AUTH_REQUIRED는 API 클라이언트가 토큰을 지우고 알리며, 최상위 레이아웃 한 곳이 로그인 화면으로 보낸다

- 상태: 대체됨(→0043)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#28](https://github.com/Rasit-NP/curtain-call/issues/28)

## 결정

- `apiRequest`는 오류 응답의 `code`가 `AUTH_REQUIRED`이면 `handleAuthRequired()`(`src/auth/authRequired.ts`)를 부른 뒤 `ApiError`를 그대로 넘긴다. `handleAuthRequired()`는 저장된 토큰을 지우고 등록된 함수에 알린다.
- 라우터 최상위 레이아웃(`RootLayout`)에 둔 `AuthRequiredRedirect`가 알림을 받아, 지금 경로(쿼리·해시 포함)와 `notice: 'AUTH_REQUIRED'`를 location state에 담아 `/login`으로 `replace` 이동한다. 이미 `/login`이면 이동하지 않는다.
- 화면은 `AUTH_REQUIRED`를 따로 처리하지 않는다. 오류를 받아도 곧 로그인 화면으로 바뀐다.
- 판단은 `code`만 본다(결정 [0008](0008-auth-required-only.md)). `auth` 옵션과 상관없이 적용한다. 공개 API에는 토큰을 붙이지 않으므로(결정 [0020](0020-frontend-auth-option.md)) 공개 API가 `AUTH_REQUIRED`를 줄 일은 없다.

## 근거

- Issue #28 완료 조건: '이 처리를 화면마다 반복하지 않도록 한곳에 둔다'. 화면마다 훅을 불러야 하는 방식은 한 화면이라도 빠뜨리면 R6 처리가 빠진다. 클라이언트에서 처리하면 화면 코드가 잊을 수 없다.
- API 클라이언트는 React와 라우터를 모르게 두고(구독 함수만 부름), 화면 이동은 라우터 안의 컴포넌트가 맡는다.
- 이미 `/login`일 때 이동하지 않는 이유: 요청 여러 개가 함께 401을 받아 두 번째 알림이 로그인 화면이 그려진 뒤에 오면, 돌아갈 경로가 `/login`으로 덮인다. 테스트로 재현했다.
- `replace`: 로그인 뒤 원래 화면으로 다시 `replace`하므로 뒤로가기 기록에 로그인 화면이 남지 않는다.

## 고려했지만 고르지 않은 것

- 화면마다 부르는 훅(`useAuthRequiredHandler`): 화면이 `catch`에서 부르는 것을 잊으면 처리가 빠진다.
- 클라이언트가 직접 `window.location`을 바꿈: 라우터 기록과 state(돌아갈 경로)를 쓸 수 없고, 메모리 라우터 테스트에서 검증할 수 없다.
- `auth: true` 요청에만 적용: 명세는 `code`로만 구분한다(spec 3.3). 결과가 같으므로 조건을 늘리지 않았다.

## 적용과 남은 점

- 결제 화면(spec 7.3)은 화면을 떠날 때 `useBlocker`로 선점 해제 요청을 보낸다. `AUTH_REQUIRED`로 로그인 화면에 보낼 때도 이 이동이 일어나 해제 요청이 토큰 없이 나가고 다시 `AUTH_REQUIRED`를 받는다. `/login`에 도착한 뒤의 알림은 무시되고, 도착 전의 알림은 같은 state로 한 번 더 `replace` 이동할 뿐이지만, 결제 화면 Issue에서 동작을 확인해야 한다.
