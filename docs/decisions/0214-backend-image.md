# 0214. 백엔드 이미지: 다단계 Dockerfile(JDK에서 bootJar, JRE에 층으로), 기반 이미지 다이제스트 고정, 비루트 uid 10001, prod 고정, 힙 512MiB·Serial GC, HEALTHCHECK

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #71 '에이전트가 정하는 것': Dockerfile 구성, 빌드 단계, 실행 이미지, 비루트 사용자, 2GB 서버에 맞는 JVM 메모리 설정)
- 관련: Issue [#71](https://github.com/Rasit-NP/curtain-call/issues/71), [#74](https://github.com/Rasit-NP/curtain-call/issues/74), 결정 [0015](0015-health-probes-off.md),
  [0191](0191-ci-workflow-security.md), [0210](0210-deploy-hosting.md), [0215](0215-deploy-check-methods.md)(확인 방법)

## 결정

- **파일**: `backend/Dockerfile`, `backend/.dockerignore`. 빌드 맥락은 `backend/`다(`docker build --tag curtain-call-backend backend`).
  `.dockerignore`는 허용 목록이다(Gradle 래퍼, `build.gradle`, `settings.gradle`, `src/main/`만 보낸다).
- **빌드 단계**: `eclipse-temurin:21-jdk-noble`에서 `./gradlew --no-daemon bootJar`로 운영 jar를 만들고, `java -Djarmode=tools -jar … extract --layers`로
  Spring Boot 층(dependencies, spring-boot-loader, snapshot-dependencies, application)을 푼다. 테스트는 돌리지 않는다(검증은 `verify.sh`와 CI).
  Gradle 배포판과 의존성은 BuildKit 캐시 마운트(`/root/.gradle`)에 두어 이미지 층에 남기지 않는다.
- **실행 단계**: `eclipse-temurin:21-jre-noble`에 네 층을 잘 바뀌지 않는 순서로 복사한다. 실행은 `java -jar /app/application.jar`(exec 형식, java가 PID 1).
- **기반 이미지 고정**: 두 이미지 모두 다중 아키텍처 인덱스의 다이제스트로 고정하고, 태그와 확인 명령, 그날의 버전(amd64 21.0.13.1+2, arm64 21.0.13+9)을 주석에 적는다.
- **비루트**: 시스템 사용자·그룹 `curtaincall`(uid·gid 10001, 홈·셸 없음)을 만들고 `USER 10001:10001`로 돈다. 앱 파일은 루트 소유라 프로세스가 고칠 수 없다.
- **프로필 고정**: `ENV SPRING_PROFILES_ACTIVE=prod`. 실행에는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`이 필요하고 없으면 시작하지 않는다.
- **JVM 메모리**: `ENV JDK_JAVA_OPTIONS="-Xmx512m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"`. Compose 환경변수로 통째로 바꿀 수 있다.
- **상태 확인**: `HEALTHCHECK`가 `curl --fail http://127.0.0.1:8080/actuator/health`를 시작하는 동안(60초) 2초마다, 그 뒤 15초마다 부른다(3번 실패하면 unhealthy).

## 근거

- **다단계 빌드**: 소스만 있으면 어디서든(#74의 Actions 러너 포함) 같은 방법으로 이미지를 만든다. 실행 이미지에는 JDK·Gradle·소스가 없다.
  #74는 이미지를 Actions에서 빌드하고 2GB 서버에서는 빌드하지 않는다. 이 Mac에서 캐시 없는 첫 빌드가 63초(Gradle 단계 35초)였다.
- **층**: 코드만 바뀌면 마지막 층(143kB)만 바뀌고 의존성 층(61.6MB)은 그대로라, 서버가 받는 양이 작다. 압축 크기는 159MB(기반 108MB)다.
- **다이제스트 고정**: 태그는 다른 내용으로 옮겨질 수 있다(결정 0191과 같은 이유). 버전 태그(`21.0.13.1_2-jre-noble`)는 이날 amd64만 있어
  이 Mac(arm64)에서 빌드할 수 없었다. 그래서 두 아키텍처가 함께 있는 `21-*-noble` 인덱스의 다이제스트를 쓴다.
- **Ubuntu(noble) 기반**: 공식 Temurin 이미지이고 `curl`(HEALTHCHECK), `useradd`가 들어 있다. Alpine(musl)보다 호환성 문제를 덜 생각해도 된다.
- **uid 10001**: 기반 이미지의 `ubuntu`(1000, sudo 그룹)와 겹치지 않고, 숫자 uid라 실행 환경이 이름을 몰라도 비루트임을 알 수 있다.
- **prod 고정**: 이 이미지는 배포용이다. 프로필이 없으면 local로 떠 개발 DB 주소·개발 JWT 키를 쓰려 한다. 프로필을 뺀 변형은 시작 중에 끝났다.
- **메모리(2GB VM)**: 서버에 OS·Docker(약 300~400MiB), PostgreSQL 17(기본 shared_buffers 128MB, 약 150~250MiB), Caddy(약 50MiB)가 함께 돈다.
  - 이 이미지를 띄워 재 보니 시작 직후 281MiB, 시연 계정 로그인 300번(bcrypt)·조회 8,000번 뒤 350MiB였다(컨테이너 메모리, CPU 2개 제한).
  - 힙 상한 512MiB에 힙 밖(메타스페이스·코드 캐시·스레드, 약 150~200MiB)을 더해도 JVM이 700MiB 안팎을 넘지 않아, 모두 합쳐 1.4GiB 안쪽이다.
  - 상한을 컨테이너 메모리 비율(`MaxRAMPercentage`)로 두지 않은 것은 Compose에 메모리 제한이 없으면 JVM이 VM 전체(2GB)를 기준으로 잡기 때문이다. 고정값은 #74의 설정과 상관없다.
  - Serial GC: 힙이 작고 요청이 적은 서버라 일시 정지가 짧고, G1보다 GC 자체가 쓰는 메모리가 적다. CPU 2개·2GB에서는 JVM이 G1을 고르므로 적는다.
  - `ExitOnOutOfMemoryError`: 메모리가 모자라면 반쯤 망가진 채 남지 않고 끝나서, 재시작 정책(#74)이 다시 띄운다.
  - `JDK_JAVA_OPTIONS`는 java 실행기만 읽는다(`JAVA_TOOL_OPTIONS`와 달리 모든 JVM 도구에 붙지 않는다).
- **exec 형식 ENTRYPOINT**: java가 SIGTERM을 직접 받아 정상 종료한다. 셸 형식으로 바꾼 변형은 `docker stop`이 SIGKILL(종료 코드 137)까지 갔다.
- **HEALTHCHECK**: Compose의 `depends_on: condition: service_healthy`와 `docker ps`가 쓸 수 있다. health는 DOWN이면 503이라 `--fail`로 충분하다(결정 0015의 응답 형식은 그대로).
  포트를 틀리게 한 변형은 30초 안에 healthy가 되지 않았다.

## 고려했지만 고르지 않은 것

- jar를 밖에서 만들고 Dockerfile은 복사만: 이미지 빌드 전에 Gradle 단계가 따로 필요하고, 만든 환경에 따라 결과가 달라질 수 있다.
- 기반 이미지 태그만(`21-jre-noble`): 보안 갱신은 저절로 따라오지만 같은 Dockerfile이 다른 이미지를 만든다. 갱신은 다이제스트를 바꾸는 커밋으로 한다.
- distroless·jlink로 줄인 JRE: 더 작지만 `curl`이 없어 HEALTHCHECK를 따로 만들어야 하고, 모듈 목록을 관리해야 한다.
- CDS·AOT 캐시로 시작 시간 줄이기: 이 Mac에서 시작이 2초라 필요하지 않다.

## 적용과 남은 점

- 기반 이미지를 올릴 때: `docker buildx imagetools inspect eclipse-temurin:21-jdk-noble`(와 `21-jre-noble`)로 새 인덱스 다이제스트를 확인해 바꾸고 주석의 날짜·버전을 고친다. 자동 갱신은 두지 않았다.
- #74: 앱은 시작할 때 DB에 붙지 못하면 끝난다(재시도하지 않는다). Compose에서 DB의 상태 확인(`pg_isready`)을 두고 `depends_on: condition: service_healthy`로 기다리게 하고, 재시작 정책을 둔다.
  웹(Caddy)은 이 이미지의 HEALTHCHECK로 백엔드를 기다릴 수 있다. 백엔드 포트(8080)는 바깥에 열지 않는다(결정 0210).
- 서버에서 더 줄여야 하면 `JDK_JAVA_OPTIONS`로 힙 상한을 바꾼다. 바꾸면 이 결정을 대체하는 결정을 남긴다.
