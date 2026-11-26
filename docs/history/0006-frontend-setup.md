# #6 프론트엔드 첫 설정

- 기간: 2026-11-26
- Issue: [#6](https://github.com/Rasit-NP/curtain-call/issues/6), PR: [#7](https://github.com/Rasit-NP/curtain-call/pull/7) (병합)
- 관련 결정: [0014](../decisions/0014-error-code-table-test.md), [0018](../decisions/0018-frontend-error-fallback.md)~[0022](../decisions/0022-nvmrc-exact.md), [0025](../decisions/0025-msw-unhandled-error-strategy.md)

## 배경

화면 Issue가 같은 기반 위에서 테스트부터 쓸 수 있도록, 화면 없이 빌드·테스트 골격과 공통 API 클라이언트를 만들었다. 화면 Issue는 MSW로 API를 흉내 내므로 백엔드를 기다리지 않는다. 백엔드 첫 설정(#5)과 동시에 진행했다.

## Issue를 쓸 때 내린 판단

- **`code`가 없는 오류 응답은 `INTERNAL_ERROR`**([0018](../decisions/0018-frontend-error-fallback.md)): 명세에 없는 경우라 Issue에 사람 확인을 요청했다.
- **엔드포인트별 API 함수는 화면 Issue로**: 그 API를 처음 쓰는 화면에서 테스트와 함께 만드는 편이 범위가 명확하다.

## 구현 중 판단과 근거

- **'응답 없음'은 프론트 전용 `NO_RESPONSE`, 본문 수신 중 끊김도 포함**([0019](../decisions/0019-frontend-no-response.md)): 결제 화면이 같은 멱등 키를 다시 쓸지 판단하는 기준이 된다(R5).
- **`auth`는 기본값 없는 필수 옵션**([0020](../decisions/0020-frontend-auth-option.md)): 공개 API에 토큰을 붙이면 만료 토큰 때문에 401이 날 수 있다.
- **ESLint로 `fetch`·`localStorage` 사용 위치를 강제**([0021](../decisions/0021-frontend-lint-boundaries.md)).
- **`.nvmrc`는 `24.16.0`**([0022](../decisions/0022-nvmrc-exact.md)).
- **오류 코드 목록은 루트 `CLAUDE.md` 표와 비교하는 테스트**([0014](../decisions/0014-error-code-table-test.md)).

## 실패와 수정

- **MSW 3 옵션 이름 변경**: `onUnhandledRequest`가 무시돼 기록이 `[]`였다. 타입 정의에서 `onUnhandledFrame`을 확인하고 고쳤다. 그대로 두면 핸들러를 빠뜨린 요청이 '응답 없음'으로 바뀌어 테스트가 거짓으로 통과할 수 있었다.
- **본문 끊김 테스트가 구현을 되돌려도 통과**: 원인을 좁혀 MSW가 응답 본문 스트림 오류를 삼킨다는 것을 재현했다. 이 테스트만 `fetch`를 직접 흉내 내게 바꿨다.
- **타이머 정리 테스트가 MSW 내부 타이머까지 셈**: 타이머 개수 대신 '응답 뒤 10초가 지나도 signal이 끊기지 않는다'를 검사하도록 바꿨다.
- 결함 기록: [`docs/agent-log.md`](../agent-log.md)

## 리뷰와 반영

PR #7을 max 강도로 리뷰해 후보 15개를 모두 검증했다(확인 14, 가능성 있음 1). 세션이 실패하는 테스트로 먼저 재현한 뒤 고친 것은 다음과 같다.

- **[중요] 핸들러가 없는 요청이 실제 네트워크로 나감**: 위 'MSW 3 옵션 이름 변경' 수정이 의도와 다르게 동작했다. 커스텀 콜백의 `defaults.error()`는 메시지만 찍고 요청을 `localhost:3000`으로 보냈다. 콜백에서 예외를 던지는 첫 수정은 500 응답이 돼서, 내장 `'error'` 전략과 이벤트 기록으로 바꿨다([0025](../decisions/0025-msw-unhandled-error-strategy.md)).
- **테스트가 끝날 때 처리 중인 요청을 놓침**: `afterEach`가 처리 중인 요청을 기다린 뒤 검사하고 `afterAll`에서도 검사하게 했다. 이어서 `'error'` 전략에는 `request:end`가 오지 않아 매번 최대 대기 시간을 채우는 것을 테스트 시간으로 발견해 고쳤다.
- **호출 실수가 '응답 없음'이 됨**: 본문 직렬화와 `Request` 생성을 `try` 밖으로 옮겼다([0019](../decisions/0019-frontend-no-response.md)).
- **본문이 멈춘 경우의 시간 제한 테스트 추가**: MSW로 만들면 undici가 잡히지 않은 예외를 내 vitest가 실패해서, 이 테스트도 `fetch`를 직접 흉내 냈다.

처리하지 않고 남은 지적(후속 Issue 필요):

- 백엔드가 꺼졌을 때 Vite 프록시의 빈 502가 '응답 없음'이 아니라 `INTERNAL_ERROR`가 된다([0018](../decisions/0018-frontend-error-fallback.md)).
- 공개 엔드포인트 호출이 `auth: false`를 쓰는지 대응표에서 추적되지 않는다([0020](../decisions/0020-frontend-auth-option.md)).
- `errorMessages.test.ts`가 루트 `CLAUDE.md`를 실행 위치 기준으로 읽고, 주석의 이유가 틀렸다. 안내 문구 테스트는 명세가 아니라 복사본과 비교한다.
- `localStorage`가 차단된 브라우저에서는 원시 예외가 나온다(현실성 낮음).
- 규칙 관련: `fetch`를 직접 흉내 낸 테스트 2개는 'API는 MSW로 흉내 낸다' 규칙의 예외인데 사람에게 확인받지 않았다. R6 되돌림 확인이 3개 중 1개만 기록됐다. 대응표의 행 이름이 테스트 이름과 다르고, 일부 행은 명세가 정하지 않은 동작에 명세 절 번호를 붙였다.

## 결과

main(#8, #10)을 병합한 뒤 `verify.sh` 전체 통과(프론트엔드 테스트 51개). 2026-11-26 병합.

## 배운 점

- 라이브러리 메이저 버전이 바뀌면 옵션 이름만이 아니라 동작도 바뀐다. 타입 정의만 보고 고친 설정이 다른 결함(실제 네트워크 호출)을 만들었고, 리뷰가 라이브러리 소스와 실제 실행으로 찾아냈다.
- 테스트 설정 자체에도 테스트가 필요하다. 가드가 동작하지 않아도 모든 테스트가 통과하는 상태였다.
