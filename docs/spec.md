# 커튼콜 명세

커튼콜의 도메인 모델, API, 화면 동작을 정한다. 규칙(R1~R6)과 오류 코드 표의 원본은 루트 `CLAUDE.md`이고,
이 문서는 그것을 구현할 수 있는 수준으로 구체화한다. 이 문서와 `CLAUDE.md`가 어긋나거나 이 문서에 없는 규칙이
필요하면 구현하지 말고 사람에게 넘긴다.

- 규칙별 테스트 계획: [`test-matrix.md`](test-matrix.md)
- 정한 경위: [9. 결정 기록](#9-결정-기록)

## 1. 예매 흐름

CGV 예매 방식을 따른다.

1. 공연 목록에서 공연을 고른다.
2. 좌석 화면에서 좌석을 1~4석 고른다. 고르는 동안에는 서버에 아무것도 잡지 않는다.
3. '좌석 선택 완료'를 누르면 고른 좌석을 한 번에 선점한다. 선점 1건은 좌석 묶음이고, 전부 잡히거나 하나도 잡히지 않는다.
4. 결제 화면에서 선점 후 5분 안에 결제한다. 결제가 승인되면 예매가 생긴다.
5. 결제가 승인되기 전에 앱 안의 다른 화면으로 가면 선점을 해제한다(결제가 거절된 뒤에 가도 같다). 창을 닫거나 새로고침하면 해제하지 않고, 5분이 지나면 만료된다.

## 2. 도메인 모델

### 2.1 엔티티

| 엔티티 | 주요 속성 | 설명 |
|---|---|---|
| 사용자(User) | `id`, `username`, `passwordHash` | `username`은 고유하다. 회원가입은 없고 시드로만 만든다. |
| 공연장(Venue) | `id`, `name`, 좌석 배치 | `name`은 고유하고 공연 응답의 `venue`로 나간다. 좌석 배치는 공연장마다 다르고, 통로와 빈자리까지 표현한다. |
| 공연장 좌석 배치(VenueSeat) | `id`, `venueId`, `row`, `number`, `gridRow`, `gridColumn` | 공연장 배치의 좌석 하나다. 같은 공연장에서 (`row`, `number`)는 고유하고 (`gridRow`, `gridColumn`)은 겹치지 않는다. |
| 공연(Performance) | `id`, `title`, `venueId`, `startsAt`, `price` | 날짜와 시간이 정해진 한 회차다. 한 공연장에서 열린다. `price`는 1석 가격(원)이고 전석이 같다. |
| 좌석(Seat) | `id`, `performanceId`, `row`, `number`, `gridRow`, `gridColumn`, `status`, `holdId` | 공연마다 있고, 공연을 만들 때 그 공연장 배치를 복사한다. 그래서 같은 공연장의 공연은 같은 배치다. (`performanceId`, `row`, `number`)는 고유하고 (`performanceId`, `gridRow`, `gridColumn`)은 겹치지 않는다. 표시 이름은 `{row}-{number}`(예: `A-3`)다. 좌석 위치는 아래 '좌석 위치'를 따른다. `status`는 `AVAILABLE`, `HELD`, `SOLD`. `holdId`는 지금 이 좌석을 잡은 선점이고 `HELD`일 때만 있다. 만료된 선점의 좌석을 새 선점이 덮어쓰면 한 좌석이 두 선점의 좌석 목록에 들어 있게 되므로, 좌석을 잡은 선점은 이 값으로 판단한다. |
| 선점(Hold) | `id`, `userId`, `performanceId`, 좌석 1~4개, `createdAt`, `expiresAt`, `status` | 한 공연의 좌석 묶음을 결제 전에 잡아 둔다. `expiresAt`은 `createdAt` + 5분(R3)이고 늘리지 않는다. `status`는 `ACTIVE`, `RELEASED`, `PAID`. |
| 예매(Reservation) | `id`, `userId`, `performanceId`, 좌석, `amount`, `reservedAt` | 결제가 승인된 선점으로 만든다. 취소 기능은 없다. |
| 결제(Payment) | `id`, `userId`, `holdId`, `amount`, `status`, `reservationId` | 모의 결제를 부를 때마다 하나 생긴다. `status`는 `SUCCEEDED`, `FAILED`. 승인되면 `reservationId`가 있다. |
| 멱등 기록(IdempotencyRecord) | `userId`, `idempotencyKey`, `holdId`, 저장한 응답, `createdAt` | 검증을 통과한 결제 요청마다 하나 생긴다. (`userId`, `idempotencyKey`)는 유니크다(R5). 같은 키로 다시 요청하면 돌려줄 응답(상태 코드와 본문)을 저장한다(6.7.1). |

**좌석 위치**: 좌석 배치는 격자다. `gridRow`·`gridColumn`은 1부터 세는 정수이고, 좌석이 격자의 몇 번째 줄·칸에 있는지를 나타낸다.
격자 1번째 줄이 무대 쪽이다. 좌석이 없는 칸이 통로와 빈자리다. `row`·`number`는 표시 이름이라 위치와 다를 수 있다(예: 통로 오른쪽 `A-5`가 격자 6번째 칸).
격자 크기는 따로 두지 않고, 좌석 위치의 최댓값으로 정한다.

### 2.2 판정 규칙

- **유효한 선점**: `status`가 `ACTIVE`이고 현재 시각 < `expiresAt`.
- **만료된 선점**: `status`가 `ACTIVE`이고 현재 시각 ≥ `expiresAt`(R3). 만료는 저장하지 않는다.
  선점·조회·결제·해제 때 현재 시각과 비교해 판정하고, 스케줄러를 두지 않는다.
- **좌석의 API 상태**: 저장 상태가 `HELD`라도 그 좌석을 잡은 선점(좌석의 `holdId`)이 만료됐으면 `AVAILABLE`로 본다.
  새 선점은 이런 좌석을 덮어쓸 수 있다.

  | 저장 상태 | 좌석을 잡은 선점(`holdId`) | API 상태 |
  |---|---|---|
  | `AVAILABLE` | 없음 | `AVAILABLE` |
  | `HELD` | 유효 | `HELD` |
  | `HELD` | 만료 | `AVAILABLE` |
  | `SOLD` | 결제됨 | `SOLD` |

- **보유 좌석 수(R2)**: 같은 사용자·같은 공연에서 유효한 선점의 좌석 수 + 예매의 좌석 수.
  R2의 "만료되지 않은 선점"은 유효한 선점을 뜻한다. 만료·해제된 선점은 세지 않고, 결제된 선점의 좌석은 예매로 한 번만 센다.
- **결제 금액**: 공연 `price` × 선점 좌석 수.
- 현재 시각은 주입한 `Clock`으로만 얻는다.

### 2.3 상태 전이

| 일 | 선점 | 좌석 | 그 밖 |
|---|---|---|---|
| 선점 성공 | `ACTIVE`로 생성 | 묶음의 좌석 모두 `HELD`, `holdId`는 새 선점 | |
| 결제 승인 | `ACTIVE` → `PAID` | `HELD` → `SOLD` | 예매 생성, 결제 `SUCCEEDED`, 멱등 기록에 `201` 응답 저장 |
| 결제 거절 | `ACTIVE` 그대로(만료 시각도 그대로) | 그대로 | 결제 `FAILED`, 멱등 기록에 `402` 응답 저장 |
| 결제 거부(404, 409) | 그대로 | 그대로 | 멱등 기록에 응답 저장 |
| 해제 | `ACTIVE` → `RELEASED` | `holdId`가 이 선점인 좌석만 `AVAILABLE`(`holdId`를 비움) | |
| 만료 | 저장 상태는 `ACTIVE` 그대로 | 저장 상태는 `HELD` 그대로, API 상태는 `AVAILABLE` | 새 선점이 좌석의 `holdId`를 덮어쓸 수 있다 |

### 2.4 동시성

구현 방법은 `backend/CLAUDE.md`를 따른다(DB로 보장하고 JVM 안의 락은 쓰지 않는다). 이 명세가 요구하는 결과는 다음과 같다.

- R1: 같은 좌석이 들어간 선점 요청이 동시에 와도 그 좌석은 한 선점만 잡는다. 묶음은 전부 성공하거나 전부 실패하고,
  실패한 요청의 좌석은 하나도 잡히지 않는다. 같은 좌석들을 다른 순서로 담은 묶음끼리도 교착 상태(deadlock)가 나서
  500으로 끝나면 안 된다. 요청에 담긴 순서와 상관없이 좌석은 언제나 `id` 오름차순으로 잠근다.
- R2: 같은 사용자의 선점 요청이 동시에 와도 보유 좌석 수가 4를 넘지 않는다. 예: 사용자 행을 잠가 같은 사용자의 선점 요청을 한 줄로 세운다.
- R4: 결제를 처리하는 동안 그 선점의 좌석을 다른 선점이 가져가지 못한다. 좌석을 잠근 뒤 만료를 판정하고,
  판정을 통과했으면 모의 결제가 늦게 끝나도 결제를 마친다.
- R5: 같은 사용자의 같은 키 결제 요청은 하나씩 처리한다. 나중 요청은 먼저 온 요청이 끝날 때까지 기다렸다가 6.7.1대로 응답을 받는다.
- 결제와 해제는 선점 행을 먼저 잠그고 그다음 좌석을 `id` 오름차순으로 잠근다. 그래서 같은 선점에 결제, 해제, 다른 키의 결제가
  동시에 와도 하나씩 처리된다. 먼저 처리된 쪽이 결제 승인이나 해제였으면 나중 요청은 `404 NOT_FOUND`를 받는다.
  먼저 처리된 결제가 거절(`402`)이나 만료(`409`)로 끝났으면 선점이 그대로이므로 나중 요청은 보통처럼 처리된다.

### 2.5 시드 데이터

`local`, `e2e` 프로필의 시드는 E2E를 돌릴 수 있게 최소한 다음을 넣는다.

- 공연 2개 이상, 공연마다 빈 좌석 5석 이상(시나리오 3은 5번째 좌석이 필요하다)
- 배치가 서로 다른 공연장, 통로나 빈자리가 있는 배치
- 시연용 계정 1개(README에 안내)와 E2E용 계정 2개(시나리오 2의 '다른 사용자')

공연장, 배치, 공연, 일시, 가격 같은 시드 값은 샘플 데이터다. 코드와 테스트는 시드 값에 기대지 않는다(테스트는 데이터를 직접 만들고,
시드 테스트는 위 조건만 확인한다). E2E도 공연·좌석 ID나 제목을 가정하지 않고 공연 목록·상세 API로 찾는다(6.9).

## 3. API 공통

- 경로는 `/api`로 시작하고 요청·응답 본문은 JSON(UTF-8)이다.
- 시각은 ISO 8601 UTC 문자열(`Instant`, 예: `2026-12-20T10:00:00Z`)이다. 화면에는 한국 시간으로 보여 준다.
- 금액은 원 단위 정수, ID는 정수다.
- 로그인이 필요한 API는 `Authorization: Bearer <accessToken>` 헤더를 받는다.
- 공개 API에는 토큰을 붙이지 않는다. 공개 API라도 잘못되거나 만료된 토큰을 붙이면 401이 나기 때문이다(3.1).

### 3.1 오류 응답

`ProblemDetail`(RFC 9457) 형식에 `code`를 더한다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 선점되거나 판매된 좌석입니다.",
  "instance": "/api/holds",
  "code": "SEAT_ALREADY_HELD"
}
```

- 클라이언트는 `code`만 보고 판단한다. `type`, `title`, `instance`는 있을 수 있지만 쓰지 않는다.
  `detail`은 사람이 읽는 설명이고 바뀔 수 있으므로, 화면 문구는 `code`로 고른다(7.6).
- 엔드포인트마다 오류 표에 적은 순서대로 검사해 처음 걸린 오류 하나만 돌려준다.
- 모든 엔드포인트에 공통으로 적용한다.
  - `400 INVALID_INPUT`: 본문이 올바른 JSON이 아님, 경로·쿼리 값의 형식이 틀림(예: 숫자 자리에 문자), 필드 검증 실패,
    지원하지 않는 메서드나 `Content-Type`. 마지막 두 경우는 Spring 기본값(405, 415) 대신 400으로 응답한다. 표에 있는 코드만 쓰기 위해서다.
  - `401 AUTH_REQUIRED`: 로그인이 필요한 API에 토큰이 없거나, 서명·형식이 잘못됐거나, 만료됨(R6).
    공개 API라도 잘못되거나 만료된 토큰을 붙이면 401이다(Spring Security 리소스 서버의 기본 동작).
  - `404 NOT_FOUND`: 없는 경로. 인증이 필요한 경로라면 토큰 검사(401)가 먼저다.
  - `500 INTERNAL_ERROR`: 그 밖의 예상하지 못한 오류. 본문에 스택 트레이스를 넣지 않는다.

### 3.2 액세스 토큰

- 로그인 응답의 `accessToken`은 HS256 JWT다. `sub`는 사용자 `id`(문자열), `exp`는 발급 시각 + 1시간이다. 갱신 토큰은 없다.
- 만료는 주입한 `Clock`으로 판정하고 허용 오차(clock skew)는 0으로 둔다. `exp`가 지난 토큰은 `401 AUTH_REQUIRED`다.
- `exp`와 같은 시각의 토큰은 유효하다(Spring Security `JwtTimestampValidator`의 동작). 같은 시각이면 만료로 보는 선점(R3)과 다르다.

### 3.3 401 처리(프론트엔드)

로그인 실패와 토큰 문제는 둘 다 401이므로 반드시 `code`로 구분한다.
"401이면 토큰을 지우고 로그인 화면으로 보낸다"는 `AUTH_REQUIRED`에만 적용한다.

| code | 처리 |
|---|---|
| `AUTH_REQUIRED` | 저장된 토큰을 지우고 로그인 화면으로 보낸다. 로그인하면 원래 화면으로 돌아간다. |
| `INVALID_CREDENTIALS` | 위 처리를 하지 않는다. 로그인 화면에 머물러 안내 문구만 보여 준다. |

## 4. 엔드포인트 목록

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/auth/login` | 공개 | 로그인 |
| GET | `/api/performances` | 공개 | 공연 목록 |
| GET | `/api/performances/{performanceId}` | 공개 | 공연 상세와 좌석 |
| POST | `/api/holds` | 필요 | 좌석 묶음 선점 |
| GET | `/api/holds/{holdId}` | 필요 | 내 선점 조회 |
| DELETE | `/api/holds/{holdId}` | 필요 | 내 선점 해제 |
| POST | `/api/payments` | 필요 | 결제(예매 확정) |
| GET | `/api/reservations` | 필요 | 내 예매 목록 |
| POST | `/api/test-support/reset` | 공개(`e2e` 프로필에만 있음) | 데이터 초기화 |
| GET | `/actuator/health` | 공개 | 상태 확인 |

인증이 필요한 엔드포인트는 모두 토큰이 없거나 잘못되면 `401 AUTH_REQUIRED`다(R6). 6장 오류 표의 첫 줄이 이것이다.

## 5. 응답 객체

같은 이름의 객체는 모든 응답에서 모양이 같다.

```text
PerformanceSummary { id, title, venue, startsAt, price }
Seat               { id, row, number, gridRow, gridColumn, status }   status: AVAILABLE | HELD | SOLD
SeatRef            { id, row, number }
Hold               { id, status, performance: PerformanceSummary, seats: SeatRef[], amount, expiresAt }
                                                 status: ACTIVE | EXPIRED
Reservation        { id, performance: PerformanceSummary, seats: SeatRef[], amount, reservedAt }
```

- 좌석 목록은 `row`, `number` 오름차순이다. `row`는 문자열 코드 포인트 순서, `number`는 숫자 순서다(`A-2`가 `A-10`보다 앞).
- `Seat.gridRow`·`gridColumn`은 2.1 '좌석 위치'의 격자 위치다. 화면은 이 값을 CSS Grid의 `grid-row`·`grid-column`에 넣어 그린다.
- `Hold.status`는 유효한 선점이면 `ACTIVE`, 만료된 선점이면 `EXPIRED`다. 해제·결제된 선점은 응답으로 나오지 않는다(조회하면 404).

## 6. 엔드포인트

### 6.1 `POST /api/auth/login` 로그인

공개.

요청:

```json
{ "username": "아이디", "password": "비밀번호" }
```

`200 OK`:

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "expiresIn": 3600 }
```

`expiresIn`은 초 단위다.

| 순서 | 상태 | code | 상황 |
|---|---|---|---|
| 1 | 400 | `INVALID_INPUT` | `username`이나 `password`가 없거나, 비었거나, 공백뿐임 |
| 2 | 401 | `INVALID_CREDENTIALS` | 없는 아이디이거나 비밀번호가 틀림. 둘을 구분하지 않는다. |

### 6.2 `GET /api/performances` 공연 목록

공개. `200 OK`: `PerformanceSummary[]`, `startsAt` 오름차순(같으면 `id` 오름차순). 공연이 없으면 `[]`.

```json
[
  { "id": 1, "title": "작은 방의 햄릿", "venue": "대학로 커튼홀", "startsAt": "2026-12-20T10:00:00Z", "price": 30000 }
]
```

### 6.3 `GET /api/performances/{performanceId}` 공연 상세와 좌석

공개. `200 OK`: `PerformanceSummary`에 `seats: Seat[]`를 더한다.

```json
{
  "id": 1,
  "title": "작은 방의 햄릿",
  "venue": "대학로 커튼홀",
  "startsAt": "2026-12-20T10:00:00Z",
  "price": 30000,
  "seats": [
    { "id": 101, "row": "A", "number": 1, "gridRow": 1, "gridColumn": 1, "status": "AVAILABLE" },
    { "id": 102, "row": "A", "number": 2, "gridRow": 1, "gridColumn": 2, "status": "HELD" },
    { "id": 103, "row": "A", "number": 3, "gridRow": 1, "gridColumn": 4, "status": "SOLD" },
    { "id": 104, "row": "A", "number": 4, "gridRow": 1, "gridColumn": 5, "status": "AVAILABLE" }
  ]
}
```

예시의 A줄은 2번과 3번 좌석 사이(격자 3번째 칸)가 통로다.

- `status`는 2.2의 API 상태다(R3 만료 반영). 누가 선점했는지는 알려 주지 않는다.

| 순서 | 상태 | code | 상황 |
|---|---|---|---|
| 1 | 400 | `INVALID_INPUT` | `performanceId`가 정수가 아님(long 범위를 넘는 값 포함, [결정 0050](decisions/0050-performance-detail-id-errors.md)) |
| 2 | 404 | `NOT_FOUND` | 공연이 없음(0이나 음수 포함) |

### 6.4 `POST /api/holds` 좌석 묶음 선점

로그인 필요. 고른 좌석을 한 번에 선점한다.

요청:

```json
{ "performanceId": 1, "seatIds": [101, 104] }
```

`201 Created`: `Hold`. 요청 시각이 `2026-12-01T10:00:00Z`인 경우:

```json
{
  "id": 31,
  "status": "ACTIVE",
  "performance": { "id": 1, "title": "작은 방의 햄릿", "venue": "대학로 커튼홀", "startsAt": "2026-12-20T10:00:00Z", "price": 30000 },
  "seats": [
    { "id": 101, "row": "A", "number": 1 },
    { "id": 104, "row": "A", "number": 4 }
  ],
  "amount": 60000,
  "expiresAt": "2026-12-01T10:05:00Z"
}
```

| 순서 | 상태 | code | 상황 |
|---|---|---|---|
| 1 | 401 | `AUTH_REQUIRED` | 토큰이 없거나 잘못됨(R6) |
| 2 | 400 | `INVALID_INPUT` | `performanceId`가 없거나 양수가 아님. `seatIds`가 없거나 비었거나, 중복이나 양수가 아닌 값이 있음 |
| 3 | 404 | `NOT_FOUND` | 공연이 없거나, `seatIds`에 그 공연의 좌석이 아닌 것이 있음 |
| 4 | 409 | `HOLD_LIMIT_EXCEEDED` | 보유 좌석 수 + 요청 좌석 수 > 4(R2). 5석 이상을 한 번에 요청해도 여기에 걸린다. |
| 5 | 409 | `SEAT_ALREADY_HELD` | 요청 좌석 중 하나라도 유효한 선점이 있거나(내 선점 포함) `SOLD`임(R1) |

- 전부 성공하거나 전부 실패한다. 실패하면 어떤 좌석도 선점되지 않는다.
- 만료된 선점이 잡고 있던 좌석은 선점할 수 있다(R3).
- 같은 공연에 내 유효한 선점이 이미 있어도 새 선점을 막지 않는다. 보유 좌석 수 한도(R2)만 적용한다.
- 이 요청은 멱등하지 않다. 클라이언트는 자동으로 다시 보내지 않는다(다시 보내면 자기 선점과 부딪혀 409가 난다).

### 6.5 `GET /api/holds/{holdId}` 내 선점 조회

로그인 필요. 결제 화면을 새로고침했을 때 선점을 다시 불러온다. `200 OK`: `Hold`. 만료됐으면 `status`가 `EXPIRED`다.

| 순서 | 상태 | code | 상황 |
|---|---|---|---|
| 1 | 401 | `AUTH_REQUIRED` | 토큰이 없거나 잘못됨(R6) |
| 2 | 400 | `INVALID_INPUT` | `holdId`가 정수가 아님 |
| 3 | 404 | `NOT_FOUND` | 선점이 없음, 다른 사용자의 선점, 해제됐거나 결제된 선점 |

### 6.6 `DELETE /api/holds/{holdId}` 내 선점 해제

로그인 필요. 묶음 전체를 해제한다. `204 No Content`.

| 순서 | 상태 | code | 상황 |
|---|---|---|---|
| 1 | 401 | `AUTH_REQUIRED` | 토큰이 없거나 잘못됨(R6) |
| 2 | 400 | `INVALID_INPUT` | `holdId`가 정수가 아님 |
| 3 | 404 | `NOT_FOUND` | 선점이 없음, 다른 사용자의 선점, 이미 해제됐거나 결제된 선점 |

- 만료된 내 선점도 `204`로 해제한다.
- 이 선점이 아직 잡고 있는 좌석만 `AVAILABLE`로 되돌린다. 만료 뒤 다른 사용자가 새로 선점한 좌석은 건드리지 않는다.
- 해제한 선점은 보유 좌석 수(R2)에서 빠진다.

### 6.7 `POST /api/payments` 결제

로그인 필요. 선점 1건을 모의 결제하고, 승인되면 예매를 만든다.

요청 헤더(필수):

```http
Idempotency-Key: 6f1c2e7a-0b5d-4c1e-9a7f-2d3b4c5e6f70
```

요청:

```json
{ "holdId": 31 }
```

`201 Created`:

```json
{
  "paymentId": 9,
  "amount": 60000,
  "reservation": {
    "id": 5,
    "performance": { "id": 1, "title": "작은 방의 햄릿", "venue": "대학로 커튼홀", "startsAt": "2026-12-20T10:00:00Z", "price": 30000 },
    "seats": [
      { "id": 101, "row": "A", "number": 1 },
      { "id": 104, "row": "A", "number": 4 }
    ],
    "amount": 60000,
    "reservedAt": "2026-12-01T10:03:00Z"
  }
}
```

`402 Payment Required`(모의 결제 거절):

```json
{ "status": 402, "code": "PAYMENT_FAILED", "detail": "결제가 승인되지 않았습니다." }
```

| 순서 | 상태 | code | 상황 |
|---|---|---|---|
| 1 | 401 | `AUTH_REQUIRED` | 토큰이 없거나 잘못됨(R6) |
| 2 | 400 | `INVALID_INPUT` | `Idempotency-Key`가 없거나 공백뿐이거나 100자를 넘음. `holdId`가 없거나 양수가 아님 |
| 3 | 저장된 응답 또는 400 | 저장된 응답의 code 또는 `INVALID_INPUT` | 같은 사용자·같은 키의 요청이 처리 중이면 끝날 때까지 기다린다. 그 키에 저장된 응답이 있으면 6.7.1대로 응답하고 끝낸다. |
| 4 | 404 | `NOT_FOUND` | 선점이 없음, 다른 사용자의 선점, 해제됐거나 이미 결제된 선점 |
| 5 | 409 | `HOLD_EXPIRED` | 선점이 만료됨(현재 시각 ≥ `expiresAt`, R4). 모의 결제를 부르지 않는다. |
| 6 | 402 | `PAYMENT_FAILED` | 모의 결제가 거절됨. 선점은 그대로 남는다(만료 시각도 그대로). |

4~6번과 승인(`201`)의 응답은 멱등 기록에 저장한다(6.7.1). 승인되면 좌석은 `SOLD`, 선점은 `PAID`가 되고 예매가 생긴다(2.3).

#### 6.7.1 멱등 키(R5)

- 클라이언트가 결제 시도마다 새로 만든다(UUID v4 권장). 길이는 1~100자다.
- 키는 사용자별로 구분한다. `backend/CLAUDE.md`의 "`Idempotency-Key`에 대한 DB 유니크 제약"은
  (`userId`, `idempotencyKey`) 복합 유니크 제약으로 만든다. 다른 사용자가 같은 키를 쓰면 별개의 요청이다.
- 검증(오류 표 2번)을 통과한 요청은 결과가 `201`, `402`, `404`, `409` 중 무엇이든 키, `holdId`와 함께 응답(상태 코드와 본문)을 저장한다.
  같은 사용자가 같은 키로 다시 요청하면:
  - `holdId`가 같으면 다시 처리하지 않고 저장한 응답을 그대로 돌려준다. 그 사이 선점이 만료되거나 해제됐어도 같다.
  - `holdId`가 다르면 `400 INVALID_INPUT`이고 아무것도 처리하지 않는다.
- 검증 실패(`400`)와 예상하지 못한 오류(`500`)는 저장하지 않는다. 처리한 것이 없으므로 같은 키로 다시 보내면 처음 온 요청처럼 처리한다.
- 같은 키 요청이 동시에 오면 하나씩 처리한다. 나중 요청은 먼저 요청이 끝날 때까지 기다렸다가 위 규칙대로 응답을 받는다.
  먼저 요청이 `500`으로 끝났으면 저장된 것이 없으므로 처음 온 요청처럼 처리한다.
- 결제가 거절(`402`)된 뒤 다시 결제하려면 새 키로 요청한다. 같은 키는 계속 같은 `402`를 돌려준다.
- 저장한 응답은 지우지 않는다(보관 기간 제한 없음).

#### 6.7.2 모의 결제

- `PaymentGateway` 인터페이스 뒤의 `FakePaymentGateway`가 승인과 거절을 흉내 낸다. 실제 PG는 부르지 않는다.
- 테스트가 아닌 프로필(`local`, `e2e`, `prod`)에서는 항상 승인한다.
- 테스트에서는 다음을 할 수 있어야 한다. 아무것도 지정하지 않으면 승인한다.
  - 다음 호출의 결과를 거절로 지정한다.
  - 호출 횟수를 확인한다. R4, R5의 "모의 결제를 부르지 않는다"를 이것으로 검증한다.
  - 호출 중간에 멈추게 했다가 풀어 준다(예: 래치). R4의 결제 처리 중 동시성을 이것으로 검증한다.
  - 통합 테스트는 스프링 컨텍스트를 공유하므로 매 테스트 전에 위 설정과 호출 횟수를 초기화한다.
- 금액은 2.2의 결제 금액이다.

### 6.8 `GET /api/reservations` 내 예매

로그인 필요. `200 OK`: 내 `Reservation[]`, `reservedAt` 내림차순(같으면 `id` 내림차순). 없으면 `[]`.

| 순서 | 상태 | code | 상황 |
|---|---|---|---|
| 1 | 401 | `AUTH_REQUIRED` | 토큰이 없거나 잘못됨(R6) |

### 6.9 `POST /api/test-support/reset` E2E 데이터 초기화

- `e2e` 프로필에서만 등록하고, 그 프로필에서만 인증 없이 연다. 데이터를 비우고 시드를 다시 넣는다. `204 No Content`.
- 다른 프로필에서는 등록하지 않는다. 이 경로도 다른 경로처럼 인증이 필요하므로 토큰 없이 부르면 `401 AUTH_REQUIRED`,
  유효한 토큰이 있으면 `404 NOT_FOUND`다.
- E2E는 초기화 뒤의 ID가 매번 같다고 가정하지 않는다. 필요한 ID는 공연 목록·상세 API로 찾는다.

### 6.10 `GET /actuator/health` 상태 확인

공개. E2E의 서버 준비 확인에 쓴다. Actuator 기본 응답(`{"status":"UP"}`)을 그대로 쓴다.

## 7. 화면(프론트엔드)

### 7.1 경로

| 경로 | 화면 | 로그인 |
|---|---|---|
| `/login` | 로그인 | - |
| `/` | 공연 목록 | 필요 없음 |
| `/performances/:performanceId` | 좌석 선택 | 필요 |
| `/holds/:holdId` | 결제·예매 확인 | 필요 |
| `/reservations` | 내 예매 | 필요 |

- 로그인이 필요한 화면에 토큰 없이 들어오면 `/login`으로 보내고, 로그인하면 원래 화면으로 돌아간다(R6).
- 로그인 화면의 입력 이름은 '아이디', '비밀번호'이고 버튼 이름은 '로그인'이다.

### 7.2 좌석 선택

- 좌석 버튼의 접근성 이름은 `{row}-{number} {상태}`다.

  | 상태 | 이름 예 | 누르면 |
  |---|---|---|
  | `AVAILABLE`, 고르지 않음 | `A-3 선택 가능` | 고른다 |
  | 내가 고름(선점 전) | `A-3 선택됨` | 선택을 취소한다 |
  | `HELD` | `A-4 선점됨` | 비활성 |
  | `SOLD` | `A-5 판매됨` | 비활성 |

- 최대 4석까지 고른다. 4석을 고른 상태에서 다른 좌석을 누르면 고르지 않고 `HOLD_LIMIT_EXCEEDED` 문구를 보여 준다(R2).
  화면은 이번에 고른 좌석만 센다. 이미 예매했거나 다른 창에서 선점한 좌석까지 합친 한도는 서버가 `409 HOLD_LIMIT_EXCEEDED`로 막는다.
- 고른 좌석과 합계 금액을 보여 준다.
- '좌석 선택 완료' 버튼(1석 이상 골랐을 때만 누를 수 있음)이 `POST /api/holds`를 보낸다.
  - `201`: `/holds/{id}`로 이동한다.
  - `409 SEAT_ALREADY_HELD`: 안내 문구를 보여 주고, 좌석 상태를 다시 불러와 고를 수 없게 된 좌석을 선택에서 뺀다(R1).
  - `409 HOLD_LIMIT_EXCEEDED`: 안내 문구를 보여 준다(R2).
  - `404 NOT_FOUND`: 안내 문구와 공연 목록 링크를 보여 준다.

### 7.3 결제·예매 확인

- 들어오면 `GET /api/holds/{holdId}`로 선점을 불러와 공연, 좌석, 금액, 남은 시간(`expiresAt`까지)을 보여 준다.
  - `status`가 `EXPIRED`: `HOLD_EXPIRED` 문구와 '좌석 다시 선택' 링크를 보여 주고, 결제 버튼은 두지 않는다.
  - `404 NOT_FOUND`: 안내 문구와 '내 예매' 링크를 보여 준다(이미 결제했거나 해제한 선점일 수 있다).
- 남은 시간이 0이 되면 결제 버튼을 막고 만료 안내를 보여 준다(R3). 판정 기준은 서버이고, 화면의 남은 시간은 안내용이다.
- '결제하기' 버튼이 `POST /api/payments`를 보낸다.
  - 멱등 키(`crypto.randomUUID()`)는 이렇게 쓴다(R5). 응답을 받았으면 상태 코드와 상관없이 다음에 누를 때 새 키를 만든다.
    응답을 받지 못했으면(네트워크 오류, 10초 시간 초과(7.5)) 다음에 누를 때 같은 키를 다시 쓴다. 자동으로 다시 보내지는 않는다.
  - 요청 중에는 버튼을 누를 수 없다.
  - `201`: 같은 화면에 예매 완료(좌석, 금액)와 '내 예매' 링크를 보여 준다.
  - `402 PAYMENT_FAILED`: 안내 문구를 보여 준다. 선점이 남아 있으므로 다시 결제할 수 있다.
  - `409 HOLD_EXPIRED`: 안내 문구와 '좌석 다시 선택' 링크를 보여 준다(R4).
  - `404 NOT_FOUND`: 안내 문구와 '내 예매' 링크를 보여 준다.
- 결제가 승인(`201`)되기 전에 앱 안의 다른 화면으로 이동하면 `DELETE /api/holds/{holdId}`를 보낸다.
  결제가 거절(`402`)된 뒤의 이동, 브라우저 뒤로가기, '좌석 다시 선택', 메뉴 이동이 모두 여기에 들어간다.
  해제 요청이 실패해도 이동은 막지 않는다(선점은 5분 뒤 만료된다). 승인된 뒤에는 보내지 않는다.
- 새로고침하거나 창·탭을 닫으면 해제하지 않는다. 새로고침하면 같은 선점을 다시 불러오고, 닫으면 5분 뒤 만료된다.
- 구현 주의: 화면을 떠나는지는 라우터 이동으로 판단한다(예: React Router의 `useBlocker`).
  컴포넌트 정리 함수(`useEffect` cleanup)로 판단하면 개발 모드의 `StrictMode`가 effect를 한 번 더 실행할 때
  들어오자마자 해제된다. E2E는 개발 서버로 돌기 때문에 이 문제가 그대로 드러난다.

### 7.4 내 예매

`GET /api/reservations`로 내 예매를 최신순으로 보여 준다. 예매마다 공연 제목, 일시, 장소, 좌석, 금액을 보여 준다.

### 7.5 화면 공통

- 데이터를 불러오는 화면은 로딩, 빈 목록, 오류 상태를 모두 보여 준다.
- 모든 API 요청은 10초 안에 응답이 없으면 끊고(`AbortController`) 응답을 받지 못한 것으로 본다. `fetch`에는 기본 시간 제한이 없어,
  응답이 오지 않으면 로딩이나 막힌 버튼이 풀리지 않기 때문이다.
- 안내 문구는 `role="alert"` 영역에 보여 준다. 단위 테스트와 E2E가 이 역할로 찾는다.

### 7.6 안내 문구

프론트엔드는 아래 문구를 code별로 한 파일에 모은다.

| code | 문구 |
|---|---|
| `INVALID_INPUT` | 입력한 내용을 확인해 주세요. |
| `INVALID_CREDENTIALS` | 아이디 또는 비밀번호가 올바르지 않습니다. |
| `AUTH_REQUIRED` | 로그인이 필요합니다. (로그인 화면으로 보내면서 보여 준다) |
| `PAYMENT_FAILED` | 결제가 승인되지 않았습니다. 다시 시도해 주세요. |
| `NOT_FOUND` | 요청한 정보를 찾을 수 없습니다. |
| `SEAT_ALREADY_HELD` | 이미 다른 고객이 선택한 좌석입니다. 다른 좌석을 선택해 주세요. |
| `HOLD_LIMIT_EXCEEDED` | 한 공연에서 최대 4석까지 예매할 수 있습니다. |
| `HOLD_EXPIRED` | 좌석 선점 시간(5분)이 지났습니다. 좌석을 다시 선택해 주세요. |
| `INTERNAL_ERROR` | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. |
| (응답 없음) | 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요. |

## 8. 만들지 않는 것

- 규칙에 없어 두지 않는 것: 예매 취소, 선점 시간 연장, 이미 시작한 공연의 예매 제한, 좌석 등급별 가격,
  화면에서 결제 거절을 일으키는 방법, 내 선점 목록 API. 필요하면 새 Issue에서 사람이 정한다.
  - 내 선점 목록 API가 없으므로, 창을 닫았다가 5분 안에 돌아오면 내 좌석도 '선점됨'으로 보이고 보유 좌석 수에 들어간다. 5분 뒤 만료로 풀린다.
- 루트 `CLAUDE.md`의 범위 밖: 대기열, Redis, Kafka, 실제 PG 연동, 회원가입, 관리자 화면.

## 9. 결정 기록

Issue [#3](https://github.com/Rasit-NP/curtain-call/issues/3)에서 사람이 정했다(2026-11-26).

| 항목 | 결정 | 근거 |
|---|---|---|
| 선점 단위 | 선점 1건은 좌석 묶음(1~4석)이다. 좌석을 고른 뒤 결제 화면으로 넘어갈 때 한 번에 선점하고, 결제는 선점 1건을 받는다. | CGV 예매 방식 |
| 선점 해제 | `DELETE /api/holds/{holdId}`를 둔다. 결제가 승인되기 전에 앱 안의 다른 화면으로 가면(거절된 뒤 포함) 해제하고, 창을 닫으면 해제하지 않고 5분 뒤 만료된다. | 사람이 CGV에서 확인한 동작(다른 페이지로 이동하면 풀리고, 창을 닫으면 풀리지 않음). 다른 예매 시스템도 자동 만료와 즉시 해제를 함께 둔다([Wix Ticket Reservations](https://dev.wix.com/docs/api-reference/business-solutions/events/registration/ticketing/ticket-reservations/introduction)의 예약 취소, [seats.io](https://docs.seats.io/docs/api/hold-tokens)의 보류 토큰 즉시 만료). |
| 결제 거절 응답 | `402 PAYMENT_FAILED`. 루트 `CLAUDE.md` 오류 코드 표에 추가했다. | 다른 오류처럼 `ProblemDetail` + `code`로 처리한다. |
| 결제 거절 뒤 | 같은 키 재요청에는 같은 `402`를 돌려준다. 선점은 원래 만료 시각까지 유지하고, 다시 결제하려면 새 키를 쓴다. | R5 "같은 결과를 돌려준다" |
| 같은 키, 다른 `holdId` | `400 INVALID_INPUT`. 아무것도 처리하지 않는다. | 표에 있는 코드로 처리한다. 참고로 [IETF Idempotency-Key 초안](https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header)은 422를 권하고, [Stripe](https://docs.stripe.com/api/idempotent_requests)는 `idempotency_error`로 거부한다. |
| API 형태 | 평면 자원(4장) | 공개 경로가 `GET /api/performances/**`뿐이라 인증 설정이 단순하다. |
| 401 처리 | `AUTH_REQUIRED`에만 토큰 삭제와 로그인 화면 이동을 적용한다. `frontend/CLAUDE.md`도 고쳤다. | 로그인 실패도 401(`INVALID_CREDENTIALS`)이다. |

에이전트가 정하고 PR 리뷰에서 확인받는 세부:

- 오류 검사 순서, 응답 필드 이름, 화면 경로·버튼 이름·안내 문구, 시드 최소 조건, 정렬 기준이 같을 때 `id` 순
- 좌석에 지금 잡은 선점(`holdId`)을 두고, 멱등 기록을 결제와 따로 둔다(2.1)
- `Idempotency-Key` 길이(1~100자)와 사용자별 구분. 검증을 통과한 결제 요청은 `201`·`402`·`404`·`409` 모두 응답을 저장한다.
  그래서 "같은 키, 다른 `holdId`는 400"이 이미 처리한 모든 키에 적용된다. `400`·`500`은 처리한 것이 없어 저장하지 않는다.
- 같은 키 동시 요청은 기다렸다가 같은 결과를 준다. IETF 초안은 처리 중인 같은 키에 409를 권하지만, R5의 "같은 결과"를 지키고 새 코드를 만들지 않으려고 이렇게 정했다.
- 표에 없는 오류 상황: 지원하지 않는 메서드·`Content-Type`은 `400 INVALID_INPUT`, 없는 경로는 `404 NOT_FOUND`(3.1)
- JWT 허용 오차 0과 `exp` 같은 시각 처리(3.2), 프론트엔드 API 요청의 10초 시간 제한(7.5)
