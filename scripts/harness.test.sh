#!/usr/bin/env bash
# 하네스 테스트: scripts/verify.sh, Stop 훅(.claude/hooks/stop-verify.sh), .gitignore, 문서 목차(scripts/check-docs-index.sh)가
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

# ---------- .gitignore ----------

test_gitignore_rules() {
  local path
  for path in .idea/misc.xml .verify-logs/quick.log .env .env.local .claude/settings.local.json \
    backend/build/x backend/.gradle/x frontend/node_modules/x frontend/dist/x frontend/coverage/x \
    e2e/node_modules/x e2e/playwright-report/x e2e/test-results/x; do
    git -C "$REPO" check-ignore -q "$path" || fail "제외돼야 하는데 제외되지 않음: $path" || return 1
  done
  for path in .env.example .claude/settings.json .claude/hooks/stop-verify.sh scripts/verify.sh \
    backend/gradle/wrapper/gradle-wrapper.jar; do
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

# ---------- 실행 ----------

echo "하네스 테스트"
run_test "verify.sh: 실행 권한이 있다" test_verify_is_executable
run_test "verify.sh: 표식 파일이 없는 영역은 CLAUDE.md가 있어도 SKIP하고 통과한다" test_skip_areas_without_marker
run_test "verify.sh: 표식 파일이 있는 영역은 실행하고, 실패하면 종료 코드 1로 끝난다" test_run_areas_with_marker
run_test "verify.sh: 실패 상세에 로그 앞쪽의 첫 오류 줄을 줄 번호와 함께 보여 준다" test_failure_shows_first_error_line
run_test "verify.sh: 전체 검증은 하네스 테스트를 실행하고, 실패하면 전체를 실패로 본다" test_all_runs_harness_tests
run_test "Stop 훅: settings.json에 등록돼 있고 훅 스크립트를 실행할 수 있다" test_hook_registered
run_test "Stop 훅: verify.sh가 통과하면 아무것도 출력하지 않고 멈춤을 허용한다" test_hook_passes_silently
run_test "Stop 훅: verify.sh가 실패하면 종료 코드 2로 멈춤을 막고 결과를 에이전트에게 준다" test_hook_blocks_on_failure
run_test "Stop 훅: 이미 훅으로 이어 가는 중이면 다시 막지 않고 사용자에게 경고만 남긴다" test_hook_does_not_block_twice
run_test ".gitignore: 생성 파일과 비밀값은 제외하고, 설정·스크립트·.env.example은 남긴다" test_gitignore_rules
run_test "문서 목차: docs/decisions와 docs/history의 목차가 실제 파일과 맞다" test_docs_index_matches_repo
run_test "문서 목차: 목차에 없는 파일과 파일이 없는 목차 항목을 찾아 실패한다" test_docs_index_detects_mismatch

echo
echo "결과: 통과 $PASSED, 실패 $FAILED"
[ "$FAILED" -eq 0 ]
