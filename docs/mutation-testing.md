# 뮤테이션 테스트(PIT) 보고서

규칙 코드(R1~R6)의 테스트가 결과를 제대로 검증하는지 PIT로 잰 기록이다. Issue [#69](https://github.com/Rasit-NP/curtain-call/issues/69)에서 기준선을 재고,
살아남은 변이를 분류해 테스트를 보강한 뒤 다시 쟀다. 로드맵 7단계에서 같은 명령·설정으로 다시 재고 이 문서에 이어 적는다.

뮤테이션 테스트는 운영 코드를 조금씩 바꾼 변이(예: `<=`를 `<`로, 조건을 늘 참으로, 반환값을 null로)를 만들고 테스트를 돌린다.
테스트가 실패하면 변이가 '죽었다', 모두 통과하면 '살아남았다'고 한다. 살아남은 변이는 테스트가 그 변화를 확인하지 않는다는 뜻이다.

- 점수(mutation score): 죽인 변이 / 전체 변이. 어떤 테스트도 그 줄을 돌지 않은 '덮이지 않은' 변이도 분모에 들어간다.
- 테스트 강도(test strength): 죽인 변이 / 덮인 변이. 테스트가 돈 코드에서 변화를 얼마나 잡는지 본다.

## 측정 방법

- 명령: `./scripts/verify.sh mutation`(Docker 확인 뒤 `backend/`에서 `./gradlew pitest`). CI에서는 수동 실행 워크플로 `Mutation`(`.github/workflows/mutation.yml`).
- 리포트: `backend/build/reports/pitest/index.html`(HTML), `mutations.xml`(XML, 변이마다 상태와 죽인 테스트). 워크플로는 산출물 `mutation-report-<시도>`로 30일 남긴다.
- 도구와 설정([결정 0200](decisions/0200-pit-setup.md)): Gradle 플러그인 `info.solidsoft.pitest` 1.19.0, PIT 1.30.2, `pitest-junit5-plugin` 1.2.3.
  연산자 `STRONGER`, 스레드 4, 시간 제한 기본값, 증분 분석 없음. 단위 테스트와 통합 테스트(Testcontainers PostgreSQL)를 모두 쓴다.
- 대상([결정 0201](decisions/0201-pit-target-classes.md)): R1~R6을 판정하거나 규칙의 오류 코드를 내는 15개 클래스. 뺀 클래스와 이유도 결정 0201에 있다.
- 실행 시점([결정 0202](decisions/0202-pit-when-to-run.md)): 로컬 수동 명령과 수동 실행 워크플로. 전체 검증·quick·PR CI에는 없다.
  측정 대상 클래스를 바꾸는 PR은 돌려서 결과를 PR 본문에 적는다.
- 점수 기준([결정 0203](decisions/0203-pit-threshold.md)): 93% 미만이면 실패(`mutationThreshold`). PIT는 점수를 정수로 반올림해 비교한다.
- 분류 기준([결정 0204](decisions/0204-pit-survivor-classification.md)): 테스트 빈틈, 동등 변이, 죽은 코드, 검증할 가치가 낮음, PIT 측정 한계. 설정으로 뺀 변이는 없다.

## 결과 요약

측정 환경: macOS(10코어), Docker Desktop, JDK 21(Gradle 툴체인), PIT 스레드 4. 2026-11-28.

| | 기준선 | 재측정(보강 후) |
|---|---|---|
| 변이 | 209 | 209 |
| 죽음 | 180 | 195 |
| 살아남음 | 27 | 12 |
| 덮이지 않음 | 2 | 2 |
| 시간 초과·메모리 오류 | 0 | 0 |
| 점수 | 86%(86.1) | 93%(93.3) |
| 테스트 강도 | 87%(87.0) | 94%(94.2) |
| 줄 커버리지(대상 클래스) | 95%(276/292) | 96%(280/292) |
| 살펴본 테스트 클래스 | 135 | 137 |
| PIT가 돌린 테스트 수 | 1,376(변이당 6.6) | 1,041(변이당 5.0) |
| 실행 시간(`./gradlew pitest`) | 3분 30초, 3분 29초(두 번) | 3분 22초 |

- CI 표준 러너(ubuntu-24.04, 4코어)에서 같은 명령으로 잰 재측정(실행 [46076896926](https://github.com/Rasit-NP/curtain-call/actions/runs/46076896926), 수동 실행 워크플로를 임시 트리거로 돌림)도 195개를 죽였다(93%, 강도 94%). PIT 6분 32초, 단계 7분 21초.
- 기준선은 같은 설정으로 두 번 쟀고, 209개 변이의 판정이 하나하나 같았다. 동시성 테스트도 시간 초과 없이 돌았다.
- 재측정은 운영 코드를 바꾸지 않고 테스트만 더한 뒤 같은 명령·설정으로 쟀다. 바뀐 15개는 모두 '살아남음 → 죽음'이고, 죽인 테스트가 모두 이번에 더한 테스트다(아래 '더한 테스트').
  기준값을 넣은 뒤 `verify.sh mutation`으로 한 번 더 돌린 실행도 195개를 죽였다(3분 23초).
- 커버리지 계산은 30~31초였다. 나머지가 변이 분석이다. PIT는 커버리지 단계에서 잰 시간이 짧은 테스트부터 돌리고 처음 죽인 테스트에서 멈춘다.
  그래서 돌린 테스트 수는 실행마다 조금 다르고(재측정 1,041, 그 뒤 실행 1,100), 새 단위 테스트가 통합 테스트보다 먼저 변이를 죽여 재측정이 빨라졌다.

### 클래스별

| 클래스 | 변이 | 살아남음(전→후) | 덮이지 않음(전→후) | 점수(전→후) | 테스트 강도(전→후) |
|---|---|---|---|---|---|
| `auth.api.CurrentUserIds` | 5 | 1 → 1 | 0 → 0 | 80% → 80% | 80% → 80% |
| `auth.infra.AuthRequiredEntryPoint` | 3 | 2 → 0 | 0 → 0 | 33% → 100% | 33% → 100% |
| `auth.infra.JwtConfig` | 21 | 9 → 0 | 0 → 0 | 57% → 100% | 57% → 100% |
| `auth.infra.SecurityConfig` | 4 | 4 → 4 | 0 → 0 | 0% → 0% | 0% → 0% |
| `hold.application.HoldService` | 23 | 0 → 0 | 0 → 0 | 100% → 100% | 100% → 100% |
| `hold.domain.Hold` | 33 | 1 → 0 | 0 → 0 | 97% → 100% | 97% → 100% |
| `hold.domain.HoldLimit` | 11 | 0 → 0 | 0 → 0 | 100% → 100% | 100% → 100% |
| `hold.domain.HoldRepository` | 3 | 1 → 1 | 0 → 0 | 67% → 67% | 67% → 67% |
| `hold.domain.SeatAvailability` | 17 | 1 → 0 | 0 → 0 | 94% → 100% | 94% → 100% |
| `payment.api.IdempotencyKeyHeader` | 21 | 2 → 2 | 0 → 0 | 90% → 90% | 90% → 90% |
| `payment.api.StoredPaymentResponses` | 7 | 1 → 0 | 0 → 0 | 86% → 100% | 86% → 100% |
| `payment.application.PaymentService` | 25 | 0 → 0 | 1 → 1 | 96% → 96% | 100% → 100% |
| `payment.domain.IdempotencyRecord` | 10 | 3 → 2 | 0 → 0 | 70% → 80% | 70% → 80% |
| `performance.domain.Seat` | 16 | 2 → 2 | 1 → 1 | 81% → 81% | 87% → 87% |
| `reservation.domain.Reservation` | 10 | 0 → 0 | 0 → 0 | 100% → 100% | 100% → 100% |
| 합계 | 209 | 27 → 12 | 2 → 2 | 86.1% → 93.3% | 87.0% → 94.2% |

연산자별 변이 수: 조건 지우기(같음 비교) 늘 거짓 48·늘 참 48, null 반환 33, true 반환 15, 기본형 0 반환 14, void 호출 지우기 12, 빈 값 반환 10, false 반환 7,
경계 바꾸기 6, 조건 지우기(크기 비교) 늘 거짓 6·늘 참 6, 산술 바꾸기 3, switch 1.

## 살아남은·덮이지 않은 변이 분류(기준선 29개)

줄 번호는 이 측정 때의 운영 코드다. '늘 참'·'늘 거짓'은 PIT가 바꾼 바이트코드 분기를 뜻한다. 각 분기는 `javap`로 확인했다.

### 테스트 빈틈: 15개, 모두 테스트를 더해 죽였다

| # | 위치 | 변이 | 무엇이 빠졌나 | 죽인 테스트 |
|---|---|---|---|---|
| 1 | `AuthRequiredEntryPoint.commence:40` | `instanceof OAuth2AuthenticationException` 분기를 늘 거짓 | 잘못된 토큰에도 `WWW-Authenticate: Bearer`만 보냄. 테스트는 `startsWith("Bearer")`만 봤다(결정 0034가 정한 값) | R6 `wwwAuthenticateTellsMissingFromInvalidToken`(통합) |
| 2 | 같은 줄 | 같은 분기를 늘 참 | 토큰이 없어도 `error="invalid_token"`을 보냄 | 같음 |
| 3 | `JwtConfig.isUserId:76` | `sub instanceof String` 검사를 지움 | sub가 없으면(null) false 대신 NPE | R6 `rejectsNonUserIdSubject`(단위, null) |
| 4 | 같은 줄 | 정규식 `[1-9][0-9]*` 검사를 지움 | `0`·`-1`·`+1`·`01`이 `Long.parseLong`을 통과해 사용자 id로 받아짐. 테스트에 이 값이 없었다 | 같음(`"0"`) |
| 5 | `JwtConfig.signingKey:88` | `isBlank()` 검사를 지움 | 공백뿐인 32바이트 키를 받음. 테스트의 공백 키는 3바이트라 길이 검사에 먼저 걸렸다 | `rejectsBlankSecretOfAnyLength`(단위) |
| 6 | `JwtConfig.jwtDecoder:58` | `setJwtValidator` 호출 지움 | 기본 검증기(시스템 시계, sub 검사 없음)를 씀. **PIT 측정 한계**이기도 하다(아래) | R6 `decoderAppliesValidator`(단위) |
| 7 | `JwtConfig.jwtDecoder:59` | null 반환 | 디코더 빈이 없음. PIT 측정 한계 | 같음 |
| 8 | `JwtConfig.jwtEncoder:52` | null 반환 | 인코더 빈이 없음(로그인 불가). PIT 측정 한계 | 같음 |
| 9 | `JwtConfig.validator:68` | `setClock` 호출 지움 | 만료를 주입한 시계가 아닌 시스템 시계로 판정. PIT 측정 한계 | R6 `validatorJudgesExpiryWithInjectedClock`(단위) |
| 10 | `JwtConfig.validator:69` | `setAllowEmptyExpiryClaim(false)` 호출 지움 | exp 없는 토큰을 받음. PIT 측정 한계 | R6 `validatorRejectsMissingExpiryAndNonUserIdSubject`(단위) |
| 11 | `JwtConfig.validator:71` | null 반환 | 검증기가 없음. PIT 측정 한계 | R6 `validatorJudgesExpiryWithInjectedClock`(단위) |
| 12 | `Hold.<init>:76` | null 좌석 id 검사를 늘 거짓 | null이 든 좌석 묶음으로 선점을 만듦. 0석·5석·중복은 테스트가 있었고 null은 없었다 | `holdRejectsNullSeatId`(단위) |
| 13 | `SeatAvailability.apiStatus:36` | 'HELD인데 선점이 없음' 검사를 늘 거짓 | 메서드 설명이 약속한 `IllegalArgumentException` 대신 NPE. 이 검사를 부르는 테스트가 없었다 | R1 `heldSeatWithoutHolderIsNotJudged`(단위) |
| 14 | `StoredPaymentResponses.respond:54` | 저장한 상태와 code의 일치 검사를 늘 거짓 | 상태 409에 code PAYMENT_FAILED처럼 어긋난 저장값으로 다른 응답(402)을 만듦. 처음 응답과 '같은 결과'(R5)가 깨진다 | R5 `storedStatusMustMatchCode`(단위) |
| 15 | `IdempotencyRecord.complete:64` | '이미 채운 응답' 검사를 늘 거짓 | 같은 기록의 응답을 두 번 채워 처음 응답을 덮어씀 | R5 `responseIsCompletedOnce`(단위) |

6~11은 '테스트 빈틈'이자 'PIT 측정 한계'다. 이 동작은 원래 통합 테스트(`AuthenticationTest`)가 지키고 있었다(아래 손으로 넣은 변이 J1~J6).
빈 생성 메서드를 스프링 없이 직접 불러 확인하는 단위 테스트를 더해, PIT가 재고 `verify.sh quick`에서도 토큰 검증 규칙을 확인하게 했다.

### 동등 변이: 6개

| # | 위치 | 변이 | 같은 이유 |
|---|---|---|---|
| 16 | `CurrentUserIds.from:20` | `jwt.getSubject() != null` 검사를 지움 | sub가 null이면 `Long.parseLong(null)`이 `NumberFormatException`을 던지고, 바로 아래에서 같은 401 AUTH_REQUIRED로 바꾼다. sub가 없는 토큰은 그 전에 토큰 검증에서 거부된다 |
| 17 | `HoldRepository.findHoldersOf:49` | 'holdId가 없으면 빈 맵' 분기를 늘 거짓 | `findAllById(빈 집합)`이 빈 목록을 돌려줘 결과가 같은 빈 맵이다. 쿼리 하나를 아끼는 분기다 |
| 18 | `IdempotencyRecord.response:56` | `responseStatus == null` 검사를 지움 | 상태가 없으면 본문도 없다(`complete`가 둘을 함께 채우고, DB `ck_idempotency_records_response_together`가 막는다). 그래서 다음 검사(본문 null)가 같은 예외를 던진다 |
| 19 | 같은 줄 | `responseBody == null` 검사를 지움 | 같은 불변식. 상태가 있는데 본문이 없는 기록은 만들 수 없다(`StoredResponse`가 본문 null을 거부) |
| 20 | `Seat.sell:90` | `status != HELD` 검사를 지움 | HELD가 아니면 holdId가 없다(DB `ck_seats_hold_id_only_when_held`, `Seat`의 전이 메서드). 다음 검사(holdId null)가 같은 예외를 던진다 |
| 21 | 같은 줄 | `holdId == null` 검사를 지움 | HELD면 holdId가 있다(같은 제약). HELD가 아니면 앞 검사가 먼저 막는다. 남는 경우는 불변식이 막는 상태(HELD인데 holdId 없음)뿐이다 |

### 죽은 코드: 2개(덮이지 않음)

| # | 위치 | 변이 | 이유 |
|---|---|---|---|
| 22 | `PaymentService.pay:101`(람다) | `orElseThrow`의 예외 생성을 null 반환 | 방금 잡은(또는 이미 있던) 멱등 기록을 같은 트랜잭션에서 다시 읽지 못하는 경우다. `claim`이 넣었거나 커밋된 기록과 부딪혔을 때만 이 줄에 오므로 정상 흐름에서 도달할 수 없다 |
| 23 | `Seat.getPerformance:102` | null 반환 | 운영 코드와 테스트 어디에서도 부르지 않는다. 지우는 것은 운영 코드 변경이라 이 Issue 범위 밖이다 |

### 검증할 가치가 낮음: 2개

| # | 위치 | 변이 | 이유 |
|---|---|---|---|
| 24 | `IdempotencyKeyHeader.parse:56` | 헤더 개수 오류의 `detail` 문구 고르기를 늘 거짓 | 헤더가 없을 때와 두 개일 때의 `detail` 문구만 바뀐다. 상태(400)와 code(INVALID_INPUT)는 같다. 명세는 `detail`을 '바뀔 수 있는 설명'으로 두고 화면 문구는 `code`로 고른다(spec 3장) |
| 25 | 같은 줄 | 같은 분기를 늘 참 | 같음 |

### PIT 측정 한계: 스프링 시작 코드 4개(+ 위 6~11)

| # | 위치 | 변이 |
|---|---|---|
| 26 | `SecurityConfig.securityFilterChain:51`(람다) | e2e 프로필 분기를 늘 거짓(e2e에서도 초기화 경로를 막음) |
| 27 | 같은 줄 | 같은 분기를 늘 참(e2e가 아니어도 초기화 경로를 엶) |
| 28 | `SecurityConfig.securityFilterChain:60` | 필터 체인 빈을 null로 |
| 29 | `SecurityConfig.passwordEncoder:65` | 비밀번호 인코더 빈을 null로 |

스프링 테스트 컨텍스트는 한 번 만들면 다시 쓴다. 그래서 빈을 만드는 코드의 줄은 컨텍스트를 처음 만든 테스트만 덮은 것으로 잡히고, PIT는 그 테스트만 돌린다.
대상을 `JwtConfig`·`SecurityConfig`로 좁히고 모든 테스트 결과를 남기게(`fullMutationMatrix`) 잠시 바꿔 보니, 이 변이들에 돈 테스트는 컨텍스트 5종을 처음 만든
`HealthCheckTest`, `ErrorResponseTest`, `ErrorPageTest`, `CurrentUserIdTest`, `E2eProfileAuthTest`의 첫 테스트 5개뿐이었다. 모두 토큰 검증이나 경로 보호를 확인하지 않는다.

같은 변이를 손으로 넣고 인증 통합 테스트(`AuthenticationTest`, `E2eProfileAuthTest`, `TestDataReset*`, `LoginApiTest`)를 돌렸다. 10개 모두 테스트가 실패했다(운영 코드는 매번 되돌렸다).

| | 손으로 넣은 변이 | 실패한 테스트(일부) |
|---|---|---|
| S1 | e2e 분기를 늘 참 | `AuthenticationTest` 'e2e가 아닌 프로필에서 POST /api/test-support/reset은 토큰 없이 401' |
| S2 | e2e 분기를 늘 거짓 | `E2eProfileAuthTest` 'e2e 프로필에서는 reset을 토큰 없이 부를 수 있다', `TestDataResetApiTest` 여러 개 |
| S3 | `securityFilterChain`이 null | `E2eProfileAuthTest` 시드 계정 로그인 3개 등 |
| S4 | `passwordEncoder`가 null | `AuthenticationTest` R6 로그인 흐름, 잘못된 토큰 등 |
| J1 | `setJwtValidator` 호출 삭제 | `AuthenticationTest` R6 'exp 없음' 등 |
| J2 | `jwtDecoder`가 null | `AuthenticationTest` R6 여러 개 |
| J3 | `jwtEncoder`가 null | `AuthenticationTest` R6 여러 개 |
| J4 | `setClock` 호출 삭제 | `AuthenticationTest` R6 'exp 1초 전과 같은 시각은 허용, 1초 뒤는 401' 등 |
| J5 | `setAllowEmptyExpiryClaim` 호출 삭제 | `AuthenticationTest` R6 'exp 없음' |
| J6 | `validator`가 null | `AuthenticationTest` R6 여러 개 |

`SecurityConfig`는 `HttpSecurity`가 스프링 컨텍스트 안에서만 만들어져 단위 테스트로 확인할 수 없어, 4개를 살아남은 채로 둔다. PIT로는 이 클래스의 퇴보를 잡지 못하므로
R6 통합 테스트와 규칙 코드 되돌림 확인이 지킨다. 재측정 뒤 남은 14개는 #16~#29다.

## 더한 테스트

| 테스트(파일) | 계층 | 죽인 변이 | 잡는 결함 |
|---|---|---|---|
| R6: 401의 WWW-Authenticate는 토큰이 없으면 Bearer이고, 토큰이 잘못됐으면 Bearer error="invalid_token"이다(`AuthenticationTest`) | 통합 | #1, #2 | 토큰이 없을 때와 잘못됐을 때의 `WWW-Authenticate` 값이 뒤바뀌거나 하나로 합쳐짐 |
| R6: sub가 1 이상의 long을 부호·앞자리 0 없이 쓴 값이면 사용자 id로 받는다(`JwtConfigTest`) | 단위 | - | 정상 sub(1, 42, long 최댓값)를 거부 |
| R6: sub가 없거나 0·음수·부호·앞자리 0·공백·소수·문자·long 범위 밖이면 사용자 id로 받지 않는다(`JwtConfigTest`) | 단위 | #3, #4 | 정규식 검사가 빠져 `0`·`-1`·`+1`·`01`을 사용자 id로 받음, sub가 없으면 NPE |
| 공백뿐인 서명 키는 32바이트 이상이어도 키를 만들지 않는다(`JwtConfigTest`) | 단위 | #5 | 공백 검사가 빠져 공백 32바이트 키로 서명 |
| R6: 토큰 검증기는 주입한 시계로 만료를 판정한다(`JwtConfigTest`) | 단위 | #9, #11 | 검증기가 시스템 시계를 씀, 검증기가 없음 |
| R6: 토큰 검증기는 exp가 없는 토큰과 sub가 사용자 id가 아닌 토큰을 거부한다(`JwtConfigTest`) | 단위 | #10 | exp 없는 토큰을 받음 |
| R6: 같은 키로 만든 인코더·디코더에서 정상 토큰은 통과하고, 서명이 맞아도 검증 규칙에 걸리면 거부한다(`JwtConfigTest`) | 단위 | #6, #7, #8 | 디코더에 검증기를 붙이지 않음, 인코더·디코더 빈이 없음 |
| 좌석 id에 null이 있으면 선점을 만들지 않는다(`HoldTest`) | 단위 | #12 | null 좌석 id 검사가 빠짐 |
| R1: HELD 좌석을 좌석을 잡은 선점 없이 판정하면 선점할 수 있다고 보지 않고 IllegalArgumentException을 던진다(`SeatAvailabilityTest`) | 단위 | #13 | 선점 없이 HELD 좌석을 판정하는 실수를 알리는 검사가 빠짐 |
| R5: 저장한 응답의 상태 코드와 code가 맞지 않으면 다른 응답을 만들지 않고 IllegalStateException을 던진다(`StoredPaymentResponsesTest`, 새 파일) | 단위 | #14 | 어긋난 저장값으로 처음과 다른 응답을 돌려줌 |
| R5: 멱등 기록의 응답은 한 번만 채운다(`IdempotencyRecordTest`, 새 파일) | 단위 | #15 | 같은 키의 응답을 덮어씀 |

새 테스트는 모두 지금 코드에서 통과하고, 재측정에서 PIT가 기록한 '죽인 테스트'가 위 표의 테스트다(변이마다 `mutations.xml`의 `killingTest`로 확인).
운영 코드 결함은 없었다. 살아남은 변이는 모두 테스트 빈틈이거나 위 분류의 것이었다.

## PIT가 바꾸지 못하는 보장과 그것을 지키는 테스트

PIT는 컴파일된 바이트코드만 바꾼다. 다음은 바이트코드 밖(애노테이션, JPQL·SQL 문자열, DB 제약·마이그레이션)에 있거나, 고른 연산자 묶음(`STRONGER`)이 바꾸지 않는 곳이다.
이 보장은 동시성 테스트, 스키마 테스트, 규칙 코드 되돌림 확인(진행 기록에 남은 것)이 지킨다.

| 보장 | 있는 곳 | PIT가 못 바꾸는 이유 | 지키는 테스트 | 되돌림 확인 |
|---|---|---|---|---|
| R1 같은 좌석은 한 명만: 좌석 행 잠금 | `SeatRepository.lockByPerformanceIdAndIdInOrderById`의 `@Lock(PESSIMISTIC_WRITE)` | 애노테이션 | `HoldConcurrencyTest` R1 3개(같은 좌석 10개, 겹치는 묶음, 반대 순서 묶음 50회) | #41: `@Lock`을 지우자 같은 좌석 10개 중 201이 5개 |
| R1 교착 상태 없음: id 오름차순 잠금 | 같은 쿼리의 `order by s.id asc` | JPQL 문자열 | `HoldConcurrencyTest` '반대 순서 묶음 50회', `PaymentConcurrencyTest` '결제와 다른 사용자의 같은 좌석 선점(반대 순서)' | #41: 요청 순서대로 한 좌석씩 잠그자 1회째에 500(`deadlock detected`). #55: id 내림차순으로 바꾸자 500 |
| R2 같은 사용자 요청을 한 줄로: 사용자 행 잠금 | `UserRepository.lockById`의 `FOR NO KEY UPDATE`와 그 호출 | SQL 문자열. 호출은 반환값이 있어(`Optional`) `STRONGER`의 void 호출 지우기 대상이 아니다 | `HoldConcurrencyTest` R2 '같은 사용자 3석 묶음 두 개 동시', `PaymentConcurrencyTest` R2 '결제가 만료 시각을 넘겨 끝나는 동안 4석 선점' | #41: 지우자 1회째에 `[201, 201]`(6석). #55: 결제 쪽을 지우자 R2·R4 동시성 실패 |
| R4 결제 중 만료돼도 좌석을 뺏기지 않음, 결제·해제 한 줄로: 선점 행·좌석 잠금 | `HoldRepository.lockById`의 `@Lock`, 좌석 잠금 쿼리 | 애노테이션 | `PaymentConcurrencyTest` R4 2개, '같은 선점에 결제와 해제가 동시에', '같은 선점을 다른 키로 동시에 결제' | #55: 선점 행·좌석 잠금을 일반 조회로 바꾸자 각각 실패 |
| R5 같은 키는 하나만: 멱등 기록 유니크 제약과 `ON CONFLICT DO NOTHING` | `V5` `uk_idempotency_records_user_key`, `IdempotencyRecordRepository.claim`의 SQL | 마이그레이션, SQL 문자열 | `PaymentConcurrencyTest` R5 2개(같은 키 5개 동시, 먼저 요청이 500), `IdempotencyRecordSchemaTest` R5 유니크 | #56: (사용자, 키) 유니크 제약을 지우자 `ON CONFLICT`가 맞는 제약을 찾지 못해 R5 API 테스트 11개와 스키마 테스트가 실패. 사용자 행 잠금을 지우고 기록을 처리 끝에 저장하자 같은 키 동시 5개가 `[201, 500, 500, 500, 500]` |
| 한 선점에 예매 하나, 한 좌석에 예매 하나 | `V4` `uk_reservations_hold`, `uk_reservation_seats_seat` | 마이그레이션 | `ReservationSchemaTest` 2개, `PaymentConcurrencyTest` '같은 선점을 다른 키로 동시에 결제하면 예매는 하나' | 스키마 테스트가 제약을 직접 확인 |
| HELD일 때만 holdId, 응답 상태·본문은 함께 | `V3` `ck_seats_hold_id_only_when_held`, `V5` `ck_idempotency_records_response_together` | 마이그레이션 | `SeatSchemaTest`, `IdempotencyRecordSchemaTest` | 스키마 테스트가 제약을 직접 확인. 위 동등 변이 #18~#21의 근거 |
| 선점은 한 트랜잭션: 전부 잡거나 하나도 잡지 않음 | `HoldService`·`PaymentService`의 `@Transactional` | 애노테이션 | `HoldApiTest` R1 '묶음 중 한 좌석이라도 이미 선점됐으면 409이고 나머지 좌석도 선점되지 않는다', `PaymentIdempotencyApiTest` R5 '500은 저장하지 않음(되돌려짐)' | |
| R2 한도 4석, R3 유효 5분 | `HoldLimit.MAX_SEATS_PER_PERFORMANCE = 4`, `Hold.VALIDITY = Duration.ofMinutes(5)` | 4는 컴파일할 때 쓰는 곳에 박히는 상수이고, 5분은 정적 초기화다. `STRONGER`에는 상수 바꾸기 연산자가 없고 PIT는 정적 초기화 변이를 만들지 않는다 | `HoldLimitTest` R2 경계, `HoldTest` R3 '만료 시각은 만든 시각 + 5분', `HoldApiTest` R2·R3 경계 | #41: 한도를 5석으로 바꾸자 R2 4개 실패. #67: CI에서 4→5로 바꾸자 백엔드·프론트엔드 R2 테스트 실패 |
| R6 인증이 필요한 경로 | `SecurityConfig`의 경로 문자열(`requestMatchers`), `@CurrentUserId`의 SpEL | 문자열, 애노테이션 | `AuthenticationTest` R6(토큰 없이 선점·결제 401, 공개 경로, 메서드), `CurrentUserIdTest` | 위 S1~S4, #27 |
| 요청 검증(400) | `CreateHoldRequest`·`PayRequest`의 `@Positive`, `@NotEmpty`, `@UniqueElements` | 애노테이션 | `HoldApiTest`·`PaymentApiTest`의 400 테스트 | 규칙 밖 명세 동작 |

## 다시 잴 때(7단계)

1. `main`에서 수동 실행 워크플로 `Mutation`을 돌린다(Actions 화면, 또는 `gh workflow run mutation.yml --ref main`). 로컬에서는 `./scripts/verify.sh mutation`.
2. 산출물 `mutation-report-<시도>`의 `mutations.xml`로 위 '결과 요약'·'클래스별' 표를 다시 만들고, 이 문서에 날짜와 함께 이어 적는다.
3. 남은 14개(#16~#29)와 같은지 비교한다. 새로 살아남은 변이는 위 다섯 갈래로 다시 나눈다.
4. 스프링 시작 코드는 손으로 넣은 변이 S1~S4, J1~J6을 다시 넣어 확인한다.
5. 측정 대상·연산자·스레드를 바꿨다면 점수를 그대로 비교하지 않는다(결정 0200, 0201).
