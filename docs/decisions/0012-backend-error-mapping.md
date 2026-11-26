# 0012. 백엔드 오류 응답: 입력 오류 상태만 INVALID_INPUT, 기능 오류는 ApiException, 나머지는 500

- 상태: 에이전트 결정
- 날짜: 2026-11-26
- 결정한 사람: 에이전트(독립 리뷰 반영)
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

`GlobalExceptionHandler` 한곳에서 `ProblemDetail` + `code`를 만든다. Spring MVC 입력 오류(400·405·406·413·415)와 `@Validated`의 `ConstraintViolationException`은 `400 INVALID_INPUT`, 없는 경로는 `404 NOT_FOUND`, 기능 코드는 `ApiException(ErrorCode)`로 그 코드의 상태를 내고, 그 밖은 `500 INTERNAL_ERROR`(원인은 로그에만).

## 근거

- 처음에는 404가 아닌 4xx를 모두 400으로 바꿨는데, 독립 리뷰가 `ResponseStatusException(409)` 같은 오류가 조용히 400이 되고 표의 코드를 낼 방법이 없다는 것을 찾았다.
- 405·415를 400으로 바꾸는 것은 표에 있는 코드만 쓰기 위해서다(spec 3.1).
- 부모 `handleExceptionInternal`을 거쳐 커밋된 응답 보호와 500 관측 처리를 그대로 쓴다.

## 고려했지만 고르지 않은 것

- 모든 4xx → 400: 위 이유로 기각.

## 적용과 남은 점

PR #8 리뷰에서 남은 지적: 서블릿 필터·컨테이너 단계 오류는 `/error`로 가서 `code`가 없다. `ConstraintViolationException`은 서버 쪽 검증 실패도 400으로 만든다. 리뷰 검증을 중단한 채 병합돼 아직 처리하지 않았다(후속 Issue 필요).
