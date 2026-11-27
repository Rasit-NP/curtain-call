# 백엔드 규칙

## 기술 구성

- Java 21, Spring Boot(start.spring.io에서 선택 가능한 최신 안정 버전), Gradle 래퍼(`./gradlew`)
- Spring Web, Spring Data JPA, Spring Security, OAuth2 Resource Server(JWT), Validation, Flyway, Actuator, PostgreSQL 17
- 테스트: JUnit, AssertJ, Spring Boot Test, Testcontainers(PostgreSQL), JaCoCo
- 로컬 개발 DB는 루트 `compose.yaml`의 `db` 서비스로 띄운다. E2E DB는 실행마다 Testcontainers로 새로 띄운다(`bootE2eRun`, 결정 0161·0162).
  통합 테스트와 E2E에는 Docker가 필요하다.

## 명령 (`backend/`에서 실행)

- 단위 테스트: `./gradlew test` (`@Tag("integration")` 테스트 제외)
- 통합 테스트: `./gradlew integrationTest` (`@Tag("integration")` 테스트만)
- 클래스 하나: `./gradlew test --tests '*HoldServiceTest'` / 메서드 하나: `--tests '*HoldServiceTest.methodName'`
- 빌드와 커버리지: `./gradlew assemble jacocoTestReport`
- 뮤테이션 테스트: `./gradlew pitest`(보통은 루트에서 `./scripts/verify.sh mutation`). 규칙 코드 15개 클래스에 단위·통합 테스트를 모두 돌린다. Docker가 필요하고 3분 남짓 걸린다.
- 로컬 실행: 루트에서 `docker compose up -d db` 후 `./gradlew bootRun`
- E2E용 실행: `./gradlew bootE2eRun --args='--server.address=127.0.0.1 --server.port=<포트>'`(e2e 프로필, 실행마다 새 PostgreSQL 컨테이너).
  보통은 E2E(`e2e/playwright.config.ts`)가 띄운다.
- 리포트: `build/reports/tests/test/`, `build/reports/tests/integrationTest/`, `build/reports/jacoco/test/html/`, `build/reports/pitest/`(`index.html`, `mutations.xml`)

## 빌드 설정 (첫 설정 Issue에서 만든다)

- `test` 작업은 `integration` 태그를 제외하고, `integrationTest` 작업을 따로 등록해 그 태그만 실행한다. `check`는 둘 다 실행한다.
- `jacocoTestReport`는 단위·통합 테스트 결과를 합쳐서 만든다.
- `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`
- `pitest` 블록: 측정 대상 클래스를 `targetClasses`에 하나씩 적고, 점수 기준은 `mutationThreshold`다(결정 0200, 0201, 0203). 대상이나 설정을 바꾸면 기준선을 다시 잰다.

## 패키지 구조

- 기능 단위로 나눈다. 예: `performance`, `hold`, `reservation`, `payment`, `auth`, `common`
- 기능 안은 `api`(컨트롤러, 요청·응답 DTO), `application`(서비스), `domain`(엔티티, 규칙), `infra`(외부 연동 구현)로 나눈다.
- 컨트롤러는 엔티티를 그대로 반환하지 않는다. 요청과 응답은 record DTO로 만든다.

## 비즈니스 규칙 구현

- R1, R2의 동시성은 DB로 보장한다(조건부 UPDATE, 행 잠금 등). `synchronized` 같은 JVM 안의 락은 쓰지 않는다. 서버가 여러 대면 깨진다.
- 조건부 UPDATE는 영향받은 행 수로 성공 여부를 판단한다.
- 잠금 조회 전에 같은 엔티티를 일반 조회로 불러오지 않는다. 영속성 컨텍스트에 남은 이전 값이 그대로 쓰일 수 있다.
- 현재 시각은 주입한 `Clock`으로 얻고 `Instant`로 다룬다. `Instant.now()`, `LocalDateTime.now()`를 직접 부르지 않는다.
- 결제는 `PaymentGateway` 인터페이스 뒤에 둔다. `FakePaymentGateway`가 성공과 실패를 흉내 내고, 테스트에서 결과를 지정할 수 있어야 한다.
- R5는 `Idempotency-Key`에 대한 DB 유니크 제약으로 보장한다.

## API

- 경로는 `/api`로 시작하고, 요청과 응답은 JSON이다.
- 오류 응답은 `ProblemDetail`(RFC 9457) 형식에 `code` 속성을 더한다.
  예: `{"status": 409, "code": "SEAT_ALREADY_HELD", "detail": "이미 선점된 좌석입니다."}`
- 오류 코드는 루트 CLAUDE.md의 'API 오류 코드' 표에 있는 것만 쓴다. 예외 처리는 `@RestControllerAdvice` 한곳에 모은다.
  - 예외: 요청이 앱에 닿기 전에 Tomcat이 거부한 오류는 공통 처리기를 부를 수 없어 `ProblemDetailErrorReportValve`가 공통 처리기와 같은 필드로 쓴다(결정 0037). 공통 처리기 밖에서 오류 본문을 만드는 곳은 이 밸브 하나뿐이고, 더 만들지 않는다.
- 처리하지 못한 예외는 `500 INTERNAL_ERROR`로 응답하고, 스택 트레이스를 응답 본문에 넣지 않는다.

## 프로필

- `local`: 로컬 개발. `compose.yaml`의 DB와 시드 데이터를 쓴다.
- `e2e`: 시드를 넣고 E2E 지원 엔드포인트가 켜진다. DB 주소를 정하지 않는다(초기화가 개발 DB를 지우지 않게).
  E2E 실행기(`src/e2eRun`, `bootE2eRun`)가 실행마다 새 PostgreSQL 컨테이너를 붙여 띄운다(결정 0161, 0162).
- `test`: 통합 테스트. Testcontainers DB를 쓰고 시드 데이터를 넣지 않는다.
- `prod`: 배포용. DB 접속 정보와 `JWT_SECRET`은 환경변수로만 받는다.

## 인증 (JWT)

- 액세스 토큰만 쓴다. HS256 서명, 유효 시간 1시간, 갱신 토큰은 만들지 않는다.
- 인증 필터를 직접 만들지 않는다. Spring Security OAuth2 Resource Server의 JWT 지원을 쓰고,
  검증은 `NimbusJwtDecoder.withSecretKey(...)`, 발급은 `NimbusJwtEncoder`로 한다.
- `POST /api/auth/login`: 아이디와 비밀번호를 받아 `{accessToken, expiresIn}`을 돌려준다. 실패하면 `401 INVALID_CREDENTIALS`.
- 토큰이 없거나 잘못되면 `401 AUTH_REQUIRED`를 `ProblemDetail`로 돌려준다(인증 진입점을 바꿔 JSON으로 응답).
- 세션을 만들지 않고(`STATELESS`) CSRF 보호는 끈다. 쿠키로 인증하지 않기 때문이다.
- 인증 없이 열리는 경로: `POST /api/auth/login`, `GET /api/performances/**`, `GET /actuator/health`. 나머지는 인증이 필요하다.
- 서명 키: `local`, `e2e`, `test`는 저장소에 둔 개발 전용 키(이름에 `dev` 포함, 32바이트 이상)를 쓴다.
  `prod`는 환경변수 `JWT_SECRET`만 쓰고, 값이 없으면 애플리케이션이 시작되지 않게 한다.
- 비밀번호는 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`로 해시해서 저장한다.
- 테스트 계정은 시드 데이터로 넣는다. 시연용(README 안내)과 E2E용 계정을 따로 둔다.
- 인증이 필요한 API 테스트는 `spring-security-test`의 `jwt()`로 요청을 만든다.
  로그인부터 토큰 사용까지의 실제 흐름은 별도 통합 테스트로 검증한다.

## E2E 지원

- `e2e` 프로필에서만 `POST /api/test-support/reset` 컨트롤러를 등록한다(`@Profile("e2e")`).
  데이터를 비우고 시드를 다시 넣으며, 인증 없이 호출할 수 있다.
  Flyway 이력을 뺀 모든 테이블을 한 트랜잭션에서 비우고 `db/seed`를 버전 순서로 다시 실행한다(결정 0160).
- 이 컨트롤러가 `e2e`가 아닌 프로필에서는 등록되지 않는 것을 테스트로 확인한다.
- E2E 실행기는 테스트 소스와 따로 `src/e2eRun`에 둔다. 테스트 클래스(`@TestConfiguration`, 시험용 컨트롤러)가 클래스패스에 있으면
  컴포넌트 스캔에 걸려 E2E 서버에 들어간다. 그래서 `bootTestRun`(테스트 클래스패스로 띄우기)은 쓰지 않는다.

## 마이그레이션과 데이터

- 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration/V{번호}__{설명}.sql`)으로만 바꾼다.
- 시드 데이터(공연, 좌석, 테스트 계정)는 `src/main/resources/db/seed/`에 두고, 테스트 프로필의 Flyway 위치에서는 뺀다.
- 시드 파일은 스키마 마이그레이션과 버전 번호가 겹치지 않게 `V1000__`부터 쓴다.
- 테스트는 필요한 데이터를 직접 만든다.

## 테스트 작성

- 단위 테스트: 도메인 규칙과 서비스 로직. 스프링 컨텍스트 없이 돌리고, 시간은 `Clock.fixed(...)`로 고정한다.
- 통합 테스트: `@Tag("integration")`을 붙이고 `@SpringBootTest`와 MockMvc로 API부터 DB까지 검증한다.
- 통합 테스트에서 시간을 바꿔야 하면, 테스트용으로 시각을 옮길 수 있는 `Clock` 빈을 쓴다.
- PostgreSQL 컨테이너는 모든 통합 테스트가 하나를 공유하고 `@ServiceConnection`으로 연결한다.
- 통합 테스트에 `@Transactional` 롤백을 쓰지 않는다. 다른 스레드의 트랜잭션과 실제 커밋 동작을 가리기 때문이다.
  대신 각 테스트 전에 테이블을 비운다(Flyway 이력 테이블 제외).
- 동시성 테스트(R1, R2)는 `CountDownLatch`로 여러 스레드를 동시에 출발시키고, 성공 개수와 DB 최종 상태를 모두 검증한다.
- Docker가 꺼져 있어 통합 테스트를 실행할 수 없으면 코드를 고치지 말고 사람에게 알린다.
- 스프링이 시작할 때만 도는 코드(빈 생성, 보안 설정)는 PIT가 통합 테스트로 재지 못한다. 빈 생성 메서드를 스프링 없이 부를 수 있으면 단위 테스트로도 확인한다(결정 0204).