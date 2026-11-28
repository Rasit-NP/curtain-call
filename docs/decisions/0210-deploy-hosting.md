# 0210. 배포 호스팅: AWS Lightsail 서울 리전 2GB VM 한 대에 Docker Compose로 웹(Caddy)·백엔드·PostgreSQL 17을 올리고, 프론트엔드와 API는 같은 출처로 둔다

- 상태: 채택
- 날짜: 2026-11-28
- 결정한 사람: 사람(2026-11-28 메인 세션. Issue #71·#74의 '이미 정한 정책'에 적었다)
- 관련: Issue [#71](https://github.com/Rasit-NP/curtain-call/issues/71), [#74](https://github.com/Rasit-NP/curtain-call/issues/74), 로드맵 [#64](https://github.com/Rasit-NP/curtain-call/issues/64),
  결정 [0184](0184-deploy-first.md), [0214](0214-backend-image.md)(백엔드 이미지)

## 결정

- AWS Lightsail 서울 리전의 2GB VM 한 대에 Docker Compose로 웹(Caddy), 백엔드, PostgreSQL 17을 올린다.
- 프론트엔드와 API는 같은 출처(같은 도메인)로 둔다. 웹 서버가 화면을 내보내고 `/api`를 백엔드로 넘긴다.
- 도메인은 사람이 산다.

## 근거

메인 세션에서 사람이 정했다. 이 Issue는 그 결정을 따르는 백엔드 이미지를 만든다.

## 고려했지만 고르지 않은 것

메인 세션에서 다뤘다. 이 Issue 세션에서는 다시 고르지 않았다.

## 적용과 남은 점

- 백엔드 이미지는 이 서버의 메모리(2GB)에 맞춰 JVM 메모리를 정했다(결정 [0214](0214-backend-image.md)).
- 운영 Compose 파일, 서버 준비, 배포 워크플로는 #74다. #74가 같은 정책을 자기 결정 기록으로 남기면 이 파일을 참조한다.
