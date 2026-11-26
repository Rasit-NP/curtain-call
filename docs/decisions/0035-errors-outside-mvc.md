# 0035. MVC 밖 오류: /error 컨트롤러, Tomcat 파라미터 해석 예외, Tomcat 오류 보고 밸브

- 상태: 대체됨(→0037)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27), PR #8에서 넘겨받은 일, 결정 [0012](0012-backend-error-mapping.md)

## 결정

경로에 따라 셋으로 나눠 모두 `ProblemDetail` + `code`로 응답한다. 상태 → code는 `ErrorCode.forFrameworkStatus`(404 → NOT_FOUND, 400·405·406·413·415 → INVALID_INPUT, 그 밖 → INTERNAL_ERROR) 하나로 정한다(0012의 규칙을 enum으로 옮김).

1. 서블릿 필터·컨테이너가 `/error`로 넘긴 오류: Boot의 `BasicErrorController` 대신 `ProblemErrorController`가 원래 상태로 code를 정해 `ApiException`을 던지고, `GlobalExceptionHandler`가 응답을 만든다. 원래 예외는 원인으로 붙여 500이면 로그에 남는다.
2. 쿼리·폼 값의 잘못된 퍼센트 인코딩(`?x=%zz`): Tomcat은 값을 처음 읽을 때 `InvalidParameterException`을 던지므로 MVC 인자 해석 중에 공통 처리기로 온다. 이 예외를 그 오류 코드(없으면 400)로 처리한다.
3. 요청이 앱에 닿기 전에 Tomcat이 거부한 경우(경로의 `%zz`, 인코딩된 슬래시 `%2F`): `/error`에도 가지 않고 Host의 `ErrorReportValve`가 HTML을 쓴다. `ProblemDetailErrorReportValve`로 바꾼다(Boot가 넣은 기본 밸브를 빼고, Host의 `errorReportValveClass`도 바꿈).

## 근거

- spec 3.1: 모든 오류 응답에 `code`가 있어야 하고, 클라이언트는 `code`만 본다.
- 실제 서버로 재현했다(MockMvc는 `/error` 전달을 흉내 내지 않는다). 2는 500 INTERNAL_ERROR, 3은 `text/html` 400이었다.
- 3에서 기본 밸브만 빼면 Host가 시작할 때 클래스 이름이 같은 밸브가 없다고 보고 기본 밸브를 다시 넣었다(835바이트 HTML). 그래서 `errorReportValveClass`를 바꿨다.

## 고려했지만 고르지 않은 것

- `ErrorAttributes`에 `code`만 더하기: 본문이 `ProblemDetail` 모양이 아니다.
- 모든 요청의 파라미터를 미리 읽는 필터: 요청마다 본문을 읽게 되고 직접 만드는 필터가 늘어난다.

## 적용과 남은 점

- 2와 3은 Tomcat에 묶인 코드다. 서버를 바꾸면 다시 확인해야 한다(`ErrorPageTest`가 잡는다).
- `ConstraintViolationException`을 서버 쪽 원인과 나눌지는 이 Issue에서 서비스·엔티티 검증을 쓰지 않아 정하지 않았다(PR #8).
