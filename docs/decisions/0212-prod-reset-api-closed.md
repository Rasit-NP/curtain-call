# 0212. 운영에서 E2E 초기화 API: 토큰이 없으면 401 AUTH_REQUIRED, 유효한 토큰이 있으면 404 NOT_FOUND(명세 6.9 그대로)를 prod 프로필 테스트로 고정한다

- 상태: 채택
- 날짜: 2026-11-28
- 결정한 사람: 사람(2026-11-28 메인 세션. Issue #71 '이미 정한 정책'과 완료 조건)
- 관련: Issue [#71](https://github.com/Rasit-NP/curtain-call/issues/71), 로드맵 [#64](https://github.com/Rasit-NP/curtain-call/issues/64) '넘겨받은 일',
  결정 [0160](0160-e2e-reset-impl.md), [0215](0215-deploy-check-methods.md), spec 6.9, R6

## 결정

- 운영(prod)에서 `POST /api/test-support/reset`은 다른 비공개 경로와 같다. 토큰이 없으면 `401 AUTH_REQUIRED`, 유효한 토큰이 있으면 `404 NOT_FOUND`다.
- `prod` 프로필에서 이 응답을 테스트로 확인한다(Issue 완료 조건). 테스트 방법은 결정 [0215](0215-deploy-check-methods.md)(에이전트 결정)다.

## 근거

- 명세 6.9가 이미 정한 동작이다. 지금까지는 작은 컨텍스트의 빈 등록 조건(`TestDataResetProfileTest`)과 test 프로필의 401·404(`AuthenticationTest`)만 보았다.
  배포 서버의 DB는 초기화하지 않으므로(결정 0211), 운영에서 초기화가 열리면 시연 데이터가 E2E 시드로 바뀐다.
  초기화 컨트롤러·서비스를 prod에도 등록하는 변형으로 돌려 보니 이 테스트가 204를 받았고, 시연 계정 대신 E2E 계정이 생겼다.

## 고려했지만 고르지 않은 것

정책은 명세와 Issue가 정했다. 테스트 방법의 대안은 결정 0215에 있다.

## 적용과 남은 점

- 이미지 확인 스크립트(`backend/scripts/check-image.sh`)도 실제 이미지에서 토큰 없는 초기화가 401인지 본다.
