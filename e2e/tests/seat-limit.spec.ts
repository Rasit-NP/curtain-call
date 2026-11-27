import { expect, test } from '@playwright/test';
import { USER } from './support/accounts';
import { findPerformance, resetData } from './support/api';
import { logIn, screenRegion, seatButton, seatList } from './support/ui';

// 시나리오 3(대응표 'E2E 시나리오 대응'): R2 한 공연에서 최대 4석

const HOLD_LIMIT_EXCEEDED = '한 공연에서 최대 4석까지 예매할 수 있습니다.';

test.beforeEach(async ({ request }) => {
  await resetData(request);
});

test('R2: 4석을 고른 뒤 5번째 좌석은 선택되지 않고 안내 문구가 보인다', async ({ page, request }) => {
  const performance = await findPerformance(request, 5);
  const firstFour = performance.availableSeats.slice(0, 4);
  const fifth = performance.availableSeats[4];

  await logIn(page, USER);
  await page.goto(`/performances/${performance.id}`);
  const seatScreen = screenRegion(page, '좌석 선택');
  for (const seat of firstFour) {
    await seatButton(page, seat, '선택 가능').click();
    await expect(seatButton(page, seat, '선택됨')).toBeVisible();
  }
  await expect(seatScreen.getByRole('alert')).toHaveCount(0);

  // 남은 좌석 버튼은 막지 않는다. 눌렀을 때 고를 수 없는 이유를 보여 준다(spec 7.2).
  await seatButton(page, fifth, '선택 가능').click();

  await expect(seatScreen.getByRole('alert')).toHaveText(HOLD_LIMIT_EXCEEDED);
  await expect(seatButton(page, fifth, '선택 가능')).toBeEnabled();
  await expect(seatScreen.getByRole('button', { name: /선택됨$/ })).toHaveCount(4);
  await expect(seatScreen.getByText(`선택한 좌석 ${seatList(firstFour)}`, { exact: true })).toBeVisible();
});
