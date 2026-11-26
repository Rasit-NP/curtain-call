#!/usr/bin/env bash
# 커튼콜 검증 스크립트: 모든 검증의 단일 진입점
#
# 사용법: ./scripts/verify.sh [quick|backend|frontend|e2e|all]
#   quick     백엔드 단위 테스트 + 프론트엔드 테스트 (작업 중 수시로)
#   backend   백엔드 단위·통합 테스트, 빌드, 커버리지
#   frontend  프론트엔드 타입 검사, 린트, 테스트(커버리지), 빌드
#   e2e       DB를 띄우고 Playwright E2E 테스트
#   all       backend → frontend → e2e (기본값, PR 전에 반드시 통과)
#
# - 단계별 전체 로그는 .verify-logs/<단계>.log 에 남는다.
# - 실패하면 마지막에 실패한 단계의 로그 끝부분과 리포트 경로를 출력한다.
# - 아직 만들지 않은 영역(폴더가 없는 경우)은 SKIP으로 표시하고 실패로 보지 않는다.
# - macOS 기본 bash(3.2)에서도 돌아가도록 연관 배열 등은 쓰지 않는다.

set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/.verify-logs"
MODE="${1:-all}"
TAIL_LINES=40

SUMMARY=""
FAILED_STEPS=""
FAILED=0

# record <단계> <상태> <로그 경로> <리포트 경로>
record() {
  SUMMARY="${SUMMARY}$(printf '%-22s %-5s %s' "$1" "$2" "$4")"$'\n'
  if [ "$2" = "FAIL" ]; then
    FAILED=1
    FAILED_STEPS="${FAILED_STEPS}$1|$3|$4"$'\n'
  fi
}

# run_step <단계> <작업 폴더(루트 기준)> <리포트 경로> <명령...>
run_step() {
  local name="$1" dir="$2" report="$3"
  shift 3
  local log="$LOG_DIR/$name.log"

  if [ ! -d "$ROOT/$dir" ]; then
    echo "SKIP  $name ($dir/ 폴더 없음)"
    record "$name" "SKIP" "-" "-"
    return 0
  fi

  echo "RUN   $name: $*"
  if (cd "$ROOT/$dir" && "$@") >"$log" 2>&1; then
    echo "PASS  $name"
    record "$name" "PASS" "$log" "$report"
    return 0
  fi

  echo "FAIL  $name (로그: ${log#"$ROOT"/})"
  record "$name" "FAIL" "$log" "$report"
  return 1
}

# 의존성이 없을 때만 설치한다 (frontend/, e2e/ 안에서 실행됨)
# shellcheck disable=SC2329  # run_step을 통해 간접 호출된다
ensure_node_modules() {
  [ -d node_modules ] || npm ci
}

# shellcheck disable=SC2329  # run_step을 통해 간접 호출된다
ensure_e2e_deps() {
  ensure_node_modules || return 1
  npx playwright install chromium
}

backend_unit() {
  run_step backend-unit backend "backend/build/reports/tests/test/index.html" \
    ./gradlew test
}

backend_all() {
  backend_unit
  run_step backend-integration backend "backend/build/reports/tests/integrationTest/index.html" \
    ./gradlew integrationTest
  run_step backend-build backend "backend/build/reports/jacoco/test/html/index.html" \
    ./gradlew assemble jacocoTestReport
}

frontend_quick() {
  run_step frontend-install frontend "-" ensure_node_modules || return 1
  run_step frontend-test frontend "-" npm run test
}

frontend_all() {
  run_step frontend-install frontend "-" ensure_node_modules || return 1
  run_step frontend-typecheck frontend "-" npm run typecheck
  run_step frontend-lint frontend "-" npm run lint
  run_step frontend-test frontend "frontend/coverage/index.html" npm run test:coverage
  run_step frontend-build frontend "-" npm run build
}

e2e_all() {
  if [ ! -d "$ROOT/e2e" ]; then
    echo "SKIP  e2e (e2e/ 폴더 없음)"
    record "e2e" "SKIP" "-" "-"
    return 0
  fi
  if [ -f "$ROOT/compose.yaml" ]; then
    run_step e2e-db . "-" docker compose up -d --wait db || return 1
  fi
  run_step e2e-install e2e "-" ensure_e2e_deps || return 1
  run_step e2e-test e2e "e2e/playwright-report/index.html" npx playwright test
}

case "$MODE" in
  quick|backend|frontend|e2e|all) ;;
  *)
    echo "사용법: $0 [quick|backend|frontend|e2e|all]" >&2
    exit 2
    ;;
esac

mkdir -p "$LOG_DIR"

case "$MODE" in
  quick)
    backend_unit
    frontend_quick
    ;;
  backend)
    backend_all
    ;;
  frontend)
    frontend_all
    ;;
  e2e)
    e2e_all
    ;;
  all)
    backend_all
    frontend_all
    if [ "$FAILED" -eq 0 ]; then
      e2e_all
    else
      echo "SKIP  e2e (앞 단계 실패)"
      record "e2e" "SKIP" "-" "-"
    fi
    ;;
esac

echo
echo "===== 검증 요약: $MODE ====="
printf '%s' "$SUMMARY"

if [ "$FAILED" -ne 0 ]; then
  echo
  echo "===== 실패 상세 ====="
  printf '%s' "$FAILED_STEPS" | while IFS='|' read -r name log report; do
    [ -n "$name" ] || continue
    echo "--- $name: 로그 마지막 ${TAIL_LINES}줄 (${log#"$ROOT"/}) ---"
    tail -n "$TAIL_LINES" "$log"
    if [ "$report" != "-" ]; then
      echo "리포트: $report"
    fi
    echo
  done
  echo "결과: 실패"
  exit 1
fi

echo "결과: 통과"
exit 0