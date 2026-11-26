# 0008. 프론트의 '401이면 토큰 삭제·로그인 이동'은 AUTH_REQUIRED에만

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 사람
- 관련: Issue [#3](https://github.com/Rasit-NP/curtain-call/issues/3), PR [#4](https://github.com/Rasit-NP/curtain-call/pull/4)

## 결정

`401 AUTH_REQUIRED`일 때만 토큰을 지우고 로그인 화면으로 보낸다. 로그인 실패(`401 INVALID_CREDENTIALS`)는 로그인 화면에 안내 문구만 보여 준다. `frontend/CLAUDE.md`도 함께 고쳤다.

## 근거

- 로그인 실패도 401이라 상태 코드만 보면 로그인 화면에서 무한히 로그인 화면으로 보내게 된다.

## 고려했지만 고르지 않은 것

- 상태 코드로만 판단: 위 이유로 기각.

## 적용과 남은 점

클라이언트는 `code`만 보고 판단한다(spec 3.1).
