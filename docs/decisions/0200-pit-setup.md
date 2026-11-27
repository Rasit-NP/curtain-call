# 0200. 뮤테이션 테스트 도입: Gradle PIT 플러그인으로 규칙 코드에 단위·통합 테스트를 함께 돌리고, 연산자 STRONGER·스레드 4·증분 분석 없음으로 잰다

- 상태: 에이전트 결정
- 날짜: 2026-11-28
- 결정한 사람: 에이전트(Issue #69 '에이전트가 정하는 것': PIT 도입 방식, 변이를 죽일 테스트, 실행 명령의 모양, 리포트 위치)
- 관련: Issue [#69](https://github.com/Rasit-NP/curtain-call/issues/69), 결정 [0001](0001-verify-single-entry.md), [0016](0016-integration-test-base.md),
  [0180](0180-roadmap2-transition.md), [0201](0201-pit-target-classes.md)(측정 대상)

## 결정

- **도구**: Gradle 플러그인 `info.solidsoft.pitest` 1.19.0, PIT 1.30.2, JUnit 5 플러그인(`pitest-junit5-plugin`) 1.2.3. `backend/build.gradle`의 `pitest` 블록 하나에 둔다.
- **명령**: `backend/`에서 `./gradlew pitest`. 리포트는 `backend/build/reports/pitest/`의 `index.html`(HTML)과 `mutations.xml`(XML)이다.
  실행마다 같은 경로에 덮어쓴다(`timestampedReports = false`).
- **변이를 죽일 테스트**: 테스트 소스 세트 전체(`targetTests = com.curtaincall.*`), 곧 단위 테스트와 `@Tag("integration")` 통합 테스트를 모두 쓴다.
  Docker가 필요하다. 동시성 테스트도 뺀 것이 없다.
- **연산자**: `STRONGER`. PIT 1.30.2에서 기본 묶음 `DEFAULTS`(부호·산술 바꾸기, void 호출 지우기, 조건을 늘 거짓으로(`*_ELSE`), 경계 바꾸기,
  증감 바꾸기, 반환값 바꾸기)에 조건을 늘 참으로 만드는 `REMOVE_CONDITIONALS_ORDER_IF`·`REMOVE_CONDITIONALS_EQUAL_IF`와 `EXPERIMENTAL_SWITCH`를 더한 묶음이다.
- **실행 설정**: 스레드 4, 시간 제한은 PIT 기본값(정상 실행 시간 × 1.25 + 4초), 증분 분석 없음(매번 처음부터 잰다).
  작업자(minion) JVM은 Java 21 툴체인으로 띄운다(`jvmPath`).

## 근거

- Gradle 플러그인: 백엔드 빌드가 Gradle이고, 1.19.0이 Gradle 9를 지원하는 첫 판이다(릴리스 노트 'Initial support for Gradle 9', Java 17·Gradle 8.4 이상).
  PIT는 지금 최신 안정판(1.30.2, 2026-11-05)을 쓴다. 플러그인 기본값(1.22.1)보다 새 판이다.
- JUnit 5 플러그인 1.2.3: JUnit Platform 실행기를 `provided`로 두어 프로젝트의 실행기(JUnit 6.1.3)를 그대로 쓴다(1.2.2부터).
- 통합 테스트를 넣는다: 규칙의 중심인 `HoldService`·`PaymentService`에는 단위 테스트가 없고 통합 테스트만 있다. 단위 테스트만 쓰면 이 두 클래스의 변이는 모두 '덮이지 않음'이 된다.
  PIT는 변이마다 그 줄을 덮는 테스트만, 빠른 테스트부터 돌리고 처음 죽인 테스트에서 멈추므로 통합 테스트를 넣어도 시간이 크게 늘지 않는다.
  작업자 JVM 안에서는 스프링 컨텍스트와 PostgreSQL 컨테이너를 한 번 띄워 계속 쓴다(결정 0016의 공유 컨테이너가 그대로 동작한다).
- `STRONGER`: 대상이 규칙 코드로 좁아 변이 수가 많지 않다. 규칙은 대부분 조건(한도 비교, 만료 비교, 주인·상태 비교)이라, 조건마다 '늘 거짓'뿐 아니라
  '늘 참'도 만들면 조건 한쪽만 확인하는 테스트를 더 잘 드러낸다. `ALL`은 의미가 겹치거나 동등 변이가 많은 실험 연산자까지 넣어 분류 비용이 커진다.
- 스레드 4: CI 표준 러너(공개 저장소, 4코어)와 같은 값이다. 7단계에서 같은 설정으로 다시 잴 때 로컬과 CI의 조건 차이를 줄인다.
- 증분 분석 없음: 기준선과 재측정은 같은 조건에서 처음부터 잰 값이어야 비교할 수 있다. 이전 결과를 재사용하면 테스트가 바뀌어도 이전 판정이 남을 수 있다.
- 리포트 경로 고정: 문서와 CI 산출물이 같은 경로를 가리키게 한다.

## 고려했지만 고르지 않은 것

- 단위 테스트만: 서비스 변이가 모두 '덮이지 않음'이라 점수가 규칙 코드의 테스트 품질을 보여 주지 못한다.
- 통합 테스트용 PIT 작업을 따로 둠: 같은 변이를 두 번 재고 결과를 합쳐야 한다. 한 번에 모든 테스트를 쓰면 변이마다 죽인 테스트가 하나로 남는다.
- `DEFAULTS`: 위 `STRONGER` 근거.
- 증분 분석(`historyInputLocation`): 위 근거. 시간이 문제가 되면 실행 시점 정책과 함께 다시 본다.
- Maven 플러그인, 다른 뮤테이션 도구: 빌드가 Gradle이고, 자바 뮤테이션 테스트 도구 중 PIT가 사실상 표준이다.

## 적용과 남은 점

- 측정 결과와 살아남은 변이의 분류는 `docs/mutation-testing.md`에 있다.
- 실행 시점(로컬·CI)과 점수 기준(기록·실패)은 Issue #69 '아직 정하지 않은 정책' 1·2번으로 사람에게 묻는다. 이 결정에는 넣지 않는다.
- 대상·연산자·스레드를 바꾸면 기준선과 점수를 그대로 비교할 수 없다. 바꿀 때는 새 결정을 남기고 기준선을 다시 잰다.
