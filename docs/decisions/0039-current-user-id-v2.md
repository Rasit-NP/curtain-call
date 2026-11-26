# 0039. 현재 사용자 id(개정): 토큰이 없으면 @CurrentUserId가 401 AUTH_REQUIRED를 낸다

- 상태: 제안(PR 병합 전)
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(PR #32 리뷰 지적을 고치라는 사람의 지시에 따라)
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27), PR [#32](https://github.com/Rasit-NP/curtain-call/pull/32), 대체하는 결정 [0033](0033-current-user-id.md)

## 결정

0033을 대체한다. 바뀐 것은 인증 주체가 토큰이 아닐 때의 동작이다.

- 컨트롤러는 현재 사용자 id를 `@CurrentUserId long userId`로만 얻는다. 이 애너테이션은 `@AuthenticationPrincipal(expression = "T(com.curtaincall.auth.api.CurrentUserIds).from(#this)")`의 메타 애너테이션이다.
- `CurrentUserIds.from`은 인증 주체가 액세스 토큰(`Jwt`)이면 sub를 long으로 바꾸고, 아니면(공개 경로의 익명 요청) `ApiException(AUTH_REQUIRED)`를 던져 401 AUTH_REQUIRED가 된다.
- 테스트 도우미는 0033과 같다(`AuthRequests.asUser(id)`, 토큰 검증 자체는 `TestTokens`).

## 근거

- 리뷰 지적: 공개 경로(`GET /api/performances/**`)에서 `@CurrentUserId`를 쓰면 익명 요청의 주체가 문자열 `anonymousUser`라 표현식이 실패해 500이 됐다. 시험용 컨트롤러로 재현했다.
- 사용자 id가 필요한 처리는 로그인이 필요하므로 R6의 401 AUTH_REQUIRED가 맞다. 표 안의 코드다.
- SpEL은 호출한 메서드의 런타임 예외를 감싸지 않고 그대로 던져, 공통 처리기가 `ApiException`으로 받는다(테스트로 확인).

## 고려했지만 고르지 않은 것

- `HandlerMethodArgumentResolver`를 직접 만들기: 0033과 같은 이유로 고르지 않았다.
- null을 돌려주기: `long` 인자는 null을 받을 수 없어 다시 500이 된다.

## 적용과 남은 점

선점·결제·내 예매 Issue의 컨트롤러가 이것을 쓴다. `@WebMvcTest`처럼 보안 필터 없이 돌려 인증 정보가 아예 없으면 Spring이 표현식을 부르지 않아 이 처리를 거치지 않는다.
