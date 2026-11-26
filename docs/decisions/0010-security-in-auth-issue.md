# 0010. 첫 설정에서 Spring Security를 빼고 인증 Issue에서 한 번에 넣는다

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 에이전트(Issue 작성 때 제안), 사람이 Issue로 승인
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

백엔드 첫 설정에는 Spring Security, OAuth2 Resource Server, 로그인, JWT, `401 AUTH_REQUIRED`를 넣지 않는다. 첫 설정의 테스트는 보안이 들어온 뒤에도 기대값을 바꾸지 않고 통과해야 한다.

## 근거

- 첫 설정에서 보안 설정을 먼저 만들면, 인증 Issue가 main에 병합된 보안 설정을 바꾸게 된다. 이는 루트 `CLAUDE.md` '사람에게 넘기는 조건'(인증·보안 변경)에 걸린다.
- 보안이 들어오면 공개 경로가 아닌 곳은 토큰 검사(401)가 먼저라, 토큰 없이 비공개 경로로 오류를 검증한 테스트는 기대값이 바뀐다.

## 고려했지만 고르지 않은 것

- 최소 보안 설정을 첫 설정에 포함: 위 조건에 걸린다.

## 적용과 남은 점

그래서 `ErrorResponseTest`는 서블릿 필터를 끈 MockMvc(`addFilters = false`)로 MVC 안의 오류 변환만 검증한다.
