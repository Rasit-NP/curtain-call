# #42 프론트엔드 좌석 선택 화면(R1·R2·R6): 좌석 격자, 4석 선택, 선점 요청

- 기간: 2026-11-27 ~
- Issue: [#42](https://github.com/Rasit-NP/curtain-call/issues/42)
- 관련 결정: (정책 답을 받은 뒤 적는다)

## 배경

로드맵 [#13](https://github.com/Rasit-NP/curtain-call/issues/13)의 3단계(선점) 프론트엔드다. 좌석 선택 화면(`/performances/:performanceId`)을 만든다. 좌석을 1~4석 고르고 '좌석 선택 완료'로 한 번에 선점한다(명세 1장).
2단계(#36)의 공연 목록 항목이 이 화면으로 링크하지만, 지금은 라우터 기본 404 화면으로 간다.
1단계(#28)의 인증 가드와 `401 AUTH_REQUIRED` 공통 처리, 2단계의 `useLoad`·`LoadStatus`·형식 함수를 그대로 쓴다.
백엔드 선점 생성(#41)이 다른 세션에서 동시에 진행되므로, 공연 상세(6.3)와 선점(6.4) API는 MSW로 흉내 낸다.

## 진행

- 메인 세션이 Issue를 만들고 이 Issue용 워크트리 세션을 시작했다. 브랜치는 `origin/main`에서 만든 `feat/42-frontend-seat-selection`이다.
- Issue의 '아직 정하지 않은 정책' 7개와, 명세·Issue를 읽으며 찾은 화면 동작 질문을 구현 전에 이 세션에서 사람에게 한 번에 물었다.
