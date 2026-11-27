# 0202. 뮤테이션 테스트 실행 시점: 로컬 수동 명령(verify.sh mutation)과 수동 실행 워크플로. 전체 검증·quick·PR CI에는 넣지 않고, 측정 대상을 바꾼 PR은 돌려 PR 본문에 적는다

- 상태: 채택
- 날짜: 2026-11-28
- 결정한 사람: 사람(Issue #69 '아직 정하지 않은 정책' 1번. 첫 측정 뒤 이 Issue 세션에서 선택지 셋을 묻고, 사람이 추천안을 골랐다)
- 관련: Issue [#69](https://github.com/Rasit-NP/curtain-call/issues/69), 결정 [0001](0001-verify-single-entry.md), [0191](0191-ci-workflow-security.md),
  [0193](0193-ci-workflow-impl.md), [0200](0200-pit-setup.md), [0203](0203-pit-threshold.md)(점수 기준)

## 결정

- 로컬: `./scripts/verify.sh mutation`(새 모드). Docker를 확인한 뒤 `backend/`에서 `./gradlew pitest`를 돌린다.
- CI: 수동 실행 워크플로 `.github/workflows/mutation.yml`(`workflow_dispatch`만). 같은 `verify.sh mutation`을 부르고, PIT 리포트를 성공해도 산출물로 남긴다.
- `verify.sh` 전체(`all`), `quick`(Stop 훅), PR·main push CI(`ci.yml`)에는 넣지 않는다.
- 측정 대상 클래스(결정 [0201](0201-pit-target-classes.md))를 바꾸는 PR은 PR 전에 `verify.sh mutation`을 돌려 결과(점수, 새로 살아남은 변이)를 PR 본문에 적는다.
- 로드맵 7단계의 재측정은 수동 실행 워크플로(CI 표준 러너)에서 같은 명령·설정으로 한다.

## 근거

물을 때 보인 근거다.

- 첫 측정은 로컬(스레드 4) 3분 30초였다. 지금 로컬 전체 검증은 약 2분, CI의 캐시 적중 실행은 약 2분 30초라, 전체 검증에 넣으면 PR마다 로컬 검증이 두 배 넘게 길어진다.
  CI(4코어)에서는 5분 안팎으로 어림했다.
- 남은 로드맵에서 규칙 코드를 바꾸는 PR은 적다(3단계 회원가입의 인증, 5단계 예매 취소). 프론트엔드만 바꾸는 PR까지 PIT를 돌릴 이유가 없다.
- 수동 실행 워크플로는 7단계 재측정을 누구나 같은 조건(CI 표준 러너)에서 다시 돌릴 수 있게 하고, 리포트를 산출물로 남긴다. 공개 저장소라 실행 시간 비용이 없다.
- quick은 작업을 끝낼 때마다 도는 Stop 훅이라 3분 넘는 단계와 Docker가 필요한 단계를 넣지 않는다.

## 고려했지만 고르지 않은 것

물을 때 보인 선택지다.

- 로컬 수동 명령만: 워크플로를 고치지 않지만, 결과가 실행한 기계에 따라 달라지고 리포트가 남지 않는다.
- PR마다 CI에서(`all`과 `ci.yml`에 넣기): 테스트 품질의 퇴보를 자동으로 잡지만, 모든 PR의 로컬 전체 검증이 약 2분에서 5분 30초로, CI가 약 5분 늘어난다.

## 적용과 남은 점

- 구현: `scripts/verify.sh`(`mutation` 모드), `.github/workflows/mutation.yml`, 하네스 테스트 2개(모드, 워크플로).
  워크플로는 CI 워크플로와 같은 규칙(결정 0191: 공식 액션·SHA 고정, `contents: read`, 토큰을 남기지 않는 checkout)을 따르고, 하네스가 모든 워크플로 파일에 이 규칙을 검사한다.
  러너·캐시·시간 제한은 결정 0193과 같게 두고, 리포트는 측정 기록이라 30일 보관한다(실패 산출물은 7일).
- 루트 `CLAUDE.md`의 '명령'과 '완료 조건', `backend/CLAUDE.md`의 '명령'에 적었다.
- 수동 실행 워크플로는 기본 브랜치(main)에 있어야 Actions 화면과 `gh workflow run`에 나타난다. 이 PR에서는 임시 트리거로 한 번 돌려 확인한다(진행 기록 #69).
