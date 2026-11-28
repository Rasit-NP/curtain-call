# syntax=docker/dockerfile:1
# 백엔드 컨테이너 이미지(Issue #71, 결정 0214). 배포 서버(2GB VM)에서 prod 프로필로 돈다.
#
#   빌드: docker build --tag curtain-call-backend backend      (저장소 루트에서. 빌드 맥락은 backend/)
#   실행: 환경변수 DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET(이름은 루트 .env.example)이 필요하다. 없으면 시작하지 않는다.
#   확인: backend/scripts/check-image.sh(이미지를 만들어 PostgreSQL 17에 붙여 띄우고 확인한다)
#
# 기반 이미지는 다중 아키텍처 인덱스의 다이제스트로 고정한다(태그는 다른 내용으로 옮겨질 수 있다). 올릴 때는 새 다이제스트를 확인해 바꾼다:
#   docker buildx imagetools inspect eclipse-temurin:21-jdk-noble   (2026-11-28: amd64 21.0.13.1+2, arm64 21.0.13+9)

# 1단계: 운영 jar를 만들고 Spring Boot 층으로 푼다. 테스트는 돌리지 않는다(검증은 verify.sh와 CI가 한다).
FROM eclipse-temurin:21-jdk-noble@sha256:dc333cdac241f073941f043792f2a2f9b790bfe7cd98ef2843c08880986708de AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src/main src/main
# Gradle 배포판과 의존성은 빌드 캐시(BuildKit)에 두어 다시 빌드할 때 내려받지 않는다. 이미지 층에는 남지 않는다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain bootJar \
 && cp build/libs/curtain-call-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# 2단계: 실행 이미지. JRE만 두고, 잘 바뀌지 않는 층(의존성)부터 쌓아 코드만 바뀌면 마지막 층만 바뀐다.
FROM eclipse-temurin:21-jre-noble@sha256:54999a956a8bf8be9af0052790a6a832bf91a62fad3199fd194c3cc28057fc62
RUN groupadd --system --gid 10001 curtaincall \
 && useradd --system --uid 10001 --gid curtaincall --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin curtaincall
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
# 파일은 루트 소유(읽기만)로 두고, 프로세스는 숫자 uid의 비루트 사용자로 돈다.
USER 10001:10001

# 이미지는 배포용이다. 프로필을 prod로 고정해, 환경변수가 빠지면 개발 DB·개발 키로 뜨지 않고 시작에 실패한다.
ENV SPRING_PROFILES_ACTIVE=prod
# 2GB VM에 웹(Caddy)·PostgreSQL과 함께 둔다. 힙 상한을 고정하고(컨테이너 메모리 제한과 상관없이), 힙이 작고 요청이 적어
# 메모리를 덜 쓰는 Serial GC를 쓴다. 메모리가 모자라면 망가진 채 남지 않고 끝나서 재시작 정책이 다시 띄우게 한다.
# java 실행기가 읽는 변수다. Compose 환경변수로 통째로 바꿀 수 있다(값은 결정 0214의 메모리 계산을 따른다).
ENV JDK_JAVA_OPTIONS="-Xmx512m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# 상태 확인: Compose의 depends_on(service_healthy)과 docker ps가 쓴다. 시작하는 동안(최대 60초)은 2초마다, 그 뒤에는 15초마다 본다.
# health가 DOWN이면 503이라 curl --fail이 실패한다.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --start-interval=2s --retries=3 \
    CMD curl --fail --silent --output /dev/null http://127.0.0.1:8080/actuator/health || exit 1
# exec 형식: java가 PID 1이 되어 docker stop의 SIGTERM을 직접 받고 정상 종료(graceful shutdown)한다.
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
