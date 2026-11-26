# 결정 기록 (작업용)

에이전트가 작업 중에 지금 유효한 결정을 확인하는 문서다. 결정 하나에 파일 하나(`NNNN-제목.md`)를 둔다.
작업을 시작하기 전에 이 목차를 읽고, 관련 결정 파일을 연다. 판단의 자세한 경위는 [`docs/history/`](../history/README.md)에 있다.

## 쓰는 법

- 새 결정은 다음 번호로 파일을 만들고 이 목차에 한 줄을 더한다. 목차와 파일이 어긋나면 `verify.sh harness`가 실패한다.
- 항목: 상태, 날짜, 결정한 사람, 관련 Issue·PR, 결정, 근거, 고려했지만 고르지 않은 것, 적용과 남은 점.
- 상태: `채택`(병합됨), `제안`(PR 병합 전 또는 사람 확인 대기), `대체됨(→NNNN)`.
- 결정을 바꿀 때는 원래 파일을 고치지 않는다. 새 파일을 만들고 원래 파일의 상태만 `대체됨(→NNNN)`으로 바꾼다.
- PR이 병합되면 그 PR의 `제안` 결정을 `채택`으로 바꾼다.

## 목차

| 번호 | 결정 | 상태 |
|---|---|---|
| [0001](0001-verify-single-entry.md) | 검증은 verify.sh 하나로, 영역은 표식 파일로 판단 | 채택 |
| [0002](0002-stop-hook-quick.md) | 작업을 끝낼 때 Stop 훅으로 quick 검증, 같은 턴에 한 번만 막기 | 채택 |
| [0003](0003-hold-is-seat-bundle.md) | 선점 1건은 좌석 묶음(1~4석), 결제는 선점 1건 | 채택 |
| [0004](0004-hold-release.md) | 결제 전 다른 화면으로 가면 선점 해제, 창을 닫으면 만료까지 유지 | 채택 |
| [0005](0005-payment-failed-402.md) | 결제 거절은 402 PAYMENT_FAILED, 같은 키 재요청에는 같은 402 | 채택 |
| [0006](0006-idempotency-record.md) | 멱등 키는 사용자별, 검증을 통과한 결제 결과는 모두 저장, 같은 키 동시 요청은 기다림 | 채택 |
| [0007](0007-flat-api.md) | API는 평면 자원으로 | 채택 |
| [0008](0008-auth-required-only.md) | 프론트의 '401이면 토큰 삭제·로그인 이동'은 AUTH_REQUIRED에만 | 채택 |
| [0009](0009-seat-hold-id.md) | 좌석에 지금 잡은 선점(holdId)을 둔다 | 채택 |
| [0010](0010-security-in-auth-issue.md) | 첫 설정에서 Spring Security를 빼고 인증 Issue에서 한 번에 넣는다 | 채택 |
| [0011](0011-dev-db-credentials.md) | 로컬·E2E DB 접속 정보는 개발 전용 값으로 저장소에 둔다 | 채택(PR #8 병합으로 확인) |
| [0012](0012-backend-error-mapping.md) | 백엔드 오류 응답: 입력 오류 상태만 INVALID_INPUT, 기능 오류는 ApiException, 나머지는 500 | 채택 |
| [0013](0013-flyway-out-of-order-local.md) | local·e2e에서 Flyway out-of-order를 켠다 | 채택 |
| [0014](0014-error-code-table-test.md) | 오류 코드 enum은 루트 CLAUDE.md 표를 읽어 검사하고, Gradle 입력으로 선언한다 | 채택 |
| [0015](0015-health-probes-off.md) | health 응답을 {"status":"UP"}로 두려고 프로브 그룹을 끈다 | 채택 |
| [0016](0016-integration-test-base.md) | 통합 테스트는 컨테이너 하나 공유, 테스트 시계, 테이블 비우기 | 채택 |
| [0017](0017-java21-toolchain.md) | Java 21을 Gradle 툴체인으로 고정 | 채택 |
| [0018](0018-frontend-error-fallback.md) | 프론트: code가 없는 오류 응답은 INTERNAL_ERROR | 채택(PR #7 병합으로 확인) |
| [0019](0019-frontend-no-response.md) | 프론트: '응답 없음'은 프론트 전용 NO_RESPONSE, 본문 수신 중 끊김도 포함 | 채택 |
| [0020](0020-frontend-auth-option.md) | 프론트 API 클라이언트의 auth는 기본값 없는 필수 옵션 | 채택 |
| [0021](0021-frontend-lint-boundaries.md) | fetch는 src/api, localStorage는 src/auth에서만 쓰도록 ESLint로 막는다 | 채택 |
| [0022](0022-nvmrc-exact.md) | .nvmrc는 정확한 버전으로 고정 | 채택 |
| [0023](0023-review-depth-by-risk.md) | 리뷰 강도는 PR 위험도로 정하고 항상 명시 | 채택 |
| [0024](0024-docs-two-tracks.md) | 문서화를 작업용 결정 기록과 포트폴리오용 진행 기록으로 나눈다 | 제안(PR 병합 전) |
| [0025](0025-msw-unhandled-error-strategy.md) | 프론트 테스트: 핸들러 없는 요청은 MSW 'error' 전략으로 막고, 테스트가 끝날 때 처리 중인 요청을 기다린다 | 채택 |
