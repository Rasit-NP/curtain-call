# #28 프론트엔드 인증(R6): 로그인 화면, 인증 가드, 401 AUTH_REQUIRED 처리

- 기간: 2026-11-26 ~
- Issue: [#28](https://github.com/Rasit-NP/curtain-call/issues/28), PR: [#31](https://github.com/Rasit-NP/curtain-call/pull/31)
- 관련 결정: [0040](../decisions/0040-frontend-auth-required-central.md), [0041](../decisions/0041-frontend-login-return-path.md), [0042](../decisions/0042-frontend-guard-test-route.md)

## 배경

로드맵 [#13](https://github.com/Rasit-NP/curtain-call/issues/13)의 1단계(인증, R6) 프론트엔드다. 첫 설정(#6)에서 만든 API 클라이언트(`auth` 필수 옵션), 토큰 저장(`src/auth/`), 안내 문구, MSW 위에 로그인 화면, 인증 가드, `401 AUTH_REQUIRED` 공통 처리를 만든다. 뒤의 화면 Issue(좌석 선택, 결제, 내 예매)가 이것을 그대로 쓴다.

백엔드 인증(#27)과 고치는 폴더가 겹치지 않아, 메인 세션이 Issue마다 별도 세션·워크트리를 만들어 동시에 진행했다. API는 MSW로 흉내 내므로 백엔드를 기다리지 않는다.

## 구현 중 판단과 근거

모두 에이전트가 정했다. 사람이 정한 것은 브리프의 '토큰 삭제·로그인 이동은 AUTH_REQUIRED에만'([0008](../decisions/0008-auth-required-only.md), 기존 결정)이다.

- **`AUTH_REQUIRED`는 클라이언트에서 한 번에 처리**([0040](../decisions/0040-frontend-auth-required-central.md)): 처음에는 화면이 `catch`에서 부르는 훅을 생각했다. 그러면 화면 하나만 빠뜨려도 R6 처리가 빠진다. API 클라이언트가 토큰을 지우고 알리고, 최상위 레이아웃의 컴포넌트 하나가 로그인 화면으로 보내게 했다. 화면은 아무것도 하지 않는다.
- **돌아갈 경로는 location state, 앱 안의 경로만**([0041](../decisions/0041-frontend-login-return-path.md)): 쿼리(`?redirect=`)는 외부 링크로 넣을 수 있다. state도 `//host`는 거른다.
- **가드 이동에는 '로그인이 필요합니다.'를 붙이지 않음**([0041](../decisions/0041-frontend-login-return-path.md)): 명세는 그 문구를 `AUTH_REQUIRED`로 보낼 때만 정했다. 명세에 없는 동작은 더하지 않았다.
- **로그인 화면의 클라이언트 입력 검증 없음**: 빈 값은 서버가 `400 INVALID_INPUT`으로 막고(spec 6.1) 화면은 그 문구를 보여 준다. 명세에 없는 검증을 더하지 않았다.
- **가드는 실제 라우트 구성으로 검증**([0042](../decisions/0042-frontend-guard-test-route.md)): `createRoutes({ protectedChildren })`로 가드 아래에 테스트용 화면을 더한다. 테스트가 별도 라우트를 조립하면 실제 구성에서 레이아웃이 빠져도 통과하기 때문이다.
- **새 라이브러리 없음**: React Router는 첫 설정(#6)에 이미 있었다.

## 실패와 수정

- **테스트를 먼저 쓰고 실패 확인**: 로그인 API, 클라이언트의 `AUTH_REQUIRED` 처리, 로그인 화면, 가드, 공통 이동 테스트를 먼저 써서 모듈이 없어 실패하는 것을 확인한 뒤 구현했다.
- **10초 시간 초과 테스트가 멈춤**: 가짜 타이머를 켜자 `user.type`에서 5초 제한에 걸렸다. 로그를 찍어 첫 조작에서 멈추는 것을 확인했고, React Testing Library가 조작 뒤 `setTimeout(0)`을 기다리면서 Jest 가짜 타이머만 알아본다는 것이 원인이었다. `shouldAdvanceTime`으로 풀었다([0042](../decisions/0042-frontend-guard-test-route.md)).
- **ESLint `react-refresh/only-export-components`**: 테스트 도우미 파일에 컴포넌트와 함수를 함께 내보내 실패했다. 테스트용 화면을 별도 파일로 옮겼다.
- **R6 되돌림 확인**: 토큰 삭제, 로그인 이동, code 구분(401 상태로만 판단하게 바꿈), 가드, 로그인의 `auth: false`를 하나씩 되돌려 각각 대응 테스트가 실패하는 것을 확인하고 복구했다.
- **되돌림 확인에서 빈틈 발견**: '이미 `/login`이면 이동하지 않음'을 지워도 모든 테스트가 통과했다. 두 요청이 함께 401을 받는 테스트는 두 알림이 화면이 바뀌기 전에 와서 이 경우를 만들지 못했다. 두 번째 응답을 로그인 화면이 그려진 뒤에 보내도록 테스트를 바꾸자, 그 줄을 지우면 돌아갈 경로가 `/login`으로 덮여 실패했다.

## 리뷰와 반영

(작성 중)

## 결과

(작성 중)

## 배운 점

(작성 중)
