#!/usr/bin/env bash
# Stop 훅: 에이전트가 작업을 마칠 때 ./scripts/verify.sh quick을 실행한다. (.claude/settings.json 의 hooks.Stop)
#
# - 통과하면 아무것도 출력하지 않고 멈춤을 허용한다(종료 코드 0).
# - 실패하면 검증 출력을 stderr로 내보내고 종료 코드 2로 멈춤을 막는다.
#   Claude Code가 이 stderr를 에이전트에게 전달하고, 에이전트는 CLAUDE.md의 '실패 처리' 절차를 따른다.
# - 이미 이 훅 때문에 작업을 이어 가는 중(입력 JSON의 stop_hook_active가 true)이면 다시 막지 않는다.
#   고치지 못하는 실패로 끝없이 되풀이하지 않기 위해서다. 대신 실패한 채로 끝났다는 경고를 사용자에게 남긴다.

ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

# Claude Code가 stdin으로 넘기는 입력 JSON. 터미널에서 직접 실행하면 읽지 않는다.
INPUT=""
if [ ! -t 0 ]; then
  INPUT="$(cat)"
fi

if OUTPUT="$(bash "$ROOT/scripts/verify.sh" quick 2>&1)"; then
  exit 0
fi

if printf '%s' "$INPUT" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  printf '%s\n' '{"systemMessage": "Stop 훅: verify.sh quick이 실패한 채로 작업이 끝났습니다. .verify-logs/의 로그를 확인하세요."}'
  exit 0
fi

{
  echo "Stop 훅: ./scripts/verify.sh quick이 실패했습니다. CLAUDE.md의 '실패 처리' 절차대로 원인을 확인하고 고치세요."
  echo
  printf '%s\n' "$OUTPUT"
} >&2
exit 2
