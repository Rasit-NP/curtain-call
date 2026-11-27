# #67 CI(GitHub Actions): PR과 main push마다 verify.sh 전체 실행, 캐시, 이전 실행 취소

- 기간: 2026-11-28 ~
- Issue: [#67](https://github.com/Rasit-NP/curtain-call/issues/67), PR: [#68](https://github.com/Rasit-NP/curtain-call/pull/68)
- 관련 결정: [0190](../decisions/0190-ci-scope-docs-only.md), [0191](../decisions/0191-ci-workflow-security.md), [0192](../decisions/0192-ci-in-workflow.md)(사람),
  [0193](../decisions/0193-ci-workflow-impl.md)(에이전트, 0162의 'CI는 범위 밖' 대체). 기존 [0001](../decisions/0001-verify-single-entry.md), [0002](../decisions/0002-stop-hook-quick.md),
  [0161](../decisions/0161-e2e-environment.md), [0162](../decisions/0162-e2e-setup-impl.md), [0183](../decisions/0183-github-actions.md), [0186](../decisions/0186-handoff-roadmap-issue-scope.md)을 따른다

## 배경

로드맵 2 [#64](https://github.com/Rasit-NP/curtain-call/issues/64)의 1단계 첫 항목이다. 사람이 GitHub Actions 도입을 정했다(결정 0183).
지금까지 검증은 각 세션이 로컬에서 돌린 `verify.sh`와 Stop 훅뿐이었고, 결과는 PR 본문에만 남았다.

- `verify.sh`와 하네스(`scripts/harness.test.sh`)는 macOS에서만 돌았다. 통합 테스트와 E2E는 Testcontainers로 DB를 띄워 Docker가 필요하다.
- 저장소가 비공개라 Actions 실행 시간에 월 한도가 있다.
- 결정 0162는 'CI(GitHub Actions)로 돌리는 것은 범위 밖'이라고 적었다. 이 Issue에서 새 결정으로 대체한다.

## 진행

1. Issue, 루트·영역별 `CLAUDE.md`, 결정 목차와 관련 결정(0001, 0002, 0161, 0162, 0183, 0186), `verify.sh`·하네스·PR 템플릿을 읽었다.
   워크트리 브랜치는 이미 `origin/main`(`4349942`)과 같아 이름만 `chore/67-ci-github-actions`로 바꿨다.
2. 물을 때 보일 사실을 모았다.
   - 저장소는 비공개다. Actions 설정은 '모든 액션 허용', SHA 고정 강제 없음, 워크플로 기본 토큰 권한 read이고, 워크플로는 하나도 없다.
   - 계정 요금제는 API(`gh api user`)로 보이지 않았다(`plan`이 null). 사람에게 묻는다.
   - 로드맵 1의 PR은 25개(2026-11-26~27, 약 36시간)였고 PR마다 커밋이 1~11개였다. `docs/`만 바꾼 PR은 #40, #49 두 개였다.
   - 루트 `CLAUDE.md`는 백엔드 `ErrorCodeTest`와 프론트엔드 `errorMessages.test.ts`가 읽는다(오류 코드 표). `docs/`를 읽는 테스트는 없다.
   - 다른 워크트리에 남은 `.verify-logs/`의 파일 시각으로 보면 이 Mac에서 전체 검증은 2분 안팎이다(통합 테스트 36초, 프론트엔드 9초, E2E 15초).
3. Issue의 '아직 정하지 않은 정책' 1~3번을 구현 전에 한 번에 물었다(아래 '정책 결정(사람)'). 사람이 모두 추천안을 골랐다.
4. 브랜치 보호를 쓸 수 있는지 API로 확인했다. `GET /repos/…/branches/main/protection`이 403("Upgrade to GitHub Pro or make this repository public to enable this feature")이었다.
   Free 요금제의 비공개 저장소에서는 브랜치 보호를 쓸 수 없어, 사람이 저장소를 공개로 바꾼 뒤에 설정할 수 있다. 사람에게 알린다.
5. 하네스 테스트 6개부터 썼다(워크플로 규칙 검사 4개, `docs/`만 바뀌었는지 판단하는 스크립트 2개). 워크플로와 스크립트가 없어 6개 모두 실패하는 것을 확인했다.
6. 쓸 액션의 최신 판과 커밋 SHA를 API로 확인했다(2026-11-28): checkout v7.0.1, setup-java v6.3.0, setup-node v7.1.0, cache v6.1.0, upload-artifact v7.0.2.
   모두 node24로 돈다. 판마다 `action.yml`의 입력 이름과 큰 판의 릴리스 노트를 읽고 썼다.
   upload-artifact는 v4.4부터 점으로 시작하는 파일을 빼므로 `.verify-logs/`를 올리려면 `include-hidden-files: true`가 필요하다.
7. 워크플로(`.github/workflows/ci.yml`)와 범위 판단 스크립트(`scripts/ci-scope.sh`)를 써서 6개를 통과시켰다(결정 [0193](../decisions/0193-ci-workflow-impl.md)).
8. 검사가 실제 위반을 잡는지 워크플로와 스크립트를 하나씩 망가뜨려 확인하고 되돌렸다. 14가지 모두 해당 테스트가 실패했다.
   - 워크플로: checkout을 태그(`@v7`)로, 공식이 아닌 액션, `persist-credentials` 줄 삭제, `contents: write`, `pull-requests: write` 추가, 잡에 `write-all`,
     `pull_request_target` 트리거, `./gradlew test`·`npm run test` 직접 호출, 요약 표시 줄 불일치.
   - 스크립트: `--no-renames` 삭제(코드를 `docs/`로 옮긴 PR이 하네스만 돎), `core.quotePath` 기본값(한글 파일 이름이 따옴표로 나와 문서 PR이 전체로 돎),
     '바뀐 파일 없음'과 '알 수 없음'을 하네스로.
   - 처음에는 `contents: write` 변형이 통과했다. 변형이 워크플로 맨 위 주석의 같은 문구를 바꿨기 때문이었고, 권한 줄을 바꾸자 실패했다.
9. 러너는 UTC·영어 로캘이고 이 Mac은 KST·한국어(JVM `ko_KR`)다. CI 실행 횟수를 줄이려고 먼저 로컬에서 환경을 바꿔 돌렸다.
   - 백엔드: `JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US -Duser.timezone=UTC'`, `TZ=UTC`로 단위 153개·통합 307개 모두 통과(49초).
     Gradle 데몬을 쓰지 않아(`-Dorg.gradle.daemon=false`) 다른 세션의 데몬에 이 설정이 남지 않게 했다.
   - 프론트엔드: `TZ=UTC`로 220개 모두 통과.
10. 결정 기록(0190~0193), 루트 `CLAUDE.md`('구성', '명령'의 'CI', '작업 흐름' 4번, '실패 처리' 1번, '완료 조건'), PR 템플릿('검증 결과'의 CI 줄, 체크리스트)을 고쳤다.
    루트 `CLAUDE.md`는 이미 224줄로 맨 위 주석의 '200줄 이하'를 넘어 있어, CI 내용은 꼭 필요한 것만 7줄로 더했다.

11. 로컬 `./scripts/verify.sh` 전체가 통과했다(26초. 통합 테스트는 바로 앞 UTC·영어 실행에서 307개가 돌아 UP-TO-DATE로 재사용).
12. 워크플로 커밋만 올려 초안 PR [#68](https://github.com/Rasit-NP/curtain-call/pull/68)을 열고, 실행이 시작된 뒤 문서 커밋을 올렸다.
    첫 실행([46065866907](https://github.com/Rasit-NP/curtain-call/actions/runs/46065866907))이 `cancelled`로 끝나 같은 PR의 이전 실행 취소를 확인했다.
13. 실행이 도는 동안 `/code-review medium`으로 리뷰했다(아래 '리뷰').
14. 캐시 없는 실행 [46065904319](https://github.com/Rasit-NP/curtain-call/actions/runs/46065904319)(비공개일 때)가 처음부터 통과했다. 잡 4분 1초, `verify.sh` 단계 3분 36초.
    - 단계별(로그의 줄 시각): 하네스 1초, 백엔드 단위 61초(Gradle 배포판·의존성 내려받기와 컴파일 포함), 통합 77초, 빌드·커버리지 4초,
      프론트엔드 의존성 3초·타입 검사 4초·린트 3초·테스트(커버리지) 26초·빌드 4초, E2E 의존성과 Chromium 9초·타입 검사 1초·테스트 22초.
    - 캐시 4개(Gradle 의존성, Gradle 래퍼, npm, Playwright 브라우저)가 끝에서 저장됐다(12초). JDK 21은 러너에 있던 것을 썼다(`Resolved Java 21.0.13+2 from tool-cache`).
    - Linux 러너에서만 드러난 `verify.sh`·하네스 결함은 없었다. 하네스 25개, 백엔드, 프론트엔드, E2E(Playwright 시스템 라이브러리 설치 없이)가 그대로 통과했다.
15. 사람이 저장소를 공개로 바꿨다(2026-11-28, "저장소를 public으로 전환했습니다"). 영향을 확인해 결정 기록에 반영했다.
    - 표준 러너 실행 시간이 무료가 됐다(0190). 브랜치 보호를 이제 설정할 수 있다. 필수 검사 이름은 `verify`이고 설정은 사람이 한다(0192).
    - 포크 PR도 `pull_request`로 돌지만 읽기 토큰만 받고 비밀값이 없다. 포크 PR이 만든 캐시는 main 캐시를 바꾸지 못한다(0191).
    - 실행 로그와 실패 산출물을 누구나 본다. 비밀값을 쓰지 않아 새로 드러나는 것은 없다(0193).
    - GitHub 문서 기준으로 공개 저장소의 러너가 더 커서(2코어→4코어), 실행 B(비공개)와 이후 실행(공개)의 시간을 그대로 비교할 수 없다.
      같은 조건의 캐시 없는 실행은 병합 뒤 main push가 된다. PR에서 만든 캐시는 main이 쓰지 못하기 때문이다.
16. 실패를 잡는지 확인 1: 임시 커밋으로 백엔드 R2 한도(`HoldLimit.MAX_SEATS_PER_PERFORMANCE`)를 4→5로, 프론트엔드 `HOLD_LIMIT_EXCEEDED` 안내 문구의 '4석'을 '5석'으로 바꿨다.
    실행 [46066634270](https://github.com/Rasit-NP/curtain-call/actions/runs/46066634270)(공개 뒤 첫 실행, 잡 1분 55초)이 의도대로 실패했다.
    - 캐시 4개가 모두 적중했다(`Cache hit for: setup-java-…-gradle-…`, `…-gradle-wrapper-…`, `node-cache-…-npm-…`, `playwright-Linux-…`). 백엔드 단위 단계가 61초에서 19초로 줄었다.
    - `backend-unit`·`backend-integration`·`backend-build`·`frontend-test`가 FAIL, E2E는 '앞 단계 실패'로 SKIP. 실패 상세의 첫 오류 줄이 R2 테스트 이름(`HoldLimitTest`, `HoldApiTest`, 좌석 선택 화면 R2 테스트 등)을 정확히 보였다.
    - 산출물 `verify-failure-1`(358KB, 7일 뒤 만료)에 `.verify-logs/` 9개와 백엔드 테스트 리포트·결과 XML이 있었다.
    - 되돌림 커밋(`git revert`)으로 원래대로 돌렸다. 되돌린 뒤 `backend/`·`frontend/`는 main과 같다.
17. 러너에서만 드러난 결함을 찾았다: 실패 상세와 산출물의 `frontend-test.log`에 색 코드가 149줄 섞여 있었다(로컬 로그에는 없음). 로컬에서는 `CI=true`, `GITHUB_ACTIONS=true`로도 재현되지 않았고,
    `FORCE_COLOR=1`로 색을 강제해 같은 증상을 만들었다. `NO_COLOR=1`을 더하면 Vitest 출력의 색은 사라지지만 커버리지 표(istanbul)와 Rolldown 빌드 출력은 `FORCE_COLOR`를 따랐다.
    - 하네스 테스트(색을 켜는 환경에서 단계 명령이 받는 `NO_COLOR`·`FORCE_COLOR`)를 먼저 써 실패를 확인하고, `verify.sh`가 `NO_COLOR=1`을 넘기고 `FORCE_COLOR`를 지우게 고쳤다.
      `unset FORCE_COLOR` 줄만 빼도 이 테스트가 실패하는 것을 확인했다. `FORCE_COLOR=1 CI=true ./scripts/verify.sh frontend`에서 프론트엔드 로그 5개의 색 코드가 0줄이 됐다. `docs/agent-log.md`에 적었다.
18. 실행 화면 요약(Job Summary)은 로그인한 사람에게만 보여, 공개 저장소를 로그인하지 않은 브라우저로 열면 보이지 않았다(API로도 읽을 수 없다).
    요약 단계가 같은 내용을 단계 로그에도 남기게 했다(`tee -a`). 에이전트는 `gh run view --log`로 같은 요약을 본다.
19. 캐시 없는 실행이 4분 안팎이라 시간 제한을 `verify.sh` 단계 25분→15분, 잡 30분→20분으로 줄였다.
20. 실패를 잡는지 확인 2: 임시 커밋으로 E2E 시나리오 4(`auth-guard.spec.ts`)가 없는 제목('로그인 화면')을 기다리게 했다. 앞 단계는 모두 통과해야 E2E가 돈다.
    실행 [46067334152](https://github.com/Rasit-NP/curtain-call/actions/runs/46067334152)(잡 2분 34초, `verify.sh` 2분 14초)이 의도대로 실패했다.
    - 캐시 4개가 모두 적중했다. 모든 단계를 끝까지 돈 캐시 적중 실행이다: 백엔드 단위 25초, 통합 56초, 프론트엔드 테스트 10초, E2E 의존성 1초, E2E 테스트 22초.
    - `e2e-test`만 FAIL. 요약 단계 로그(실행 화면 요약과 같은 내용)에 실패 상세가 남았다: `✘ … tests/auth-guard.spec.ts:12:1 › R6: …`, `Locator: getByRole('heading', { name: '로그인 화면', level: 1 })`, `Error: element(s) not found`.
    - 산출물에 Playwright 리포트(`playwright-report/index.html`, 트레이스 보기 포함), 실패한 테스트의 스크린샷·`trace.zip`·`error-context.md`, `backend.log`, `.verify-logs/` 14개가 있었다.
    - 17번의 고침을 CI에서 확인했다: `.verify-logs/` 14개 모두 색 코드 0줄(고치기 전 실행에서는 `frontend-test.log` 149줄).
21. E2E 임시 커밋을 `git revert`로 되돌렸다. 되돌린 뒤 `backend/`·`frontend/`·`e2e/`는 main과 같다. 로컬 `./scripts/verify.sh` 전체를 다시 통과시켰다
    (프론트엔드 220개·E2E 4개 실행, 백엔드는 입력이 같아 UP-TO-DATE, 로그 색 코드 0줄). 되돌린 뒤 마지막 실행의 링크와 결과는 PR 본문에 적는다.

## 리뷰

- 강도 `medium`: CI 설정은 `low`지만, 하네스에 검사 6개(140줄)를 더하고 검증 범위를 정하는 스크립트를 새로 만들어 루트 `CLAUDE.md` '리뷰'의 '하네스를 크게 고쳤으면 medium'을 따랐다.
- 지적 2개, 모두 사소함(nit).
  - 루트 `CLAUDE.md`가 맨 위 주석의 '200줄 이하'를 더 넘는다(224→231줄): 이 PR에서는 꼭 필요한 줄만 더했고, 줄이는 일은 범위 밖이라 후속으로 남긴다.
  - 범위 단계가 `all`·`harness`가 아닌 값을 내면 설치 단계를 건너뛰고 `verify.sh`가 사용법 오류로 실패한다: 실패하는 방향이라 검증이 빠지지 않고, 스크립트는 늘 둘 중 하나만 낸다. 고치지 않았다.

## 정책 결정(사람)

Issue의 '아직 정하지 않은 정책' 1~3번을 구현 전에 한 번에 물었다. 질문마다 선택지, 추천, 근거를 붙였다.

1. **요금제와 실행 시간(1번)**: 계정 요금제를 물었다. 어림(전체 검증 1회 6~10분, PR 하나에 push 2~3번과 병합 뒤 main 1번으로 20~40분, PR 40개면 800~1,600분)을 함께 보였다.
   - 답: "Free입니다만, 이후에 public으로 공개해야 하기 때문에, 요금제는 크게 걱정하지 않아도 될 듯 합니다."(원문)
   - 공개 저장소는 표준 러너 실행 시간이 무료다. 공개 전까지는 월 2,000분 안에서 쓴다.
2. **문서만 바뀐 PR(1번)**: (1) PR이 바꾼 파일이 모두 `docs/` 아래면 `verify.sh harness`만(추천), (2) 늘 전체, (3) 건너뛰기.
   근거: 하네스가 결정·진행 기록 목차를 검사한다. 루트 `CLAUDE.md`는 백엔드·프론트엔드 테스트가 오류 코드 표를 읽어 문서로 치지 않는다. 같은 잡이 늘 돌아 결과를 남기므로 필수 검사로 걸어도 '대기'로 남지 않는다.
   - 답: (1).
3. **워크플로 보안(2번)**: (1) GitHub 공식 `actions/*`만, 커밋 SHA로 고정하고 버전은 주석에, 토큰은 `contents: read`, checkout이 토큰을 디스크에 남기지 않음, 하네스가 이 규칙을 검사(추천),
   (2) 도구 제작사 공식 액션까지, SHA 고정, (3) 공식 `actions/*`만, 태그 고정.
   근거: 필요한 일(체크아웃, Java·Node 설치와 캐시, 브라우저 캐시, 산출물 올리기)이 공식 액션으로 다 된다. 태그는 옮겨질 수 있다(2025년 tj-actions/changed-files 사건).
   - 답: (1).
4. **작업 흐름(3번)**: (1) PR 전 로컬 `verify.sh` 전체는 그대로 두고, PR을 만든 뒤 CI 결과를 확인하고 실패하면 고쳐 다시 통과시키는 것까지 완료 조건에(추천),
   (2) CI가 로컬 전체를 대신(PR 전에는 quick만), (3) 지금 흐름 그대로(CI는 참고만).
   근거: 이 Mac에서 로컬 전체는 2분 안팎이라 먼저 잡으면 CI 시간을 아낀다. CI에서만 나는 실패는 환경 차이라 분석해 기록할 거리다.
   - 답: (1).

## 결과

- 워크플로 `CI`의 잡 `verify`가 PR과 main push마다 `verify.sh`를 실행한다. 같은 PR의 이전 실행 취소, 캐시 4개 적중, 실패하면 요약과 산출물이 남는 것을 이 PR의 실행 기록으로 확인했다.
- 실행 시간: 캐시 없는 첫 실행(비공개, 2코어) 잡 4분 1초, 캐시가 맞은 실패 확인 실행(공개, 백엔드·프론트엔드 실패로 E2E 건너뜀) 잡 1분 55초. 모든 단계를 끝까지 돈 캐시 적중 실행(공개, E2E 하나만 실패시킨 실행) 잡 2분 34초. 러너 크기가 달라(2코어→4코어) 캐시 효과만 따로 떼어 말할 수는 없지만, 백엔드 단위 단계(의존성 내려받기 포함)는 61초→19~25초, E2E 의존성은 9초→1초였다.
- Linux 러너에서만 드러난 결함 1개(로그의 색 코드)를 재현 테스트부터 써서 고쳤다. 시간대·로캘 차이로 깨지는 테스트는 없었다.
- 남은 일(사람): main 브랜치 보호(필수 검사 `verify`), 허용할 액션 좁히기·SHA 고정 강제, 외부 기여자 포크 PR 승인 설정. 병합 뒤 main push 실행 결과는 메인 세션이 확인한다.

## 배운 점

- 러너와 로컬의 차이(시간대, 로캘)는 CI에 올리기 전에 로컬에서 환경 변수만 바꿔 먼저 돌려 보면 CI 실행을 아낄 수 있다. 다만 실제로 드러난 차이(색 코드)는 예상한 곳이 아니었다.
- 도구가 '로그가 파일이면 색을 끈다'고 기대하면 안 된다. CI를 알아보는 라이브러리는 터미널이 아니어도 색을 켠다. 로그를 파일로 남기는 쪽이 끄는 것을 정해야 한다.
- 검사를 망가뜨려 확인할 때는 변형이 의도한 줄을 바꿨는지 먼저 본다. 같은 문구가 주석에 있으면 엉뚱한 곳이 바뀌어 검사가 통과한다.
- CI를 시험하는 실행은 순서를 미리 짜면 적게 든다: 두 번째 push로 이전 실행 취소를, 첫 통과 실행으로 캐시 저장을, 앞 단계 실패 실행으로 캐시 적중과 실패 산출물을, E2E만 실패하는 실행으로 E2E 산출물을 한꺼번에 확인했다.
