# 0031. 보안 설정: 공개 경로는 메서드까지 정하고, 오류 전달(ERROR 디스패치)은 인증 없이 지나게 한다

- 상태: 제안(PR 병합 전)
- 날짜: 2026-11-26
- 결정한 사람: 에이전트(Issue #27의 완료 조건을 구현하며)
- 관련: Issue [#27](https://github.com/Rasit-NP/curtain-call/issues/27), 결정 [0010](0010-security-in-auth-issue.md)

## 결정

`SecurityConfig`의 필터 체인 하나에 모두 둔다.

- 인증 없이 여는 것: `POST /api/auth/login`, `GET /api/performances/**`, `GET /actuator/health`. 메서드까지 정해, 같은 경로라도 다른 메서드(예: `GET /api/auth/login`)는 토큰 없이 401이다.
- `e2e` 프로필이 켜져 있을 때만 `POST /api/test-support/reset`을 연다. 프로필은 `Environment`로 확인한다(필터 체인을 둘로 나누지 않음).
- 서블릿 컨테이너의 오류 전달(`DispatcherType.ERROR`, `/error`)은 인증 없이 지나게 한다. `/error`를 직접 부르는 요청(REQUEST 디스패치)은 다른 비공개 경로처럼 인증이 필요하다.
- `STATELESS`, CSRF·HTTP Basic·폼 로그인·로그아웃은 끈다. 인증 진입점은 `oauth2ResourceServer`와 `exceptionHandling` 양쪽에 같은 것을 단다(잘못된 토큰과 토큰 없음이 같은 401 AUTH_REQUIRED).

## 근거

- spec 4의 공개 경로가 메서드별로 정해져 있다.
- ERROR 디스패치를 막으면, 공개 경로에서 서블릿 필터가 던진 예외(500)가 `/error`에서 익명 요청으로 다시 검사돼 401이 된다. 이 설정을 잠시 빼면 `ErrorPageTest`의 필터 예외 테스트가 실패하는 것을 확인했다.
- reset 경로 예외를 프로필 조건 하나로 두면 체인 순서(@Order)를 신경 쓸 필요가 없다.

## 고려했지만 고르지 않은 것

- `@Profile("e2e")` 필터 체인을 따로 두기: 체인 순서와 매처가 늘어난다.
- 로그인 경로는 모든 메서드를 공개: spec 4와 다르고, 공개 경로가 넓어진다.

## 적용과 남은 점

- 병합 뒤 이 설정을 바꿔야 하면 사람에게 넘긴다(루트 `CLAUDE.md`, 결정 0010).
- reset 컨트롤러 자체(`@Profile("e2e")`)와 '다른 프로필에서 등록되지 않음' 테스트는 E2E 첫 설정 Issue(6단계)에서 만든다.
