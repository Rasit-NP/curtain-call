import { expect, test } from '@playwright/test';
import { USER } from './support/accounts';
import { findPerformance, resetData } from './support/api';
import { logInOnLoginScreen, screenRegion } from './support/ui';

// 시나리오 4(대응표 'E2E 시나리오 대응'): R6 로그인하지 않으면 좌석 선택 화면에 들어갈 수 없다

test.beforeEach(async ({ request }) => {
  await resetData(request);
});

test('R6: 로그인하지 않고 좌석 선택 화면에 들어가면 로그인 화면으로 이동하고, 로그인하면 그 화면으로 돌아온다', async ({ page, request }) => {
  const performance = await findPerformance(request, 1);

  await page.goto(`/performances/${performance.id}`);

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '로그인 화면', level: 1 }) ).toBeVisible();
  await expect(page.getByRole('heading', { name: '좌석 선택' })).toHaveCount(0);

  // 로그인하면 원래 화면으로 돌아온다(spec 7.1).
  await logInOnLoginScreen(page, USER);
  await expect(page).toHaveURL(new RegExp(`/performances/${performance.id}$`));
  await expect(screenRegion(page, '좌석 선택').getByRole('heading', { name: performance.title, level: 2 })).toBeVisible();
});
