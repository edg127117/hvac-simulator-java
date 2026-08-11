import { expect, test } from '@playwright/test'

test('edited Gaia 1.1 scenario runs through Java and renders five result charts', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('model-version')).toHaveValue('gaia-1.1')
  await expect(page.getByRole('spinbutton', { name: /冷机额定制冷量/ })).toBeDisabled()

  await page.getByLabel('场景计算', { exact: true }).check()
  const capacity = page.getByRole('spinbutton', { name: /冷机额定制冷量/ })
  await expect(capacity).toBeEnabled()
  await capacity.fill('1450')
  const creationResponse = page.waitForResponse((response) => (
    response.request().method() === 'POST' && response.url().endsWith('/api/simulation-runs')
  ))
  await page.getByRole('button', { name: '运行仿真' }).click()
  const response = await creationResponse
  expect(response.ok(), await response.text()).toBeTruthy()

  await expect(page.getByText('已完成', { exact: true })).toBeVisible()
  await expect(page.locator('.chart-panel')).toHaveCount(5)
  await expect(page.locator('canvas')).toHaveCount(5)
  await expect(page.getByRole('button', { name: '发送到中央平台' })).toBeEnabled()
})
