# 0191. CI 보안: GitHub 공식 actions/*만 커밋 SHA로 고정해 쓰고, 토큰은 contents: read, checkout은 토큰을 남기지 않는다

- 상태: 채택
- 날짜: 2026-11-28
- 결정한 사람: 사람(Issue #67 '아직 정하지 않은 정책' 2번. 이 Issue 세션에서 선택지 셋을 묻고, 사람이 추천안을 골랐다)
- 관련: Issue [#67](https://github.com/Rasit-NP/curtain-call/issues/67), 결정 [0186](0186-handoff-roadmap-issue-scope.md), [0193](0193-ci-workflow-impl.md)(구현)

## 결정

- 워크플로는 GitHub 공식 `actions/*` 액션만 쓴다. 커밋 SHA(40자)로 고정하고, 버전은 줄 끝 주석에 적는다. 예: `actions/checkout@<SHA> # v7.0.1`
- 워크플로 토큰(`GITHUB_TOKEN`) 권한은 워크플로 맨 위에서 `contents: read`만 준다.
- `actions/checkout`은 토큰을 git 설정에 남기지 않는다(`persist-credentials: false`).
- 하네스(`scripts/harness.test.sh`)가 이 규칙을 검사한다.

## 근거

물을 때 보인 근거다.

- 필요한 일(체크아웃, Java·Node 설치와 의존성 캐시, 브라우저 캐시, 산출물 올리기)이 공식 액션으로 다 된다.
- 태그는 저장소 주인이 다른 커밋으로 옮길 수 있다. 2025년 tj-actions/changed-files의 태그가 악성 커밋으로 옮겨진 일이 있었다. 커밋 SHA는 바뀌지 않는다.
- 검증 중에는 npm 의존성의 설치 스크립트와 Gradle 플러그인이 돈다. 토큰이 git 설정에 남아 있으면 그 코드가 읽을 수 있다.
  비공개 저장소에서는 읽기 토큰으로도 코드를 가져갈 수 있다.
- 물을 때 저장소 설정은 '모든 액션 허용', SHA 고정 강제 없음, 워크플로 기본 토큰 권한 read였다. 저장소 설정이 바뀌어도 같은 권한으로 돌도록 워크플로에 적는다.

## 고려했지만 고르지 않은 것

- 도구 제작사 공식 액션까지 허용(예: `gradle/actions/setup-gradle`), SHA 고정: Gradle 전용 캐시 정리가 있지만 믿어야 할 제작사가 늘어난다.
- 공식 `actions/*`만, 태그 고정(`@v7`): 갱신이 쉽지만 태그가 옮겨지면 검토하지 않은 코드가 돈다.

## 적용과 남은 점

- 액션을 올릴 때는 새 판의 커밋 SHA를 확인해 바꾸고 주석의 버전도 고친다. 자동 갱신(Dependabot)은 두지 않았다.
- 저장소 설정(Settings → Actions → General)에서 허용할 액션을 GitHub가 만든 것으로 좁히고 SHA 고정 강제를 켜면 같은 규칙을 GitHub가 막아 준다. 저장소 설정은 사람이 한다.
- 사람이 2026-11-28 이 Issue 진행 중에 저장소를 공개로 바꿨다. 이제 외부 사람의 포크 PR도 `pull_request`로 돈다. 이때도 토큰은 읽기 전용이고 비밀값은 넘어가지 않는다.
  이 워크플로는 비밀값을 쓰지 않는다. 포크 PR의 실행이 만든 캐시는 그 PR 안에서만 쓰여 main의 캐시를 바꾸지 못한다.
- 공개 뒤에는 읽기 토큰으로 코드를 가져가는 위험은 사라졌지만, 토큰을 git 설정에 남기지 않는 규칙은 그대로 둔다. 체크아웃 뒤의 단계는 토큰이 필요 없다.
- 처음 기여하는 외부 사람의 포크 PR 실행을 승인받게 하는 설정(Settings → Actions → General → Fork pull request workflows)도 사람이 정한다.
