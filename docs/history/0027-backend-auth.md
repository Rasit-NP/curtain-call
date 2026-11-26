# #27 백엔드 인증(R6)

- 기간: 2026-11-26 ~ 2026-11-27
- Issue: [#27](https://github.com/Rasit-NP/curtain-call/issues/27), PR: [#32](https://github.com/Rasit-NP/curtain-call/pull/32)
- 관련 결정: [0031](../decisions/0031-security-paths-and-error-dispatch.md)~[0039](../decisions/0039-current-user-id-v2.md)(0032·0033·0035는 리뷰 뒤 0038·0039·0037로 대체)

## 배경

로드맵 [#13](https://github.com/Rasit-NP/curtain-call/issues/13)의 1단계 백엔드다. 백엔드 첫 설정(#5)은 Spring Security를 넣지 않았고([0010](../decisions/0010-security-in-auth-issue.md)), 보안 설정(공개 경로, E2E reset 경로의 예외)을 이 Issue에서 한 번에 넣는다. PR #8 리뷰가 남긴 일(Security 예외가 catch-all에 걸려 500이 되는 문제, MVC 밖 `/error`의 오류 형식)도 이 Issue 범위다.

프론트엔드 인증(#28)과 고치는 폴더가 겹치지 않아, 메인 세션이 Issue마다 별도 세션·워크트리를 열어 동시에 진행했다. 결정 파일 번호가 겹치지 않게 이 Issue는 0031~0039를 쓰기로 메인 세션이 정했다.

## 진행 순서

1. 테스트를 먼저 썼다: 로그인 API, R6 보안, 현재 사용자 id, e2e 프로필과 시드, MVC 밖 오류(실제 서버), 프로필·서명 키 단위 테스트.
2. 사용자 테이블·엔티티, JWT 키·인코더·디코더만 넣고(Boot 기본 보안 체인 그대로) 실행해 통합 테스트 72개 중 49개가 실패하는 것을 확인했다.
3. 보안 체인, 인증 진입점, 로그인 API, `/error` 컨트롤러, 시드를 넣자 `ErrorPageTest` 3개만 남았다(아래 '실패와 수정').
4. R6 되돌림 확인: 인증 요구(`authenticated()` → `permitAll()`), 서명 검증(항상 통과하는 검증기), 만료 판정(허용 오차 60초, 시스템 시계) 각각 해당 테스트가 실패하고, 복구하면 통과했다.

## 판단과 근거

모두 에이전트가 정했다. 사람이 정한 것은 Issue 본문(공개 경로, 서명 키 정책, 시드 개수 등)과 결정 파일 번호 범위다.

- **보안 설정**([0031](../decisions/0031-security-paths-and-error-dispatch.md)): 공개 경로를 메서드까지 정하고, reset 경로 예외는 `e2e` 프로필 조건으로 같은 체인에 둔다. 오류 전달(ERROR 디스패치)은 인증 없이 지나게 했다. 막으면 공개 경로의 필터 예외가 `/error`에서 401로 바뀌는 것을 되돌림으로 확인했다.
- **토큰 검증**([0032](../decisions/0032-jwt-validation.md)): `JwtValidators.createDefault()`는 시스템 시계와 60초 허용 오차를 쓰므로 검증기를 직접 정했다. exp 없는 토큰과 숫자가 아닌 sub도 401로 막는다.
- **현재 사용자 id**([0033](../decisions/0033-current-user-id.md)): `@CurrentUserId`(`@AuthenticationPrincipal` 메타 애너테이션). 테스트 도우미는 `AuthRequests.asUser(id)`.
- **로그인과 인증 오류**([0034](../decisions/0034-login-and-auth-errors.md)): 타이밍 공격 방지와 '없는 아이디 = 틀린 비밀번호'를 Spring Security의 `DaoAuthenticationProvider`에 맡기고, `BadCredentialsException`만 401 INVALID_CREDENTIALS로 바꿨다(PR #8의 "Security 예외가 500" 문제). 인증 진입점은 본문을 직접 쓰지 않고 공통 처리기에 넘긴다.
- **MVC 밖 오류**([0035](../decisions/0035-errors-outside-mvc.md)): `/error` 컨트롤러, Tomcat 파라미터 해석 예외 처리, Tomcat 오류 보고 밸브 교체.
- **계정 시드**([0036](../decisions/0036-seed-accounts.md)): `demo`, `e2e-user1`, `e2e-user2`. 시드가 test 프로필 Flyway 위치에 없어, e2e 프로필 테스트가 시드 파일을 직접 실행해 로그인까지 확인한다.

## 실패와 수정

- **공개 경로의 `?x=%zz`가 500**: Tomcat은 쿼리 값을 처음 읽을 때 해석하므로, MVC가 `@RequestParam`을 만들다 `InvalidParameterException`(IllegalStateException)이 나고 catch-all이 500 INTERNAL_ERROR로 바꿨다. 실제 서버에 소켓으로 요청을 보내는 테스트로 재현했다(`java.net.URI`는 `%zz`를 거부, MockMvc는 Tomcat 해석을 거치지 않음). 이 예외를 Tomcat이 정한 상태로 처리해 400 INVALID_INPUT이 됐다.
- **경로의 `%zz`·`%2F`가 HTML 400**: Tomcat이 앱에 넘기기 전에 거부해 `/error`에도 가지 않고 Host의 `ErrorReportValve`가 HTML을 썼다. JSON을 쓰는 밸브로 바꿨는데 여전히 HTML(이번엔 835바이트)이 나왔다. Boot가 넣은 기본 밸브는 뺐지만, Host가 시작할 때 `errorReportValveClass`와 클래스 이름이 같은 밸브가 없다고 보고 기본 밸브를 다시 넣었기 때문이다(`StandardHost`를 `javap`로 확인). Host의 `errorReportValveClass`를 바꿔 해결했다.
- 결함 기록: [`docs/agent-log.md`](../agent-log.md)

## 리뷰

`/code-review high`(인증 PR, [0023](../decisions/0023-review-depth-by-risk.md)). 리뷰 중 '`Accept: text/html`이면 진입점이 401 대신 빈 200을 낼 수 있다'는 가설은 직접 요청해 보니 Spring이 `application/problem+json`으로 돌려줘 기각했다. 중요한 결함은 없었고 사소한 지적 6건이 나왔다. 사람이 모두 고치라고 해(메인 세션을 거쳐 전달) 같은 PR에서 처리했다. 지적마다 실패하는 테스트로 먼저 재현했다.

| 지적 | 처리 |
|---|---|
| 1. Long 범위를 넘는 sub가 검증을 지나 `@CurrentUserId`에서 500 | 고침: sub는 1 이상의 long만 허용([0038](../decisions/0038-jwt-validation-v2.md)) |
| 2. '보안 방화벽' 테스트가 실제로는 Tomcat 거부를 검사 | 고침: 이름을 Tomcat 거부로 바꾸고, Spring Security 방화벽 거부(`;`, `%25`, `//`) 테스트 추가. 동작은 이미 맞았고(400), ERROR 디스패치 허용을 빼면 실패하는 것을 확인 |
| 3. 표에 없는 컨테이너 상태가 500 | 고침: 실험해 보니 505(HTTP 버전), 501(`Transfer-Encoding`), 417(`Expect`)이 500이었다. Tomcat 거부 단계에서는 4xx·501·505를 400 INVALID_INPUT으로, 503 등 서버 사정만 500으로([0037](../decisions/0037-errors-outside-mvc-v2.md)). 표 안의 코드로 처리해 사람에게 넘기지 않았다 |
| 4. 공개 경로의 `@CurrentUserId`가 익명 요청에 500 | 고침: 토큰이 아니면 401 AUTH_REQUIRED([0039](../decisions/0039-current-user-id-v2.md)) |
| 5. R6 표 행의 테스트 이름에 `R6:` 없음 | 고침: 표시 이름과 대응표 행에 `R6:` 추가 |
| 6. 밸브가 공통 처리기 밖에서 오류 본문을 만듦 | 근거를 두고 유지: Tomcat이 거부한 요청은 서블릿에 닿지 않아 공통 처리기를 부를 수 없다. 대신 확인 중 찾은 필드 차이(`type`)를 고치고 필드 비교 테스트와 500 경고 로그를 더했다([0037](../decisions/0037-errors-outside-mvc-v2.md)). `backend/CLAUDE.md`에 예외로 적을지는 사람이 정한다 |

리뷰 뒤 사람이 6번의 밸브를 `backend/CLAUDE.md`에 예외로 적기로 정했다(2026-11-27). 규칙에는 "예외 처리는 `@RestControllerAdvice` 한곳에 모은다"만 있어, 그대로 두면 이후 세션과 리뷰가 밸브를 규칙 위반으로 보고 지우거나 다시 지적할 수 있기 때문이다. 'API' 절에 "Tomcat이 앱에 닿기 전에 거부한 오류만 밸브가 쓰고, 이런 곳은 이 밸브 하나뿐"이라고 한 줄을 더했다.

- 처음에는 메인 세션이 이 Issue 세션에 사람의 답을 전달했다. Issue 세션은 다른 세션이 전한 지시만으로는 규칙 문서(`CLAUDE.md`)를 고치지 않는다며 두 번 거절했다(원문을 그대로 전해 와도 같음).
- 사람이 메인 세션에 직접 고치라고 지시해, 메인 세션이 이 PR의 워크트리에서 `backend/CLAUDE.md`, [0037](../decisions/0037-errors-outside-mvc-v2.md), 이 기록, PR 본문을 고쳤다.

## 결과

PR [#32](https://github.com/Rasit-NP/curtain-call/pull/32). 처음 `./scripts/verify.sh` 전체 통과(백엔드 단위 30개, 통합 72개). 리뷰 지적을 고친 뒤 다시 전체 통과(백엔드 단위 50개, 통합 84개, 프론트엔드, 하네스; E2E는 아직 없어 SKIP). `backend/CLAUDE.md`에 밸브 예외를 적은 뒤에는 문서만 바뀌어 `verify.sh harness`와 `quick`을 다시 통과시켰다. 사람이 PR #32를 main에 병합했다(병합 커밋 `5b69ad1`). 프론트엔드 인증 PR #31보다 먼저 병합돼, #31이 그 위에 리베이스했다.

## 배운 점

- MockMvc는 서블릿 컨테이너의 해석과 `/error` 전달을 흉내 내지 않는다. "MVC 밖 오류"는 실제 서버로만 재현됐다.
- 컨테이너가 거부하는 요청은 단계마다 처리하는 곳이 다르다(MVC 인자 해석 중, `/error` 전달, 앱에 닿기 전). 한 가지 수정으로 모두 막히지 않았고, 경우마다 테스트를 두어 각각 확인해야 했다.
- 같은 형식을 두 곳에서 만들면 어긋난다. 밸브와 공통 처리기의 본문은 처음부터 `type` 하나가 달랐고, 리뷰 지적을 확인하며 필드 비교 테스트를 쓰고서야 드러났다.
