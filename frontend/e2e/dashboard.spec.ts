import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_SALES_SUMMARY = [
  { month: '2025-07', totalSales: 1000000 },
  { month: '2025-08', totalSales: 1200000 },
  { month: '2025-09', totalSales: 900000 },
  { month: '2024-07', totalSales: 800000 },
  { month: '2024-08', totalSales: 950000 },
]

async function mockSalesSummary(page: Page, payload: unknown, status = 200) {
  await page.route(
    (url) => url.pathname === '/api/v1/dashboard/sales-summary',
    async (route) => {
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(payload) })
    },
  )
}

test.describe('ダッシュボード', () => {
  test('DB-01: バッチ処理セクション表示', async ({ page }) => {
    await mockAllApis(page)
    await mockSalesSummary(page, MOCK_SALES_SUMMARY)
    await loginAndGoto(page, '/dashboard')

    await expect(page.getByRole('heading', { name: 'バッチ処理' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '新規受注取込' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '売上明細取込' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '出荷実績CSV' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '新規会員取込' })).toBeVisible()
  })

  test('DB-02: 売上チャート・ワークフローセクション表示', async ({ page }) => {
    await mockAllApis(page)
    await mockSalesSummary(page, MOCK_SALES_SUMMARY)
    await loginAndGoto(page, '/dashboard')

    await expect(page.getByText('売上推移')).toBeVisible()
    await expect(page.getByText('受注から出荷の流れ')).toBeVisible()
  })

  test('DB-03: 売上データ取得エラー → エラーメッセージ＋リトライボタン', async ({ page }) => {
    await mockAllApis(page)
    await mockSalesSummary(page, { message: 'Server Error' }, 500)
    await loginAndGoto(page, '/dashboard')

    // チャート箇所のErrorMessage
    await expect(page.getByRole('button', { name: /再試行|リトライ|retry/i })).toBeVisible({ timeout: 10000 })
  })
})
