#!/usr/bin/env bash
# 문서 목차 검사: <폴더>/README.md가 그 폴더의 NNNN-*.md 파일을 빠짐없이 링크하고,
# 목차가 링크하는 NNNN-*.md 파일이 모두 있는지 확인한다.
#
# 사용법: bash scripts/check-docs-index.sh docs/decisions
# 하네스 테스트(scripts/harness.test.sh)가 docs/decisions와 docs/history에 대해 실행한다.

dir="${1:?사용법: $0 <폴더>}"
index="$dir/README.md"
[ -f "$index" ] || { echo "목차 없음: $index"; exit 1; }

status=0

# 목차에 없는 파일
for file in "$dir"/[0-9][0-9][0-9][0-9]-*.md; do
  [ -e "$file" ] || continue
  name="$(basename "$file")"
  if ! grep -q "($name)" "$index"; then
    echo "목차에 없는 파일: $name"
    status=1
  fi
done

# 파일이 없는 목차 항목
for name in $(grep -oE '\([0-9]{4}-[^)]*\.md\)' "$index" | tr -d '()'); do
  if [ ! -f "$dir/$name" ]; then
    echo "파일이 없는 목차 항목: $name"
    status=1
  fi
done

exit "$status"
