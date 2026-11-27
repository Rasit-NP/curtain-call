import { expect, test } from '@playwright/test';
import { OTHER_USER, USER } from './support/accounts';
import { findPerformance, holdSeats, resetData, seatStatus } from './support/api';
import { logIn, screenRegion, seatButton } from './support/ui';

// 시나리오 2(대응표 'E2E 시나리오 대응'): R1 같은 좌석은 한 명만 선점한다

const SEAT_ALREADY_HELD = '이미 다른 고객이 선택한 좌석입니다. 다른 좌석을 선택해 주세요.';

test.beforeEach(async ({ request }) => {
  await resetData(request);
});

test("R1: 다른 사용자가 먼저 선점한 좌석을 고르면 안내 문구가 보이고, 결제 화면으로 넘어가지 않으며, 그 좌석이 '선택 불가'로 바뀐다", async ({
  page,
  request,
}) => {
  const performance = await findPerformance(request, 2);
  const [taken, free] = performance.availableSeats;

  await logIn(page, USER);
  await page.goto(`/performances/${performance.id}`);
  const seatScreen = screenRegion(page, '좌석 선택');
  await expect(seatButton(page, taken, '선택 가능')).toBeEnabled();

  // 좌석 화면을 연 뒤 다른 사용자가 먼저 선점한다. 화면은 좌석 상태를 저절로 다시 불러오지 않으므로 아직 '선택 가능'이다(spec 7.2).
  await holdSeats(request, OTHER_USER, performance.id, [taken.id]);

  await seatButton(page, taken, '선택 가능').click();
  await seatButton(page, free, '선택 가능').click();
  await expect(seatButton(page, taken, '선택됨')).toBeVisible();
  await expect(seatButton(page, free, '선택됨')).toBeVisible();
  await seatScreen.getByRole('button', { name: '좌석 선택 완료' }).click();

  await expect(seatScreen.getByRole('alert')).toHaveText(SEAT_ALREADY_HELD);
  // 다시 불러온 좌석 상태로 잡힌 좌석은 '선택 불가'가 되고, 고른 좌석은 비워진다(spec 7.2).
  await expect(seatButton(page, taken, '선택 불가')).toBeDisabled();
  await expect(seatButton(page, free, '선택 가능')).toBeEnabled();
  // 결제 화면으로 넘어가지 않는다.
  await expect(page).toHaveURL(new RegExp(`/performances/${performance.id}$`));
  await expect(page.getByRole('heading', { name: '결제', level: 1 })).toHaveCount(0);
  // 묶음은 전부 잡히거나 하나도 잡히지 않는다: 함께 고른 좌석도 선점되지 않았다(spec 1).
  expect(await seatStatus(request, performance.id, free.id)).toBe('AVAILABLE');
});
