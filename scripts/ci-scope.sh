#!/usr/bin/env bash
# CI 검증 범위: 두 커밋 사이에 바뀐 파일이 모두 docs/ 아래면 harness, 아니면 all을 출력한다(결정 0190).
#
# 사용법: bash scripts/ci-scope.sh <기준 커밋> <대상 커밋>
#   CI 워크플로(.github/workflows/ci.yml)가 PR의 병합 커밋(HEAD)과 그 첫 부모(PR의 대상 브랜치, HEAD^1)로 부른다.
#   main push는 이 스크립트를 부르지 않고 늘 전체를 실행한다.
#
# - 출력(stdout)은 verify.sh 모드 이름 하나다. 판단 근거(바뀐 파일)는 stderr로 CI 로그에 남긴다.
# - 범위를 줄일 근거가 없으면 전체다: 바뀐 파일을 알 수 없거나(없는 커밋 등) 바뀐 파일이 없을 때.
# - 이름 바꾸기는 지운 파일과 새 파일로 본다(--no-renames). 코드 파일을 docs/로 옮겨도 코드가 바뀐 것이다.
# - docs/ 밖의 문서는 문서로 치지 않는다. 백엔드·프론트엔드 테스트가 루트 CLAUDE.md의 오류 코드 표를 읽는다.
#   docs/ 아래 파일을 읽는 테스트를 만들면 이 규칙을 고친다.
# - 한글 파일 이름을 그대로 비교하도록 core.quotePath를 끈다(켜져 있으면 "docs/\355..."처럼 따옴표로 감싸 나온다).

base="${1:?사용법: $0 <기준 커밋> <대상 커밋>}"
head="${2:?사용법: $0 <기준 커밋> <대상 커밋>}"

if ! files="$(git -c core.quotePath=false diff --name-only --no-renames "$base" "$head" --)"; then
  echo "바뀐 파일을 알 수 없어 전체를 실행한다." >&2
  echo all
  exit 0
fi

if [ -z "$files" ]; then
  echo "바뀐 파일이 없어 전체를 실행한다." >&2
  echo all
  exit 0
fi

count="$(printf '%s\n' "$files" | wc -l | tr -d '[:space:]')"
outside="$(printf '%s\n' "$files" | grep -v '^docs/')"

if [ -n "$outside" ]; then
  echo "바뀐 파일 ${count}개 중 docs/ 밖의 파일이 있어 전체를 실행한다(처음 20개):" >&2
  printf '%s\n' "$outside" | head -n 20 | sed 's/^/  /' >&2
  echo all
else
  echo "바뀐 파일 ${count}개가 모두 docs/ 아래라 하네스만 실행한다." >&2
  echo harness
fi
