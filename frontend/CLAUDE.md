# 프론트엔드 규칙

## 기술 구성

- React + TypeScript, Vite, React Router
- 테스트: Vitest(jsdom), React Testing Library, user-event, jest-dom, MSW, `@vitest/coverage-v8`
- 패키지 관리자는 npm이다. Node.js는 LTS 버전을 쓰고 루트 `.nvmrc`에 고정한다.
- 상태 관리 라이브러리와 UI 컴포넌트 라이브러리는 쓰지 않는다. React 기본 훅과 CSS Modules로 만든다.

## npm 스크립트

`scripts/verify.sh`가 아래 이름으로 호출하므로 이름을 바꾸지 않는다.

- `dev`: `vite`
- `build`: `tsc -b && vite build`
- `typecheck`: `tsc -b`
- `lint`: `eslint .`
- `test`: `vitest run`
- `test:coverage`: `vitest run --coverage`
- 파일 하나만 실행: `npx vitest run src/pages/seat/SeatSelectPage.test.tsx`

## 구조

- `src/api/`: API 클라이언트. `fetch` 호출은 여기서만 한다.
- `src/auth/`: 로그인 상태와 토큰 저장
- `src/pages/`: 라우트 단위 화면(로그인, 공연 목록, 좌석 선택, 결제·예매 확인, 내 예매)
- `src/components/`: 여러 화면에서 쓰는 컴포넌트
- `src/test/`: 테스트 설정, MSW 핸들러, 테스트 데이터 생성 함수

## API 연동

- 개발 서버는 `/api` 요청을 백엔드(`http://localhost:8080`)로 프록시한다. 코드에서는 `/api`로 시작하는 상대 경로만 쓴다.
  환경변수 `CURTAINCALL_API_TARGET`이 있으면 그곳으로 넘긴다. E2E가 자기가 띄운 백엔드로 바꿀 때 쓴다(결정 0161·0162).
- 오류는 응답 본문(`ProblemDetail`)의 `code`로 구분한다. 사용자에게 보여 줄 문구는 code별로 한 파일에 모은다.
- `401 AUTH_REQUIRED`를 받으면 저장된 토큰을 지우고 로그인 화면으로 보낸다.
  로그인 실패(`401 INVALID_CREDENTIALS`)에는 적용하지 않고, 로그인 화면에 안내 문구만 보여 준다(`docs/spec.md`).

## 인증 토큰

- 로그인 응답의 액세스 토큰은 `localStorage`에 저장하고, `src/auth/` 밖에서는 직접 읽거나 쓰지 않는다.
- 인증이 필요한 API 요청에는 `Authorization: Bearer <토큰>`을 붙인다.
- 좌석 선택, 결제, 내 예매 화면은 로그인하지 않으면 로그인 화면으로 보낸다.

## 화면과 접근성

- 버튼과 입력은 역할과 이름으로 찾을 수 있게 만든다(`<button>`, `<label>` 사용). 단위 테스트와 E2E가 이 이름으로 요소를 찾는다.
- 좌석 버튼의 접근성 이름에는 좌석 표시 이름과 상태를 넣는다. 예: `A3 선택 가능`, `A3 선택됨`, `A4 선택 불가`(`docs/spec.md` 7.2)
- 데이터를 불러오는 화면은 로딩, 빈 목록, 오류 상태를 모두 보여 준다.

## 테스트 작성

- 사용자 관점에서 검증한다. `getByRole`, `getByLabelText`로 요소를 찾고 `user-event`로 조작한다.
  상태 변수나 CSS 클래스 같은 구현 세부는 검사하지 않는다.
- API는 MSW로 흉내 낸다. 실패 응답(401, 409, 500)과 느린 응답도 핸들러로 만들어 검증한다.
- 화면마다 정상 흐름, 입력 검증(예: 5번째 좌석 선택 막기), 오류 코드별 안내 문구, 로딩 상태를 검증한다.
- 테스트 이름은 관련 규칙이 있으면 규칙 번호로 시작한다. 예: `R2: 5번째 좌석은 선택할 수 없다`
- 커버리지에서 `src/test/`, `src/main.tsx`, 타입 정의 파일(`*.d.ts`)은 뺀다.