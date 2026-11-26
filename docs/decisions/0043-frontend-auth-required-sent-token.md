# 0043. 프론트: 401 AUTH_REQUIRED 공통 처리는 그 요청에 보낸 토큰이 지금 토큰과 같을 때만 한다

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(PR #31 리뷰 반영, 사람이 수정 지시)
- 관련: Issue [#28](https://github.com/Rasit-NP/curtain-call/issues/28), PR [#31](https://github.com/Rasit-NP/curtain-call/pull/31)
- 대체한 결정: [0040](0040-frontend-auth-required-central.md)

## 결정

0040의 구조는 그대로 두고, 공통 처리를 하는 조건과 로그인 화면 판단을 바꾼다.

- `apiRequest`는 오류 응답의 `code`가 `AUTH_REQUIRED`이면 `handleAuthRequired(sentToken)`을 부른 뒤 `ApiError`를 그대로 넘긴다. `sentToken`은 그 요청에 붙여 보낸 토큰이고, 붙이지 않았으면(`auth: false` 또는 저장된 토큰 없음) `null`이다.
- `handleAuthRequired(sentToken)`은 지금 저장된 토큰이 `sentToken`과 같을 때만 토큰을 지우고 등록된 함수에 알린다. 다르면 요청을 보낸 뒤 다시 로그인한 것이므로 아무것도 하지 않는다. 둘 다 `null`이면(토큰 없이 보냄) 알린다.
- 라우터 최상위 레이아웃(`RootLayout`)의 `AuthRequiredRedirect`가 알림을 받아, 지금 경로(쿼리·해시 포함)와 `notice: 'AUTH_REQUIRED'`를 location state에 담아 `/login`으로 `replace` 이동한다.
- 이미 로그인 화면이면 이동하지 않는다. 로그인 화면인지는 `matchPath(LOGIN_PATH, pathname)`으로, 라우터와 같은 규칙(끝의 `/`, 대소문자 무시)으로 판단한다.
- 화면은 `AUTH_REQUIRED`를 따로 처리하지 않는다. 판단은 `code`만 본다(결정 [0008](0008-auth-required-only.md)).

## 근거

- PR #31 리뷰: 만료 토큰으로 보낸 요청 두 개 중 느린 쪽이, 사용자가 다시 로그인한 뒤에 `AUTH_REQUIRED`로 돌아오면 새 토큰을 지우고 로그인 화면으로 다시 보냈다. 사용자는 두 번 로그인해야 한다. 테스트로 재현했다.
- 그 응답은 지난 토큰에 대한 판정이므로 지금 토큰의 상태를 알려 주지 않는다. 보낸 토큰과 지금 토큰을 비교하면 판단할 수 있다.
- 로그인 화면 판단: 라우터는 `/login/`도 로그인 화면으로 그리는데 경로를 정확히 비교하면 놓친다. 그러면 늦게 온 알림이 돌아갈 경로를 `/login/`으로 덮어, 로그인해도 로그인 화면에 머문다. 테스트로 재현했다.
- 나머지 근거(화면마다 훅을 부르지 않는 이유, `replace`)는 0040과 같다.

## 고려했지만 고르지 않은 것

- 요청마다 번호(세대)를 매겨 로그인할 때 올리기: 같은 효과지만 로그인 상태를 따로 관리해야 한다. 토큰 비교로 충분하다.
- 늦게 온 응답도 알리되 토큰만 지우지 않기: 새 토큰으로 보고 있는 화면을 로그인 화면으로 보낸다.

## 적용과 남은 점

- 결제 화면(spec 7.3)이 떠날 때 보내는 선점 해제 요청은 `AUTH_REQUIRED`로 토큰을 지운 뒤에는 토큰 없이 나가고, 다시 `AUTH_REQUIRED`를 받으면 알림이 간다(둘 다 `null`). 그때 로그인 화면이면 무시되지만, 결제 화면 Issue에서 동작을 확인해야 한다.
