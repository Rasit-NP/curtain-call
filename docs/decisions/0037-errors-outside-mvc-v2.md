# 0037. MVC 밖 오류(개정): Tomcat이 거부한 요청은 별도 규칙으로 code를 정하고, 밸브는 공통 처리기와 같은 필드로 쓴다

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(PR #32 리뷰 지적을 고치라는 사람의 지시에 따라). `backend/CLAUDE.md`에 이 밸브를 예외로 적는 것은 사람이 정했다(2026-11-27, 메인 세션 대화).
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27), PR [#32](https://github.com/Rasit-NP/curtain-call/pull/32), 대체하는 결정 [0035](0035-errors-outside-mvc.md), 결정 [0012](0012-backend-error-mapping.md)

## 결정

0035를 대체한다. 바뀐 것은 3번(Tomcat이 거부한 요청)의 code 규칙과 밸브의 본문·로그다. 1·2번은 그대로다.

1. 서블릿 필터·컨테이너가 `/error`로 넘긴 오류(Spring Security 방화벽의 거부 포함): `ProblemErrorController`가 원래 상태로 code를 정해 `ApiException`을 던지고 `GlobalExceptionHandler`가 응답을 만든다. 규칙은 앱 안과 같은 `ErrorCode.forFrameworkStatus`(404 → NOT_FOUND, 400·405·406·413·415 → INVALID_INPUT, 그 밖 → INTERNAL_ERROR).
2. 쿼리·폼 값의 잘못된 퍼센트 인코딩: Tomcat `InvalidParameterException`을 그 오류 코드(없으면 400)로 처리한다.
3. 요청이 앱에 닿기 전에 Tomcat이 거부한 경우: `ProblemDetailErrorReportValve`가 쓴다.
   - code는 `ErrorCode.forContainerStatus`: 404 → NOT_FOUND, 4xx 전부와 501·505 → INVALID_INPUT, 그 밖의 5xx(503 등) → INTERNAL_ERROR. 상태도 code의 상태로 맞춘다.
   - 본문 필드는 공통 처리기와 같다(`title`, `status`, `detail`, `code`. `type`이 `about:blank`이면 Spring처럼 생략). `ErrorPageTest`가 두 본문의 필드가 같은지 확인한다.
   - INTERNAL_ERROR이면 원래 상태를 경고 로그로 남긴다.
   - 오류 본문을 공통 처리기 밖에서 만드는 곳은 이 밸브 하나다. 요청이 서블릿에 닿지 않아 Spring의 처리기를 부를 수 없기 때문이다.

## 근거

- 리뷰 지적: 지원하지 않는 HTTP 버전(505), 알 수 없는 `Transfer-Encoding`(501), 잘못된 `Expect`(417)가 500 INTERNAL_ERROR로 나갔다(실제 서버로 재현). 모두 클라이언트가 보낸 요청을 해석할 수 없다는 뜻이고, spec 3.1은 그런 경우(잘못된 본문, 지원하지 않는 메서드·`Content-Type`)를 400 INVALID_INPUT으로 정한다. 500은 '그 밖의 예상하지 못한 오류'다. 표 안의 코드로 처리할 수 있어 새 코드는 만들지 않았다.
- 앱 안의 규칙은 바꾸지 않았다. 앱 코드가 정한 `ResponseStatusException(409)`·`501` 같은 상태를 조용히 400으로 바꾸지 않기 위해서다(0012). 밸브가 받는 요청은 앱 코드를 거치지 않으므로 이 걱정이 없다.
- 리뷰 지적: 밸브만 `type`을 넣어 공통 처리기와 필드가 달랐다. 필드 비교 테스트로 재현하고 맞췄다.
- 밸브를 없애고 공통 처리기로 보내는 방법은 없다. Tomcat이 매핑 단계에서 거부한 요청은 서블릿·필터·`/error`를 거치지 않는다(0035에서 확인).

## 고려했지만 고르지 않은 것

- 원래 상태(501·505·503)를 그대로 두고 code만 붙이기: 상태와 code의 짝이 오류 코드 표와 달라진다.
- `forFrameworkStatus`에 501·505를 더하기: 앱 코드의 501도 400이 된다.
- 503 등을 새 코드로 나누기: 표에 없는 코드가 필요해 사람의 결정이 필요하다. 지금은 서버 사정이므로 INTERNAL_ERROR로 둔다.

## 적용과 남은 점

- `backend/CLAUDE.md` 'API'의 "예외 처리는 `@RestControllerAdvice` 한곳에 모은다" 아래에 이 밸브가 유일한 예외라고 적었다. 사람이 2026-11-27에 PR #32에 적기로 정했다. 규칙 문서와 코드를 함께 병합해, 이후 세션과 리뷰가 밸브를 규칙 위반으로 보고 지우거나 다시 지적하지 않게 하고, 공통 처리기 밖의 오류 처리가 늘지 않게 하려는 것이다.
- 2와 3은 Tomcat에 묶인 코드다. 서버를 바꾸면 `ErrorPageTest`로 다시 확인해야 한다.
