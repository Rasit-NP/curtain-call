# 결정 기록 (작업용)

에이전트가 작업 중에 지금 유효한 결정을 확인하는 문서다. 결정 하나에 파일 하나(`NNNN-제목.md`)를 둔다.
작업을 시작하기 전에 이 목차를 읽고, 관련 결정 파일을 연다. 판단의 자세한 경위는 [`docs/history/`](../history/README.md)에 있다.

## 쓰는 법

- 새 결정은 다음 번호로 파일을 만들고 이 목차에 한 줄을 더한다. 목차와 파일이 어긋나면 `verify.sh harness`가 실패한다.
- 항목: 상태, 날짜, 결정한 사람, 관련 Issue·PR, 결정, 근거, 고려했지만 고르지 않은 것, 적용과 남은 점.
- 상태는 '결정한 사람'으로 정한다([0045](0045-decision-status-by-decider.md)).
  - `채택`: 사람이 정했거나 확인한 결정.
  - `제안`: 사람에게 물었고 답을 기다릴 때만 쓴다. 답을 받으면 답대로 적고 `채택`으로 바꾼다.
  - `에이전트 결정`: 에이전트가 정하고 사람이 확인하지 않은 결정. PR이 병합돼도 `채택`으로 바꾸지 않는다. 사람이 확인하면 그때 `채택`으로 바꾼다.
  - `대체됨(→NNNN)`: 새 결정으로 바뀐 결정.
- 확인은 사람이 그 결정을 두고 답한 것이다. PR 병합, 보고 뒤 무응답은 확인이 아니다.
- '결정한 사람'에 사람과 에이전트가 함께 있으면, 사람이 정하거나 확인하지 않은 내용이 남아 있는 한 `에이전트 결정`이다. 사람이 정한 부분은 '결정한 사람'에 적는다.
- 결정을 바꿀 때는 원래 파일을 고치지 않는다. 새 파일을 만들고 원래 파일의 상태만 `대체됨(→NNNN)`으로 바꾼다.
- 목차의 상태와 파일의 상태를 같게 둔다.

## 목차

| 번호 | 결정 | 상태 |
|---|---|---|
| [0001](0001-verify-single-entry.md) | 검증은 verify.sh 하나로, 영역은 표식 파일로 판단 | 에이전트 결정 |
| [0002](0002-stop-hook-quick.md) | 작업을 끝낼 때 Stop 훅으로 quick 검증, 같은 턴에 한 번만 막기 | 에이전트 결정 |
| [0003](0003-hold-is-seat-bundle.md) | 선점 1건은 좌석 묶음(1~4석), 결제는 선점 1건 | 채택 |
| [0004](0004-hold-release.md) | 결제 전 다른 화면으로 가면 선점 해제, 창을 닫으면 만료까지 유지 | 채택 |
| [0005](0005-payment-failed-402.md) | 결제 거절은 402 PAYMENT_FAILED, 같은 키 재요청에는 같은 402 | 채택 |
| [0006](0006-idempotency-record.md) | 멱등 키는 사용자별, 검증을 통과한 결제 결과는 모두 저장, 같은 키 동시 요청은 기다림 | 에이전트 결정 |
| [0007](0007-flat-api.md) | API는 평면 자원으로 | 채택 |
| [0008](0008-auth-required-only.md) | 프론트의 '401이면 토큰 삭제·로그인 이동'은 AUTH_REQUIRED에만 | 채택 |
| [0009](0009-seat-hold-id.md) | 좌석에 지금 잡은 선점(holdId)을 둔다 | 에이전트 결정 |
| [0010](0010-security-in-auth-issue.md) | 첫 설정에서 Spring Security를 빼고 인증 Issue에서 한 번에 넣는다 | 채택 |
| [0011](0011-dev-db-credentials.md) | 로컬·E2E DB 접속 정보는 개발 전용 값으로 저장소에 둔다 | 에이전트 결정 |
| [0012](0012-backend-error-mapping.md) | 백엔드 오류 응답: 입력 오류 상태만 INVALID_INPUT, 기능 오류는 ApiException, 나머지는 500 | 에이전트 결정 |
| [0013](0013-flyway-out-of-order-local.md) | local·e2e에서 Flyway out-of-order를 켠다 | 에이전트 결정 |
| [0014](0014-error-code-table-test.md) | 오류 코드 enum은 루트 CLAUDE.md 표를 읽어 검사하고, Gradle 입력으로 선언한다 | 에이전트 결정 |
| [0015](0015-health-probes-off.md) | health 응답을 {"status":"UP"}로 두려고 프로브 그룹을 끈다 | 에이전트 결정 |
| [0016](0016-integration-test-base.md) | 통합 테스트는 컨테이너 하나 공유, 테스트 시계, 테이블 비우기 | 에이전트 결정 |
| [0017](0017-java21-toolchain.md) | Java 21을 Gradle 툴체인으로 고정 | 에이전트 결정 |
| [0018](0018-frontend-error-fallback.md) | 프론트: code가 없는 오류 응답은 INTERNAL_ERROR | 에이전트 결정 |
| [0019](0019-frontend-no-response.md) | 프론트: '응답 없음'은 프론트 전용 NO_RESPONSE, 본문 수신 중 끊김도 포함 | 에이전트 결정 |
| [0020](0020-frontend-auth-option.md) | 프론트 API 클라이언트의 auth는 기본값 없는 필수 옵션 | 에이전트 결정 |
| [0021](0021-frontend-lint-boundaries.md) | fetch는 src/api, localStorage는 src/auth에서만 쓰도록 ESLint로 막는다 | 에이전트 결정 |
| [0022](0022-nvmrc-exact.md) | .nvmrc는 정확한 버전으로 고정 | 에이전트 결정 |
| [0023](0023-review-depth-by-risk.md) | 리뷰 강도는 PR 위험도로 정하고 항상 명시 | 에이전트 결정 |
| [0024](0024-docs-two-tracks.md) | 문서화를 작업용 결정 기록과 포트폴리오용 진행 기록으로 나눈다 | 채택 |
| [0025](0025-msw-unhandled-error-strategy.md) | 프론트 테스트: 핸들러 없는 요청은 MSW 'error' 전략으로 막고, 테스트가 끝날 때 처리 중인 요청을 기다린다 | 에이전트 결정 |
| [0026](0026-issue-creation-scope.md) | Issue 등록은 로드맵에서 지금 진행할 단계만, 더 필요하면 목록을 먼저 보여 주고 묻는다 | 채택 |
| [0027](0027-issue-per-worktree-session.md) | Issue 작업은 Issue마다 별도 세션과 그 세션의 git 워크트리에서, 메인 체크아웃은 고치지 않는다 | 채택 |
| [0028](0028-instruction-scope.md) | 사람의 마지막 지시에 적힌 일만 하고, 행동은 그 행동을 지시받은 뒤에 한다 | 채택 |
| [0029](0029-guard-main-checkout-hook.md) | 메인 체크아웃 파일 수정을 PreToolUse 훅으로 막고, 메인 워크트리는 git으로 판단한다 | 채택 |
| [0030](0030-guard-undecidable-input.md) | 메인 체크아웃 훅: 판단할 수 없는 입력과 다른 저장소의 파일은 막지 않고, jq가 없으면 sed로 읽는다 | 에이전트 결정 |
| [0031](0031-security-paths-and-error-dispatch.md) | 보안 설정: 공개 경로는 메서드까지 정하고, 오류 전달(ERROR 디스패치)은 인증 없이 지나게 한다 | 에이전트 결정 |
| [0032](0032-jwt-validation.md) | 토큰 검증: 기본 검증기 대신 주입한 시계·허용 오차 0·exp 필수·sub 숫자 필수 | 대체됨(→0038) |
| [0033](0033-current-user-id.md) | 컨트롤러는 현재 사용자 id를 @CurrentUserId로만 얻는다 | 대체됨(→0039) |
| [0034](0034-login-and-auth-errors.md) | 로그인은 빈이 아닌 DaoAuthenticationProvider로 확인하고, 인증 오류 응답도 공통 처리기가 만든다 | 에이전트 결정 |
| [0035](0035-errors-outside-mvc.md) | MVC 밖 오류: /error 컨트롤러, Tomcat 파라미터 해석 예외, Tomcat 오류 보고 밸브 | 대체됨(→0037) |
| [0036](0036-seed-accounts.md) | 계정 시드: 시연용 demo, E2E용 e2e-user1·e2e-user2, 테스트에서는 시드 파일을 직접 실행해 검증 | 에이전트 결정 |
| [0037](0037-errors-outside-mvc-v2.md) | MVC 밖 오류(개정): Tomcat이 거부한 요청은 별도 규칙으로 code를 정하고, 밸브는 공통 처리기와 같은 필드로 쓴다 | 에이전트 결정 |
| [0038](0038-jwt-validation-v2.md) | 토큰 검증(개정): sub는 1 이상의 long이어야 한다 | 에이전트 결정 |
| [0039](0039-current-user-id-v2.md) | 현재 사용자 id(개정): 토큰이 없으면 @CurrentUserId가 401 AUTH_REQUIRED를 낸다 | 에이전트 결정 |
| [0040](0040-frontend-auth-required-central.md) | 프론트: 401 AUTH_REQUIRED는 API 클라이언트가 토큰을 지우고 알리며, 최상위 레이아웃 한 곳이 로그인 화면으로 보낸다 | 대체됨(→0043) |
| [0041](0041-frontend-login-return-path.md) | 프론트: 로그인 뒤 돌아갈 경로는 location state로 넘기고 앱 안의 경로만 받는다. 가드 이동에는 안내 문구를 붙이지 않는다 | 에이전트 결정 |
| [0042](0042-frontend-guard-test-route.md) | 프론트 테스트: 가드는 실제 라우트 구성에 테스트용 보호 화면을 더해 검증하고, user-event와 가짜 타이머는 shouldAdvanceTime으로 함께 쓴다 | 에이전트 결정 |
| [0043](0043-frontend-auth-required-sent-token.md) | 프론트: 401 AUTH_REQUIRED 공통 처리는 그 요청에 보낸 토큰이 지금 토큰과 같을 때만 한다 | 에이전트 결정 |
| [0044](0044-ask-policy-before-implementing.md) | 정책 결정(사용자가 겪는 동작, API 계약, 계정·인증·보안, Issue에서 정하기로 한 값, 규칙 모호·충돌)은 구현 전에 Issue 세션에서 사람에게 묻는다 | 채택 |
| [0045](0045-decision-status-by-decider.md) | 결정 상태는 결정한 사람으로 정한다: '제안'은 물었을 때만, 확인받지 않은 에이전트 결정은 '에이전트 결정', 병합은 확인이 아니다 | 채택 |
| [0046](0046-venue-seat-layout.md) | 좌석 배치는 공연장마다 다르고, 통로와 빈자리까지 표현한다 | 채택 |
| [0047](0047-seat-grid-position.md) | 좌석 응답 Seat에 gridRow·gridColumn(1부터)을 더한다 | 채택 |
| [0048](0048-venue-performance-schema.md) | 공연장·배치·공연·공연 좌석 테이블: 공연 좌석은 공연장 배치를 복사하고, 제약은 공연 좌석에 직접 건다 | 에이전트 결정 |
| [0049](0049-performance-seed.md) | 공연 시드: 배치가 다른 공연장 2곳, 공연 3개, 모든 좌석 AVAILABLE. 시드 테스트는 값이 아닌 성질만 본다 | 에이전트 결정 |
| [0050](0050-performance-detail-id-errors.md) | 공연 상세의 performanceId: long으로 해석할 수 없으면 400 INVALID_INPUT, 0·음수는 없는 공연(404) | 채택 |
| [0051](0051-seat-grid-orientation-and-size.md) | 좌석 배치 격자: 1번째 줄이 무대 쪽이고, 격자 크기는 좌석 위치의 최댓값으로 정한다 | 채택 |
| [0060](0060-frontend-query-screens.md) | 프론트: 공연 목록·내 예매 화면 표시, 공통 헤더, 로그아웃 | 채택 |
| [0061](0061-frontend-header-login-logout.md) | 프론트: 헤더 로그인 뒤에는 보던 화면으로, 로그인 화면에도 헤더, 로그아웃 뒤 안내 문구 없음 | 채택 |
| [0062](0062-frontend-load-status-role.md) | 프론트: 화면 제목·항목 배치, 로딩·빈 목록 문구는 role="status"이고 오류 문구만 role="alert" | 에이전트 결정 |
| [0063](0063-frontend-header-token-subscription.md) | 프론트: 헤더의 로그인 상태는 토큰 저장소의 변경 알림으로 다시 그리고, 로그아웃은 일반 이동(push)으로 보낸다 | 에이전트 결정 |
| [0064](0064-frontend-date-format-intl.md) | 프론트: 일시는 Intl.DateTimeFormat(Asia/Seoul)의 부분을 받아 직접 이어 붙인다 | 에이전트 결정 |
| [0065](0065-frontend-load-hook-and-screen-tests.md) | 프론트: 화면 데이터는 useLoad 훅으로 불러오고, 화면 테스트는 라우터 위치가 아니라 그려진 화면을 기다린다 | 에이전트 결정 |
