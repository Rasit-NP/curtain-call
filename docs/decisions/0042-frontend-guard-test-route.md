# 0042. 프론트 테스트: 가드는 실제 라우트 구성에 테스트용 보호 화면을 더해 검증하고, user-event와 가짜 타이머는 shouldAdvanceTime으로 함께 쓴다

- 상태: 에이전트 결정
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#28](https://github.com/Rasit-NP/curtain-call/issues/28)

## 결정

- `routes.tsx`는 `createRoutes({ protectedChildren })`를 내보낸다. 앱은 `createRoutes()`(= `routes`)를 쓰고, 테스트(`src/test/renderApp.tsx`)는 가드 아래에 테스트용 화면(`/test/protected`)을 더한다. 실제 화면은 `routes.tsx`의 `protectedRoutes`에 더한다.
- 가짜 타이머와 user-event를 함께 쓰는 테스트는 `vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true })`와 `userEvent.setup({ advanceTimers })`를 쓴다.

## 근거

- 가드와 `AUTH_REQUIRED` 처리는 레이아웃·라우트 구성에 걸려 있다. 테스트가 별도 라우트를 조립하면 실제 구성에서 레이아웃이 빠져도 통과한다.
- React Testing Library는 user-event 조작이 끝날 때 `setTimeout(0)`을 기다리는데 Jest 가짜 타이머만 알아본다. vitest 가짜 타이머에서는 이 대기가 풀리지 않아 테스트가 멈췄다(재현함). `shouldAdvanceTime`이면 가짜 시계도 실제 시간만큼 흘러 풀린다.

## 고려했지만 고르지 않은 것

- 테스트마다 `RootLayout`·`LoginPage`·`RequireAuth`로 라우트를 조립: 위 이유.
- 타이핑·클릭을 실제 타이머로 한 뒤 가짜 타이머로 전환: 요청의 10초 타이머가 클릭 중에 실제 타이머로 걸려 앞당길 수 없다.

## 적용과 남은 점

- `shouldAdvanceTime`이면 `findBy`가 기다리는 실제 시간만큼 가짜 시계도 흐른다. 시간 경계(예: 9,999ms)는 API 클라이언트 테스트처럼 user-event 없이 검증한다.
