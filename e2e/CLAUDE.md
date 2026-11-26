# E2E 규칙

## 구성

- `e2e/`는 독립된 npm 패키지다. Playwright(TypeScript)와 Chromium만 쓰고, Node 버전은 루트 `.nvmrc`를 따른다.
- DB는 `scripts/verify.sh e2e`가 `docker compose up -d --wait db`로 먼저 띄운다.
- `playwright.config.ts`의 `webServer`로 백엔드와 프론트엔드를 띄운다.
    - 백엔드: `cd ../backend && ./gradlew bootRun --args='--spring.profiles.active=e2e'`,
      준비 확인 `http://localhost:8080/actuator/health`, 대기 시간 3분
    - 프론트엔드: `cd ../frontend && npm run dev -- --port 5173 --strictPort`, 준비 확인 `http://localhost:5173`
    - `reuseExistingServer: !process.env.CI`: 로컬에서는 이미 떠 있는 서버를 쓰고, CI에서는 새로 띄운다.
- 테스트들이 DB 하나를 공유하므로 하나씩 순서대로 실행한다(`workers: 1`, `fullyParallel: false`).
- 리포터는 `list`와 `html`을 쓰고, html은 반드시 `open: 'never'`로 둔다.
  기본값은 실패 시 리포트 서버를 띄우고 기다리기 때문에 명령이 끝나지 않는다.
- 실패한 테스트만 스크린샷과 트레이스를 남긴다(`screenshot: 'only-on-failure'`, `trace: 'retain-on-failure'`).

## 명령 (`e2e/`에서 실행)

- 전체: `npx playwright test`
- 파일 하나: `npx playwright test tests/booking.spec.ts`
- 결과 위치: 리포트 `playwright-report/`, 스크린샷·트레이스 `test-results/`

## 테스트 데이터

- 모든 테스트는 `beforeEach`에서 `POST /api/test-support/reset`을 호출해 데이터를 초기화한다.
  다른 테스트가 남긴 데이터에 기대지 않는다.
- 로그인은 시드에 있는 E2E 전용 계정으로 한다. 시연용 계정은 쓰지 않는다.

## 시나리오

1. 로그인 → 공연 선택 → 좌석 2석 선점 → 결제 → 예매 완료 → 내 예매에서 확인
2. R1: 다른 사용자가 먼저 선점한 좌석을 고르면 안내 문구가 보이고 선점되지 않는다
3. R2: 4석을 고른 뒤에는 5번째 좌석을 고를 수 없다
4. R6: 로그인하지 않고 좌석 선택 화면에 들어가면 로그인 화면으로 이동한다

- 선점 만료(R3, R4)는 시간을 조작해야 하므로 E2E가 아니라 백엔드 테스트로 검증한다.

## 작성 규칙

- 요소는 `getByRole`, `getByLabel`로 찾는다. CSS 선택자와 고정 대기(`waitForTimeout`)는 쓰지 않는다.
- 다른 사용자의 행동은 브라우저를 하나 더 띄우지 않고 `request` 픽스처로 API를 호출해 만든다.
- 테스트 이름은 관련 규칙이 있으면 규칙 번호로 시작한다.
- 실패하면 `test-results/`의 오류 메시지와 스크린샷부터 확인한다.