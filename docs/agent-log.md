# 에이전트 결함 기록

에이전트가 테스트·검증 결과에서 결함을 찾아 고친 사례를 한 줄씩 남긴다(루트 `CLAUDE.md`의 '실패 처리' 6번).

| 날짜 | 결함 | 발견 경위 | 수정 | Issue | PR |
|---|---|---|---|---|---|
| 2026-11-26 | `scripts/verify.sh`에 실행 권한이 없어 문서의 `./scripts/verify.sh` 명령이 동작하지 않음 | 실행하면 `permission denied`(종료 코드 126) | 실행 권한 추가(git 100755) | [#1](https://github.com/Rasit-NP/curtain-call/issues/1) | [#2](https://github.com/Rasit-NP/curtain-call/pull/2) |
| 2026-11-26 | 영역 폴더에 CLAUDE.md만 있어도 만든 영역으로 보고 실행해 `verify.sh quick`이 항상 실패 | 저장소 복사본에서 `quick` 실행: `./gradlew: No such file or directory`, `npm ci` EUSAGE | 표식 파일(`backend/gradlew`, `frontend/package.json`, `e2e/package.json`)로 영역 판단 | [#1](https://github.com/Rasit-NP/curtain-call/issues/1) | [#2](https://github.com/Rasit-NP/curtain-call/pull/2) |
| 2026-11-26 | 실패 상세가 로그 끝 40줄만 보여 줘 로그 맨 앞의 원인이 잘림 | `npm ci` 실패 로그(77줄)의 원인이 1~4번째 줄에 있고 끝 40줄은 도움말뿐 | 첫 오류로 보이는 줄을 줄 번호와 함께 먼저 출력 | [#1](https://github.com/Rasit-NP/curtain-call/issues/1) | [#2](https://github.com/Rasit-NP/curtain-call/pull/2) |
| 2026-11-26 | 명세의 결제 검사 순서대로면 같은 멱등 키 동시 요청이 저장 전 확인을 지나쳐 404를 받음(R5 위반) | 독립 리뷰(서브에이전트)로 명세를 규칙과 대조 | 같은 키 요청을 먼저 잡고 기다린 뒤 저장된 응답을 확인 | [#3](https://github.com/Rasit-NP/curtain-call/issues/3) | [#4](https://github.com/Rasit-NP/curtain-call/pull/4) |
| 2026-11-26 | "같은 키, 다른 선점은 400" 결정과 "404·409 결과는 저장 안 함"이 충돌해 그런 키를 다른 선점에 쓸 수 있음 | 독립 리뷰(서브에이전트) | 검증을 통과한 결제 결과(201·402·404·409)를 모두 저장 | [#3](https://github.com/Rasit-NP/curtain-call/issues/3) | [#4](https://github.com/Rasit-NP/curtain-call/pull/4) |
| 2026-11-26 | R1 교착 상태 테스트가 겹치는 좌석 1개뿐인 묶음이라 좌석 정렬을 되돌려도 실패하지 않음 | 독립 리뷰(서브에이전트) | 같은 두 좌석을 반대 순서로 담아 여러 번 반복하는 테스트로 교체 | [#3](https://github.com/Rasit-NP/curtain-call/issues/3) | [#4](https://github.com/Rasit-NP/curtain-call/pull/4) |
| 2026-11-26 | 좌석을 지금 잡은 선점을 가리키는 값이 없어, 만료 뒤 덮어쓴 좌석의 판정과 해제 범위가 정해지지 않음 | 독립 리뷰(서브에이전트) | `Seat.holdId` 추가 | [#3](https://github.com/Rasit-NP/curtain-call/issues/3) | [#4](https://github.com/Rasit-NP/curtain-call/pull/4) |
