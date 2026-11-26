# 0033. 컨트롤러는 현재 사용자 id를 @CurrentUserId로만 얻는다

- 상태: 대체됨(→0039)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트(Issue #27이 '한 가지로 정한다'를 요구)
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27)

## 결정

- `com.curtaincall.auth.api.CurrentUserId`: `@AuthenticationPrincipal(expression = "T(java.lang.Long).parseLong(subject)")`을 메타 애너테이션으로 단 파라미터 애너테이션. 컨트롤러는 `@CurrentUserId long userId`로 받는다.
- 테스트는 `com.curtaincall.support.AuthRequests.asUser(userId)`(`jwt()`로 sub에 id를 담음)로 인증된 요청을 만든다.
- 토큰 검증 자체를 보는 테스트는 `TestTokens`로 실제로 서명한 토큰을 쓴다(`jwt()`는 해석을 건너뛴다).

## 근거

- 각 컨트롤러가 `Jwt`를 받아 직접 `Long.parseLong(jwt.getSubject())`를 하면 방법이 여럿이 된다.
- 메타 애너테이션은 Spring Security가 그대로 지원해 인자 해석기를 직접 만들 필요가 없다.

## 고려했지만 고르지 않은 것

- `HandlerMethodArgumentResolver`를 직접 만들기: 같은 일을 하는 코드가 늘어난다.

## 적용과 남은 점

선점·결제·내 예매 Issue의 컨트롤러가 이것을 쓴다.
