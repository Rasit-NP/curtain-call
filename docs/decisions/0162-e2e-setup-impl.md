# 0162. E2E 구성: 백엔드는 따로 둔 소스 세트의 실행기(bootE2eRun)가 Testcontainers DB를 붙여 띄우고, 포트는 설정 파일이 고른 빈 포트를 환경변수로 작업자와 나눈다

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #59 '에이전트가 정하는 것': `e2e/` 패키지 구성. 결정 0161을 구현하는 방법)
- 관련: Issue [#59](https://github.com/Rasit-NP/curtain-call/issues/59), 결정 [0016](0016-integration-test-base.md), [0161](0161-e2e-environment.md),
  [0163](0163-e2e-test-writing.md)

## 결정

- **임시 DB**: backend에 소스 세트 `e2eRun`(`src/e2eRun`)을 따로 두고, 그 안의 실행기 `com.curtaincall.e2e.E2eApplication`을 Gradle 작업 `bootE2eRun`으로 띄운다.
  실행기는 `SpringApplication.from(CurtainCallApplication::main).with(E2eDatabase.class).withAdditionalProfiles("e2e")`이고,
  `E2eDatabase`는 `@ServiceConnection`을 붙인 PostgreSQL 17 컨테이너(Testcontainers)다. Flyway가 그 빈 DB에 마이그레이션과 시드를 넣는다.
  `e2eRun`은 운영 코드와 운영 의존성에 `spring-boot-testcontainers`, `testcontainers-postgresql`만 더한다. 테스트 클래스는 클래스패스에 없다.
  `testClasses`가 `e2eRunClasses`에 기대게 해, quick 검증(단위 테스트)에서도 실행기가 컴파일된다.
- **포트**: `playwright.config.ts`가 127.0.0.1의 빈 포트 둘(백엔드, 프론트엔드)을 한 Node 프로세스에서 함께 열어 고르고(서로 다르게) 닫은 뒤,
  `E2E_BACKEND_PORT`·`E2E_FRONTEND_PORT` 환경변수에 둔다. 설정 파일을 다시 읽는 작업자 프로세스는 실행기의 환경을 물려받아 같은 값을 쓴다.
  환경변수를 미리 주면 그 값을 쓴다. DB 포트는 Testcontainers가 고른다.
- **서버**(`webServer` 둘, 모두 `reuseExistingServer: false`, 끝낼 때 SIGTERM):
  - 백엔드: `./gradlew bootE2eRun --args='--server.address=127.0.0.1 --server.port=<포트> --logging.file.name=../e2e/test-results/backend.log'`,
    준비 확인 `/actuator/health`(결정 0015), 3분.
  - 프론트엔드: `npm run dev -- --host 127.0.0.1 --port <포트> --strictPort`, 환경변수 `CURTAINCALL_API_TARGET`으로 Vite 프록시 대상을 위 백엔드로 바꾼다. 1분.
  - baseURL은 프론트엔드 주소 하나다. 테스트의 API 호출(`request` 픽스처)도 프론트엔드 개발 서버의 `/api`로 보낸다.
- **verify.sh e2e**: Docker 확인(`docker info`) → 프론트엔드 의존성(없을 때만 `npm ci`) → E2E 의존성과 Chromium → 타입 검사(`npm run typecheck`) → `npx playwright test`.
  compose 개발 DB를 띄우던 단계는 뺐다. 실패 요약의 리포트 칸에 백엔드 로그 경로를 함께 적는다. 하네스 테스트에 이 순서와 '개발 DB를 띄우지 않음'을 더했다.
- **패키지**: `@playwright/test` 1.65.1(2026-11-13 출시, 지금 최신 안정판), `typescript` 6.0(프론트엔드와 같은 판, 타입 검사만), `@types/node` 24(프론트엔드와 같음).
  모듈 형식은 정하지 않는다(CommonJS). Playwright가 TypeScript를 직접 읽는다.
- **compose.yaml**: `name: curtain-call`(결정 0161). 서비스 정의는 그대로라 메인 체크아웃의 컨테이너를 다시 만들지 않는다.

## 근거

- 실제로 띄워 확인했다(사람의 8080·5432와 겹치지 않는 18931 포트).
  - `bootE2eRun`이 새 컨테이너를 띄우고 마이그레이션·시드 8개를 넣은 뒤 약 3초 만에 떴다. 초기화 204, 공연 목록, 로그인 200.
  - Gradle 클라이언트의 프로세스 그룹에 SIGKILL을 보내도(Playwright가 정상 종료에 실패하면 쓰는 방법) Gradle 데몬이 빌드를 취소해 앱 JVM이 1초 안에 끝났다.
    컨테이너는 지워졌고 정리 컨테이너(Ryuk)도 2초 안에 끝났다.
  - E2E를 돌린 뒤 2초 안에 개발 DB 말고 남은 컨테이너·프로세스가 없었다.
- 소스 세트를 따로 둔 이유: Spring Boot가 권하는 '테스트 클래스패스로 띄우기'(`bootTestRun`)를 쓰면 테스트 소스의 시험용 컨트롤러 4개와
  `IntegrationTestConfig`(`@TestConfiguration`, 고정 시계 `@Primary`)가 운영 애플리케이션의 컴포넌트 스캔에 걸린다.
  `@TestConfiguration`을 스캔에서 빼는 필터는 스프링 테스트 컨텍스트에서만 등록된다.
- Testcontainers: 통합 테스트가 이미 쓰는 방법이고(결정 0016), JVM이 강제로 끝나도 Ryuk가 컨테이너를 지운다.
- 포트를 실행기에서 고르는 이유: Playwright는 `webServer`를 `globalSetup`보다 먼저 띄우므로 `globalSetup`에서 고를 수 없다.
  설정 파일은 실행기와 작업자에서 따로 읽히므로, 고른 값을 환경변수로 넘기지 않으면 작업자가 다른 포트를 고른다.
  작업자가 같은 값을 쓰는 것은 E2E가 통과하는 것으로 확인된다(다른 포트면 `request`가 서버에 닿지 않는다).
- 127.0.0.1에만 연다: E2E 서버를 다른 기기에 열 이유가 없다.
- 백엔드 로그를 파일로: Playwright 출력(`.verify-logs/e2e-test.log`)에 섞으면 verify.sh의 '첫 오류로 보이는 줄'이 정상 로그의 단어를 잡을 수 있다.
  `test-results/`는 Playwright가 실행을 시작할 때(서버를 띄우기 전에) 비운다.
- `docker info`를 먼저: Docker가 꺼져 있으면 백엔드가 뜨지 않아 Playwright가 '서버를 띄우지 못했다'고만 알린다. 원인을 첫 단계에서 바로 보이게 한다.
- 타입 검사: Playwright는 TypeScript를 타입 검사 없이 실행하므로 잘못된 타입이 실행 중에야 드러난다.

## 고려했지만 고르지 않은 것

- `bootTestRun`(테스트 클래스패스로 띄우기): 위 근거.
- compose의 E2E 전용 서비스와 셸 스크립트: 실행마다 프로젝트 이름과 포트를 정하고 끝날 때 지우는 스크립트가 필요하고, 프로세스가 강제로 끝나면 컨테이너가 남는다.
  `npx playwright test`만으로는 돌지 않고 스크립트를 거쳐야 한다.
- 백엔드 출력을 Playwright 출력으로(`stdout: 'pipe'`): 위 '백엔드 로그를 파일로'.
- 고정 포트: 결정 0161에서 사람이 고르지 않았다.

## 적용과 남은 점

- 코드: `backend/src/e2eRun/`, `backend/build.gradle`(`e2eRun`, `bootE2eRun`), `e2e/playwright.config.ts`, `frontend/vite.config.ts`, `compose.yaml`, `scripts/verify.sh`, `scripts/harness.test.sh`.
- Testcontainers의 PostgreSQL·Ryuk 컨테이너는 모든 주소(0.0.0.0)의 임의 포트에 열린다. 통합 테스트와 같고, 실행 중에만 있는 테스트 데이터다.
- 고른 포트를 닫았다가 서버가 다시 여는 사이에 다른 프로세스가 그 포트를 가져가면 그 실행은 실패한다. 드물고, 다시 돌리면 새 포트를 고른다.
- CI(GitHub Actions)로 돌리는 것은 범위 밖이다(로드맵 '사람이 정할 것').
