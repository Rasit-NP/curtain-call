# #51 백엔드 선점 조회·해제(R2·R3·R6): 내 선점 조회 API, 내 선점 해제 API, 해제 동시성

- 기간: 2026-11-27
- Issue: [#51](https://github.com/Rasit-NP/curtain-call/issues/51), PR: (만들면 적는다)
- 관련 결정: [0120](../decisions/0120-hold-query-release-impl.md), [0121](../decisions/0121-release-race-test-staggered.md).
  기존 [0070](../decisions/0070-hold-schema.md), [0071](../decisions/0071-missing-token-user-not-handled.md),
  [0074](../decisions/0074-valid-hold-single-place.md), [0090](../decisions/0090-path-id-ascii-decimal.md),
  [0091](../decisions/0091-strict-long-binding.md), [0100](../decisions/0100-hold-concurrency-v2.md)을 따른다(바꾸지 않는다)

## 배경

로드맵 [#13](https://github.com/Rasit-NP/curtain-call/issues/13)의 4단계 백엔드다. 결제 화면(프론트엔드 [#52](https://github.com/Rasit-NP/curtain-call/issues/52), 동시에 진행)은
새로고침할 때 `GET /api/holds/{holdId}`로 선점을 다시 불러오고, 결제 전에 앱 안의 다른 화면으로 갈 때 `DELETE /api/holds/{holdId}`를 부른다(명세 7.3).
3단계(#41)가 선점 생성, 선점 테이블과 좌석의 `hold_id`, 유효한 선점의 판정 `Hold.isValidAt`을 만들었고,
3단계 뒤 점검(#45·#46)이 경로 id의 엄격한 해석과 해제의 잠금 순서(선점 행 → 좌석 `id` 오름차순, 사용자 행은 잠그지 않음)를 정했다.
Issue에는 아직 정하지 않은 정책이 없다. 이 세션은 `backend/`와 `docs/`만 고치고, 결정 번호는 0120부터 쓴다.

## 진행 순서

1. Issue, 루트·백엔드 `CLAUDE.md`, 명세(2.2, 2.3, 2.4, 5, 6.5, 6.6), 대응표, 관련 결정(0070, 0071, 0074, 0090, 0091, 0100)과
   기존 선점 코드·테스트(`HoldService`, `HoldApiTest`, `HoldConcurrencyTest`)를 읽었다.
2. 브랜치를 `feat/51-hold-query-release`로 바꾸고 시작점이 최신 `origin/main`(c947d05)인지 확인했다.
3. 구현하며 정책 결정이 생기는지 살폈다. 조회 응답의 모양(5장 `Hold`), 만료된 선점의 seats(결정 0070의 '만든 때의 묶음'), 0·음수 holdId(404, 6.3과 같은 규칙),
   오류 순서는 모두 명세와 결정에 있어 사람에게 물을 것이 없었다.
4. 테스트를 먼저 썼다. 통합 테스트 `HoldQueryAndReleaseApiTest` 30개(정수가 아닌 holdId 8가지 × 조회·해제 포함), `HoldConcurrencyTest`에 동시성 테스트 2개,
   단위 테스트 4개(`Hold.release`, `Seat.release`. 메서드는 '구현 전' 예외를 던지는 뼈대만 두었다).
   - 단위 4개는 모두 실패했다(`UnsupportedOperationException`).
   - 통합 32개 중 30개가 실패했다. 경로가 없어 조회·해제가 404였다. 통과한 2개는 '다른 사용자의 선점이나 없는 선점을 해제하면 404', '만료된 다른 사용자의 선점을 해제하면 404'로,
     경로가 없어도 404라 우연히 통과한 것이다(구현 뒤에는 서비스의 404 판정으로 통과한다).
5. 구현했다(결정 0120). 단위·통합 테스트가 모두 통과했다(`HoldQueryAndReleaseApiTest` 30개, `HoldConcurrencyTest` 7개).
6. 선점 행 잠금의 SQL이 명세 2.4·결정 0100대로 `FOR NO KEY UPDATE`인지 임시 테스트로 확인하고 지웠다(아래 '판단과 근거').
7. 되돌림 확인을 했다(아래 '규칙 테스트 되돌림 확인'). 이때 '만료된 선점의 해제와 다른 사용자의 같은 좌석 선점' 동시성 테스트가 결함을 놓친다는 것을 알고 고쳤다(아래 '실패와 수정').
8. 대응표, 결정 기록(0120, 0121), 결함 기록, 이 진행 기록을 고쳤다.

## 판단과 근거

사람이 정한 것: 없다. Issue 본문의 '이미 정한 정책'(명세 6.5·6.6, 2.3, 2.4, 결정 0090·0100)을 그대로 따랐고, 구현 중에 새 정책 결정은 생기지 않았다.

에이전트가 정한 것(Issue 본문 '에이전트가 정하는 것'):

- **해제의 잠금과 거르기**([0120](../decisions/0120-hold-query-release-impl.md)): 선점 행을 `lockById`(JPA `PESSIMISTIC_WRITE`)로 먼저 잠그고, 잠근 뒤 읽은 값으로 주인과
  저장 상태를 본다. 좌석은 선점의 좌석 묶음(`hold_seats`)을 선점 생성과 같은 쿼리(`ORDER BY id FOR NO KEY UPDATE`)로 잠그고, 잠근 뒤 holdId가 이 선점인 좌석만 되돌린다.
  조건부 UPDATE 한 문장(`WHERE hold_id = ?`)은 행을 잠그는 순서를 정할 수 없어, 같은 좌석을 id 순서로 잠그는 선점 생성과 교착 상태가 될 수 있어 고르지 않았다.
  좌석을 묶음의 기본 키로 찾으므로 `seats.hold_id` 인덱스는 여전히 필요 없다(결정 0070 '적용과 남은 점').
- **404 판정 한 곳**: 조회와 해제가 같은 조건(`isOpenHoldOf`: 내 선점이고 저장 상태가 ACTIVE, 만료 포함)을 쓴다. 결제된 선점(PAID)도 이 조건으로 404가 되지만,
  테스트는 결제 API가 생기는 5단계에서 실제 결제로 만든다(Issue 본문).
- **상태 전이는 엔티티 메서드**: `Hold.release()`(ACTIVE → RELEASED, 현재 시각을 받지 않음), `Seat.release()`(HELD → AVAILABLE, holdId 비움).
  앞 상태가 아니면 예외를 던진다. 서비스가 걸러 정상 흐름에서는 생기지 않는다.
- **잠금 모드 확인**: 기존 코드와 결정 0100은 JPA `PESSIMISTIC_WRITE`가 PostgreSQL에서 `FOR NO KEY UPDATE`라고 적었지만 확인한 테스트가 없었다.
  임시 통합 테스트에서 해제와 같은 잠금(선점 행, 좌석 행)을 쥔 채 다른 연결로 `FOR KEY SHARE NOWAIT`(통과)와 `FOR NO KEY UPDATE NOWAIT`(`55P03`으로 막힘)를 보내 확인하고 지웠다
  (Hibernate 7.4.5). 외래 키 확인의 `FOR KEY SHARE`와 부딪히지 않는다는 결정 0100의 전제가 맞다.
- **테스트 방법**([0121](../decisions/0121-release-race-test-staggered.md)): 조회·해제 API는 한 클래스(`HoldQueryAndReleaseApiTest`)에서 MockMvc로 검증하고,
  응답과 함께 DB 최종 상태(선점의 저장 상태, 좌석의 저장 상태와 holdId)를 본다. 정수가 아닌 holdId는 내 선점의 id를 바꿔 쓴 값으로 보내,
  느슨하게 해석하면 내 선점(200·204)이 되게 했다. 동시성 테스트는 기존 `HoldConcurrencyTest`에 더해 `CountDownLatch` 도우미를 같이 쓴다.

## 실패와 수정

- **경합 테스트가 한 순서만 만들었다.** 처음 쓴 '만료된 선점의 해제와 다른 사용자의 같은 좌석 선점이 동시에 와도…' 테스트는 두 요청을 같은 순간에 보냈다.
  R3 되돌림 확인에서 해제의 holdId 거르기를 빼자(덮어쓴 좌석까지 되돌리는 변형) 순서대로 하는 R3 테스트는 실패했지만 이 테스트는 50회 모두 통과했다.
  - 원인 가설: 해제는 좌석을 잠그기 전에 선점 행 하나만 잠그지만, 선점 생성은 사용자 행, 공연, 좌석 수, 보유 좌석 수를 먼저 거친다. 그래서 해제가 거의 언제나 먼저 좌석을 잡아
    '선점이 먼저 좌석을 잡거나 커밋한 뒤 해제가 온다'는 순서가 생기지 않는다. 이 순서에서만 거르기를 뺀 결함이 드러난다.
  - 수정: 반복마다 해제를 0~10ms(`(반복 번호 % 6) × 2`) 늦게 보낸다. 같은 변형에서 첫 반복(2ms)에 새 선점의 4석이 모두 풀려 실패했다(1회 시도로 고침).
  - 원래 코드로는 3회 연속 통과했다. 지연은 순서를 섞을 뿐이고 검증은 어느 순서에서도 같다.

## 규칙 테스트 되돌림 확인

구현의 해당 부분을 잠시 바꾸고 선점 관련 단위·통합 테스트(`HoldTest`, `SeatTest`, `HoldLimitTest`, `HoldApiTest`, `HoldQueryAndReleaseApiTest`, `HoldConcurrencyTest`)를 돌렸다.
매번 원래대로 되돌리고 `git diff`로 운영 코드가 커밋과 같은지 확인했다.

| 되돌린 것 | 실패한 테스트 |
|---|---|
| 조회의 만료 비교 경계: `hold.isValidAt(now)` → `!now.isAfter(expiresAt)`(만료 시각과 같은 순간을 유효로) | R3 '선점 조회는 만료 시각 1초 전까지 ACTIVE이고, 만료 시각부터 EXPIRED'(만료 시각에 ACTIVE), '만료된 선점의 조회는 seats와 amount가 만든 때의 묶음이다'. 2개 |
| 해제할 좌석을 holdId로 거르는 조건: 이 선점 → holdId가 있는 모든 좌석 | R3 '만료된 내 선점을 해제해도 그 좌석을 새로 선점한 다른 사용자의 선점은 유지된다'(밥의 좌석이 AVAILABLE). 처음 쓴 동시성 테스트는 놓쳤고, 고친 뒤에는 동시성 테스트도 실패(첫 반복, 2회 실행) |
| 해제 상태 저장: `hold.release()`를 뺌 | R2 '선점을 해제하면 그 좌석은 보유 좌석 수에서 빠져 다시 4석을 선점할 수 있다'(201 대신 409 HOLD_LIMIT_EXCEEDED) 외 8개(해제 뒤 404, 해제 204의 RELEASED, 동시 해제 2개 등). 9개 |
| 선점 행 잠금: `lockById` → 일반 조회 | '같은 선점에 해제 요청 5개가 동시에 오면 하나만 204'(첫 반복에 5개 모두 204). 1개 |
| 좌석 잠금 순서: id 오름차순 한 문장 → id 내림차순으로 하나씩 | '만료된 선점의 해제와 다른 사용자의 같은 좌석 선점…500이 나지 않는다'(`deadlock detected`로 500). 3회 실행 모두 실패(6, 18, 12번째 반복) |
| 좌석 잠금: 잠금 조회 → 일반 조회(`findAllById`) | 같은 동시성 테스트(새 선점의 4석이 모두 풀림, 첫 반복). 2회 실행 모두 실패 |

## 리뷰

(PR을 만든 뒤 `/code-review high`로 리뷰하고 적는다)

## 결과

(PR을 만든 뒤 적는다)

## 배운 점

- 동시성 테스트는 '같은 순간에 출발'시켜도 요청마다 잠그기 전 단계의 길이가 다르면 한 순서만 계속 나올 수 있다. 되돌림 확인에서 동시성 테스트가 아니라
  순서대로 하는 테스트만 실패하면, 동시성 테스트가 그 순서를 만들지 못하는 것은 아닌지 먼저 본다.
