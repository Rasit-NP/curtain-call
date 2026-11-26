# 규칙-테스트 대응표

R1~R6(루트 `CLAUDE.md`)마다 정상·실패·경계값 테스트를 어느 계층에서 검증할지 정한다.
동작의 기준은 [`spec.md`](spec.md)이고, 괄호 안의 숫자는 그 문서의 절 번호다.

현황(2026-11-26): 백엔드 첫 설정(#5)에서 공통 오류 응답, 상태 확인, 테스트 기반 테스트를 썼다. 프론트엔드 첫 설정(#6)에서 공통 API 클라이언트·토큰 저장·안내 문구 테스트와 R6 토큰 첨부 테스트(프론트)를 썼다. 그 밖의 R1~R6 테스트는 미작성이다.

## 읽는 법

| 계층 | 도구 | 실행(각 폴더에서) |
|---|---|---|
| 백엔드 단위 | JUnit, AssertJ, `Clock.fixed`. 스프링 컨텍스트 없음 | `./gradlew test` |
| 백엔드 통합 | `@Tag("integration")`, `@SpringBootTest` + MockMvc, Testcontainers PostgreSQL, 시각을 옮길 수 있는 `Clock` 빈 | `./gradlew integrationTest` |
| 프론트 | Vitest, React Testing Library, user-event, MSW | `npm run test` |
| E2E | Playwright, `e2e/CLAUDE.md`의 시나리오 1~4 | `npx playwright test` |

- 구분: 정상(규칙대로 허용), 실패(규칙대로 거부), 경계(만료 시각, 4석 같은 경계값).
- `(동시성)`: `CountDownLatch`로 여러 스레드를 동시에 출발시키고, 성공 개수와 DB 최종 상태를 모두 검증한다.
- 테스트 이름(또는 표시 이름)은 표의 이름으로 쓴다. 구현하며 다듬어도 규칙 번호로 시작하게 한다.
- 상태는 `미작성`, `작성`(파일 경로), `해당 없음` 중 하나다. 테스트를 추가하거나 바꾸는 PR에서 이 표를 같이 고친다.
- 규칙 테스트는 구현의 해당 부분을 잠시 되돌렸을 때 실패하는지 확인하고, PR 본문의 '규칙 테스트 되돌림 확인'에 적는다.

## R1. 같은 좌석을 동시에 선점하면 한 명만 성공

> 같은 좌석에 선점 요청이 동시에 들어오면 한 명만 성공한다. 나머지는 `409 SEAT_ALREADY_HELD`.

| 구분 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 정상 | R1: 유효한 선점이 없고 판매되지 않은 좌석만 선점할 수 있다고 판정한다 | 백엔드 단위 | 미작성 |
| 정상 | R1: 선택 가능한 좌석 묶음을 선점하면 201을 받고 묶음의 모든 좌석이 HELD로 보인다 | 백엔드 통합 | 미작성 |
| 실패 | R1: 같은 좌석에 선점 요청 10개가 동시에 오면 하나만 201이고 나머지는 409 SEAT_ALREADY_HELD다 | 백엔드 통합(동시성) | 미작성 |
| 실패 | R1: 겹치는 묶음(A-1·A-2와 A-2·A-3)을 동시에 선점하면 하나만 성공하고, 실패한 쪽 좌석은 하나도 잡히지 않는다 | 백엔드 통합(동시성) | 미작성 |
| 실패 | R1: 같은 두 좌석을 반대 순서로 담은 묶음([A-1, A-2]와 [A-2, A-1])을 동시에 선점하기를 여러 번(예: 50회) 반복해도 매번 하나만 성공하고 500이 나지 않는다 | 백엔드 통합(동시성) | 미작성 |
| 실패 | R1: 묶음 중 한 좌석이라도 이미 선점됐으면 409 SEAT_ALREADY_HELD이고 나머지 좌석도 선점되지 않는다 | 백엔드 통합 | 미작성 |
| 실패 | R1: 판매된 좌석이나 내가 이미 선점한 좌석을 선점하면 409 SEAT_ALREADY_HELD다(보유 좌석 수는 한도 안에서) | 백엔드 통합 | 미작성 |
| 경계 | R1: 서로 다른 좌석을 동시에 선점하면 모두 성공한다 | 백엔드 통합(동시성) | 미작성 |
| 실패 | R1: 선점됨·판매됨 좌석은 고를 수 없다 | 프론트 | 미작성 |
| 실패 | R1: 선점 요청이 409 SEAT_ALREADY_HELD면 안내 문구를 보여 주고, 좌석 상태를 다시 불러와 그 좌석을 선택에서 뺀다 | 프론트 | 미작성 |
| 실패 | R1: 다른 사용자가 먼저 선점한 좌석을 고르면 안내 문구가 보이고 선점되지 않는다(시나리오 2) | E2E | 미작성 |

- 만료된 선점의 좌석을 다시 선점하는 경우는 R3에서 다룬다.
- 교착 상태 테스트는 좌석을 `id` 순으로 잠그는 부분(spec 2.4)을 되돌렸을 때 실패해야 한다. 겹치는 좌석이 하나뿐인 묶음으로는
  교착 상태가 생기지 않으므로 같은 두 좌석을 반대 순서로 담고, 한 번에 걸리지 않을 수 있으니 여러 번 반복한다.

## R2. 한 공연에서 최대 4석

> 한 사용자는 공연 하나에서 최대 4석(만료되지 않은 선점 + 예매)까지 가진다. 넘으면 `409 HOLD_LIMIT_EXCEEDED`.

| 구분 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 정상 | R2: 보유 좌석이 없으면 4석 묶음을 선점할 수 있다 | 백엔드 통합 | 미작성 |
| 경계 | R2: 보유 3석이면 1석은 허용하고 2석은 거부한다 | 백엔드 단위 | 미작성 |
| 경계 | R2: 만료·해제된 선점과 다른 공연의 좌석은 보유 좌석 수에 넣지 않는다 | 백엔드 단위 | 미작성 |
| 경계 | R2: 예매 2석이 있으면 2석은 선점할 수 있고 3석은 409 HOLD_LIMIT_EXCEEDED다 | 백엔드 통합 | 미작성 |
| 경계 | R2: 4석 선점이 만료된 순간 새로 4석을 선점할 수 있다 | 백엔드 통합 | 미작성 |
| 경계 | R2: 선점을 해제하면 그 좌석은 보유 좌석 수에서 빠져 다시 4석을 선점할 수 있다 | 백엔드 통합 | 미작성 |
| 실패 | R2: 5석 묶음은 409 HOLD_LIMIT_EXCEEDED이고 아무 좌석도 선점되지 않는다 | 백엔드 통합 | 미작성 |
| 실패 | R2: 같은 사용자가 서로 다른 좌석 3석 묶음 두 개를 동시에 선점하면 하나만 201이고 나머지는 409 HOLD_LIMIT_EXCEEDED다 | 백엔드 통합(동시성) | 미작성 |
| 실패 | R2: 4석을 고른 뒤 5번째 좌석은 선택되지 않고 안내 문구가 보인다 | 프론트 | 미작성 |
| 실패 | R2: 선점 요청이 409 HOLD_LIMIT_EXCEEDED면 안내 문구를 보여 준다 | 프론트 | 미작성 |
| 실패 | R2: 4석을 고른 뒤에는 5번째 좌석을 고를 수 없다(시나리오 3) | E2E | 미작성 |

## R3. 선점은 5분간 유효(지연 만료)

> 현재 시각이 `expiresAt`과 같거나 이후면 만료로 보고, 다른 사용자가 선점할 수 있다. 만료는 조회·선점 시점에 판정한다.

| 구분 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 정상 | R3: 선점의 만료 시각은 만든 시각 + 5분이다 | 백엔드 단위 | 미작성 |
| 경계 | R3: 현재 시각이 만료 시각 1초 전이면 유효하고, 같거나 이후면 만료로 판정한다 | 백엔드 단위 | 미작성 |
| 경계 | R3: 만료 시각 1초 전에는 다른 사용자가 선점할 수 없다(409 SEAT_ALREADY_HELD) | 백엔드 통합 | 미작성 |
| 경계 | R3: 만료 시각과 같은 순간에는 다른 사용자가 선점할 수 있다 | 백엔드 통합 | 미작성 |
| 경계 | R3: 공연 상세는 만료 시각 1초 전까지 HELD, 만료 시각부터 AVAILABLE을 보여 준다 | 백엔드 통합 | 미작성 |
| 경계 | R3: 선점 조회는 만료 시각부터 status EXPIRED를 돌려준다 | 백엔드 통합 | 미작성 |
| 실패 | R3: 만료된 내 선점을 해제해도 그 좌석을 새로 선점한 다른 사용자의 선점은 유지된다 | 백엔드 통합 | 미작성 |
| 정상 | R3: 결제 화면은 남은 시간을 보여 주고, 0이 되면 결제 버튼을 막고 만료 안내를 보여 준다 | 프론트 | 미작성 |
| 실패 | R3: 선점 조회 결과가 EXPIRED면 결제 버튼 없이 만료 안내를 보여 준다 | 프론트 | 미작성 |
| - | 시간을 조작해야 하므로 E2E에서 다루지 않고 백엔드에서 검증한다(`e2e/CLAUDE.md`) | E2E | 해당 없음 |

## R4. 만료된 선점으로는 결제할 수 없음

> 만료된 선점으로 결제하면 거부한다. `409 HOLD_EXPIRED`.

| 구분 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 경계 | R4: 만료 시각 1초 전에는 결제할 수 있고, 같은 순간부터는 결제할 수 없다고 판정한다 | 백엔드 단위 | 미작성 |
| 정상 | R4: 만료 시각 1초 전의 결제는 201이고 예매가 생긴다 | 백엔드 통합 | 미작성 |
| 경계 | R4: 만료 시각과 같은 순간의 결제는 409 HOLD_EXPIRED다 | 백엔드 통합 | 미작성 |
| 실패 | R4: 만료된 선점으로 결제하면 예매가 생기지 않고, 좌석이 판매되지 않으며, 모의 결제를 부르지 않는다 | 백엔드 통합 | 미작성 |
| 실패 | R4: 만료 뒤 다른 사용자가 좌석을 선점했으면 원래 사용자의 결제는 409 HOLD_EXPIRED이고 다른 사용자의 선점은 그대로다 | 백엔드 통합 | 미작성 |
| 경계 | R4: 결제를 처리하는 동안 만료 시각이 지나도 다른 사용자가 그 좌석을 선점하지 못한다 | 백엔드 통합(동시성) | 미작성 |
| 실패 | R4: 결제 응답이 409 HOLD_EXPIRED면 만료 안내와 '좌석 다시 선택' 링크를 보여 준다 | 프론트 | 미작성 |
| - | 시간을 조작해야 하므로 E2E에서 다루지 않는다(R3과 같음) | E2E | 해당 없음 |

## R5. 같은 멱등 키로는 예매가 하나만 생김

> 같은 멱등 키로 결제를 여러 번 요청해도 예매는 하나만 생기고, 같은 결과를 돌려준다. 세부는 spec 6.7.1.

| 구분 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 정상 | R5: 같은 키로 두 번 결제하면 두 번 다 같은 201 응답이고 예매는 하나다 | 백엔드 통합 | 미작성 |
| 실패 | R5: 같은 키로 결제 요청 5개가 동시에 오면 예매는 하나이고 모두 같은 201 응답이다 | 백엔드 통합(동시성) | 미작성 |
| 실패 | R5: 402로 거절된 결제를 같은 키로 다시 요청하면 모의 결제를 다시 부르지 않고 같은 402를 돌려주며, 선점은 유지된다 | 백엔드 통합 | 미작성 |
| 정상 | R5: 결제가 거절된 뒤 새 키로 다시 결제하면 승인될 수 있다 | 백엔드 통합 | 미작성 |
| 실패 | R5: 이미 처리한 키로 다른 선점을 결제하면 400 INVALID_INPUT이고 아무것도 처리하지 않는다 | 백엔드 통합 | 미작성 |
| 실패 | R5: Idempotency-Key가 없거나 공백뿐이면 400 INVALID_INPUT이다 | 백엔드 통합 | 미작성 |
| 경계 | R5: 키 길이 100자는 허용하고 101자는 400 INVALID_INPUT이다 | 백엔드 통합 | 미작성 |
| 경계 | R5: 다른 사용자가 같은 키를 쓰면 서로 영향 없이 따로 처리된다 | 백엔드 통합 | 미작성 |
| 경계 | R5: 404나 409로 끝난 요청도 저장해, 같은 키로 다시 보내면 같은 응답이고 다른 선점에는 쓸 수 없다(400) | 백엔드 통합 | 미작성 |
| 경계 | R5: 검증 실패(400)로 끝난 요청은 저장하지 않아 같은 키를 다시 쓸 수 있다 | 백엔드 통합 | 미작성 |
| 정상 | R5: 응답을 받은 뒤에는 새 키로, 응답을 받지 못한 뒤(10초 시간 초과 포함)에는 같은 키로 다시 결제한다 | 프론트 | 미작성 |
| 정상 | R5: 결제 요청 중에는 결제 버튼을 누를 수 없다 | 프론트 | 미작성 |
| - | 같은 키 재요청은 화면 조작으로 만들 수 없어 백엔드 통합 테스트가 맡는다 | E2E | 해당 없음 |

## R6. 로그인하지 않으면 선점·결제를 할 수 없음

> 로그인하지 않은 사용자는 선점과 결제를 할 수 없다. `401 AUTH_REQUIRED`.

| 구분 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 정상 | R6: 유효한 토큰이면 선점·선점 조회·해제·결제·내 예매를 쓸 수 있다 | 백엔드 통합 | 미작성 |
| 정상 | R6: 로그인 API로 받은 토큰으로 선점까지 할 수 있다(실제 흐름). 선점 생성 Issue에서 완성한다 | 백엔드 통합 | 미작성 |
| 정상 | R6: 로그인 API로 받은 토큰은 인증을 통과하고(등록되지 않은 비공개 경로가 404), 토큰이 없으면 401이다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 정상 | R6: 같은 키로 서명한 정상 토큰은 인증을 통과한다(등록되지 않은 비공개 경로가 404) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 실패 | R6: 토큰 없이 선점·선점 조회·해제·결제·내 예매를 부르면 401 AUTH_REQUIRED다(기능 경로가 없어도 보안 설정으로) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 실패 | R6: 서명이 다르거나 형식이 잘못된 토큰은 401 AUTH_REQUIRED다(다른 키, 서명·내용 변조, alg none, JWT 아님, exp 없음, sub 없음·숫자 아님·long 범위 밖) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 경계 | R6: sub가 long 최댓값인 토큰은 인증을 통과하고, 1 더 크면 401 AUTH_REQUIRED다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 실패 | R6: 공개 경로에서 @CurrentUserId를 쓰면 토큰 없는 요청은 500이 아니라 401 AUTH_REQUIRED다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/api/CurrentUserIdTest.java`) |
| 실패 | R6: Bearer 뒤가 비었거나 Bearer가 아닌 인증 헤더는 토큰이 없는 것과 같이 401 AUTH_REQUIRED다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 경계 | R6: exp 1초 전과 exp와 같은 시각의 토큰은 허용하고, exp 1초 뒤 토큰은 401 AUTH_REQUIRED다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 경계 | R6: 공연 목록과 공연 상세는 토큰 없이 200이다. 공연 Issue에서 작성한다 | 백엔드 통합 | 미작성 |
| 경계 | R6: 인증 없이 열리는 경로(POST 로그인, GET 공연, GET health)는 토큰 없이 인증 검사를 지나고, 공개 경로라도 정해진 메서드가 아니면 401 AUTH_REQUIRED다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 실패 | R6: 로그인하지 않고 좌석 선택·결제·내 예매 화면에 들어가면 로그인 화면으로 보내고, 로그인하면 원래 화면으로 돌아온다(실제 경로. 화면 Issue에서 작성) | 프론트 | 미작성 |
| 실패 | R6: 토큰 없이 로그인이 필요한 화면에 들어가면 로그인 화면으로 보내고, 로그인하면 원래 경로(쿼리 포함)로 돌아온다(가드 아래 테스트용 화면) | 프론트 | 작성(`frontend/src/auth/RequireAuth.test.tsx`) |
| 경계 | R6: 가드가 보낸 로그인 화면에서 한 번 실패해도, 다시 로그인하면 원래 경로로 돌아온다 | 프론트 | 작성(`frontend/src/auth/RequireAuth.test.tsx`) |
| 실패 | R6: API가 401 AUTH_REQUIRED를 주면 토큰을 지우고 로그인 화면으로 보내며 로그인이 필요하다는 문구를 보여 준다 | 프론트 | 작성(`frontend/src/auth/AuthRequiredRedirect.test.tsx`, `frontend/src/api/client.test.ts`) |
| 실패 | R6: AUTH_REQUIRED로 로그인 화면에 온 뒤 로그인하면 원래 화면(쿼리 포함)으로 돌아온다 | 프론트 | 작성(`frontend/src/auth/AuthRequiredRedirect.test.tsx`) |
| 경계 | R6: 요청 두 개가 AUTH_REQUIRED를 받고 두 번째가 로그인 화면에 온 뒤에 끝나도, 로그인하면 원래 화면으로 돌아온다 | 프론트 | 작성(`frontend/src/auth/AuthRequiredRedirect.test.tsx`) |
| 경계 | R6: 인증이 필요한 API의 다른 401(INVALID_CREDENTIALS)에는 토큰을 지우거나 화면을 옮기지 않는다 | 프론트 | 작성(`frontend/src/auth/AuthRequiredRedirect.test.tsx`, `frontend/src/api/client.test.ts`) |
| 경계 | R6: 로그인 실패(401 INVALID_CREDENTIALS)는 토큰을 지우거나 화면을 옮기지 않고 안내 문구만 보여 준다 | 프론트 | 작성(`frontend/src/pages/login/LoginPage.test.tsx`) |
| 정상 | R6: 로그인 요청에는 저장된 토큰이 있어도 Authorization 헤더를 붙이지 않는다 | 프론트 | 작성(`frontend/src/api/auth.test.ts`) |
| 정상 | R6: 공개 API 요청에는 저장된 토큰이 있어도 붙이지 않는다 | 프론트 | 작성(`frontend/src/api/client.test.ts`) |
| 정상 | R6: 인증이 필요한 요청에는 저장된 토큰을 Bearer로 붙인다 | 프론트 | 작성(`frontend/src/api/client.test.ts`) |
| 경계 | R6: 인증이 필요한 요청이라도 저장된 토큰이 없으면 헤더 없이 보낸다 | 프론트 | 작성(`frontend/src/api/client.test.ts`) |
| 실패 | R6: 로그인하지 않고 좌석 선택 화면에 들어가면 로그인 화면으로 이동한다(시나리오 4) | E2E | 미작성 |

토큰 검증(서명, 형식, 만료) 테스트는 실제로 서명한 토큰으로 요청한다. `spring-security-test`의 `jwt()`는 토큰 해석을 건너뛰어
이 경우를 검증하지 못한다. `backend/CLAUDE.md`가 말하는 '로그인부터 토큰 사용까지의 실제 흐름' 통합 테스트에 해당한다.

## 규칙 밖 명세 동작

R1~R6에 직접 속하지 않지만 명세에 정한 동작이다. 테스트 이름에 규칙 번호를 붙이지 않는다.

| 동작 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 로그인(6.1) | 로그인에 성공하면 200과 accessToken, expiresIn 3600을 주고, 토큰의 sub는 사용자 id, exp는 발급 시각 + 1시간이다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/api/LoginApiTest.java`) |
| 로그인(6.1) | 없는 아이디와 틀린 비밀번호는 모두 401 INVALID_CREDENTIALS다(설명도 같다) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/api/LoginApiTest.java`) |
| 로그인(6.1) | 아이디 대소문자나 앞뒤 공백이 다르면 다른 아이디로 보고, 비밀번호가 BCrypt 한도(72바이트)를 넘어도 500이 아니라 401 INVALID_CREDENTIALS다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/api/LoginApiTest.java`) |
| 로그인(6.1) | 아이디나 비밀번호가 없거나 비었거나 공백뿐이면, 또는 본문이 없거나 JSON이 아니면 400 INVALID_INPUT이다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/api/LoginApiTest.java`) |
| 공연(6.2, 6.3) | 공연 목록은 시작 시각 오름차순(같으면 id 순)이고, 없는 공연의 상세는 404 NOT_FOUND다 | 백엔드 통합 | 미작성 |
| 선점(6.4) | 없는 공연이나 그 공연에 없는 좌석으로 선점하면 404 NOT_FOUND다 | 백엔드 통합 | 미작성 |
| 선점(6.4) | seatIds가 비었거나 중복이 있으면 400 INVALID_INPUT이다 | 백엔드 통합 | 미작성 |
| 선점 조회(6.5) | 다른 사용자의 선점과 해제·결제된 선점은 404 NOT_FOUND다 | 백엔드 통합 | 미작성 |
| 선점 해제(6.6) | 내 선점을 해제하면 204이고 그 좌석이 AVAILABLE이 된다 | 백엔드 통합 | 미작성 |
| 선점 해제(6.6) | 만료된 내 선점의 해제는 204이고, 다른 사용자의 선점과 이미 해제·결제된 선점은 404 NOT_FOUND다 | 백엔드 통합 | 미작성 |
| 결제(6.7) | 결제가 승인되면 좌석은 SOLD, 선점은 PAID가 되고 금액은 가격 × 좌석 수다 | 백엔드 단위, 백엔드 통합 | 미작성 |
| 결제(6.7) | 결제가 거절되면 402 PAYMENT_FAILED이고 예매가 생기지 않으며 선점은 유지된다 | 백엔드 통합 | 미작성 |
| 결제(6.7) | 이미 결제된 선점을 새 키로 결제하면 404 NOT_FOUND이고 예매는 하나다 | 백엔드 통합 | 미작성 |
| 결제(2.4) | 같은 선점을 서로 다른 키로 동시에 결제하면 하나만 201이고 예매는 하나다 | 백엔드 통합(동시성) | 미작성 |
| 결제(2.4) | 같은 선점에 결제와 해제가 동시에 오면 하나씩 처리된다. 결제가 먼저 승인되면 해제는 404 NOT_FOUND, 결제가 먼저 거절되면 해제는 204다 | 백엔드 통합(동시성) | 미작성 |
| 내 예매(6.8) | 내 예매만 최신순(같으면 id 내림차순)으로 돌려준다 | 백엔드 통합 | 미작성 |
| 오류 형식(3.1) | 오류 응답은 ProblemDetail에 code가 있고, 500 응답에 스택 트레이스가 없다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/common/api/ErrorResponseTest.java`) |
| 오류 형식(3.1) | 잘못된 JSON, 형식이 틀린 경로·쿼리 값, 필수 쿼리 값 없음, 검증 실패(본문 필드, 쿼리 값, `@Validated` 빈), 지원하지 않는 메서드(405)·`Content-Type`(415)은 400 INVALID_INPUT이고, 없는 경로는 404 NOT_FOUND다(MVC 안의 오류 변환. 서블릿 필터를 끈 MockMvc) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/common/api/ErrorResponseTest.java`) |
| 오류 형식(3.1) | `ApiException`으로 알린 오류는 그 코드의 상태·code·설명으로 응답하고, 코드를 정하지 않은 상태 예외(`ResponseStatusException`)는 500 INTERNAL_ERROR다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/common/api/ErrorResponseTest.java`) |
| 오류 형식(3.1) | 오류 코드와 상태는 루트 `CLAUDE.md`의 오류 코드 표와 같다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/common/api/ErrorCodeTest.java`) |
| 오류 형식(3.1) | 공개 API에 잘못된 토큰을 붙이면 401 AUTH_REQUIRED이고, 토큰 검사를 지난 공개 경로에서도 지원하지 않는 메서드는 400 INVALID_INPUT, 없는 경로는 404 NOT_FOUND다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 오류 형식(3.1) | MVC 밖 오류도 ProblemDetail과 code다: 공개 경로의 잘못된 쿼리·경로 퍼센트 인코딩, Tomcat이 거부한 인코딩된 슬래시, Spring Security 방화벽이 거부한 경로(세미콜론, 인코딩된 %, 이중 슬래시), 폼 본문은 400 INVALID_INPUT(401이나 500이 아님), 서블릿 필터의 예외는 500 INTERNAL_ERROR(예외 내용 없음). 비공개 경로는 토큰 검사(401)가 먼저이고, /error를 직접 부르면 토큰 없이 401이다(실제 서버) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/common/api/ErrorPageTest.java`) |
| 오류 형식(3.1) | Tomcat이 거부한 잘못된 요청(지원하지 않는 HTTP 버전 505, Transfer-Encoding 501, Expect 417)은 500이 아니라 400 INVALID_INPUT이고, Tomcat이 거부한 요청의 본문은 공통 처리기의 본문과 필드가 같다(실제 서버) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/common/api/ErrorPageTest.java`) |
| 오류 형식(3.1) | 코드 없이 상태만 정해진 오류의 code: 앱 안(MVC 예외, /error)은 입력 오류 상태만 INVALID_INPUT, Tomcat이 앱에 넘기기 전에 거부한 요청은 4xx와 501·505가 INVALID_INPUT, 404는 NOT_FOUND, 그 밖은 INTERNAL_ERROR | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/common/api/ErrorCodeStatusMappingTest.java`) |
| E2E 지원(6.9) | reset 컨트롤러는 e2e 프로필에서만 등록된다. E2E 첫 설정 Issue에서 작성한다 | 백엔드 통합 | 미작성 |
| E2E 지원(6.9) | e2e 프로필에서는 POST /api/test-support/reset을 토큰 없이 부를 수 있다(인증 검사를 지나 컨트롤러가 없으면 404) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/E2eProfileAuthTest.java`) |
| E2E 지원(6.9) | e2e가 아닌 프로필에서 POST /api/test-support/reset은 토큰 없이 401 AUTH_REQUIRED, 유효한 토큰이면 404 NOT_FOUND다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 시드(2.5) | 시드 계정(시연용 demo, E2E용 e2e-user1·e2e-user2)으로 로그인할 수 있다(시드 파일을 실제 스키마에서 실행) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/E2eProfileAuthTest.java`) |
| 상태 확인(6.10) | `GET /actuator/health`는 200 `{"status":"UP"}`이다(다른 속성 없음) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/HealthCheckTest.java`) |
| 결제 화면(7.3) | 승인 전에(402 뒤 포함) 앱 안의 다른 화면으로 이동하면 해제 요청을 보내고, 승인된 뒤에는 보내지 않는다 | 프론트 | 미작성 |
| 결제 화면(7.3) | 결제가 승인되면 예매 완료를 보여 주고, 402면 안내 문구와 함께 다시 결제할 수 있다 | 프론트 | 미작성 |
| 화면 공통(7.5) | 데이터를 불러오는 화면마다 로딩·빈 목록·오류 상태를 보여 준다 | 프론트 | 미작성 |
| 화면 공통(7.5) | API 요청이 10초 동안 응답하지 않거나 응답 헤더 뒤 본문이 10초 안에 끝나지 않으면 끊고 응답 없음으로 넘기며, 9,999ms에 온 응답은 받고, 응답을 받은 뒤에는 끊지 않는다 | 프론트(API 클라이언트) | 작성(`frontend/src/api/client.test.ts`) |
| 화면 공통(7.5) | 네트워크 오류와 본문을 받는 중 연결 끊김은 응답 없음으로 넘긴다 | 프론트(API 클라이언트) | 작성(`frontend/src/api/client.test.ts`) |
| 화면 공통(7.5) | 직렬화할 수 없는 본문, GET 요청의 본문 같은 호출 실수는 요청을 보내지 않고 응답 없음이 아닌 오류로 거부한다 | 프론트(API 클라이언트) | 작성(`frontend/src/api/client.test.ts`) |
| 화면 공통(7.5) | API 요청이 응답 없음(10초 시간 초과, 네트워크 오류)으로 끝나면 '서버에 연결할 수 없습니다' 문구를 보여 준다(로그인 화면) | 프론트 | 작성(`frontend/src/pages/login/LoginPage.test.tsx`) |
| 로그인 화면(6.1, 7.1) | 입력 이름은 '아이디'·'비밀번호', 버튼 이름은 '로그인'이고, 처음에는 안내 문구가 없다 | 프론트 | 작성(`frontend/src/pages/login/LoginPage.test.tsx`) |
| 로그인 화면(6.1, 7.1) | 로그인에 성공하면 입력한 값으로 요청하고, 토큰을 저장한 뒤 돌아갈 화면이 없으면 공연 목록(/)으로 간다 | 프론트 | 작성(`frontend/src/pages/login/LoginPage.test.tsx`) |
| 로그인 화면(7.5, 7.6) | 400 INVALID_INPUT, 500 INTERNAL_ERROR, code 없는 응답, 네트워크 오류는 code별 안내 문구를 `role="alert"`로 보여 주고 화면에 머문다 | 프론트 | 작성(`frontend/src/pages/login/LoginPage.test.tsx`) |
| 로그인 화면 | 로그인 요청 중에는 로그인 버튼을 누를 수 없고, 다시 누르면 이전 안내 문구를 지운다 | 프론트 | 작성(`frontend/src/pages/login/LoginPage.test.tsx`) |
| 로그인 화면 | 로그인 뒤 돌아갈 경로가 앱 안의 경로가 아니면(`//host`) 공연 목록(/)으로 간다 | 프론트 | 작성(`frontend/src/auth/RequireAuth.test.tsx`) |
| 로그인 API(6.1) | 아이디와 비밀번호를 POST /api/auth/login으로 보내고 토큰 응답을 돌려준다 | 프론트 | 작성(`frontend/src/api/auth.test.ts`) |
| 오류 형식(3.1) | 오류 응답이면 상태와 `code`를 호출한 쪽에 넘기고, `code`가 없거나 오류 코드 표에 없거나 본문이 JSON이 아니면 `INTERNAL_ERROR`로 본다 | 프론트(API 클라이언트) | 작성(`frontend/src/api/client.test.ts`) |
| 안내 문구(7.6) | 루트 `CLAUDE.md` 오류 코드 표의 모든 code와 응답 없음에 명세와 같은 문구가 있다 | 프론트 | 작성(`frontend/src/api/errorMessages.test.ts`) |
| 토큰 저장(3.2) | 액세스 토큰을 `localStorage`에 저장하고 읽고 지운다 | 프론트 | 작성(`frontend/src/auth/tokenStorage.test.ts`) |
| 기본 흐름(1) | 로그인 → 공연 선택 → 좌석 2석 선점 → 결제 → 예매 완료 → 내 예매에서 확인(시나리오 1) | E2E | 미작성 |

## 테스트 기반과 설정

규칙·명세 동작이 아니라, 규칙 테스트가 기대는 기반(주입한 시계, 운영과 같은 종류의 DB, 테스트마다 데이터 정리, 프론트엔드의 MSW 설정)과 프로필 설정을 검증한다.

| 대상 | 테스트 | 계층 | 상태 |
|---|---|---|---|
| 프로필 | 프로필을 주지 않으면 local로 뜨고, local·e2e는 환경변수 없이 compose DB와 시드를 쓴다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/ApplicationProfilesTest.java`) |
| 프로필 | test는 DB 주소를 정하지 않고 시드를 쓰지 않으며, prod는 DB 접속 정보를 기본값 없이 환경변수로만 받는다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/ApplicationProfilesTest.java`) |
| 프로필 | local·e2e는 시드가 적용된 DB에 나중에 추가된 마이그레이션도 적용하도록 Flyway out-of-order를 켜고, test·prod는 끈다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/ApplicationProfilesTest.java`) |
| 프로필 | 모든 프로필에서 open-in-view를 끄고 ddl-auto는 validate다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/ApplicationProfilesTest.java`) |
| 프로필 | local·e2e·test는 저장소에 둔 개발 전용 JWT 서명 키(이름에 dev, 32바이트 이상)를 쓰고, prod는 JWT_SECRET으로만 받으며 없으면 값을 정할 수 없다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/ApplicationProfilesTest.java`) |
| 보안 설정 | JWT 서명 키가 없거나 32바이트보다 짧으면 키를 만들지 않아 애플리케이션이 시작되지 않는다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/auth/infra/JwtConfigTest.java`) |
| 보안 설정 | 인증된 요청에도 세션과 쿠키를 만들지 않는다(STATELESS) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/AuthenticationTest.java`) |
| 인증 도우미 | 컨트롤러의 @CurrentUserId는 로그인 토큰의 사용자 id이고, 테스트 도우미 AuthRequests.asUser(id)로 만든 요청이면 그 id다(공개 경로에서도) | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/auth/api/CurrentUserIdTest.java`) |
| 시계 | 운영 Clock 빈은 UTC 시스템 시계다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/common/infra/ClockConfigTest.java`) |
| 시계 | 테스트 시계는 시각을 정하거나 옮길 수 있고 되돌리면 정해진 시각으로 돌아간다 | 백엔드 단위 | 작성(`backend/src/test/java/com/curtaincall/support/MutableClockTest.java`) |
| 통합 기반 | 운영 코드에 주입되는 Clock은 테스트 시계이고, 테스트마다 정해진 시각으로 되돌린다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/support/IntegrationTestSupportTest.java`) |
| 통합 기반 | 테스트마다 시작 전에 테이블을 비우고, Flyway 이력 테이블은 남긴다 | 백엔드 통합 | 작성(`backend/src/test/java/com/curtaincall/support/IntegrationTestSupportTest.java`) |
| 프론트 MSW | 핸들러가 없는 요청은 실제 네트워크로 나가지 않고 실패한다 | 프론트 | 작성(`frontend/src/test/lifecycle.test.ts`) |
| 프론트 MSW | 테스트가 끝날 때 핸들러가 없는 요청이 있었으면(아직 처리 중이던 요청 포함) 테스트를 실패로 만든다 | 프론트 | 작성(`frontend/src/test/lifecycle.test.ts`) |
| 프론트 MSW | 테스트가 끝날 때 처리 중인 요청은 그 테스트의 핸들러로 처리한다 | 프론트 | 작성(`frontend/src/test/lifecycle.test.ts`) |
| 프론트 라우터 | 첫 화면 경로(/)에서 오류 없이 공통 레이아웃을 그린다 | 프론트 | 작성(`frontend/src/App.test.tsx`) |
| 프론트 라우터 | /login 경로에서 로그인 화면을 그린다 | 프론트 | 작성(`frontend/src/App.test.tsx`) |

## E2E 시나리오 대응

`e2e/CLAUDE.md`의 시나리오를 이 명세의 화면 흐름으로 옮긴 것이다.

| 시나리오 | 규칙 | 진행 | 상태 |
|---|---|---|---|
| 1 | 기본 흐름 | 로그인 → 공연 목록에서 공연 선택 → 좌석 2석 선택 → '좌석 선택 완료'(묶음 선점) → '결제하기' → 예매 완료 → 내 예매에 같은 좌석이 보임 | 미작성 |
| 2 | R1 | 좌석 화면을 연 뒤 다른 사용자가 `request` 픽스처로 좌석 하나를 선점 → 그 좌석을 고르고 '좌석 선택 완료' → 안내 문구가 보이고, 결제 화면으로 넘어가지 않으며, 그 좌석이 '선점됨'으로 바뀜 | 미작성 |
| 3 | R2 | 4석을 고른 뒤 5번째 좌석을 누름 → 선택되지 않고 안내 문구가 보임 | 미작성 |
| 4 | R6 | 로그인하지 않고 `/performances/:performanceId`에 들어감 → `/login`으로 이동 | 미작성 |

시나리오 2에서 다른 사용자의 선점은 좌석 화면을 연 뒤에 만든다. 먼저 선점된 좌석은 처음부터 '선점됨'으로 비활성이라 고를 수 없기 때문이다.
