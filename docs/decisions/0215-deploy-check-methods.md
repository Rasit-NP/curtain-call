# 0215. 배포 준비 확인 방법: prod 프로필 통합 테스트는 공유 컨테이너의 새 DB에 환경변수 이름으로 붙이고, 이미지는 verify.sh 밖의 확인 스크립트로 본다

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #71 '에이전트가 정하는 것': 이미지 검증 방법(명령 또는 테스트)과 verify.sh에 넣을지. 운영 시연 데이터·초기화 API의 테스트 방법)
- 관련: Issue [#71](https://github.com/Rasit-NP/curtain-call/issues/71), [#74](https://github.com/Rasit-NP/curtain-call/issues/74), 결정 [0016](0016-integration-test-base.md),
  [0161](0161-e2e-environment.md), [0211](0211-prod-demo-data.md), [0212](0212-prod-reset-api-closed.md), [0213](0213-prod-demo-data-impl.md), [0214](0214-backend-image.md)

## 결정

- **prod 프로필 통합 테스트**(`ProdProfileTest`, `@Tag("integration")`, `verify.sh` 백엔드에 들어간다)
  - `@ActiveProfiles("prod")`로 띄우고, DB 접속 정보와 JWT 서명 키는 운영과 같은 환경변수 이름(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`)으로만 넘긴다.
    `@ServiceConnection`을 쓰지 않아 `application.yml`의 prod 문서가 실제로 쓰인다.
  - DB는 통합 테스트가 공유하는 PostgreSQL 17 컨테이너에 새로 만든 빈 DB다(테스트 도우미 `FreshDatabases`). prod 프로필의 Flyway 위치로 처음부터 마이그레이션한다.
    `IntegrationTest`를 상속하지 않아 테이블을 비우지 않고, 테스트는 데이터를 바꾸지 않는다.
  - 보는 것: 상태 확인 `{"status":"UP"}`, 시연 계정 다섯 개 로그인, E2E 계정과 로컬 `demo`의 401 `INVALID_CREDENTIALS`, 계정이 시연 계정 다섯 개뿐인지,
    공연장·배치가 있고 공연이 없는지, 시연 데이터 스크립트를 다시 실행해도 행이 그대로인지, 초기화 경로(토큰 없이 401, 로그인으로 받은 토큰이면 404이고 데이터가 그대로), 초기화 빈이 없는지.
- **이후 마이그레이션 테스트**(`ProdMigrationOrderTest`): `application.yml`의 prod Flyway 설정(위치, out-of-order)을 읽어 새 DB에 마이그레이션한 뒤,
  임시 폴더에 '지금 가장 큰 스키마 버전 + 1' 마이그레이션을 더해 다시 마이그레이션한다. 실패하지 않고 그 버전만 적용되며 시연 계정이 그대로인지 본다.
- **이미지 확인**: `backend/scripts/check-image.sh`. 이미지를 만들고(또는 `SKIP_BUILD=1 CHECK_IMAGE=<이미지>`로 만든 이미지를), 이 실행만 쓰는 Docker 네트워크에
  PostgreSQL 17과 함께 운영 환경변수 이름으로 띄워 확인한다: 비루트 사용자, health `{"status":"UP"}`, HEALTHCHECK healthy, `SPRING_PROFILES_ACTIVE=prod`,
  시연 계정 로그인 200, 토큰 없는 초기화 401, `docker stop`의 SIGTERM으로 종료. JVM 설정·메모리·이미지 크기는 참고로 출력한다. 끝나면 컨테이너와 네트워크를 지운다.
  DB는 바깥에 열지 않고, 앱은 127.0.0.1의 빈 포트(Docker가 고른다)에만 연다.
- **`verify.sh`에는 이미지 확인을 넣지 않는다.** Dockerfile을 바꾸거나 배포 전에 손으로 돌린다. 명령은 `backend/CLAUDE.md`에 적는다.

## 근거

- 운영 설정을 그대로 시험하려면 환경변수 이름으로 넘겨야 한다. `@ServiceConnection`은 접속 정보를 덮어써 `${DB_URL}` 자리를 지나친다.
- 새 DB: test 프로필 DB의 Flyway 이력에는 시연 데이터가 없어 같이 쓰면 어느 한쪽의 검증이 깨지고, 통합 테스트는 테스트마다 테이블을 비운다.
  컨테이너를 하나 더 띄우는 것보다 빠르고, JVM이 끝나 컨테이너가 지워질 때 함께 없어진다.
- 다음 마이그레이션 버전을 계산하는 이유: 고정 번호(예: V999)로는 시연 데이터를 그 번호보다 작은 버전으로 넣은 실수를 잡지 못한다. #73의 V6이 겪을 상황을 그대로 만든다.
- 테스트가 결함을 잡는지 구현을 망가뜨려 확인했다(PR 본문에 적는다): 시연 데이터를 버전 마이그레이션으로, `ON CONFLICT` 삭제(계정, 배치), prod에 `db/seed` 추가,
  초기화 경로를 모든 프로필에서 열기, 초기화 빈을 prod에 등록. 이미지 확인은 Dockerfile 변형 넷(USER 없음, 셸 형식 ENTRYPOINT, 프로필 없음, HEALTHCHECK 포트 틀림)을 모두 잡았다.
- `verify.sh`에 넣지 않는 이유
  - 이 Issue가 고치는 파일에 `verify.sh`·하네스가 없다(메인 세션이 정한 범위). 넣으려면 하네스 테스트를 먼저 써야 하는 별도 작업이다.
  - CI 러너에는 BuildKit 캐시가 남지 않아 실행마다 Gradle 배포판·의존성을 이미지 안에서 다시 받는다(이 Mac에서 캐시 없이 63초). PR마다 1분 넘게 늘어난다.
  - 운영 설정이 깨지는 것은 `ProdProfileTest`가 PR마다 잡는다. 이미지만의 결함(Dockerfile, 기반 이미지)은 Dockerfile을 바꿀 때 생기고, 배포 전에 #74가 확인할 수 있다.

## 고려했지만 고르지 않은 것

- 이미지 확인을 Testcontainers 테스트(`ImageFromDockerfile`)로: 기본 빌더가 BuildKit 캐시 마운트를 지원하지 않고, 통합 테스트마다 이미지를 만들게 된다.
- prod 테스트용 컨테이너를 따로 띄우기: 새 DB로 충분하다.
- `IntegrationTest`를 상속하고 prod 프로필만 더하기: 테이블을 비우고 `@ServiceConnection`으로 붙어 운영 설정과 시연 데이터를 시험하지 못한다.

## 적용과 남은 점

- 코드: `backend/src/test/java/com/curtaincall/ProdProfileTest.java`, `ProdMigrationOrderTest.java`, `support/FreshDatabases.java`, `backend/scripts/check-image.sh`.
- #74 제안: 배포 워크플로가 Actions에서 이미지를 만든 뒤, 올리기 전에 `SKIP_BUILD=1 CHECK_IMAGE=<만든 이미지> backend/scripts/check-image.sh`로 확인하면
  운영과 같은 아키텍처(amd64)의 이미지를 배포 전에 본다. 넣을지는 #74가 정한다.
- 이미지 확인을 PR CI에 넣을지는 나중에 사람이 정할 수 있다(그때 하네스 테스트와 함께).
