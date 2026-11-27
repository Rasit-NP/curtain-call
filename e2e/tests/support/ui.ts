import { expect, type Locator, type Page } from '@playwright/test';
import type { Account } from './accounts';
import type { Seat } from './api';

/**
 * 화면 도우미. 요소는 역할과 이름으로 찾는다(e2e/CLAUDE.md). 이름은 명세 7장과 frontend/CLAUDE.md '화면과 접근성'의 것이다.
 */

/** 좌석 버튼의 상태 이름(spec 7.2) */
export type SeatState = '선택 가능' | '선택됨' | '선택 불가';

/** 좌석 표시 이름(spec 2.1): {row}{number}, 예: A3 */
export function seatName(seat: Seat): string {
  return `${seat.row}${seat.number}`;
}

/** 좌석을 표시 이름으로 쉼표로 이은 것(spec 7.3·7.4의 '좌석 A1, A2'). seats는 좌석 순서여야 한다. */
export function seatList(seats: Seat[]): string {
  return seats.map(seatName).join(', ');
}

/** 좌석 버튼. 접근성 이름은 '{표시 이름} {상태}'다(spec 7.2). */
export function seatButton(page: Page, seat: Seat, state: SeatState): Locator {
  return page.getByRole('button', { name: `${seatName(seat)} ${state}`, exact: true });
}

/** 화면 제목(h1)이 이름인 화면 영역. 헤더의 같은 이름 링크(예: '내 예매')와 구별할 때 쓴다. */
export function screenRegion(page: Page, title: string): Locator {
  return page.getByRole('region', { name: title, exact: true });
}

/** 지금 화면이 로그인 화면일 때 account로 로그인한다. 헤더가 '로그아웃'으로 바뀔 때까지 기다린다(spec 7.1, 7.8). */
export async function logInOnLoginScreen(page: Page, account: Account): Promise<void> {
  await page.getByLabel('아이디', { exact: true }).fill(account.username);
  await page.getByLabel('비밀번호', { exact: true }).fill(account.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃', exact: true })).toBeVisible();
}

/** 로그인 화면(/login)으로 가서 account로 로그인한다. 돌아갈 화면이 없으므로 공연 목록(/)으로 간다. */
export async function logIn(page: Page, account: Account): Promise<void> {
  await page.goto('/login');
  await logInOnLoginScreen(page, account);
}
