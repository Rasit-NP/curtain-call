# E2E 규칙

## 구성

- `e2e/`는 독립된 npm 패키지다. Playwright(TypeScript)와 Chromium만 쓰고, Node 버전은 루트 `.nvmrc`를 따른다.
  TypeScript 컴파일러는 타입 검사(`npm run typecheck`)에만 쓴다. Playwright는 타입을 검사하지 않고 읽는다.
- E2E는 실행마다 이 체크아웃의 백엔드·프론트엔드·임시 DB를 새로 띄우고, 끝나면 내린다(결정 0161).
  사람이 띄운 서버(8080·5173)와 개발 DB(compose `db`, 5432)는 쓰지도 건드리지도 않는다.
- `playwright.config.ts`가 127.0.0.1의 빈 포트 둘을 골라 `E2E_BACKEND_PORT`·`E2E_FRONTEND_PORT`에 두고(미리 주면 그 값을 쓴다),
  `webServer`로 두 서버를 띄운다(결정 0162). 워크트리 여러 곳에서 동시에 돌려도 부딪히지 않는다.
    - 백엔드: `cd ../backend && ./gradlew bootE2eRun --args='--server.address=127.0.0.1 --server.port=<포트> ...'`.
      e2e 프로필로 뜨고, DB는 실행마다 새로 만드는 PostgreSQL 컨테이너(Testcontainers)다. Flyway가 마이그레이션과 시드를 넣는다.
      준비 확인 `/actuator/health`, 대기 시간 3분. 로그는 `test-results/backend.log`에 남는다.
    - 프론트엔드: `cd ../frontend && npm run dev -- --host 127.0.0.1 --port <포트> --strictPort`, 준비 확인은 그 주소, 대기 시간 1분.
      `CURTAINCALL_API_TARGET`으로 `/api`를 위 백엔드로 넘긴다(`frontend/vite.config.ts`).
    - 이미 떠 있는 서버는 다시 쓰지 않는다(`reuseExistingServer: false`). 끝날 때 SIGTERM으로 내리고, 임시 DB는 백엔드와 함께 지워진다
      (프로세스가 강제로 끝나도 Testcontainers의 정리 컨테이너가 지운다).
- 임시 DB를 띄우므로 Docker가 떠 있어야 한다. `scripts/verify.sh e2e`가 먼저 `docker info`로 확인한다.
- 테스트의 API 호출(`request` 픽스처)도 baseURL(프론트엔드 개발 서버)의 `/api`로 보낸다.
- 테스트들이 DB 하나를 공유하므로 하나씩 순서대로 실행한다(`workers: 1`, `fullyParallel: false`). `.only`가 남아 있으면 실패한다(`forbidOnly`).
- 리포터는 `list`와 `html`을 쓰고, html은 반드시 `open: 'never'`로 둔다.
  기본값은 실패 시 리포트 서버를 띄우고 기다리기 때문에 명령이 끝나지 않는다.
- 실패한 테스트만 스크린샷과 트레이스를 남긴다(`screenshot: 'only-on-failure'`, `trace: 'retain-on-failure'`).

## 명령 (`e2e/`에서 실행)

- 처음 한 번: `npm ci`, `npx playwright install chromium`, 프론트엔드 의존성(`cd ../frontend && npm ci`). `scripts/verify.sh e2e`는 없으면 설치한다.
- 전체: `npx playwright test` (서버와 DB를 따로 띄우지 않는다)
- 파일 하나: `npx playwright test tests/booking.spec.ts`
- 타입 검사: `npm run typecheck`
- 결과 위치: 리포트 `playwright-report/`, 스크린샷·트레이스·백엔드 로그(`backend.log`) `test-results/`

## 테스트 데이터

- 모든 테스트는 `beforeEach`에서 `POST /api/test-support/reset`을 호출해 데이터를 초기화한다(`tests/support/api.ts`의 `resetData`).
  `beforeEach`는 spec 파일마다 둔다. 도우미 모듈 안에서 등록하면 그 모듈을 처음 가져온 파일에만 걸린다. 다른 테스트가 남긴 데이터에 기대지 않는다.
- 공연·좌석은 초기화 뒤의 id나 시드 값(제목, 좌석 수)을 가정하지 않고 공연 목록·상세 API로 찾는다(`findPerformance`).
  화면에서 공연을 고를 때는 공연 목록 화면의 위치로 고른다(화면은 API 순서대로다. 제목이 같은 회차가 있다).
- 로그인은 시드에 있는 E2E 전용 계정(`tests/support/accounts.ts`)으로 화면에서 한다. 시연용 계정은 쓰지 않는다.

## 시나리오

1. 로그인 → 공연 선택 → 좌석 2석 선점 → 결제 → 예매 완료 → 내 예매에서 확인(`tests/booking.spec.ts`)
2. R1: 다른 사용자가 먼저 선점한 좌석을 고르면 안내 문구가 보이고 선점되지 않는다(`tests/seat-conflict.spec.ts`)
3. R2: 4석을 고른 뒤에는 5번째 좌석을 고를 수 없다(`tests/seat-limit.spec.ts`)
4. R6: 로그인하지 않고 좌석 선택 화면에 들어가면 로그인 화면으로 이동한다(`tests/auth-guard.spec.ts`)

- 화면 흐름의 자세한 단계는 `docs/test-matrix.md` 'E2E 시나리오 대응'에 있다.
- 선점 만료(R3, R4)는 시간을 조작해야 하므로 E2E가 아니라 백엔드 테스트로 검증한다. 같은 멱등 키 재요청(R5)도 화면으로 만들 수 없어 백엔드 테스트가 맡는다.

## 작성 규칙

- 요소는 `getByRole`, `getByLabel`로 찾는다. CSS 선택자와 고정 대기(`waitForTimeout`)는 쓰지 않는다.
- 헤더와 이름이 겹치는 요소(예: '내 예매' 링크)는 화면 영역으로 좁힌다(`tests/support/ui.ts`의 `screenRegion`: 화면 제목이 이름인 region).
- 다른 사용자의 행동은 브라우저를 하나 더 띄우지 않고 `request` 픽스처로 API를 호출해 만든다.
- 테스트 이름은 관련 규칙이 있으면 규칙 번호로 시작한다.
- 실패하면 `test-results/`의 오류 메시지와 스크린샷부터 확인한다. 서버가 뜨지 않았으면 `test-results/backend.log`를 본다.
