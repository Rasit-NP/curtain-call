/**
 * E2E 전용 시드 계정(backend/src/main/resources/db/seed/V1000__seed_users.sql, 결정 0036). 시연용 계정(demo)은 쓰지 않는다(e2e/CLAUDE.md).
 * 시연·E2E 전용 계정이라 비밀번호를 저장소에 둔다(루트 CLAUDE.md '금지 사항'의 예외).
 */
export interface Account {
  username: string;
  password: string;
}

/** 시나리오의 로그인 사용자 */
export const USER: Account = { username: 'e2e-user1', password: 'e2e-pass1' };

/** 시나리오 2의 '다른 사용자'. 브라우저 없이 API로만 행동한다. */
export const OTHER_USER: Account = { username: 'e2e-user2', password: 'e2e-pass2' };
