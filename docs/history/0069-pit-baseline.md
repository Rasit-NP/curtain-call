# #69 뮤테이션 테스트(PIT) 기준선: 규칙 코드 점수 측정, 살아남은 변이 분석·테스트 보강, 재측정

- 기간: 2026-11-28 ~
- Issue: [#69](https://github.com/Rasit-NP/curtain-call/issues/69), PR: [#70](https://github.com/Rasit-NP/curtain-call/pull/70)
- 관련 결정: [0202](../decisions/0202-pit-when-to-run.md), [0203](../decisions/0203-pit-threshold.md)(사람),
  [0200](../decisions/0200-pit-setup.md), [0201](../decisions/0201-pit-target-classes.md), [0204](../decisions/0204-pit-survivor-classification.md)(에이전트).
  기존 [0001](../decisions/0001-verify-single-entry.md), [0180](../decisions/0180-roadmap2-transition.md), [0190](../decisions/0190-ci-scope-docs-only.md)~[0193](../decisions/0193-ci-workflow-impl.md)을 따른다
- 보고서: [`docs/mutation-testing.md`](../mutation-testing.md)

## 배경

로드맵 2 [#64](https://github.com/Rasit-NP/curtain-call/issues/64)의 1단계 두 번째 항목이다. CI(#67)가 병합됐다.
지금 테스트 품질은 JaCoCo 커버리지와, 규칙 코드를 손으로 되돌려 테스트가 실패하는지 보는 확인으로만 본다.
커버리지는 코드가 실행됐는지만 알려 주고, 결과를 검증했는지는 알려 주지 않는다. 뮤테이션 테스트(PIT)는 코드를 조금씩 바꾼 변이를 만들어
테스트가 그 변화를 잡는지(변이를 죽이는지) 본다.

- 백엔드 테스트 460개: 단위 153개(`test`), 통합 307개(`integrationTest`, Testcontainers PostgreSQL).
- 규칙의 중심인 `HoldService`·`PaymentService`에는 단위 테스트가 없고 통합 테스트만 있다.
- R1·R5의 동시성은 DB(행 잠금, 유니크 제약)로 보장한다. 이 부분은 JPQL·SQL 문자열, 애노테이션, 마이그레이션에 있어 PIT가 바꾸지 못한다.

## 진행

1. Issue, 루트·`backend/` `CLAUDE.md`, 결정 목차와 관련 결정(0001, 0162, 0180, 0183, 0190~0193), `verify.sh`, 진행 기록 형식을 읽었다.
   워크트리 브랜치 이름을 `test/69-pit-baseline`으로 바꿨다(`origin/main` `1f549d4`와 같은 커밋).
2. 도구 판을 확인했다(2026-11-28): Gradle 플러그인 `info.solidsoft.pitest` 1.19.0(2026-03, Gradle 9를 지원하는 첫 판), PIT 1.30.2(2026-11-05),
   `pitest-junit5-plugin` 1.2.3(JUnit Platform 실행기를 `provided`로 두어 프로젝트의 JUnit 6.1.3을 쓴다).
   PIT 1.30.2의 연산자 묶음을 소스에서 확인했다. `DEFAULTS`는 조건을 '늘 거짓'으로만 바꾸고, `STRONGER`가 '늘 참'(`*_IF`)을 더한다.
3. 측정 대상(규칙 코드 15개 클래스)과 설정(연산자 `STRONGER`, 스레드 4, 증분 분석 없음, 단위·통합 테스트 모두)을 정해 `backend/build.gradle`에 넣었다
   (결정 [0200](../decisions/0200-pit-setup.md), [0201](../decisions/0201-pit-target-classes.md)).
4. 첫 측정(기준선): 변이 209개, 죽음 180, 살아남음 27, 덮이지 않음 2. 뮤테이션 점수 86%, 테스트 강도 87%, 3분 30초(커버리지 31초).
   같은 설정으로 한 번 더 재서 같은 결과(살아남은 변이 목록까지 같음, 3분 29초)를 확인했다. 동시성 테스트도 시간 초과 없이 돌았다.
5. 살아남은 변이를 바이트코드(`javap`)와 테스트에 대조해 나눴다. R6 쪽 점수가 낮았다(`SecurityConfig` 0%, `AuthRequiredEntryPoint` 33%, `JwtConfig` 57%).
   - `SecurityConfig`·`JwtConfig`의 빈 생성 코드 변이 10개는, 토큰 검증을 확인하는 `AuthenticationTest`가 있는데도 살아남았다.
     가설: 스프링 시작 때만 도는 코드는 컨텍스트를 처음 만든 테스트만 그 줄을 덮은 것으로 잡혀, PIT가 그 테스트만 돌린다.
   - 대상을 이 두 클래스로 좁히고 모든 테스트 결과를 남기게(`fullMutationMatrix`) 잠시 바꿔 확인했다. 돈 테스트는 컨텍스트 5종을 처음 만든 테스트 5개
     (`HealthCheckTest`, `ErrorResponseTest`, `ErrorPageTest`, `CurrentUserIdTest`, `E2eProfileAuthTest`의 첫 테스트)뿐이었다. 설정은 되돌렸다.
6. Issue의 '아직 정하지 않은 정책' 1·2번을 첫 측정의 실행 시간과 점수를 근거로 한 번에 물었다(아래 '정책 결정(사람)'). 사람이 두 번 모두 추천안을 골랐다.
7. 시작 코드 변이 10개를 손으로 넣고 인증 통합 테스트(`AuthenticationTest`, `E2eProfileAuthTest`, `TestDataReset*`, `LoginApiTest`)를 돌렸다.
   10개 모두 테스트가 실패해, 테스트 빈틈이 아니라 PIT의 측정 한계임을 확인했다. 운영 코드는 매번 원래 파일로 되돌렸고 `git status`로 바뀐 것이 없음을 확인했다.
8. 살아남은 27개와 덮이지 않은 2개를 모두 나눴다(결정 [0204](../decisions/0204-pit-survivor-classification.md)): 테스트 빈틈 15(그중 6개는 측정 한계이기도 함),
   동등 변이 6, 죽은 코드 2, 검증할 가치가 낮음 2, PIT 측정 한계 4. 동등 변이 넷은 DB 제약(`ck_seats_hold_id_only_when_held`, `ck_idempotency_records_response_together`)으로 근거를 확인했다.
   운영 코드 결함은 없었다.
9. 테스트 빈틈 15개를 죽일 테스트 11개를 더했다(단위 10, 통합 1. 아래 '결과'). 지금 코드에서 모두 통과하는 것을 확인했다.
10. 재측정(같은 명령·설정): 변이 209개, 죽음 195, 살아남음 12, 덮이지 않음 2. 점수 86% → 93%, 테스트 강도 87% → 94%, 3분 22초.
    기준선과 재측정의 `mutations.xml`을 변이마다 맞춰 보니 바뀐 것은 15개였고, 모두 '살아남음 → 죽음', 죽인 테스트는 모두 새 테스트였다.
11. 실행 시점(결정 0202)을 구현하기 전에 하네스 테스트 2개(`verify.sh mutation` 모드, 수동 실행 워크플로)를 먼저 써서 실패를 확인했다.
    `verify.sh`에 `mutation` 모드(Docker 확인 → `./gradlew pitest`)와 `.github/workflows/mutation.yml`(`workflow_dispatch`만, 리포트를 성공해도 산출물로)을 넣어 통과시켰다.
    검사가 실제 위반을 잡는지 워크플로와 `verify.sh`를 하나씩 망가뜨려 확인하고 되돌렸다. 11가지(push·pull_request 트리거, Gradle 직접 호출, 리포트 누락,
    토큰을 남기는 checkout, 태그 고정, 쓰기 권한, all·quick에 PIT 포함, Docker 확인 삭제, 리포트 경로 삭제) 모두 해당 테스트가 실패했다.
12. 점수 기준(결정 0203)을 넣었다. PIT 1.30.2 소스를 읽어 점수를 정수로 반올림(HALF_UP)한 뒤 비교한다는 것을 확인했다. 기준값 93이면 지금 변이 수에서는
    새로 살아남은 변이가 2개 이상일 때 실패한다. 기준값을 잠시 94로 올려 `verify.sh mutation`을 돌리자 실패했고, 실패 상세의 첫 오류 줄이
    `Mutation score of 93 is below threshold of 94`였다(이 실행도 195개를 죽였다, 3분 23초). 기준값은 93으로 되돌렸다.
13. 보고서(`docs/mutation-testing.md`), 결정 기록 0200~0204, 대응표, 루트·`backend/` `CLAUDE.md`, PR 템플릿, `docs/agent-log.md`를 고쳤다.

14. 로컬 `./scripts/verify.sh` 전체가 통과했다(64초, 백엔드 단위 176개·통합 308개). 작은 커밋 4개로 PR [#70](https://github.com/Rasit-NP/curtain-call/pull/70)을 열었다.
15. 수동 실행 워크플로는 main에 있어야 실행할 수 있어, 임시 커밋으로 `pull_request` 트리거를 잠시 더해 PR에서 한 번 돌렸다. 실행 [46076896926](https://github.com/Rasit-NP/curtain-call/actions/runs/46076896926)이 성공했다.
    CI 표준 러너(4코어)에서 변이 209개, 죽음 195(93%), 테스트 강도 94%로 로컬과 같았다. PIT 6분 32초, `verify.sh mutation` 단계 7분 21초(캐시 없음).
    산출물 `mutation-report-1`(115KB, 30일 보관)이 남았다. 임시 커밋은 되돌림 커밋으로 지웠다. 그 커밋의 `verify`는 하네스가 이 트리거를 막아 의도대로 실패했다.

## 정책 결정(사람)

첫 측정 뒤 이 Issue 세션에서 선택지·추천·근거를 붙여 한 번에 물었다. 사람이 두 번 모두 추천안을 골랐다.

| 물은 것 | 선택지 | 사람이 고른 것 |
|---|---|---|
| 1. 실행 시점 | 수동 명령+수동 실행 워크플로(추천) / 로컬 수동 명령만 / PR마다 CI에서(`all`에도) | 수동 명령+수동 실행 워크플로 → [0202](../decisions/0202-pit-when-to-run.md) |
| 2. 점수 기준 | 기준값(보강 후 점수의 소수점 버림) 미달이면 실패(추천) / 기록만 / 낮은 기준값(기준선 86%) | 기준값 미달이면 실패 → [0203](../decisions/0203-pit-threshold.md) |

물을 때 보인 근거: 첫 측정 3분 30초는 로컬 전체 검증(약 2분)의 두 배를 넘고, 남은 로드맵에서 규칙 코드를 바꾸는 PR이 적다. 두 번 잰 결과가 같아 기준값을 걸어도 흔들리지 않는다.

## 판단과 근거(에이전트)

- 도구와 설정(결정 0200): Gradle 9를 지원하는 첫 플러그인 판(1.19.0)과 최신 PIT(1.30.2). 서비스가 통합 테스트로만 덮여 있어 단위·통합 테스트를 모두 쓴다.
  연산자는 조건을 '늘 참'으로도 바꾸는 `STRONGER`, 스레드는 CI 러너와 같은 4, 증분 분석은 쓰지 않는다(기준선과 재측정을 같은 조건에서 처음부터).
- 측정 대상(결정 0201): R1~R6을 판정하거나 규칙의 오류 코드를 내는 15개 클래스. 컨트롤러·DTO·조회·공통 오류 처리·모의 결제·로그인은 뺐다.
- 분류(결정 0204): 설정으로 빼지 않고 다섯 갈래로 나눠 이유를 적는다. 스프링 시작 코드는 손으로 변이를 넣어 확인하고, 직접 만들 수 있는 `JwtConfig`는 단위 테스트를 더했다.
  `SecurityConfig`는 측정 한계로 남겼다(대상에서 빼면 R6 경로 보호가 측정에서 사라진다).
- 동등 변이를 죽이려고 리플렉션으로 불변식을 깨는 테스트는 쓰지 않았다. 일어날 수 없는 상태를 고정해 코드를 바꾸기 어렵게 만든다.

## 실패와 수정

- 첫 측정의 R6 점수가 낮았다(`SecurityConfig` 0%). 테스트가 약하다고 보고 테스트부터 더하지 않고, 같은 변이에 어떤 테스트가 돌았는지를 먼저 확인했다(`fullMutationMatrix`).
  컨텍스트를 처음 만든 테스트 5개만 돌았다는 것을 보고 측정 한계로 판단했고, 손으로 넣은 변이 10개로 확인했다.
- 결정 기록 목차를 고치기 전에 하네스를 돌려 '목차에 없는 파일: 0200, 0201'로 실패했다. 목차에 다섯 줄을 더해 통과시켰다.

## 리뷰

`/code-review medium`(테스트·빌드 설정·스크립트·문서만 바뀌어 medium, 루트 `CLAUDE.md` '리뷰'). 지적 2개.

| 지적 | 처리 |
|---|---|
| 1. 임시 커밋의 `pull_request` 트리거가 PR에 남아 있다. 그대로 병합하면 PR마다 PIT가 돈다(결정 0202 위반) | 의도한 임시 커밋이다. CI에서 워크플로를 한 번 돌려 본 뒤 되돌림 커밋으로 지웠다 |
| 2. 하네스의 '수동 실행만' 검사가 블록 형식(`  push:`)만 봐서 `on: [workflow_dispatch, pull_request]`를 놓친다 | 한 줄 형식도 보게 고쳤다. 그 형식으로 바꾼 워크플로에서 검사가 실패하는 것을 확인하고 되돌렸다 |

## 결과

- 점수 86% → 93%, 테스트 강도 87% → 94%(변이 209개). 클래스별 전·후와 변이마다의 분류는 보고서에 있다.
- 더한 테스트(테스트가 잡는 결함은 보고서 '더한 테스트'):
  - 단위: `JwtConfigTest` 6개(R6 sub 받음·거부, 주입한 시계로 만료, exp 없음·sub 거부, 인코더·디코더, 공백 서명 키), `HoldTest` 1개(null 좌석 id),
    `SeatAvailabilityTest` 1개(R1 선점 없는 HELD 좌석), `StoredPaymentResponsesTest` 1개(R5, 새 파일), `IdempotencyRecordTest` 1개(R5, 새 파일)
  - 통합: `AuthenticationTest` 1개(R6 `WWW-Authenticate`)
- 남은 14개: 동등 변이 6, 죽은 코드 2, 검증할 가치가 낮음 2, PIT 측정 한계(`SecurityConfig`) 4.
- PIT가 바꾸지 못하는 보장(잠금 애노테이션, JPQL·SQL 문자열, DB 제약, 상수 4석·5분, 보안 설정의 경로 문자열)과 그것을 지키는 테스트·되돌림 확인을 보고서에 표로 이었다.

## 배운 점

- 뮤테이션 점수가 낮은 곳이 곧 테스트가 약한 곳은 아니다. 스프링 시작 코드처럼 도구가 테스트와 코드를 잇지 못하는 곳이 있다. 점수보다 '어떤 테스트가 돌았나'를 먼저 본다.
- 살아남은 변이 중 다수는 경계값 하나(sub `0`), 헤더 값 하나(`invalid_token`), 방어 검사 하나처럼 작은 빈틈이었다. 기존 테스트는 '나쁜 입력은 401'만 보고 어떤 나쁜 입력인지를 다양하게 넣지 않았다.
- 같은 설정으로 두 번 재서 판정이 같은지 먼저 확인하면, 기준값을 걸어도 되는지(흔들려서 무작위로 실패하지 않는지) 근거가 생긴다.
