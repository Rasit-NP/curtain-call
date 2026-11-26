# 0034. 로그인은 빈이 아닌 DaoAuthenticationProvider로 확인하고, 인증 오류 응답도 공통 처리기가 만든다

- 상태: 제안(PR 병합 전)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27), PR #8에서 넘겨받은 일(Security 예외가 500이 됨)

## 결정

- `LoginService`가 `DaoAuthenticationProvider`를 직접 만들어 쓴다(빈으로 등록하지 않음). `BadCredentialsException`(없는 아이디도 이것)만 잡아 `ApiException(INVALID_CREDENTIALS)`로 바꾼다. 그 밖의 인증 예외(예: DB 오류를 감싼 `InternalAuthenticationServiceException`)는 그대로 500이다.
- 인증 진입점(`AuthRequiredEntryPoint`)은 본문을 직접 쓰지 않고 `HandlerExceptionResolver`에 `ApiException(AUTH_REQUIRED)`를 넘긴다. `WWW-Authenticate: Bearer`(토큰이 잘못됐으면 `error="invalid_token"`)를 붙인다.

## 근거

- 없는 아이디에도 비밀번호 비교 시간을 쓰는 것(타이밍 공격 방지)과 없는 아이디·틀린 비밀번호를 같은 예외로 만드는 것을 Spring Security가 이미 한다.
- provider를 빈으로 두면 Spring Security가 전역 인증 수단으로 가져가 JWT 외의 인증 경로가 생길 수 있다.
- 오류 형식을 `GlobalExceptionHandler` 한곳에서 만든다(`backend/CLAUDE.md`).
- BCrypt는 72바이트까지만 쓰지만 `matches`는 더 긴 비밀번호에 false를 돌려줘 401이 된다(테스트로 확인).

## 고려했지만 고르지 않은 것

- `GlobalExceptionHandler`에 `AuthenticationException` → 401 처리 추가: 서버 오류인 인증 예외도 401이 된다. 지금은 로그인 밖에서 MVC로 인증 예외가 올 길이 없다.
- 진입점에서 JSON을 직접 쓰기: 오류 형식을 만드는 곳이 둘이 된다.

## 적용과 남은 점

메서드 보안(`@PreAuthorize`)을 쓰게 되면 MVC 안의 `AccessDeniedException`·`AuthenticationException` 처리를 다시 정해야 한다(403은 오류 코드 표에 없다).
