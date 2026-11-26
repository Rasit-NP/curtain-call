# #1 검증 하네스 정비

- 기간: 2026-11-26
- Issue: [#1](https://github.com/Rasit-NP/curtain-call/issues/1), PR: [#2](https://github.com/Rasit-NP/curtain-call/pull/2) (병합)
- 관련 결정: [0001](../decisions/0001-verify-single-entry.md), [0002](../decisions/0002-stop-hook-quick.md)

## 배경

코드를 쓰기 전에, 에이전트가 테스트 결과를 보고 스스로 고치는 개발 과정을 받칠 검증 하네스를 먼저 갖추기로 했다. 공모전에서 이 과정 자체가 평가 대상이기 때문이다. 저장소에는 규칙 문서(`CLAUDE.md` 4개)와 `scripts/verify.sh`만 있었다.

## 시작할 때 확인한 문제

저장소 복사본에서 `verify.sh quick`을 실제로 돌려 다음을 확인했다.

1. `verify.sh`에 실행 권한이 없어 문서의 `./scripts/verify.sh`가 `permission denied`(126)로 끝났다.
2. 영역을 폴더로 판단했는데, 영역 폴더에는 코드보다 `CLAUDE.md`가 먼저 생겨 항상 실행됐다. 그래서 `./gradlew: No such file`, `npm ci` EUSAGE로 늘 실패했다.
3. 실패 상세가 로그 끝 40줄만 보여 줘서, `npm ci` 로그의 원인(1~4번째 줄)이 잘렸다.
4. `.gitignore`, Stop 훅, PR 템플릿, 결함 기록, `needs-human` 라벨이 없었다.

## 판단과 근거

- **영역 판단을 표식 파일로 바꿨다**([0001](../decisions/0001-verify-single-entry.md)). 폴더는 문서 때문에 먼저 생기므로 기준이 될 수 없다. 영역을 실제로 만들 때 반드시 생기는 파일(`backend/gradlew`, `frontend/package.json`, `e2e/package.json`)을 기준으로 했다.
- **실패 상세에 첫 오류 줄을 줄 번호와 함께 먼저 보여 준다.** 도구마다 원인을 쓰는 위치가 다르다(npm은 맨 앞, Gradle은 중간). 끝부분만 보면 에이전트가 원인을 놓친다.
- **Stop 훅은 같은 턴에 한 번만 막는다**([0002](../decisions/0002-stop-hook-quick.md)). 고칠 수 없는 실패로 매번 막으면 끝없이 반복되기 때문이다. 두 번째부터는 사용자에게 경고만 남기고, 에이전트는 남은 일을 직접 보고한다.
- **하네스 자체를 테스트한다**(`scripts/harness.test.sh`). 가짜 `gradlew`·`npm`을 둔 임시 저장소에서 돌려, 검증 스크립트가 고장 나도 PR 전 검증이 통과하는 일을 막는다.

## 실패와 수정

- 테스트 8개를 먼저 쓰고, 수정 전 코드에서 7개가 실패하는 것을 확인한 뒤 고쳤다.
- `.gitignore` 테스트에 처음 넣은 `.idea/workspace.xml`은 IntelliJ의 `.idea/.gitignore`가 이미 제외하고 있어 루트 규칙을 검증하지 못했다. `.idea/misc.xml`로 바꿨다.
- 고친 결함 3건은 [`docs/agent-log.md`](../agent-log.md)에 있다.

## 결과

`./scripts/verify.sh` 전체 통과(harness PASS, 나머지 SKIP). 하네스 테스트 10개.

## 배운 점

- 규칙 문서와 실제 스크립트가 어긋나 있었다. 문서대로 직접 실행해 보는 것이 첫 확인 수단이었다.
- 검증 도구도 테스트하지 않으면, 고장 났을 때 아무도 모른다.
