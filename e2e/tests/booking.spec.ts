import { expect, test } from '@playwright/test';
import { USER } from './support/accounts';
import { findPerformance, resetData } from './support/api';
import { logIn, screenRegion, seatButton, seatList } from './support/ui';

// 시나리오 1(대응표 'E2E 시나리오 대응'): 기본 흐름

test.beforeEach(async ({ request }) => {
  await resetData(request);
});

test('기본 흐름: 로그인 → 공연 선택 → 좌석 2석 선점 → 결제 → 예매 완료 → 내 예매에 같은 좌석', async ({ page, request }) => {
  const performance = await findPerformance(request, 2);
  const seats = performance.availableSeats.slice(0, 2);
  const seatsText = `좌석 ${seatList(seats)}`;

  // 로그인하면 공연 목록으로 간다. 목록은 API가 준 순서대로다(spec 7.7).
  await logIn(page, USER);
  const list = screenRegion(page, '공연 목록');
  await list.getByRole('link').nth(performance.index).click();

  await expect(page).toHaveURL(new RegExp(`/performances/${performance.id}$`));
  const seatScreen = screenRegion(page, '좌석 선택');
  await expect(seatScreen.getByRole('heading', { name: performance.title, level: 2 })).toBeVisible();
  for (const seat of seats) {
    await seatButton(page, seat, '선택 가능').click();
    await expect(seatButton(page, seat, '선택됨')).toBeVisible();
  }
  await seatScreen.getByRole('button', { name: '좌석 선택 완료' }).click();

  // 결제 화면(spec 7.3)
  await expect(page).toHaveURL(/\/holds\/\d+$/);
  const paymentScreen = screenRegion(page, '결제');
  await expect(paymentScreen.getByText(seatsText, { exact: true })).toBeVisible();
  await paymentScreen.getByRole('button', { name: '결제하기' }).click();

  // 예매 완료
  const doneScreen = screenRegion(page, '예매 완료');
  await expect(doneScreen.getByRole('status')).toHaveText('예매가 완료되었습니다.');
  await expect(doneScreen.getByText(seatsText, { exact: true })).toBeVisible();
  await doneScreen.getByRole('link', { name: '내 예매' }).click();

  // 내 예매(spec 7.4)
  await expect(page).toHaveURL(/\/reservations$/);
  const reservations = screenRegion(page, '내 예매').getByRole('listitem');
  await expect(reservations).toHaveCount(1);
  await expect(reservations.getByRole('heading', { name: performance.title })).toBeVisible();
  await expect(reservations.getByText(seatsText, { exact: true })).toBeVisible();
});
