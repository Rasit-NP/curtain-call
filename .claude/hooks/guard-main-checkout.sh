#!/usr/bin/env bash
# PreToolUse 훅: Edit·Write·NotebookEdit가 이 저장소의 메인 워크트리(링크된 워크트리가 아닌 원래 체크아웃)에 있는
# 파일을 고치려 하면 막는다. (.claude/settings.json 의 hooks.PreToolUse)
#
# - Issue 작업은 Issue마다 별도 세션에서, 그 세션의 git 워크트리로 한다(루트 CLAUDE.md '작업 흐름').
# - 막을 때는 이유와 할 일을 stderr로 알리고 종료 코드 2로 끝낸다. Claude Code가 도구 실행을 막고 stderr를 에이전트에게 준다.
# - 메인 워크트리인지는 경로 접두사가 아니라 git으로 판단한다. 링크된 워크트리(.claude/worktrees/<이름>/)는
#   경로상 메인 체크아웃 폴더 안에 있기 때문이다. 파일이 속한 폴더에서 git-dir과 git-common-dir이 같으면 메인 워크트리다.
# - '이 저장소'는 CLAUDE_PROJECT_DIR(없으면 이 스크립트가 있는 저장소)과 git-common-dir이 같은 저장소다.
#   저장소 밖 파일(에이전트 메모리, 스크래치패드)과 다른 저장소의 파일은 막지 않는다.
# - 아직 없는 파일은 가장 가까운 기존 상위 폴더로 판단한다. 상대 경로는 입력의 cwd(없으면 현재 폴더) 기준이다.
# - 대상 경로를 알 수 없는 입력(빈 입력, JSON 아님, 경로 없음)은 막지 않는다.
#   Claude Code는 항상 경로를 넘기므로, 판단할 수 없는 입력 때문에 모든 수정을 막지 않는 쪽을 골랐다.
# - 한계: Bash로 파일을 쓰는 경우(리다이렉션, sed -i, git 명령 등)는 막지 못한다(docs/decisions/0027).
# - jq가 있으면 jq로 입력을 읽고, 없으면 sed로 읽는다. sed는 따옴표·역슬래시가 없는 경로만 정확히 읽는다.
# - 외부 명령은 git, jq(또는 sed)만 쓴다. macOS 기본 bash(3.2)에서도 돌아가도록 쓴다.

# 밖에서 정한 git·cd 환경이 판단을 바꾸지 않게 한다.
unset GIT_DIR GIT_WORK_TREE GIT_COMMON_DIR GIT_INDEX_FILE GIT_PREFIX CDPATH

NL='
'

# Claude Code가 stdin으로 넘기는 입력 JSON. 터미널에서 직접 실행하면 읽지 않는다.
INPUT=""
if [ ! -t 0 ]; then
  IFS= read -r -d '' INPUT
fi

# json_string <jq 필터> <키 이름>: 입력 JSON에서 문자열 값 하나를 읽는다. 없거나 문자열이 아니면 빈 문자열.
json_string() {
  local value
  if command -v jq >/dev/null 2>&1; then
    value="$(printf '%s' "$INPUT" | jq -r "($1) | strings" 2>/dev/null)"
  else
    value="$(printf '%s' "$INPUT" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p")"
  fi
  printf '%s' "${value%%"$NL"*}"
}

# git_dirs <폴더>: 그 폴더가 속한 저장소의 git-dir과 git-common-dir을 심볼릭 링크를 푼 절대 경로로 두 줄에 출력한다.
# 저장소 밖이면 1을 돌려준다.
git_dirs() {
  local out git_dir common_dir
  out="$(git -C "$1" rev-parse --git-dir --git-common-dir 2>/dev/null)" || return 1
  # rev-parse는 경로를 -C 폴더 기준 상대 경로로 줄 수 있다.
  git_dir="$(cd "$1" && cd "${out%%"$NL"*}" && pwd -P)" || return 1
  common_dir="$(cd "$1" && cd "${out#*"$NL"}" && pwd -P)" || return 1
  printf '%s\n%s' "$git_dir" "$common_dir"
}

TARGET="$(json_string '.tool_input.file_path' file_path)"
[ -n "$TARGET" ] || TARGET="$(json_string '.tool_input.notebook_path' notebook_path)"
[ -n "$TARGET" ] || exit 0

case "$TARGET" in
  /*) ;;
  *)
    CWD="$(json_string '.cwd' cwd)"
    TARGET="${CWD:-$PWD}/$TARGET"
    ;;
esac

# 아직 없는 파일은 가장 가까운 기존 상위 폴더로 판단한다.
DIR="$TARGET"
while [ ! -d "$DIR" ]; do
  case "$DIR" in
    */*) DIR="${DIR%/*}"; [ -n "$DIR" ] || DIR=/ ;;
    *) DIR=. ;;
  esac
done

# 이 저장소: CLAUDE_PROJECT_DIR, 없으면 이 스크립트가 있는 저장소(.claude/hooks/의 두 단계 위)
case "${BASH_SOURCE[0]}" in
  */*) SCRIPT_DIR="${BASH_SOURCE[0]%/*}" ;;
  *) SCRIPT_DIR=. ;;
esac
PROJECT="${CLAUDE_PROJECT_DIR:-$SCRIPT_DIR/../..}"

PROJECT_DIRS="$(git_dirs "$PROJECT")" || exit 0
TARGET_DIRS="$(git_dirs "$DIR")" || exit 0
TARGET_GIT_DIR="${TARGET_DIRS%%"$NL"*}"
TARGET_COMMON_DIR="${TARGET_DIRS#*"$NL"}"

# 다른 저장소의 파일
[ "$TARGET_COMMON_DIR" = "${PROJECT_DIRS#*"$NL"}" ] || exit 0
# 링크된 워크트리의 파일(git-dir이 <common-dir>/worktrees/<이름>)
[ "$TARGET_GIT_DIR" = "$TARGET_COMMON_DIR" ] || exit 0

MAIN="$(git -C "$DIR" rev-parse --show-toplevel 2>/dev/null)"
{
  echo "메인 체크아웃 수정 차단 훅: $TARGET"
  echo "이 파일은 이 저장소의 메인 체크아웃${MAIN:+($MAIN)}에 있습니다. 메인 체크아웃의 파일은 고치지 않습니다(루트 CLAUDE.md '작업 흐름')."
  echo "할 일: Issue 작업은 Issue마다 별도 세션에서, 그 세션의 git 워크트리로 하세요."
  echo "그런 세션이 없으면 직접 시작하지 말고 사람에게 알린 뒤 지시를 받으세요. Bash로 같은 파일을 고쳐 우회하지 마세요."
} >&2
exit 2
