import { expect, test } from '@playwright/test'

test('edited Gaia 1.1 scenario runs through Java and renders five result charts', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('model-version')).toHaveValue('gaia-1.1')
  await expect(page.getByRole('spinbutton', { name: /冷机额定制冷量/ })).toBeDisabled()
  await expect(page.getByRole('heading', { name: '公共参数' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Gaia 1.1 版本专属参数' })).toBeVisible()
  await expect(page.getByText('传感器统一偏差')).toBeVisible()

  await page.getByTestId('model-version').selectOption('gaia-1.0')
  await expect(page.getByRole('heading', { name: 'Gaia 1.0 版本专属参数' })).toBeVisible()
  await expect(page.getByText('当前版本暂无专属参数')).toBeVisible()
  await expect(page.getByText('传感器统一偏差')).toHaveCount(0)

  await page.getByTestId('model-version').selectOption('gaia-1.1')
  await expect(page.getByRole('heading', { name: 'Gaia 1.1 版本专属参数' })).toBeVisible()

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
  await expect(page.getByLabel('冷水机组 COP')).toBeChecked()
  await expect(page.getByLabel('冷却塔效率')).toBeChecked()
  await expect(page.getByLabel('冷水机组设备编号')).toHaveValue('WCR1')
  await expect(page.getByLabel('冷却塔设备编号')).toHaveValue('TOWER1')
  await expect(page.getByRole('button', { name: '发送到中央平台' })).toBeEnabled()
})
