# 0030. 메인 체크아웃 훅: 판단할 수 없는 입력과 다른 저장소의 파일은 막지 않고, jq가 없으면 sed로 읽는다

- 상태: 에이전트 결정
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#29](https://github.com/Rasit-NP/curtain-call/issues/29), 결정 [0029](0029-guard-main-checkout-hook.md)

## 결정

메인 체크아웃 수정 차단 훅(`.claude/hooks/guard-main-checkout.sh`)의 세부 판단:

1. 대상 경로를 알 수 없는 입력(빈 입력, JSON 아님, 경로가 없거나 문자열이 아님)은 막지 않는다(종료 코드 0).
2. '이 저장소'는 `CLAUDE_PROJECT_DIR`(없으면 훅 스크립트가 있는 저장소)과 git-common-dir이 같은 저장소다. 다른 저장소의 메인 워크트리는 막지 않는다.
3. 상대 경로는 입력의 `cwd`(없으면 현재 폴더) 기준으로 판단한다.
4. 입력은 jq로 읽고, jq가 없으면 sed로 읽는다. sed는 따옴표·역슬래시가 없는 경로만 정확히 읽는다.
5. 훅 matcher는 `Edit|Write|NotebookEdit`만 잡는다. `Read`·`Bash` 같은 도구를 잡지 않는 것도 테스트한다.

## 근거

1. Claude Code는 항상 대상 경로가 담긴 올바른 JSON을 넘긴다. 판단할 수 없는 입력에서 막으면 파싱 문제 하나로 모든 세션의 모든 수정이 막힌다. Bash라는 우회로가 이미 있어([0029](0029-guard-main-checkout-hook.md)의 한계), 막는 쪽으로 기울어도 얻는 것이 작다.
2. Issue #29의 완료 조건이 '이 저장소의 메인 워크트리'다. 사람이 다른 저장소의 파일 수정을 지시한 경우까지 막지 않는다.
3. 워크트리 세션의 상대 경로가 메인 체크아웃을 가리키는지 바르게 판단하려면 세션의 폴더를 기준으로 해야 한다.
4. macOS는 최근 버전에 jq가 기본으로 있지만 모든 환경에 있다고 보장할 수 없다. jq가 없을 때 훅이 조용히 꺼지지 않게 한다.
5. matcher가 넓으면(예: `.*`) 메인 세션이 메인 체크아웃 파일을 읽는 것까지 막힌다.

## 고려했지만 고르지 않은 것

- 판단할 수 없는 입력은 막기(fail-closed): 위 1번 이유로 기각.
- jq를 필수로: jq가 없는 환경에서 훅이 아무것도 막지 못한다.

## 적용과 남은 점

하네스 테스트 `test_guard_ignores_invalid_input`, `test_guard_allows_worktree_and_outside`, `test_guard_blocks_main_checkout_from_worktree_session`, `test_guard_works_without_jq`, `test_guard_registered`가 각각을 검사한다.
