# 0091. 경로·쿼리의 long 값은 모든 컨트롤러의 바인더에 등록한 편집기 하나로 해석한다(변환 서비스의 변환기는 쓰지 않는다)

- 상태: 에이전트 결정
- 날짜: 2026-11-27
- 결정한 사람: 에이전트(Issue #45 본문 '에이전트가 정하는 것')
- 관련: Issue [#45](https://github.com/Rasit-NP/curtain-call/issues/45), 결정 [0012](0012-backend-error-mapping.md), [0050](0050-performance-detail-id-errors.md), [0072](0072-json-numbers-strict.md), [0090](0090-path-id-ascii-decimal.md)

## 결정

- `StrictLongEditor`(PropertyEditor)가 결정 0090의 규칙(ASCII 10진 정수 표기, long 범위)으로 문자열을 long으로 해석한다.
- `@ControllerAdvice`인 `StrictLongBinding`의 `@InitBinder`가 모든 컨트롤러의 바인더에 이 편집기를 `long`과 `Long`으로 등록한다.
  그래서 `@PathVariable long`·`@RequestParam long`(Long 포함)은 컨트롤러가 따로 검사하지 않아도 같은 규칙을 따른다. 4단계의 `holdId`도 `long`으로 받기만 하면 된다.
- 해석에 실패하면 지금처럼 Spring MVC의 인자 형식 오류가 되고, 공통 처리기가 400 INVALID_INPUT으로 바꾼다(결정 0012). 새 오류 처리 코드는 없다.
- 빈 문자열은 Spring 기본 편집기(`CustomNumberEditor`)와 같게 `Long`이면 값 없음(null), `long`이면 해석 실패로 둔다. 빈 쿼리 값의 처리를 바꾸지 않는다.
- 테스트는 MockMvc 통합 테스트(공연 상세, 시험용 컨트롤러의 long 경로 값)와 편집기 단위 테스트로 한다.

## 근거

- 처음에는 MVC 변환 서비스에 엄격한 `Converter<String, Long>`을 더했다. 변환 서비스는 `0x2`를 거부했지만(임시 테스트로 확인) 통합 테스트는 그대로 200이었다.
  바인더(`TypeConverterDelegate`)는 변환 서비스가 실패하면 기본 편집기(`CustomNumberEditor` → `NumberUtils.parseNumber`)로 다시 해석하기 때문이다.
  바인더에 직접 등록한 편집기는 변환 서비스보다 먼저 쓰이고, 실패해도 다른 방법으로 다시 해석하지 않는다.
- 한곳에 두면 경로 id가 늘어날 때(holdId, 이후의 다른 id) 컨트롤러마다 검사를 잊을 수 없다. 공연 상세와 관계없는 시험용 경로 값으로 이것을 테스트한다.
- 경로와 쿼리를 함께 다루는 것은 spec 3.1이 둘을 같은 '형식 오류'로 묶기 때문이다. 지금 long 쿼리 값은 없다.

## 고려했지만 고르지 않은 것

- MVC 변환 서비스에 변환기 추가: 위 이유(기본 편집기로 다시 해석)로 효과가 없다.
- `@PathVariable String`으로 받고 컨트롤러마다 해석: 한곳이 아니라 holdId에서 잊을 수 있다.
- 경로 id 전용 애너테이션과 인자 해석기: `@PathVariable`의 동작(이름, 필수 여부, 형식 오류 변환)을 다시 만들어야 한다.
- `int` 등 다른 정수 타입까지: 지금 경로·쿼리에 없고(시험용 컨트롤러의 int 쿼리뿐), 결정 0090은 경로 id(long)에 대한 답이다. 생기면 그때 정한다.

## 적용과 남은 점

- 코드: `backend/src/main/java/com/curtaincall/common/api/StrictLongEditor.java`, `StrictLongBinding.java`.
- 요청 본문(JSON)의 숫자는 Jackson이 읽으므로 이 편집기를 거치지 않는다(결정 0072가 따로 정했다).
- 액세스 토큰의 `sub`는 `CurrentUserIds`가 `Long.parseLong`으로 읽는다(결정 0038). 서버가 서명한 값이라 이 결정의 범위 밖이다.
