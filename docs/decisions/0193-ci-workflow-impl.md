# 0193. CI 구성: ubuntu-24.04의 잡 하나가 verify.sh를 부르고, 도구·캐시만 워크플로가 맡는다. 같은 PR의 이전 실행은 취소, 실패하면 요약과 산출물을 남긴다

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #67 '에이전트가 정하는 것': 워크플로 구성, 실패했을 때 남길 것, 워크플로를 검증하는 방법)
- 관련: Issue [#67](https://github.com/Rasit-NP/curtain-call/issues/67), 결정 [0001](0001-verify-single-entry.md), [0162](0162-e2e-setup-impl.md),
  [0183](0183-github-actions.md), [0190](0190-ci-scope-docs-only.md), [0191](0191-ci-workflow-security.md), [0192](0192-ci-in-workflow.md)

## 결정

- **잡**: `.github/workflows/ci.yml`(워크플로 `CI`)에 잡 `verify` 하나를 둔다. 러너는 `ubuntu-24.04`로 고정한다.
  트리거는 `pull_request`(열기·새 커밋·다시 열기, 기본 종류)와 main `push`다.
- **동시 실행**: 그룹을 PR마다 하나로 두고 새 커밋이 오면 이전 실행을 취소한다. main push는 커밋 SHA마다 그룹을 따로 두어 서로 취소하지도 기다리지도 않는다.
- **순서**: 체크아웃(`fetch-depth: 2`) → 검증 범위(`scripts/ci-scope.sh`) → 전체일 때만 Java 21(Temurin)·Node(`.nvmrc`) 설치와 캐시 →
  `./scripts/verify.sh <범위>`(출력을 파일에도 남김) → 요약 → 실패하면 산출물.
  Playwright 브라우저 설치는 지금처럼 `verify.sh`가 하고, 워크플로는 그 폴더의 캐시만 맡는다.
- **범위 판단**(결정 0190의 세부): PR은 병합 커밋(`HEAD`)과 첫 부모(`HEAD^1`, PR의 대상 브랜치)를 비교한다.
  이름 바꾸기는 지운 파일과 새 파일로 보고(`--no-renames`), 한글 파일 이름을 그대로 비교한다(`core.quotePath=false`).
  바뀐 파일을 알 수 없거나 바뀐 파일이 없으면 전체다.
- **캐시**: Gradle은 `setup-java`(키: `backend`의 Gradle 파일), npm은 `setup-node`(키: `frontend`·`e2e`의 lockfile 둘), Playwright 브라우저는 `actions/cache`(`~/.cache/ms-playwright`, 키: `e2e` lockfile).
- **시간 제한**: `verify.sh` 단계 15분, 잡 20분. 캐시 없는 첫 실행이 잡 4분 1초(`verify.sh` 3분 36초)여서 약 4배를 두었다. 단계가 먼저 끊겨야 요약과 산출물 단계가 돈다.
- **실패했을 때 남길 것**:
  - 실행 화면(Job Summary): `verify.sh` 출력의 `===== 검증 요약` 줄부터 끝까지(실패 상세 포함). 그 줄이 없으면(시간 제한, 취소) 출력 마지막 60줄.
  - 산출물 `verify-failure-<시도 번호>`, 7일 보관: `.verify-logs/`, `backend/build/reports/`, `backend/build/test-results/`, `frontend/coverage/`,
    `e2e/playwright-report/`, `e2e/test-results/`(트레이스, 스크린샷, `backend.log`). `verify.sh` 단계가 실패했을 때만 올린다.
- **워크플로 검증**: 하네스에 CI 검사를 더한다. `./scripts/verify.sh`를 부르고 검증 명령을 직접 부르지 않는지, 결정 0191의 규칙(공식 액션·SHA·버전 주석,
  맨 위 `contents: read`, 쓰기 권한 없음, checkout마다 `persist-credentials: false`), `pull_request_target`·`workflow_run` 트리거가 없는지,
  요약 표시 줄이 `verify.sh` 출력과 같은지, `ci-scope.sh`의 판단(문서만, 문서와 코드, 루트 `CLAUDE.md`, 코드를 `docs/`로 옮김, 바뀐 것 없음, 없는 커밋)을 본다.
- **결정 0162 '적용과 남은 점'의 'CI(GitHub Actions)로 돌리는 것은 범위 밖'을 이 결정으로 대체한다.** 0162의 결정 본문(E2E 구성)은 그대로 유효해 0162의 상태는 바꾸지 않는다.

## 근거

- 잡 하나: `verify.sh` 하나로 검증한다는 원칙(결정 0001)을 그대로 따른다. 영역별 잡으로 나누면 잡마다 체크아웃·설치를 되풀이하고 잡마다 분 단위로 올림해 실행 시간을 더 쓴다.
  '앞 단계가 실패하면 E2E를 건너뛴다'도 워크플로에서 다시 표현해야 한다.
- `ubuntu-24.04` 고정: `ubuntu-latest`는 새 Ubuntu로 바뀌는 날 시스템 라이브러리(예: 브라우저가 쓰는 것)가 예고 없이 달라진다. 올릴 때는 이 줄을 바꾸는 PR로 한다.
- 범위 판단: PR 이벤트의 체크아웃은 병합 커밋이라, 첫 부모와 비교하면 'PR을 지금 대상 브랜치에 합쳤을 때 바뀌는 파일'이 나온다. 부모 하나만 더 받으면 된다.
  이름 바꾸기를 새 이름 하나로만 보면 코드 파일을 `docs/`로 옮긴 PR이 하네스만 돈다. 판단할 수 없을 때 범위를 줄이면 검증이 빠진다.
- main push의 동시 실행: main은 병합할 때만 움직이고, 커밋마다 결과가 있어야 메인 세션이 병합 결과를 확인할 수 있다. 같은 그룹에 두면 대기 중인 실행이 취소될 수 있다.
- `shell: bash`: 셸을 적으면 `bash -eo pipefail`로 돌아, 출력을 `tee`로 남겨도 `verify.sh`의 종료 코드가 단계 결과가 된다(적지 않으면 `pipefail`이 없다).
- 요약을 워크플로에서 만든다: `verify.sh`에 GitHub 전용 코드를 넣지 않는다. 표시 줄이 바뀌면 하네스가 잡는다.
- 산출물: `.verify-logs/`는 이름이 점으로 시작해 `include-hidden-files`가 없으면 빠진다. 성공한 실행은 올리지 않는다(Free 요금제의 저장 용량을 아낀다).
  `frontend/coverage/`는 Vitest가 테스트가 실패하면 만들지 않아 보통 비어 있지만, 커버리지 단계만 실패한 경우를 위해 둔다.
- 하네스 검사: 결정 0191을 사람이 고르면서 하네스가 검사하기로 했다. 워크플로 문법은 GitHub가 실행할 때 확인하고, 실행 결과(취소, 캐시 적중, 실패 산출물)는 이 Issue의 PR에서 실행 기록으로 확인한다.

## 고려했지만 고르지 않은 것

- 영역별 잡 병렬 실행: 위 '잡 하나'.
- `paths-ignore`로 문서 PR 거르기: 워크플로가 돌지 않아 필수 검사가 '대기'로 남는다(결정 0190).
- `verify.sh`가 `GITHUB_STEP_SUMMARY`에 직접 쓰기: GitHub 전용 코드가 단일 진입점에 들어간다.
- `actionlint` 같은 워크플로 문법 검사기를 하네스에 넣기: 로컬과 CI에 도구를 더 설치해야 한다. 이 저장소의 워크플로는 하나이고 실행 기록으로 확인한다.
- Docker 이미지(PostgreSQL) 캐시: 이미지를 저장·복원하는 시간이 받는 시간과 비슷하다.

## 적용과 남은 점

- 러너에서만 드러난 차이: Vitest·Vite가 파일로 보내는 출력에도 색 코드를 넣었다. `verify.sh`가 단계 명령에 `NO_COLOR=1`을 넘기고 `FORCE_COLOR`를 지운다(`docs/agent-log.md`, #67).
  실행 화면 요약은 로그인한 사람에게만 보여, 같은 내용을 요약 단계의 로그에도 남긴다(`gh run view --log`로 본다).
- 시간대·로캘: 러너는 UTC·영어 로캘이다. 첫 CI 실행 전에 로컬에서 백엔드 테스트 460개를 `-Duser.language=en -Duser.country=US -Duser.timezone=UTC`로,
  프론트엔드 테스트 220개를 `TZ=UTC`로 돌려 모두 통과하는 것을 확인했다.
- 캐시 범위: main에서 만든 캐시는 모든 PR이 쓰고, PR에서 만든 캐시는 그 PR 안에서만 쓴다. 이 결정이 병합된 뒤 main push가 캐시를 만들면 다음 PR은 첫 실행부터 캐시가 맞는다.
- 실행 시간(캐시 없음·캐시 적중)과 시간 제한 조정은 진행 기록(#67)에 적는다.
- 사람이 2026-11-28 이 Issue 진행 중에 저장소를 공개로 바꿨다.
  - 실행 로그, 실행 화면 요약, 실패 산출물을 누구나 볼 수 있다. 이 워크플로는 비밀값을 쓰지 않고, 산출물에는 테스트 데이터와 로그만 있다
    (시연·E2E 계정과 개발 전용 JWT 서명 키는 원래 저장소에 있다, 루트 `CLAUDE.md` '금지 사항'의 예외).
  - GitHub 문서 기준으로 공개 저장소의 표준 리눅스 러너는 비공개(2코어)보다 크다(4코어). 공개 전후 실행 시간을 그대로 비교하지 않는다.
