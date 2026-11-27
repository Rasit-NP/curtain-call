# 0201. 뮤테이션 측정 대상: R1~R6을 판정하거나 규칙의 오류 코드를 내는 15개 클래스. 컨트롤러·DTO·조회·공통 오류 처리·모의 결제·로그인은 뺀다

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #69 '에이전트가 정하는 것': 측정 대상 '규칙 코드'의 클래스 범위와 근거)
- 관련: Issue [#69](https://github.com/Rasit-NP/curtain-call/issues/69), 결정 [0200](0200-pit-setup.md)(도입 방식), [0204](0204-pit-survivor-classification.md)(분류)

## 결정

측정 대상은 아래 15개 클래스다(`backend/build.gradle`의 `pitest.targetClasses`). 기준은 '이 클래스의 코드가 R1~R6의 허용·거부를 정하거나, 규칙의 오류 코드를 낸다'이다.

| 규칙 | 클래스 | 하는 일 |
|---|---|---|
| R1·R2·R3 | `hold.application.HoldService` | 선점 생성·조회·해제, 404 → 한도 → 좌석 판정 순서, 잠금 순서 |
| R2·R3·R4 | `hold.domain.Hold` | 만료 시각(+5분), 유효 판정(`isValidAt`), 주인·상태(`isOpenFor`), 1~4석 묶음, 해제·결제 전이 |
| R2 | `hold.domain.HoldLimit` | 보유 좌석 수(유효한 선점 + 예매), 4석 한도 |
| R1·R3 | `hold.domain.SeatAvailability` | 좌석의 API 상태(만료 반영), 선점 가능 판정 |
| R1 | `hold.domain.HoldRepository` | 좌석을 잡은 선점 찾기(`findHoldersOf`, 인터페이스의 기본 메서드) |
| R1·R4 | `performance.domain.Seat` | 좌석 상태 전이(선점, 해제, 판매) |
| R4·R5 | `payment.application.PaymentService` | 멱등 키 잡기·재사용, 만료 판정(409 HOLD_EXPIRED), 결제·예매 |
| R5 | `payment.domain.IdempotencyRecord` | 같은 키·다른 선점 판정, 저장한 응답 |
| R5 | `payment.api.IdempotencyKeyHeader` | `Idempotency-Key` 헤더 검증(없으면 400) |
| R5 | `payment.api.StoredPaymentResponses` | 저장한 응답과 결제 응답 사이 변환(같은 결과를 돌려준다) |
| R2 | `reservation.domain.Reservation` | 예매 좌석(보유 좌석 수의 예매 몫) |
| R6 | `auth.api.CurrentUserIds` | 인증 주체 → 사용자 id, 아니면 401 AUTH_REQUIRED |
| R6 | `auth.infra.AuthRequiredEntryPoint` | 토큰이 없거나 잘못되면 401 AUTH_REQUIRED |
| R6 | `auth.infra.JwtConfig` | 토큰 검증(서명 키, 만료, sub) |
| R6 | `auth.infra.SecurityConfig` | 인증이 필요한 경로와 공개 경로 |

뺀 클래스와 이유:

- 컨트롤러(`HoldController`, `PaymentController`, `ReservationController` 등): 서비스 호출과 상태 코드 애노테이션뿐이다. 판정이 없다.
- 요청·응답 DTO와 결과 값(`HoldResponse`, `PaymentResponse`, `CreateHoldRequest`, `PayRequest`, `HoldDetail`, `ReservationDetail`, `PaymentOutcome`): 값을 옮긴다.
  요청 검증은 애노테이션(`@Positive`, `@UniqueElements`)이라 PIT가 바꾸지 못한다.
- 조회(`PerformanceQueryService`, `ReservationQueryService`): 좌석의 API 상태는 대상인 `SeatAvailability`에 맡긴다.
- 저장소 인터페이스의 쿼리 메서드: 애노테이션의 JPQL·SQL 문자열이라 바이트코드가 없다(`HoldRepository`의 기본 메서드만 대상).
- `Payment`(결제 기록), `Performance`·`Venue`·`VenueSeat`·`User`(엔티티): 값을 담는다. 결제 기록의 상태(SUCCEEDED·FAILED)는 `PaymentService`가 정한다.
- 공통 오류 처리(`GlobalExceptionHandler`, `ErrorCode`, `ProblemErrorController`, `ProblemDetailErrorReportValve`, `StrictLongEditor` 등): 모든 기능이 함께 쓰는 오류 형식이다.
  규칙의 오류 코드를 어느 상황에 낼지는 대상 클래스가 정한다. 오류 형식은 `ErrorResponseTest`, `ErrorCodeTest` 등이 본다.
- `FakePaymentGateway`: 실제 PG 대신 쓰는 모의 결제다. 운영 코드에 있지만 테스트 조작용이다.
- 로그인(`LoginService`, `AccessTokenIssuer`, `AuthController`): 토큰을 발급한다. R6은 '로그인하지 않은 사용자는 선점·결제를 할 수 없다'이므로 토큰을 검사하는 쪽(위 R6 클래스)만 잰다.
  로그인 실패(`INVALID_CREDENTIALS`)는 규칙 밖 명세 동작이다.
- E2E 지원(`testsupport`), 시계 설정(`ClockConfig`), 애플리케이션 시작(`CurtainCallApplication`): 규칙과 관계없다.

## 근거

- Issue #69는 '규칙 코드(R1~R6)'의 점수를 재기로 했다. 규칙과 관계없는 코드를 넣으면 점수가 규칙 테스트의 품질을 덜 보여 준다.
- 클래스 단위로 고르면 7단계에서 같은 대상을 다시 잴 수 있다. 패키지 단위(`hold.*` 등)로 고르면 DTO·컨트롤러가 섞이고, 파일을 더하기만 해도 대상이 바뀐다.
- `SecurityConfig`는 PIT가 제대로 재지 못하는 스프링 시작 코드뿐이지만 뺀 것이 아니라 대상에 둔다. R6의 경로 보호가 여기 있고, 재지 못한다는 것도 측정 결과로 남긴다(결정 0204).

## 고려했지만 고르지 않은 것

- 백엔드 운영 코드 전체: 변이가 크게 늘고, 대부분 DTO·설정·공통 오류 처리라 규칙 테스트와 관계없는 '살아남음'을 분류하느라 시간이 든다.
- 패키지 단위(`com.curtaincall.hold.*` 등): 위 근거.
- 로그인 포함: 위 '뺀 클래스'.

## 적용과 남은 점

- 대상 클래스를 바꾸면 기준선을 그대로 비교할 수 없다. 바꿀 때는 새 결정을 남기고 다시 잰다.
- 3단계(회원가입)가 `auth` 패키지에 클래스를 더하면, 그 클래스가 위 기준(R6 판정)에 들면 대상에 넣을지 그 Issue에서 정한다.
