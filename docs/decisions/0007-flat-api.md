# 0007. API는 평면 자원으로

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 사람
- 관련: Issue [#3](https://github.com/Rasit-NP/curtain-call/issues/3), PR [#4](https://github.com/Rasit-NP/curtain-call/pull/4)

## 결정

`/api/holds`, `/api/payments`, `/api/reservations`처럼 평면 자원으로 두고, 좌석은 공연 상세(`GET /api/performances/{id}`)에 포함한다.

## 근거

- 공개 경로가 `GET /api/performances/**`뿐이라 인증 설정이 단순하다.

## 고려했지만 고르지 않은 것

- 공연 아래 중첩(`/api/performances/{id}/holds`): 공개·인증 경로가 섞인다.

## 적용과 남은 점

공개 API에는 토큰을 붙이지 않는다(잘못되거나 만료된 토큰이면 공개 API도 401이 나기 때문, spec 3장).
