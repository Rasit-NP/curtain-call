# #6 프론트엔드 첫 설정

- 기간: 2026-11-26 ~ (진행 중)
- Issue: [#6](https://github.com/Rasit-NP/curtain-call/issues/6), PR: [#7](https://github.com/Rasit-NP/curtain-call/pull/7) (리뷰 중, 병합 전)
- 관련 결정: [0014](../decisions/0014-error-code-table-test.md), [0018](../decisions/0018-frontend-error-fallback.md)~[0022](../decisions/0022-nvmrc-exact.md)

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

## 리뷰에서 나온 지적 (정리 전)

PR #7을 max 강도로 리뷰해 후보 15개를 모두 검증했다(확인 14, 가능성 있음 1). 주요 지적은 다음과 같다.

- **[중요] 핸들러가 없는 요청이 실제 네트워크로 나간다.** MSW 3.0.8에서 커스텀 `onUnhandledFrame`의 `defaults.error()`는 메시지만 찍는다. 요청은 `localhost:3000`으로 실제로 나가고, 토큰과 본문도 함께 나간다. 위 '옵션 이름 변경' 수정이 의도와 다르게 동작한 것이다.
- `afterEach`가 기록을 동기적으로 확인해, 진행 중이던 요청이 다음 테스트 탓이 되거나 보고되지 않는다.
- `JSON.stringify` 실패나 GET에 본문을 넣는 프로그래밍 오류가 `NO_RESPONSE`가 된다.
- 본문 수신 중 시간 초과, `afterEach` 가드 자체를 검사하는 테스트가 없다.
- 백엔드가 꺼졌을 때 Vite 프록시의 빈 502가 `INTERNAL_ERROR`가 된다.
- 규칙 관련: `fetch`를 직접 흉내 낸 테스트는 '`API는 MSW로 흉내 낸다`' 규칙의 예외다. R6 되돌림 확인이 3개 중 1개만 기록됐다. 대응표 이름이 테스트 이름과 다르다.

## 결과

`verify.sh` 전체 통과(테스트 45개). PR #8과 합친 트리에서도 전체 통과.
