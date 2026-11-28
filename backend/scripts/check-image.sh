#!/usr/bin/env bash
# 백엔드 컨테이너 이미지 확인(Issue #71, 결정 0213).
# backend/Dockerfile로 만든 이미지를 prod 프로필로 PostgreSQL 17에 붙여 띄우고, 배포에 필요한 동작을 확인한다.
#
#   1. 이미지를 만든다(docker build backend). 태그는 CHECK_IMAGE(기본 curtain-call-backend:check).
#      이미 만든 이미지를 확인하려면 SKIP_BUILD=1 CHECK_IMAGE=<이미지>로 부른다.
#   2. 이 실행만 쓰는 Docker 네트워크에 PostgreSQL 17과 그 이미지를 띄운다. 앱에는 운영과 같은 이름의 환경변수
#      (DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, 루트 .env.example)로 이 실행에서 만든 임시 값을 준다.
#      DB는 바깥에 열지 않고, 앱은 127.0.0.1의 빈 포트(Docker가 고른다)에만 연다. 사람의 8080·5432 서버와 개발 DB는 쓰지 않는다.
#   3. 확인: 루트가 아닌 사용자로 돈다, GET /actuator/health가 {"status":"UP"}, 이미지의 HEALTHCHECK가 healthy가 된다,
#      시연 계정 demo1 로그인 200, POST /api/test-support/reset은 토큰 없이 401,
#      docker stop의 SIGTERM으로 끝난다(SIGKILL까지 가지 않는다).
#   4. 끝나면(실패해도) 컨테이너와 네트워크를 지운다. 이미지는 남긴다.
#
# 사용법(어느 폴더에서든): backend/scripts/check-image.sh
# Docker가 필요하다. 처음 빌드는 Gradle 배포판과 의존성을 내려받아 몇 분 걸린다.
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${CHECK_IMAGE:-curtain-call-backend:check}"
POSTGRES_IMAGE="postgres:17"
RUN_ID="cc-image-check-$$-${RANDOM}"
NETWORK="${RUN_ID}-net"
DB_CONTAINER="${RUN_ID}-db"
APP_CONTAINER="${RUN_ID}-app"
STARTUP_TIMEOUT_SECONDS=180
STOP_TIMEOUT_SECONDS=30

log() { printf '[check-image] %s\n' "$*"; }

fail() {
  printf '[check-image] 실패: %s\n' "$*" >&2
  if docker container inspect "$APP_CONTAINER" >/dev/null 2>&1; then
    printf '[check-image] 앱 컨테이너 로그(끝 80줄):\n' >&2
    docker logs --tail 80 "$APP_CONTAINER" >&2 2>&1 || true
  fi
  exit 1
}

cleanup() {
  docker rm -f "$APP_CONTAINER" "$DB_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

random_hex() { head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'; }

docker info >/dev/null 2>&1 || fail "Docker에 연결할 수 없다. Docker를 켠 뒤 다시 실행한다."

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  [ -f "$BACKEND_DIR/Dockerfile" ] || fail "$BACKEND_DIR/Dockerfile이 없다."
  log "이미지를 만든다: $IMAGE"
  docker build --tag "$IMAGE" "$BACKEND_DIR" || fail "이미지를 만들지 못했다."
fi
docker image inspect "$IMAGE" >/dev/null 2>&1 || fail "이미지가 없다: $IMAGE"

user="$(docker image inspect --format '{{.Config.User}}' "$IMAGE")"
case "$user" in
  "" | root | 0 | root:* | 0:*) fail "이미지가 루트 사용자로 돈다(USER: '${user}')." ;;
esac
log "확인: 루트가 아닌 사용자로 돈다(USER ${user})"

db_password="$(random_hex)"
jwt_secret="$(random_hex)"

docker network create "$NETWORK" >/dev/null
log "PostgreSQL 17을 띄운다: $DB_CONTAINER"
docker run --detach --name "$DB_CONTAINER" --network "$NETWORK" --network-alias db \
  --env POSTGRES_DB=curtaincall --env POSTGRES_USER=curtaincall --env "POSTGRES_PASSWORD=${db_password}" \
  "$POSTGRES_IMAGE" >/dev/null
# 초기화 중의 임시 서버는 TCP를 열지 않으므로 127.0.0.1로 물어 본 서버가 뜬 것을 기다린다.
for _ in $(seq 1 60); do
  if docker exec "$DB_CONTAINER" pg_isready --host 127.0.0.1 --username curtaincall --dbname curtaincall >/dev/null 2>&1; then
    db_ready=1
    break
  fi
  sleep 1
done
[ "${db_ready:-0}" = "1" ] || fail "PostgreSQL이 60초 안에 준비되지 않았다."

log "이미지를 prod 프로필로 띄운다: $APP_CONTAINER"
docker run --detach --name "$APP_CONTAINER" --network "$NETWORK" --publish 127.0.0.1::8080 \
  --env DB_URL=jdbc:postgresql://db:5432/curtaincall --env DB_USERNAME=curtaincall \
  --env "DB_PASSWORD=${db_password}" --env "JWT_SECRET=${jwt_secret}" \
  "$IMAGE" >/dev/null
port="$(docker port "$APP_CONTAINER" 8080/tcp | head -n 1 | sed 's/.*://')"
[ -n "$port" ] || fail "앱 포트를 찾지 못했다."
base="http://127.0.0.1:${port}"

health=""
start_seconds=$(date +%s)
while [ $(( $(date +%s) - start_seconds )) -lt "$STARTUP_TIMEOUT_SECONDS" ]; do
  [ "$(docker inspect --format '{{.State.Running}}' "$APP_CONTAINER")" = "true" ] || fail "앱 컨테이너가 시작 중에 끝났다."
  health="$(curl --silent --max-time 2 "${base}/actuator/health" || true)"
  [ "$health" = '{"status":"UP"}' ] && break
  sleep 1
done
[ "$health" = '{"status":"UP"}' ] || fail "${STARTUP_TIMEOUT_SECONDS}초 안에 GET /actuator/health가 {\"status\":\"UP\"}가 아니었다(마지막 응답: '${health}')."
log "확인: GET /actuator/health = ${health} ($(( $(date +%s) - start_seconds ))초)"

health_status=""
for _ in $(seq 1 30); do
  health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$APP_CONTAINER")"
  [ "$health_status" = "healthy" ] && break
  sleep 1
done
[ "$health_status" = "healthy" ] || fail "이미지의 HEALTHCHECK가 30초 안에 healthy가 되지 않았다('${health_status}')."
log "확인: HEALTHCHECK healthy"

active_profile="$(docker exec "$APP_CONTAINER" printenv SPRING_PROFILES_ACTIVE || true)"
[ "$active_profile" = "prod" ] || fail "SPRING_PROFILES_ACTIVE가 prod가 아니다('${active_profile}')."
log "확인: SPRING_PROFILES_ACTIVE=prod"

login_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 \
  --header 'Content-Type: application/json' --data '{"username":"demo1","password":"demo1234"}' "${base}/api/auth/login")"
[ "$login_status" = "200" ] || fail "시연 계정 demo1 로그인이 200이 아니었다(${login_status})."
log "확인: 시연 계정 demo1 로그인 200"

reset_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 --request POST "${base}/api/test-support/reset")"
[ "$reset_status" = "401" ] || fail "토큰 없는 POST /api/test-support/reset이 401이 아니었다(${reset_status})."
log "확인: 토큰 없는 POST /api/test-support/reset 401"

# 참고 정보(실패시키지 않는다): JVM 설정이 실제로 적용됐는지, 몇 번 요청한 뒤의 메모리 사용량.
log "참고: $(docker exec "$APP_CONTAINER" java -XX:+PrintFlagsFinal -version 2>/dev/null \
  | awk '$2 == "MaxHeapSize" { printf "MaxHeapSize=%dMiB ", $4 / 1048576 } $2 == "UseSerialGC" { printf "UseSerialGC=%s", $4 }')"
log "참고: 컨테이너 메모리 $(docker stats --no-stream --format '{{.MemUsage}}' "$APP_CONTAINER"), 이미지 크기 $(( $(docker image inspect --format '{{.Size}}' "$IMAGE") / 1048576 ))MiB"

stop_start=$(date +%s)
docker stop --time "$STOP_TIMEOUT_SECONDS" "$APP_CONTAINER" >/dev/null
stop_seconds=$(( $(date +%s) - stop_start ))
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$APP_CONTAINER")"
# 137(128+9)은 SIGTERM에 끝나지 않아 SIGKILL로 끝났다는 뜻이다. 셸이 PID 1이면 SIGTERM을 java에 넘기지 않아 이렇게 된다.
case "$exit_code" in
  0 | 143) log "확인: docker stop의 SIGTERM으로 끝났다(종료 코드 ${exit_code}, ${stop_seconds}초)" ;;
  *) fail "docker stop 뒤 종료 코드가 ${exit_code}다(SIGTERM으로 끝나지 않았다)." ;;
esac

log "통과: $IMAGE"
