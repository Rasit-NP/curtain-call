# #59 E2E 첫 설정(R1·R2·R6): Playwright 골격, e2e 프로필 데이터 초기화 API, 시나리오 1~4

- 기간: 2026-11-28 ~
- Issue: [#59](https://github.com/Rasit-NP/curtain-call/issues/59), PR: (만들면 적는다)
- 관련 결정: (정하면 적는다). 기존 [0011](../decisions/0011-dev-db-credentials.md), [0013](../decisions/0013-flyway-out-of-order-local.md),
  [0015](../decisions/0015-health-probes-off.md), [0036](../decisions/0036-seed-accounts.md), [0049](../decisions/0049-performance-seed.md)을 따른다

## 배경

로드맵 [#13](https://github.com/Rasit-NP/curtain-call/issues/13)의 6단계(마무리) 첫 항목이다. 백엔드(결제·멱등 키 #55·#56)와 프론트엔드(결제 화면 #52)가 모두 병합돼,
실제 브라우저·백엔드·DB로 처음부터 끝까지 도는 E2E를 처음 만든다.

- `e2e/`에는 `CLAUDE.md`만 있다. `e2e/package.json`이 생기면 `verify.sh`(전체)와 `verify.sh e2e`가 E2E를 돌린다.
- 백엔드 보안 설정은 `e2e` 프로필에서 `POST /api/test-support/reset`을 토큰 없이 열어 두었고, 컨트롤러는 없다.
- 시드(계정·공연장·공연·좌석)는 `db/seed`의 Flyway 마이그레이션(`V1000__`~)으로 `local`·`e2e`에서만 들어가고, 두 프로필은 같은 DB를 쓴다.
- 같은 6단계의 #61(정리)은 다른 세션에서 동시에 진행한다. 이 세션은 결정 번호를 0160부터 쓴다.

## 진행

1. Issue, 루트·영역별 `CLAUDE.md`, 명세(2.5, 6.7.2, 6.9, 6.10, 7장), 대응표, 관련 결정(0011, 0013, 0015, 0016, 0036, 0049), 검증 스크립트와 하네스 테스트를 읽었다.
   워크트리 브랜치는 이미 `origin/main`(`70bb9cc`)과 같아 이름만 `feat/59-e2e-setup`으로 바꿨다.
2. 지금 이 기계의 상태를 확인했다. 사람의 백엔드(IntelliJ, `local` 프로필)가 8080에, 메인 체크아웃의 compose 개발 DB(`curtain-call-db-1`)가 5432에 떠 있다.
   이 워크트리에서 `docker compose up -d db`를 하면 compose 프로젝트 이름이 폴더 이름(`distracted-bun-99a09c`)이 되어 DB를 하나 더 만들려다 5432에서 충돌한다.
3. Issue의 '아직 정하지 않은 정책' 1번(E2E 환경과 로컬 개발 환경)을 구현 전에 이 세션에서 물었다(아래 '정책 결정(사람)'). 다른 정책 질문은 찾지 못했다.
   답을 기다리는 동안 답과 상관없는 백엔드 초기화 API를 진행했다.
4. 초기화 API를 테스트부터 썼다. 컨트롤러·서비스는 '구현 전' 예외를 던지는 뼈대로 두고 프로필 조건도 붙이지 않았다.
   - 시드 실행 코드를 테스트 도우미(`support.SeedFiles`)에서 운영 코드(`testsupport.infra.SeedScripts`)로 옮기고 도우미는 그것을 부르게 했다. 초기화와 시드 테스트가 같은 코드로 시드를 넣는다.
     옮긴 뒤에도 순서 테스트(`SeedFilesTest`) 5개가 그대로 통과했다.
   - `E2eProfileAuthTest`의 '토큰 없이 부를 수 있다'는 컨트롤러가 없을 때 404를 기대했다(이름에 '컨트롤러가 없으면 404'). 명세 6.9와 Issue 완료 조건대로 204를 기대하게 바꿨다.
   - 실패 확인: 단위 4개(local·test·prod·프로필 없음에서 컨트롤러가 등록됨), 통합 11개(초기화 8개와 `E2eProfileAuthTest` 1개는 뼈대의 500, `AuthenticationTest`의 test 프로필 404가 500,
     test 프로필 컨텍스트에 `testDataResetController`가 있음). 초기화 테스트의 준비 단계(데이터 만들기, 초기화 전 확인)는 모두 통과해 테스트가 의도한 상황을 만드는 것을 확인했다.
5. 구현했다(결정 [0160](../decisions/0160-e2e-reset-impl.md)). 대상 테스트가 모두 통과했고, `./scripts/verify.sh backend`도 통과했다(단위 153개, 통합 307개).
6. 잘못된 구현을 잡는지 변형 하나로 확인했다. 시드 테이블은 두고 선점·결제 기록만 지운 뒤 좌석만 `AVAILABLE`로 되돌리게 바꾸자 `TestDataResetApiTest` 8개 중 7개가 실패했다(204만 보는 테스트만 통과).
   원래 코드로 되돌리고 파일이 같은지 확인했다.

## 정책 결정(사람)

Issue의 '아직 정하지 않은 정책' 1번을 구현 전에 물었다. 지금 기계의 상태(8080의 IntelliJ 백엔드, 5432의 개발 DB, 워크트리의 compose 충돌)를 먼저 보이고, 선택지 셋에 추천과 근거를 붙였다.

1. (추천) E2E는 늘 자기 전용 환경(이 체크아웃의 백엔드·프론트엔드·임시 DB)을 새로 띄우고 끝나면 내린다. 포트는 실행마다 빈 포트를 고른다.
   `e2e` 프로필에서 개발 DB 주소 기본값을 빼고, Vite 프록시 대상은 기본값(8080)을 두고 환경변수로만 바꾸며, `compose.yaml`에 `name: curtain-call`을 넣는다.
2. 1과 같지만 E2E 전용 고정 포트. 한 번에 하나의 E2E만 돈다.
3. 지금 규칙을 둔다(8080·5173 재사용, `local`·`e2e`가 같은 DB). E2E 전에 사람이 서버를 끄고, 초기화가 개발 데이터를 지우는 것을 받아들인다.

답: (기다리는 중)
