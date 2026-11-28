# #71 배포 준비(백엔드): 컨테이너 이미지, 운영 시연 데이터, 운영에서 E2E 초기화 API 닫힘 확인

- 기간: 2026-11-28 ~
- Issue: [#71](https://github.com/Rasit-NP/curtain-call/issues/71), PR: [#75](https://github.com/Rasit-NP/curtain-call/pull/75)
- 관련 결정: [0210](../decisions/0210-deploy-hosting.md), [0211](../decisions/0211-prod-demo-data.md), [0212](../decisions/0212-prod-reset-api-closed.md)(사람),
  [0213](../decisions/0213-prod-demo-data-impl.md), [0214](../decisions/0214-backend-image.md), [0215](../decisions/0215-deploy-check-methods.md)(에이전트).
  기존 [0013](../decisions/0013-flyway-out-of-order-local.md), [0015](../decisions/0015-health-probes-off.md), [0036](../decisions/0036-seed-accounts.md),
  [0160](../decisions/0160-e2e-reset-impl.md), [0161](../decisions/0161-e2e-environment.md), [0191](../decisions/0191-ci-workflow-security.md)을 따른다

## 배경

로드맵 2 [#64](https://github.com/Rasit-NP/curtain-call/issues/64)의 2단계(배포) 항목이다. 사람이 메인 세션에서 배포 방식을 정했다(2026-11-28):
AWS Lightsail 서울 리전 2GB VM 한 대에 Docker Compose로 웹(Caddy)·백엔드·PostgreSQL 17을 올리고, 배포 서버의 데이터는 초기화하지 않는다.

- `prod` 프로필은 스키마 마이그레이션만 적용해, 배포해도 공연장과 계정이 없다. 배포할 컨테이너 이미지도 없다.
- E2E 초기화 API(`POST /api/test-support/reset`)는 `e2e` 프로필에만 등록하지만, `prod` 프로필의 실제 컨텍스트에서 닫혀 있는지는 테스트로 고정하지 않았다.
  지금은 작은 컨텍스트로 빈이 등록되지 않는 것(`TestDataResetProfileTest`)과 test 프로필의 401·404(`AuthenticationTest`)만 본다.
- 공연 주기 생성 [#73](https://github.com/Rasit-NP/curtain-call/issues/73)은 이 Issue가 병합된 뒤 시작한다. #73은 공연장·시작 시각 유니크 제약 같은 스키마 마이그레이션을 더할 예정이다.
- 프론트엔드 배포 준비 #72가 같은 때 다른 워크트리에서 진행한다. 이 Issue는 `backend/`, 루트 `.env.example`, `docs/`만 고친다.

## 판단과 근거

사람이 정한 것(메인 세션, Issue '이미 정한 정책')은 결정 0210~0212에 `채택`으로 옮겼다. 이 Issue 세션에서 사람에게 새로 물은 정책은 없다.
구현하면서 정책으로 볼 만한 것(사용자가 겪는 동작, API 계약, 계정·보안)을 새로 정하지 않았다. 아래는 에이전트가 정한 것이다.

### 운영 시연 데이터를 반복 마이그레이션으로(결정 0213)

처음 떠올린 방법은 local 시드처럼 `V1000__`부터 버전 마이그레이션으로 넣는 것이었다. #73 본문을 읽다가 #73이 유니크 제약 마이그레이션(`V6__` 등)을 더할 것을 보고 다시 생각했다.

- 배포 서버는 초기화하지 않으므로, 운영 DB의 Flyway 이력에는 `V1000`이 남은 채로 다음 배포에서 `V6`을 만난다.
  out-of-order를 끈 prod에서는 `V6`이 이미 적용된 가장 큰 버전보다 작아 검증에 걸리고 앱이 뜨지 않는다. local에서 결정 0013이 재현한 것과 같은 실패다.
- prod에서 out-of-order를 켜는 방법도 있지만, 빈 DB와 운영 DB의 적용 순서가 달라진다.
- 반복 마이그레이션(`R__`)은 버전 순서에 끼지 않고 모든 버전 마이그레이션 뒤에 돈다. 대신 파일이 바뀌면 다시 돌므로 `ON CONFLICT DO NOTHING`으로 없는 행만 넣는다.

### 컨테이너 이미지(결정 0214)

- 다단계 빌드(JDK에서 `bootJar` → JRE에 Spring Boot 층), 비루트 uid 10001, `SPRING_PROFILES_ACTIVE=prod` 고정, exec 형식 `ENTRYPOINT`, `HEALTHCHECK`.
- 기반 이미지를 다이제스트로 고정하려고 확인해 보니, 버전 태그(`21.0.13.1_2-jre-noble`)는 이날 amd64만 있었다. 이 Mac(arm64)에서도 빌드하려고 두 아키텍처가 함께 있는
  `21-*-noble` 인덱스의 다이제스트를 썼다.
- JVM 메모리: 2GB VM에 PostgreSQL·Caddy와 함께 둔다. 힙 상한을 고정값(512MiB)으로 두어 #74의 Compose 메모리 제한과 상관없게 했고, Serial GC를 골랐다.
  실제로 띄워 재 보니 시작 직후 281MiB, 로그인 300번·조회 8,000번 뒤 350MiB였다.

### 확인 방법(결정 0215)

- prod 프로필 통합 테스트는 운영과 같은 환경변수 이름(`DB_URL` 등)으로 접속 정보를 넘겨 `application.yml`의 prod 문서를 그대로 시험한다.
  DB는 공유 컨테이너에 새로 만든 빈 DB다.
- 이후 마이그레이션 테스트는 '지금 가장 큰 스키마 버전 + 1'을 임시 폴더에 만들어, #73의 `V6`이 겪을 상황을 그대로 만든다.
- 이미지 확인은 `backend/scripts/check-image.sh`로 하고 `verify.sh`에 넣지 않았다. 이 Issue가 고치는 파일에 `verify.sh`·하네스가 없고,
  CI에서는 실행마다 이미지 안에서 Gradle 의존성을 다시 받아 PR마다 1분 넘게 늘어난다. #74의 배포 워크플로가 올리기 전에 쓰도록 제안했다.

## 진행

1. Issue, 루트·백엔드 `CLAUDE.md`, 결정 목차와 관련 결정(0011, 0013, 0015, 0036, 0160~0162, 0191), 명세 2.5·6.9·6.10, #73·#74 본문을 읽었다.
   `git fetch` 뒤 `origin/main`(`d6b5ba9`)에서 `chore/71-backend-deploy-prep` 브랜치를 만들었다.
2. 실패하는 테스트부터 썼다: `ProdProfileTest`(15개), `ProdMigrationOrderTest`(1개), `ApplicationProfilesTest`의 prod Flyway 위치 기대값.
   시연 데이터가 없어 11개가 실패했다(시연 계정 로그인 401, 계정 0개, 공연장 0곳, `db/demo` 없음, 이후 마이그레이션 테스트의 '시연 계정 없음', prod 위치).
   이미 있는 동작(토큰 없는 초기화 401, health, 초기화 빈 없음, E2E 계정 로그인 401)은 처음부터 통과했다.
   - `ApplicationProfilesTest`의 '`prod`는 시드를 쓰지 않는다'는 Issue가 운영 시연 데이터를 요구해 기대값을 `db/migration, db/demo`로 바꿨다(PR 본문에 적는다).
3. `db/demo/R__demo_users.sql`·`R__demo_venues.sql`과 prod Flyway 위치를 더해 모두 통과시켰다. 시연 계정 해시는 운영과 같은 인코더로 계정마다 만들었다.
4. 테스트가 결함을 잡는지 구현을 하나씩 망가뜨려 확인하고 되돌렸다. 여섯 가지 모두 해당 테스트가 실패했다.
   - 시연 데이터를 `V1000__`·`V1001__`로: 이후 마이그레이션 테스트가 `Detected resolved migration not applied to database`로 실패.
   - 계정·배치의 `ON CONFLICT` 삭제: 다시 실행 테스트가 유니크 위반으로 실패.
   - prod에 `db/seed`도: E2E 계정·`demo` 로그인 200, 계정 목록, 공연 3개로 실패.
   - (R6) 초기화 경로를 모든 프로필에서 열기: 토큰 없는 초기화가 401이 아니라 404.
   - (R6) 초기화 컨트롤러·서비스를 prod에도 등록: 유효한 토큰의 초기화가 204가 되고, 시연 계정 대신 E2E 시드가 들어가 13개가 실패.
5. 이미지 확인 스크립트를 먼저 쓰고 Dockerfile이 없어 실패하는 것을 확인한 뒤 Dockerfile과 `.dockerignore`를 썼다. 첫 실행에 통과했다
   (캐시 없는 빌드 포함 63초, 기동 2초, SIGTERM으로 0초 만에 종료 코드 143).
6. HEALTHCHECK가 실제로 healthy가 되는지도 보도록 스크립트를 보강했다(`--start-interval=2s`를 더해 시작 중에는 2초마다 본다).
7. 스크립트가 결함을 잡는지 Dockerfile 변형 이미지 넷으로 확인했다. 모두 실패로 잡았고, 남은 컨테이너·네트워크는 없었다.
   USER 없음(루트), 셸 형식 `ENTRYPOINT`(`docker stop`이 SIGKILL까지 가 종료 코드 137), 프로필 없음(local로 떠 시작 중 종료), HEALTHCHECK 포트 틀림(`starting`에서 멈춤).
8. 결정 기록(0210~0215), `backend/CLAUDE.md`(명령, 프로필, 인증, 마이그레이션과 데이터, 컨테이너 이미지, 테스트 작성), `.env.example`, 테스트 대응표를 고쳤다.
   새 필수 환경변수는 없다. `.env.example`에는 이미지가 prod로 고정돼 네 값이 필요하다는 것과 선택 변수 `JDK_JAVA_OPTIONS`를 적었다.

9. 자체 점검에서 이미지 확인 스크립트의 `curl`이 연결에 실패하면 `set -e`로 실패 메시지 없이 끝나는 것을 찾아, `|| true`로 받고 값으로 판단하게 고쳤다. 다시 돌려 통과했다.
10. 로컬 `./scripts/verify.sh` 전체가 통과했다(백엔드 단위 176개, 통합 324개, 프론트엔드, E2E). PR [#75](https://github.com/Rasit-NP/curtain-call/pull/75)를 만들었다.
11. CI `verify`가 처음 실행에서 통과했다([46149850976](https://github.com/Rasit-NP/curtain-call/actions/runs/46149850976), 3분 3초, 커밋 `583d584`). 진행 기록만 고친 다음 커밋은 문서 PR 규칙대로 하네스만 돈다(결정 0190).

## 리뷰

- 설정·시드·이미지가 중심이고 인증 동작은 바꾸지 않아 루트 `CLAUDE.md` '리뷰' 표대로 `/code-review low`로 했다(테스트 파일 제외, 한 번 읽기). 지적은 없었다.

## 남은 일

- README에 시연 계정 안내: 이 Issue가 고치는 파일에 README가 없다. 배포 주소가 정해지는 #74(또는 사람이 정한 Issue)에서 함께 쓴다(결정 0211).
