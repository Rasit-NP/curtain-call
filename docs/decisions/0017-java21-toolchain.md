# 0017. Java 21을 Gradle 툴체인으로 고정

- 상태: 채택
- 날짜: 2026-11-26
- 결정한 사람: 에이전트
- 관련: Issue [#5](https://github.com/Rasit-NP/curtain-call/issues/5), PR [#8](https://github.com/Rasit-NP/curtain-call/pull/8)

## 결정

`java.toolchain.languageVersion = 21`, `foojay-resolver-convention`으로 없으면 내려받는다.

## 근거

- `backend/CLAUDE.md`는 Java 21인데 로컬 기본 `java`는 25다. 툴체인이 없으면 25로 컴파일된다.

## 고려했지만 고르지 않은 것

- `JAVA_HOME`을 21로: 기계마다 설정해야 한다.

## 적용과 남은 점

컴파일·테스트·`bootRun` 모두 21로 도는 것을 로그로 확인했다.
