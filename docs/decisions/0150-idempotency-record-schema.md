# 0150. 멱등 기록 테이블: (사용자, 키) 유니크, 선점은 외래 키 없이, 응답은 상태 코드와 본문(201은 응답 본문 JSON 그대로, 오류는 code·detail)

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #56 '에이전트가 정하는 것': 멱등 기록 테이블과 응답 저장 형식)
- 관련: Issue [#56](https://github.com/Rasit-NP/curtain-call/issues/56), 결정 [0005](0005-payment-failed-402.md), [0006](0006-idempotency-record.md),
  [0012](0012-backend-error-mapping.md), [0071](0071-missing-token-user-not-handled.md), [0140](0140-reservation-payment-schema.md), [0141](0141-idempotency-key-format.md),
  [0151](0151-idempotency-impl.md), spec 2.1, 3.1, 6.7, 6.7.1

## 결정

`V5__create_idempotency_records.sql`의 `idempotency_records`:

| 열 | 형식 | 뜻 |
|---|---|---|
| `id` | BIGINT identity | 대리 키(다른 테이블과 같다) |
| `user_id` | BIGINT, `users` 외래 키 | 요청한 사용자(토큰의 `sub`) |
| `idempotency_key` | VARCHAR(100) | 받은 키 그대로(결정 0141: ASCII 출력 문자 1~100자). 대소문자를 구분한다 |
| `hold_id` | BIGINT, 외래 키 없음 | 요청의 `holdId` |
| `response_status` | INTEGER | 201, 402, 404, 409 |
| `response_body` | TEXT | 아래 '저장 형식' |
| `created_at` | TIMESTAMPTZ | 기록을 잡은 시각(판정에 쓰지 않는다) |

제약:

- `UNIQUE (user_id, idempotency_key)`: 키는 사용자별이다(R5, spec 6.7.1).
- `CHECK ((response_status IS NULL) = (response_body IS NULL))`: 응답은 상태와 본문이 함께 있다. 둘 다 비어 있는 것은 키를 잡은 트랜잭션 안뿐이다(결정 0151).
- `CHECK (response_status IN (201, 402, 404, 409))`: 400(검증 실패, 같은 키·다른 선점)과 500은 저장하지 않는다.
- 지우지 않는다(보관 기간 제한 없음, spec 6.7.1). 그래서 인덱스는 유니크 제약의 것 하나다.

저장 형식(`payment.api.StoredPaymentResponses`):

- 201: 응답 본문 `{ paymentId, amount, reservation }`(spec 6.7)을 JSON으로 그대로 둔다. 응답할 때 이 JSON을 `PaymentResponse`로 읽어 돌려준다.
- 402·404·409: `{"code": "...", "detail": "..."}`. 응답할 때 같은 code·detail로 `ApiException`을 던져 공통 처리기가 오류 본문(ProblemDetail)을 만든다.
  `type`, `title`, `status`, `instance`는 상태와 요청 경로로 정해지므로, 같은 경로의 요청이면 처음과 같은 본문이 된다.
- 처음 요청의 응답도 이 저장한 값으로 만든다(결정 0151). 그래서 처음 응답과 같은 키로 다시 보낸 응답은 같은 값에서 같은 코드로 만들어진다.

## 근거

- `hold_id`에 외래 키를 두지 않는 것: 없는 선점으로 끝난 요청(404)도 키, `holdId`와 함께 저장해야 한다(spec 6.7.1). 외래 키가 있으면 그 기록을 넣을 수 없다.
- `user_id`의 외래 키: 다른 테이블(선점, 예매, 결제)과 같다. 토큰의 사용자가 DB에 없으면 따로 처리하지 않고 외래 키가 막아 500이라는 결정 0071(사람이 정함)을 그대로 따른다.
- 오류의 본문 전체(ProblemDetail)를 저장하지 않는 것: 오류 본문은 공통 처리기 한곳에서만 만든다(`backend/CLAUDE.md` 'API'). 저장하려면 커밋 전에 서비스나 컨트롤러가
  ProblemDetail을 만들어야 한다. code와 detail만 저장하고 공통 처리기를 다시 지나게 하면 오류 본문을 만드는 곳이 늘지 않는다.
- 201 본문을 JSON 그대로 저장하는 것: 명세는 응답(상태 코드와 본문)을 저장하라고 한다. 예매·공연 값을 id로만 두고 다시 읽어 만들면 '저장한 응답'이 아니다.
- TEXT로 두는 것: JSONB는 키 순서와 공백을 바꿔 저장하므로 '그대로'가 DB에서 보이지 않는다. 응답할 때는 어차피 객체로 읽으므로 JSONB의 검색 기능은 필요 없다.
- 응답 열을 비울 수 있게 두는 것: 키를 먼저 잡고(INSERT) 결과를 나중에 채우는(UPDATE) 처리(결정 0151) 때문이다. 둘이 같은 트랜잭션이라 다른 요청에는 빈 응답이 보이지 않는다.
  짝 CHECK로 '상태만 있음' 같은 모양은 DB가 막는다.

## 고려했지만 고르지 않은 것

- (`user_id`, `idempotency_key`)를 기본 키로: 유니크 제약과 같은 효과지만 다른 테이블은 모두 대리 키 + 제약이다. 같은 모양을 유지했다.
- `hold_id`에 외래 키: 위 '근거'(404를 저장할 수 없다).
- 오류도 ProblemDetail 전체를 저장해 그대로 씀: 위 '근거'(공통 처리기 밖에서 오류 본문을 만들게 된다).
- 201도 결제·예매 id만 저장하고 응답할 때 다시 읽음: 저장한 응답이 아니고, 공연 정보가 바뀌면(지금은 바꾸는 기능이 없다) 응답이 달라진다.
- 열을 나눠 오류 code·detail과 201 본문을 따로 둠: 열 네 개의 비는 조합을 CHECK로 다 막아야 한다. 본문 하나와 상태로 두고 형식은 상태로 가른다.
- 응답 열 NOT NULL(결과를 얻은 뒤 한 번에 INSERT): 결정 0151의 '먼저 잡기'와 맞지 않는다.

## 적용과 남은 점

- 코드: `V5__create_idempotency_records.sql`, `payment.domain.IdempotencyRecord`·`IdempotencyRecordRepository`·`StoredResponse`, `payment.api.StoredPaymentResponses`.
- 테스트: `IdempotencyRecordSchemaTest`(유니크, 응답 모양, 외래 키), `PaymentIdempotencyApiTest`(응답이 상태·Content-Type·본문 문자열까지 같음).
- 토큰의 사용자가 DB에 없을 때 결제는 #55에서는 404였다(기록을 넣지 않았으므로). 이제 기록을 넣을 때 외래 키가 막아 500이다(결정 0071). 결정 0142의 '적용과 남은 점'에 적힌 404는
  이 결정 뒤로는 맞지 않는다.
- 저장한 201 본문은 `PaymentResponse`로 읽는다. 기록을 지우지 않으므로, 나중에 `PaymentResponse`의 모양을 바꾸면 이전 기록의 본문을 어떻게 돌려줄지 그때 정해야 한다.
- E2E 데이터 초기화(6단계, spec 6.9)는 이 테이블도 비워야 한다.
