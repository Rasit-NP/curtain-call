#!/usr/bin/env bash
# 하네스 테스트: scripts/verify.sh, Stop 훅(.claude/hooks/stop-verify.sh),
# 메인 체크아웃 수정 차단 훅(.claude/hooks/guard-main-checkout.sh), .gitignore, 문서 목차(scripts/check-docs-index.sh)가
# 문서대로 동작하는지 확인한다.
#
# 사용법: bash scripts/harness.test.sh   (./scripts/verify.sh harness 와 전체 검증에서도 실행된다)
#
# - 실제 빌드 도구 대신 가짜 gradlew, npm, npx, docker를 둔 임시 저장소에서 실행한다.
# - 임시 저장소는 테스트마다 새로 만들고, 끝나면 모두 지운다.
# - macOS 기본 bash(3.2)에서도 돌아가도록 쓴다.

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/harness-test.XXXXXX")" || exit 1
trap 'rm -rf "$WORK"' EXIT

PASSED=0
FAILED=0

# ---------- 도우미 ----------

fail() {
  echo "$*"
  return 1
}

# assert_exit <실제 종료 코드> <기대 종료 코드> <출력>
assert_exit() {
  [ "$1" -eq "$2" ] && return 0
  echo "종료 코드: 기대 $2, 실제 $1"
  echo "--- 출력 ---"
  printf '%s\n' "$3"
  return 1
}

# assert_contains <출력> <기대 문자열>
assert_contains() {
  case "$1" in
    *"$2"*) return 0 ;;
  esac
  echo "출력에 '$2'가 없음"
  echo "--- 출력 ---"
  printf '%s\n' "$1"
  return 1
}

# new_repo <이름>: verify.sh와 가짜 도구를 둔 임시 저장소를 만든다. 영역 폴더에는 CLAUDE.md만 둔다.
new_repo() {
  local dir="$WORK/$1" area tool
  mkdir -p "$dir/scripts" "$dir/bin"
  cp "$REPO/scripts/verify.sh" "$dir/scripts/verify.sh"
  for area in backend frontend e2e; do
    mkdir -p "$dir/$area"
    echo "# $area" > "$dir/$area/CLAUDE.md"
  done
  # npm: ci는 node_modules를 만들고, run은 FAKE_NPM_EXIT(기본 0)로 끝난다.
  cat > "$dir/bin/npm" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  ci) mkdir -p node_modules ;;
  run) echo "fake npm run $2"; exit "${FAKE_NPM_EXIT:-0}" ;;
esac
EOF
  # npx, docker: 이 테스트들에서는 불리면 안 된다.
  for tool in npx docker; do
    printf '#!/usr/bin/env bash\necho "unexpected call: %s $*" >&2\nexit 99\n' "$tool" > "$dir/bin/$tool"
  done
  chmod +x "$dir/bin/"*
  echo "$dir"
}

# run_verify <저장소> <모드>: 가짜 도구를 PATH 앞에 두고 verify.sh를 실행한다.
run_verify() {
  (cd "$1" && PATH="$1/bin:$PATH" bash scripts/verify.sh "$2" 2>&1)
}

# new_hook_repo <이름> <가짜 verify.sh의 종료 코드>
new_hook_repo() {
  local dir="$WORK/$1"
  mkdir -p "$dir/scripts"
  cat > "$dir/scripts/verify.sh" <<EOF
#!/usr/bin/env bash
echo "가짜 verify.sh \$1"
echo "결과: 종료 코드 $2"
exit $2
EOF
  chmod +x "$dir/scripts/verify.sh"
  echo "$dir"
}

# run_hook <저장소> <훅 입력 JSON>: Claude Code가 하듯 입력 JSON을 stdin으로 넘겨 Stop 훅을 실행한다.
run_hook() {
  printf '%s' "$2" | CLAUDE_PROJECT_DIR="$1" bash "$REPO/.claude/hooks/stop-verify.sh"
}

# run_test <설명> <함수>
run_test() {
  local out
  if out="$("$2" 2>&1)"; then
    PASSED=$((PASSED + 1))
    echo "PASS  $1"
  else
    FAILED=$((FAILED + 1))
    echo "FAIL  $1"
    printf '%s\n' "$out" | sed 's/^/      /'
  fi
}

# ---------- verify.sh ----------

test_verify_is_executable() {
  [ -x "$REPO/scripts/verify.sh" ] || fail "scripts/verify.sh에 실행 권한이 없음"
}

test_skip_areas_without_marker() {
  local dir out code
  dir="$(new_repo skip)"
  out="$(run_verify "$dir" all)"; code=$?
  assert_exit "$code" 0 "$out" &&
    assert_contains "$out" "SKIP  backend" &&
    assert_contains "$out" "SKIP  frontend" &&
    assert_contains "$out" "SKIP  e2e"
}

test_run_areas_with_marker() {
  local dir out code
  dir="$(new_repo run)"
  cat > "$dir/backend/gradlew" <<'EOF'
#!/usr/bin/env bash
echo "fake gradle $*"
exit 1
EOF
  chmod +x "$dir/backend/gradlew"
  echo '{}' > "$dir/frontend/package.json"
  out="$(run_verify "$dir" quick)"; code=$?
  assert_exit "$code" 1 "$out" &&
    assert_contains "$out" "FAIL  backend-unit" &&
    assert_contains "$out" "PASS  frontend-test"
}

test_failure_shows_first_error_line() {
  local dir out code
  dir="$(new_repo first-error)"
  # 원인이 첫 줄에 있고 뒤에 긴 출력이 붙는 로그 (npm ci 실패 로그와 같은 모양)
  cat > "$dir/backend/gradlew" <<'EOF'
#!/usr/bin/env bash
echo "HoldService.java:10: error: cannot find symbol"
i=1
while [ "$i" -le 60 ]; do echo "detail line $i"; i=$((i + 1)); done
exit 1
EOF
  chmod +x "$dir/backend/gradlew"
  out="$(run_verify "$dir" quick)"; code=$?
  assert_exit "$code" 1 "$out" &&
    assert_contains "$out" "1:HoldService.java:10: error: cannot find symbol"
}

test_e2e_runs_playwright_without_dev_db() {
  local dir out code
  dir="$(new_repo e2e)"
  echo '{}' > "$dir/e2e/package.json"
  # compose.yaml이 있어도 E2E는 개발 DB를 띄우지 않는다(결정 0161). docker는 info만 받고, 그 밖의 호출(compose 등)은 실패한다.
  echo 'services: {}' > "$dir/compose.yaml"
  cat > "$dir/bin/docker" <<'EOF'
#!/usr/bin/env bash
[ "$1" = info ] && { echo "fake docker info"; exit 0; }
echo "unexpected call: docker $*" >&2
exit 99
EOF
  # npx: 브라우저 설치는 통과하고, playwright test는 FAKE_NPX_EXIT(기본 0)로 끝난다.
  cat > "$dir/bin/npx" <<'EOF'
#!/usr/bin/env bash
echo "fake npx $*"
case "$*" in
  "playwright install chromium") exit 0 ;;
  "playwright test") exit "${FAKE_NPX_EXIT:-0}" ;;
esac
echo "unexpected call: npx $*" >&2
exit 99
EOF
  chmod +x "$dir/bin/docker" "$dir/bin/npx"
  out="$(run_verify "$dir" e2e)"; code=$?
  assert_exit "$code" 0 "$out" &&
    assert_contains "$out" "PASS  e2e-docker" &&
    assert_contains "$out" "PASS  e2e-frontend-install" &&
    assert_contains "$out" "PASS  e2e-typecheck" &&
    assert_contains "$out" "PASS  e2e-test" || return 1
  [ -d "$dir/frontend/node_modules" ] || fail "E2E 전에 프론트엔드 의존성을 설치하지 않음" || return 1

  out="$(cd "$dir" && FAKE_NPX_EXIT=1 PATH="$dir/bin:$PATH" bash scripts/verify.sh e2e 2>&1)"; code=$?
  assert_exit "$code" 1 "$out" &&
    assert_contains "$out" "FAIL  e2e-test" &&
    assert_contains "$out" "리포트: e2e/playwright-report/index.html"
}

test_steps_run_without_color() {
  local dir out code
  dir="$(new_repo no-color)"
  echo '{}' > "$dir/frontend/package.json"
  # 색을 켜는 환경(FORCE_COLOR, CI)에서도 단계 명령은 NO_COLOR=1을 받고 FORCE_COLOR는 받지 않아야 한다.
  cat > "$dir/bin/npm" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  ci) mkdir -p node_modules ;;
  run) echo "fake npm run $2 NO_COLOR=${NO_COLOR-없음} FORCE_COLOR=${FORCE_COLOR-없음}" ;;
esac
EOF
  out="$(cd "$dir" && FORCE_COLOR=1 CI=true PATH="$dir/bin:$PATH" bash scripts/verify.sh quick 2>&1)"; code=$?
  assert_exit "$code" 0 "$out" || return 1
  assert_contains "$(cat "$dir/.verify-logs/frontend-test.log")" "NO_COLOR=1 FORCE_COLOR=없음"
}

test_all_runs_harness_tests() {
  local dir out code
  dir="$(new_repo harness)"
  printf '#!/usr/bin/env bash\necho "fake harness"\nexit 1\n' > "$dir/scripts/harness.test.sh"
  out="$(run_verify "$dir" all)"; code=$?
  assert_exit "$code" 1 "$out" &&
    assert_contains "$out" "FAIL  harness"
}

# ---------- Stop 훅 ----------

test_hook_registered() {
  local settings="$REPO/.claude/settings.json" cmd
  [ -f "$settings" ] || fail ".claude/settings.json이 없음" || return 1
  [ -x "$REPO/.claude/hooks/stop-verify.sh" ] || fail ".claude/hooks/stop-verify.sh가 없거나 실행 권한이 없음" || return 1
  if command -v jq >/dev/null 2>&1; then
    cmd="$(jq -er '.hooks.Stop[].hooks[] | select(.type == "command") | .command' "$settings")" ||
      fail "settings.json에서 Stop 명령 훅을 찾지 못함(JSON 오류 포함)" || return 1
  else
    cmd="$(cat "$settings")"
  fi
  assert_contains "$cmd" ".claude/hooks/stop-verify.sh"
}

test_hook_passes_silently() {
  local dir out code
  dir="$(new_hook_repo hook-pass 0)"
  out="$(run_hook "$dir" '{"hook_event_name":"Stop","stop_hook_active":false}' 2>&1)"; code=$?
  assert_exit "$code" 0 "$out" || return 1
  [ -z "$out" ] || fail "통과했는데 출력이 있음: $out"
}

test_hook_blocks_on_failure() {
  local dir err code
  dir="$(new_hook_repo hook-fail 1)"
  err="$(run_hook "$dir" '{"hook_event_name":"Stop","stop_hook_active":false}' 2>&1 >/dev/null)"; code=$?
  assert_exit "$code" 2 "$err" &&
    assert_contains "$err" "가짜 verify.sh quick" &&
    assert_contains "$err" "결과: 종료 코드 1"
}

test_hook_does_not_block_twice() {
  local dir out code
  dir="$(new_hook_repo hook-active 1)"
  out="$(run_hook "$dir" '{"hook_event_name": "Stop", "stop_hook_active": true}' 2>/dev/null)"; code=$?
  assert_exit "$code" 0 "$out" &&
    assert_contains "$out" '"systemMessage"' || return 1
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$out" | jq -e '.systemMessage' >/dev/null || fail "올바른 JSON이 아님: $out"
  fi
}

# ---------- 메인 체크아웃 수정 차단 훅 ----------

GUARD="$REPO/.claude/hooks/guard-main-checkout.sh"

# new_git_repo <이름>: 커밋 하나가 있는 git 저장소(메인 워크트리)를 만들고, 그 안의 .claude/worktrees/wt에
# 링크된 워크트리를 만든다. 실제 저장소처럼 링크된 워크트리가 경로상 메인 체크아웃 폴더 안에 있다.
new_git_repo() {
  local dir="$WORK/$1"
  mkdir -p "$dir"
  {
    git -C "$dir" init -q &&
      echo "# main" > "$dir/README.md" &&
      git -C "$dir" add README.md &&
      git -C "$dir" -c user.name=harness -c user.email=harness@example.invalid -c commit.gpgsign=false \
        commit -q --no-verify -m init &&
      git -C "$dir" worktree add -q -b wt "$dir/.claude/worktrees/wt"
  } >/dev/null 2>&1 || { echo "임시 git 저장소를 만들지 못함: $dir" >&2; return 1; }
  echo "$dir"
}

# guard_input <도구 이름> <대상 경로> [<cwd>]: Claude Code가 PreToolUse 훅에 넘기는 입력 JSON을 만든다.
guard_input() {
  local key=file_path
  [ "$1" = NotebookEdit ] && key=notebook_path
  printf '{"session_id":"harness","hook_event_name":"PreToolUse","cwd":"%s","tool_name":"%s","tool_input":{"%s":"%s"}}' \
    "${3:-$WORK}" "$1" "$key" "$2"
}

# run_guard <프로젝트 폴더> <입력 JSON>: 입력 JSON을 stdin으로 넘겨 메인 체크아웃 수정 차단 훅을 실행한다.
run_guard() {
  printf '%s' "$2" | CLAUDE_PROJECT_DIR="$1" bash "$GUARD"
}

# expect_guard <기대 종료 코드> <프로젝트 폴더> <도구 이름> <대상 경로> [<cwd>]
# 막을 때(2)는 stderr에 이유와 할 일이 있는지도 확인한다.
expect_guard() {
  local expected="$1" project="$2" err code
  shift 2
  err="$(run_guard "$project" "$(guard_input "$@")" 2>&1 >/dev/null)"; code=$?
  assert_exit "$code" "$expected" "$err" || { echo "대상: $1 $2 (프로젝트: $project)"; return 1; }
  if [ "$expected" -eq 2 ]; then
    assert_contains "$err" "메인 체크아웃" && assert_contains "$err" "워크트리" ||
      { echo "대상: $1 $2"; return 1; }
  fi
}

test_guard_registered() {
  local settings="$REPO/.claude/settings.json" matcher tool
  [ -x "$GUARD" ] || fail ".claude/hooks/guard-main-checkout.sh가 없거나 실행 권한이 없음" || return 1
  if ! command -v jq >/dev/null 2>&1; then
    assert_contains "$(cat "$settings")" ".claude/hooks/guard-main-checkout.sh"
    return
  fi
  matcher="$(jq -er '[.hooks.PreToolUse[]
      | select(any(.hooks[]; .type == "command" and (.command | contains(".claude/hooks/guard-main-checkout.sh"))))]
      | first | .matcher' "$settings")" ||
    fail "settings.json에서 이 훅을 부르는 PreToolUse 항목과 matcher를 찾지 못함(JSON 오류 포함)" || return 1
  # matcher는 도구 이름 전체와 맞춰 보는 정규식이다.
  for tool in Edit Write NotebookEdit; do
    printf '%s' "$tool" | grep -Eq "^(${matcher})\$" || fail "matcher '$matcher'가 $tool을 잡지 않음" || return 1
  done
  for tool in Read Bash Glob; do
    if printf '%s' "$tool" | grep -Eq "^(${matcher})\$"; then
      fail "matcher '$matcher'가 파일을 고치지 않는 도구 $tool까지 잡음"
      return 1
    fi
  done
}

test_guard_blocks_main_checkout() {
  local dir
  dir="$(new_git_repo guard-block)" || return 1
  mkdir -p "$dir/docs with space"
  expect_guard 2 "$dir" Edit "$dir/README.md" &&
    expect_guard 2 "$dir" Write "$dir/new/nested/file.md" &&
    expect_guard 2 "$dir" Write "$dir/docs with space/a.md" &&
    expect_guard 2 "$dir" NotebookEdit "$dir/notebook.ipynb" &&
    expect_guard 2 "$dir" Write "$dir/.claude/worktrees/not-a-worktree/file.md"
}

test_guard_blocks_main_checkout_from_worktree_session() {
  local dir wt
  dir="$(new_git_repo guard-from-wt)" || return 1
  wt="$dir/.claude/worktrees/wt"
  # 워크트리 세션(CLAUDE_PROJECT_DIR가 링크된 워크트리)이 절대 경로로 메인 체크아웃 파일을 고치려는 경우
  expect_guard 2 "$wt" Edit "$dir/README.md" &&
    # 상대 경로는 입력의 cwd를 기준으로 판단한다.
    expect_guard 2 "$dir" Edit "README.md" "$dir" &&
    expect_guard 0 "$dir" Edit "README.md" "$wt"
}

test_guard_allows_worktree_and_outside() {
  local dir wt other project
  dir="$(new_git_repo guard-allow)" || return 1
  wt="$dir/.claude/worktrees/wt"
  other="$(new_git_repo guard-other-repo)" || return 1
  mkdir -p "$WORK/outside dir"
  for project in "$dir" "$wt"; do
    expect_guard 0 "$project" Edit "$wt/README.md" &&
      expect_guard 0 "$project" Write "$wt/new/nested/file.md" &&
      expect_guard 0 "$project" NotebookEdit "$wt/notebook.ipynb" &&
      expect_guard 0 "$project" Write "$WORK/outside dir/memory.md" &&
      expect_guard 0 "$project" Write "$WORK/not-yet/created/file.md" &&
      # 다른 저장소의 메인 워크트리는 이 저장소가 아니므로 막지 않는다.
      expect_guard 0 "$project" Edit "$other/README.md" || return 1
  done
}

test_guard_ignores_invalid_input() {
  local dir input out code
  dir="$(new_git_repo guard-invalid)" || return 1
  for input in "" "not json" "[]" '{"tool_name":"Edit"}' '{"tool_name":"Edit","tool_input":{}}' \
    '{"tool_name":"Edit","tool_input":{"file_path":""}}' '{"tool_name":"Edit","tool_input":{"file_path":42}}' \
    '{"cwd":"relative-cwd","tool_name":"Write","tool_input":{"file_path":"not/yet.md"}}'; do
    out="$(run_guard "$dir" "$input" 2>&1)"; code=$?
    assert_exit "$code" 0 "$out" || { echo "입력: $input"; return 1; }
  done
}

test_guard_works_without_jq() {
  local dir bin out code tool
  dir="$(new_git_repo guard-no-jq)" || return 1
  bin="$WORK/guard-no-jq-bin"
  mkdir -p "$bin"
  for tool in git sed; do
    ln -s "$(command -v "$tool")" "$bin/$tool" || return 1
  done
  out="$(guard_input Edit "$dir/README.md" | PATH="$bin" CLAUDE_PROJECT_DIR="$dir" "$BASH" "$GUARD" 2>&1)"; code=$?
  assert_exit "$code" 2 "$out" || return 1
  out="$(guard_input Edit "$dir/.claude/worktrees/wt/README.md" |
    PATH="$bin" CLAUDE_PROJECT_DIR="$dir" "$BASH" "$GUARD" 2>&1)"; code=$?
  assert_exit "$code" 0 "$out"
}

# ---------- .gitignore ----------

test_gitignore_rules() {
  local path
  for path in .idea/misc.xml .verify-logs/quick.log .env .env.local .claude/settings.local.json \
    backend/build/x backend/.gradle/x frontend/node_modules/x frontend/dist/x frontend/coverage/x \
    e2e/node_modules/x e2e/playwright-report/x e2e/test-results/x; do
    git -C "$REPO" check-ignore -q "$path" || fail "제외돼야 하는데 제외되지 않음: $path" || return 1
  done
  for path in .env.example .claude/settings.json .claude/hooks/stop-verify.sh .claude/hooks/guard-main-checkout.sh \
    scripts/verify.sh backend/gradle/wrapper/gradle-wrapper.jar; do
    if git -C "$REPO" check-ignore -q "$path"; then
      fail "남아야 하는데 제외됨: $path"
      return 1
    fi
  done
}

# check_index <폴더>: 목차 검사 스크립트를 실행하고 출력과 종료 코드를 돌려준다.
check_index() {
  bash "$REPO/scripts/check-docs-index.sh" "$1" 2>&1
}

# new_docs_dir <이름>: 목차와 항목 파일 둘을 둔 임시 문서 폴더를 만든다.
new_docs_dir() {
  local dir="$WORK/$1"
  mkdir -p "$dir"
  printf '# 1\n' > "$dir/0001-first.md"
  printf '# 2\n' > "$dir/0002-second.md"
  printf '| [0001](0001-first.md) | 첫째 |\n| [0002](0002-second.md) | 둘째 |\n' > "$dir/README.md"
  echo "$dir"
}

test_docs_index_matches_repo() {
  local dir out code
  for dir in docs/decisions docs/history; do
    [ -f "$REPO/$dir/README.md" ] || { fail "$dir/README.md 없음"; return 1; }
    out="$(check_index "$REPO/$dir")"; code=$?
    assert_exit "$code" 0 "$out" || return 1
  done
}

test_docs_index_detects_mismatch() {
  local dir out code
  dir="$(new_docs_dir docs-ok)"
  out="$(check_index "$dir")"; code=$?
  assert_exit "$code" 0 "$out" || return 1

  dir="$(new_docs_dir docs-missing)"
  printf '# 3\n' > "$dir/0003-third.md"
  out="$(check_index "$dir")"; code=$?
  assert_exit "$code" 1 "$out" || return 1
  assert_contains "$out" "0003-third.md" || return 1

  dir="$(new_docs_dir docs-stale)"
  rm "$dir/0002-second.md"
  out="$(check_index "$dir")"; code=$?
  assert_exit "$code" 1 "$out" || return 1
  assert_contains "$out" "0002-second.md" || return 1
}

# ---------- CI 워크플로 ----------

WORKFLOW="$REPO/.github/workflows/ci.yml"

# code_lines <파일>: 주석 줄과 빈 줄을 뺀 줄
code_lines() {
  grep -vE '^[[:space:]]*(#|$)' "$1"
}

# workflow_files: .github/workflows의 워크플로 파일. 하나도 없으면 1을 돌려준다.
workflow_files() {
  local f found=1
  for f in "$REPO"/.github/workflows/*.yml "$REPO"/.github/workflows/*.yaml; do
    [ -f "$f" ] || continue
    echo "$f"
    found=0
  done
  return "$found"
}

test_ci_workflow_calls_verify_only() {
  local direct
  [ -f "$WORKFLOW" ] || fail ".github/workflows/ci.yml이 없음" || return 1
  code_lines "$WORKFLOW" | grep -q '\./scripts/verify\.sh' || fail "워크플로가 ./scripts/verify.sh를 부르지 않음" || return 1
  # 검증 명령을 워크플로에 따로 늘어놓지 않는다(결정 0001). 도구 설치·캐시는 액션으로 한다.
  # 'cache: gradle', 'cache: npm'처럼 도구 이름만 쓴 설정은 명령이 아니다.
  direct="$(code_lines "$WORKFLOW" | grep -E '\./gradlew|(^|[^[:alnum:]_./-])gradle[[:space:]]+[[:alnum:]-]|(^|[^[:alnum:]_-])(npx|vitest)([[:space:]]|$)|npm[[:space:]]+(ci|i|install|run|test|exec)([[:space:]]|$)|playwright[[:space:]]+(test|install)')"
  [ -z "$direct" ] || fail "워크플로가 verify.sh를 거치지 않고 검증 명령을 부름: $direct"
}

test_ci_workflows_pin_official_actions() {
  local files f bad
  files="$(workflow_files)" || fail ".github/workflows에 워크플로가 없음" || return 1
  while IFS= read -r f; do
    # GitHub 공식 actions/*만, 40자 커밋 SHA로 고정하고 버전을 주석에 적는다(결정 0191).
    bad="$(grep -E '^[[:space:]]*(-[[:space:]]+)?uses:' "$f" |
      grep -vE '^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]*actions/[A-Za-z0-9._-]+@[0-9a-f]{40}[[:space:]]+#[[:space:]]*v[0-9]')"
    [ -z "$bad" ] || fail "${f#"$REPO"/}: 공식 actions/*를 커밋 SHA와 버전 주석으로 고정하지 않음: $bad" || return 1
  done <<EOF
$files
EOF
}

test_ci_workflows_least_privilege() {
  local files f checkouts kept
  files="$(workflow_files)" || fail ".github/workflows에 워크플로가 없음" || return 1
  while IFS= read -r f; do
    # 워크플로 맨 위에서 토큰 권한을 저장소 읽기로 둔다(결정 0191).
    grep -A1 -E '^permissions:[[:space:]]*$' "$f" | grep -qE '^[[:space:]]+contents:[[:space:]]*read[[:space:]]*$' ||
      fail "${f#"$REPO"/}: 맨 위 permissions 바로 아래가 contents: read가 아님" || return 1
    if code_lines "$f" | grep -qE 'write-all|:[[:space:]]*write[[:space:]]*(#|$)'; then
      fail "${f#"$REPO"/}: 쓰기 권한이 있음"
      return 1
    fi
    # 외부 PR의 코드가 쓰기 토큰·비밀값과 함께 돌 수 있는 트리거는 쓰지 않는다(저장소를 공개로 바꾼 뒤에도 안전하게).
    if code_lines "$f" | grep -qE '(^|[^[:alnum:]_])(pull_request_target|workflow_run)([^[:alnum:]_]|$)'; then
      fail "${f#"$REPO"/}: pull_request_target 또는 workflow_run 트리거가 있음"
      return 1
    fi
    # checkout은 토큰을 디스크(git 설정)에 남기지 않는다. 검증 중 도는 의존성 설치 스크립트가 읽지 못하게 한다.
    checkouts="$(grep -cE 'uses:[[:space:]]*actions/checkout@' "$f")"
    kept="$(grep -cE '^[[:space:]]+persist-credentials:[[:space:]]*false[[:space:]]*$' "$f")"
    [ "$checkouts" -eq "$kept" ] ||
      fail "${f#"$REPO"/}: checkout ${checkouts}개 중 persist-credentials: false가 ${kept}개" || return 1
  done <<EOF
$files
EOF
}

test_ci_workflow_summary_marker() {
  [ -f "$WORKFLOW" ] || fail ".github/workflows/ci.yml이 없음" || return 1
  # 워크플로는 verify.sh 출력의 이 줄부터 끝까지를 실행 화면(Job Summary)에 옮긴다.
  grep -q '===== 검증 요약' "$WORKFLOW" || fail "워크플로가 '===== 검증 요약' 줄로 요약을 찾지 않음" || return 1
  grep -q 'echo "===== 검증 요약' "$REPO/scripts/verify.sh" || fail "verify.sh가 '===== 검증 요약' 줄을 출력하지 않음"
}

# new_scope_repo <이름>: docs/a.md, backend/A.java, 루트 CLAUDE.md를 커밋한 git 저장소를 만든다.
new_scope_repo() {
  local dir="$WORK/$1"
  mkdir -p "$dir/docs" "$dir/backend"
  echo a > "$dir/docs/a.md"
  echo A > "$dir/backend/A.java"
  echo c > "$dir/CLAUDE.md"
  { git -C "$dir" init -q && scope_commit "$dir" init; } >/dev/null 2>&1 ||
    { echo "임시 git 저장소를 만들지 못함: $dir" >&2; return 1; }
  echo "$dir"
}

# scope_commit <저장소> <메시지>: 지금 변경을 모두 커밋한다.
scope_commit() {
  git -C "$1" add -A &&
    git -C "$1" -c user.name=harness -c user.email=harness@example.invalid -c commit.gpgsign=false \
      commit -q --no-verify -m "$2"
}

# expect_scope <기대 출력> <저장소> <기준> <대상> <설명>: scripts/ci-scope.sh의 출력(stdout)과 종료 코드 0을 확인한다.
expect_scope() {
  local out code
  out="$(cd "$2" && bash "$REPO/scripts/ci-scope.sh" "$3" "$4" 2>/dev/null)"; code=$?
  [ "$code" -eq 0 ] && [ "$out" = "$1" ] && return 0
  echo "$5: 기대 '$1'(종료 코드 0), 실제 '$out'(종료 코드 $code)"
  return 1
}

test_ci_scope_docs_only_runs_harness() {
  local dir
  dir="$(new_scope_repo scope-docs)" || return 1
  echo b > "$dir/docs/b.md"
  echo a2 > "$dir/docs/a.md"
  mkdir -p "$dir/docs/history" && echo h > "$dir/docs/history/한글 이름.md"
  scope_commit "$dir" docs >/dev/null 2>&1 || return 1
  expect_scope harness "$dir" HEAD^ HEAD "docs/만 바뀜(한글·공백 파일 이름 포함)"
}

test_ci_scope_runs_all_otherwise() {
  local dir
  dir="$(new_scope_repo scope-all)" || return 1
  echo b > "$dir/docs/b.md"
  echo A2 > "$dir/backend/A.java"
  scope_commit "$dir" mixed >/dev/null 2>&1 || return 1
  expect_scope all "$dir" HEAD^ HEAD "docs/와 코드가 함께 바뀜" || return 1

  echo c2 > "$dir/CLAUDE.md"
  scope_commit "$dir" claude-md >/dev/null 2>&1 || return 1
  expect_scope all "$dir" HEAD^ HEAD "루트 CLAUDE.md만 바뀜(테스트가 오류 코드 표를 읽음)" || return 1

  git -C "$dir" mv backend/A.java docs/A.java >/dev/null 2>&1 &&
    scope_commit "$dir" move-into-docs >/dev/null 2>&1 || return 1
  expect_scope all "$dir" HEAD^ HEAD "코드 파일을 docs/로 옮김" || return 1

  expect_scope all "$dir" HEAD HEAD "바뀐 파일 없음" || return 1
  expect_scope all "$dir" 0123456789abcdef0123456789abcdef01234567 HEAD "없는 커밋"
}

# ---------- 실행 ----------

echo "하네스 테스트"
run_test "verify.sh: 실행 권한이 있다" test_verify_is_executable
run_test "verify.sh: 표식 파일이 없는 영역은 CLAUDE.md가 있어도 SKIP하고 통과한다" test_skip_areas_without_marker
run_test "verify.sh: 표식 파일이 있는 영역은 실행하고, 실패하면 종료 코드 1로 끝난다" test_run_areas_with_marker
run_test "verify.sh: 실패 상세에 로그 앞쪽의 첫 오류 줄을 줄 번호와 함께 보여 준다" test_failure_shows_first_error_line
run_test "verify.sh: E2E는 개발 DB를 띄우지 않고 Docker 확인·프론트엔드 의존성·타입 검사 뒤 Playwright를 돌리며, 실패하면 리포트 경로를 보여 준다" test_e2e_runs_playwright_without_dev_db
run_test "verify.sh: 전체 검증은 하네스 테스트를 실행하고, 실패하면 전체를 실패로 본다" test_all_runs_harness_tests
run_test "verify.sh: 색을 켜는 환경(CI, FORCE_COLOR)에서도 단계 명령에 NO_COLOR=1만 넘겨(FORCE_COLOR는 지움) 로그에 색 코드를 남기지 않는다" test_steps_run_without_color
run_test "Stop 훅: settings.json에 등록돼 있고 훅 스크립트를 실행할 수 있다" test_hook_registered
run_test "Stop 훅: verify.sh가 통과하면 아무것도 출력하지 않고 멈춤을 허용한다" test_hook_passes_silently
run_test "Stop 훅: verify.sh가 실패하면 종료 코드 2로 멈춤을 막고 결과를 에이전트에게 준다" test_hook_blocks_on_failure
run_test "Stop 훅: 이미 훅으로 이어 가는 중이면 다시 막지 않고 사용자에게 경고만 남긴다" test_hook_does_not_block_twice
run_test "메인 체크아웃 훅: settings.json에 Edit·Write·NotebookEdit만 잡는 PreToolUse 훅으로 등록돼 있다" test_guard_registered
run_test "메인 체크아웃 훅: 메인 워크트리의 파일(있는 파일, 아직 없는 경로, 공백 경로, 노트북)은 종료 코드 2로 막고 이유와 할 일을 알린다" test_guard_blocks_main_checkout
run_test "메인 체크아웃 훅: 워크트리 세션에서도 메인 체크아웃 파일은 막고, 상대 경로는 cwd 기준으로 판단한다" test_guard_blocks_main_checkout_from_worktree_session
run_test "메인 체크아웃 훅: 링크된 워크트리, 저장소 밖, 다른 저장소의 파일은 막지 않는다" test_guard_allows_worktree_and_outside
run_test "메인 체크아웃 훅: 대상 경로를 알 수 없는 입력은 막지 않는다" test_guard_ignores_invalid_input
run_test "메인 체크아웃 훅: jq가 없어도 판단한다" test_guard_works_without_jq
run_test ".gitignore: 생성 파일과 비밀값은 제외하고, 설정·스크립트·.env.example은 남긴다" test_gitignore_rules
run_test "문서 목차: docs/decisions와 docs/history의 목차가 실제 파일과 맞다" test_docs_index_matches_repo
run_test "문서 목차: 목차에 없는 파일과 파일이 없는 목차 항목을 찾아 실패한다" test_docs_index_detects_mismatch
run_test "CI 워크플로: ./scripts/verify.sh를 부르고 검증 명령을 직접 늘어놓지 않는다" test_ci_workflow_calls_verify_only
run_test "CI 워크플로: GitHub 공식 actions/*만 커밋 SHA와 버전 주석으로 고정해 쓴다" test_ci_workflows_pin_official_actions
run_test "CI 워크플로: 토큰은 contents: read만, 위험한 트리거 없음, checkout은 토큰을 남기지 않는다" test_ci_workflows_least_privilege
run_test "CI 워크플로: verify.sh의 '검증 요약' 줄로 실행 화면 요약을 만든다" test_ci_workflow_summary_marker
run_test "CI 범위: PR이 바꾼 파일이 모두 docs/ 아래면 하네스만 실행한다" test_ci_scope_docs_only_runs_harness
run_test "CI 범위: docs/ 밖 파일(루트 CLAUDE.md, docs/로 옮긴 코드 포함)이 바뀌었거나 알 수 없으면 전체를 실행한다" test_ci_scope_runs_all_otherwise

echo
echo "결과: 통과 $PASSED, 실패 $FAILED"
[ "$FAILED" -eq 0 ]
