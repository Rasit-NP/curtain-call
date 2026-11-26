# #5 백엔드 첫 설정

- 기간: 2026-11-26 ~ (진행 중)
- Issue: [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR: [#8](https://github.com/Rasit-NP/curtain-call/pull/8) (리뷰 중, 병합 전)
- 관련 결정: [0010](../decisions/0010-security-in-auth-issue.md)~[0017](../decisions/0017-java21-toolchain.md)

## 배경

명세가 끝나 구현을 시작했다. 기능 Issue(인증, 공연, 선점, 결제)가 같은 기반 위에서 테스트부터 쓸 수 있도록, 기능 없이 빌드·실행·테스트 골격과 공통 오류 응답을 먼저 만들었다. 프론트엔드 첫 설정(#6)과 파일이 겹치지 않아, 별도 세션과 워크트리에서 동시에 진행했다.

## Issue를 쓸 때 내린 판단

- **Spring Security를 이 Issue에서 뺐다**([0010](../decisions/0010-security-in-auth-issue.md)). 첫 설정에서 보안 설정을 만들면 인증 Issue가 main에 병합된 보안 설정을 바꾸게 되고, 이는 '사람에게 넘기는 조건'에 걸린다. 대신 "보안이 들어온 뒤에도 이 Issue의 테스트는 기대값을 바꾸지 않고 통과해야 한다"는 제약을 걸었다.
- **개발용 DB 접속 정보를 저장소에 둔다**([0011](../decisions/0011-dev-db-credentials.md)). `CLAUDE.md`의 비밀값 예외 목록에 없는 경우라 Issue에 사람 확인을 요청했다.

## 구현 중 판단과 근거

- **Java 21 툴체인**([0017](../decisions/0017-java21-toolchain.md)): 로컬 기본 `java`가 25라, 툴체인이 없으면 문서와 다른 버전으로 컴파일된다.
- **통합 테스트 기반**([0016](../decisions/0016-integration-test-base.md)): 컨테이너 하나 공유, 테스트 시계, 테이블 비우기. `@Container`를 쓰지 않은 이유는 클래스마다 컨테이너가 멈춰 컨텍스트 캐시와 어긋나기 때문이다.
- **오류 테스트는 서블릿 필터를 끈 MockMvc로 MVC 안의 변환만 검증**: 0010의 제약을 지키기 위해서다.
- **health 프로브 끄기**([0015](../decisions/0015-health-probes-off.md)): Boot 4 응답에 `groups`가 붙어 명세와 달랐다.

## 실패와 수정

- **health 응답 불일치**: `bootRun` 뒤 `curl`로 `{"groups":[...],"status":"UP"}`를 확인했다. 엄격 비교 테스트로 재현한 뒤 고쳤다.
- **`@Validated` 빈의 검증 실패가 500**: 시험용 컨트롤러를 둘로 나눠 원인을 확정했다(`@Validated`가 있으면 AOP 검증이 `ConstraintViolationException`을 던져 catch-all로 빠짐). 400으로 처리하도록 고쳤다.
- **독립 리뷰가 찾은 것**
  - 404가 아닌 4xx를 모두 400으로 바꾸면 `ResponseStatusException(409)`가 조용히 400이 된다. 실패하는 테스트를 먼저 쓰고 매핑을 좁혔다([0012](../decisions/0012-backend-error-mapping.md)).
  - Flyway 이력 보존 테스트가 0행끼리 비교해 아무것도 검증하지 못했다. 표식 행으로 강화했다.
- **PR 리뷰가 찾고 병합 전에 고친 것**
  - local·e2e에서 시드(`V1000__`)가 적용된 뒤 마이그레이션이 추가되면 기동이 실패한다. compose DB로 재현한 뒤 out-of-order를 켰다([0013](../decisions/0013-flyway-out-of-order-local.md)).
  - 오류 코드 표만 바꾸면 `:test UP-TO-DATE`로 `ErrorCodeTest`가 돌지 않는다. 재현한 뒤 Gradle 입력으로 선언했다([0014](../decisions/0014-error-code-table-test.md)).
- 결함 기록: [`docs/agent-log.md`](../agent-log.md)

## 리뷰에서 남은 지적 (정리 전)

PR #8을 max 강도로 리뷰했을 때 나온 후보 중 아직 처리하지 않은 것이다. 검증 단계는 [0023](../decisions/0023-review-depth-by-risk.md) 결정에 따라 중단했다.

- 서블릿 필터·컨테이너 단계 오류(잘못된 퍼센트 인코딩, 폼 본문 등)는 `/error`로 가서 `code`가 없거나 500이 된다.
- `ConstraintViolationException`은 서버 쪽 검증 실패(엔티티 검증, 반환값 검증)도 400으로 만들고 DEBUG로만 남긴다.
- 포트 5432와 compose 프로젝트 이름이 고정돼 있지 않아, 워크트리끼리 DB가 충돌한다.
- 운영 `Clock` 빈이 스프링 컨텍스트에 등록됐는지는 검사하지 않는다.
- catch-all이 이후 Spring Security 예외(`BadCredentialsException` 등)를 500으로 바꿀 수 있다.
- 대응표 상태 값 `미작성(인증 Issue)`이 형식에 맞지 않는다.

## 결과

`verify.sh` 전체 통과(단위 20개, 통합 22개). PR #7과 합친 트리에서도 전체 통과(문서 두 파일만 충돌).
