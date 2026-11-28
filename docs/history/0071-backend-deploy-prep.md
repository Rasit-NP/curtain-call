# #71 배포 준비(백엔드): 컨테이너 이미지, 운영 시연 데이터, 운영에서 E2E 초기화 API 닫힘 확인

- 기간: 2026-11-28 ~
- Issue: [#71](https://github.com/Rasit-NP/curtain-call/issues/71), PR: (작성 중)
- 관련 결정: (작성 중)

## 배경

로드맵 2 [#64](https://github.com/Rasit-NP/curtain-call/issues/64)의 2단계(배포) 항목이다. 사람이 메인 세션에서 배포 방식을 정했다(2026-11-28):
AWS Lightsail 서울 리전 2GB VM 한 대에 Docker Compose로 웹(Caddy)·백엔드·PostgreSQL 17을 올리고, 배포 서버의 데이터는 초기화하지 않는다.

- `prod` 프로필은 스키마 마이그레이션만 적용해, 배포해도 공연장과 계정이 없다. 배포할 컨테이너 이미지도 없다.
- E2E 초기화 API(`POST /api/test-support/reset`)는 `e2e` 프로필에만 등록하지만, `prod` 프로필의 실제 컨텍스트에서 닫혀 있는지는 테스트로 고정하지 않았다.
  지금은 작은 컨텍스트로 빈이 등록되지 않는 것(`TestDataResetProfileTest`)과 test 프로필의 401·404(`AuthenticationTest`)만 본다.
- 공연 주기 생성 [#73](https://github.com/Rasit-NP/curtain-call/issues/73)은 이 Issue가 병합된 뒤 시작한다. #73은 공연장·시작 시각 유니크 제약 같은 스키마 마이그레이션을 더할 예정이다.

## 진행

1. Issue, 루트·백엔드 `CLAUDE.md`, 결정 목차와 관련 결정(0011, 0013, 0015, 0036, 0160~0162, 0191), 명세 2.5·6.9·6.10, #73·#74 본문을 읽었다.
   `git fetch` 뒤 `origin/main`(`d6b5ba9`)에서 `chore/71-backend-deploy-prep` 브랜치를 만들었다.
