# 0011. 로컬·E2E DB 접속 정보는 개발 전용 값으로 저장소에 둔다

- 상태: 에이전트 결정
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5) '참고', PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

`compose.yaml`과 `local`·`e2e` 프로필에 개발 전용 DB 접속 정보(`curtaincall` / `curtaincall-dev`)를 둔다. 배포용(`prod`)은 환경변수 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`로만 받고 기본값을 두지 않는다.

## 근거

- `verify.sh e2e`와 Playwright가 환경변수 없이 DB와 백엔드를 띄운다.
- 실제 비밀번호가 아니고 `127.0.0.1`에만 열리는 로컬 컨테이너다.

## 고려했지만 고르지 않은 것

- `.env`로만 받기: E2E와 새 체크아웃에서 별도 설정이 필요하다.

## 적용과 남은 점

루트 `CLAUDE.md` '금지 사항'의 예외 목록(시연·E2E 계정, 개발 JWT 키)에 DB 접속 정보는 없어서 Issue에서 사람 확인을 요청했다.
