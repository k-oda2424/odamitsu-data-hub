import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_STOCK = [
  {
    goodsNo: 101, warehouseNo: 1, goodsName: 'テスト商品A', warehouseName: '本倉庫', shopNo: 1,
    unit1StockNum: 100, unit2StockNum: 20, unit3StockNum: 5, enoughStock: 50,
  },
  {
    goodsNo: 102, warehouseNo: 1, goodsName: 'テスト商品B', warehouseName: '本倉庫', shopNo: 1,
    unit1StockNum: 5, unit2StockNum: 1, unit3StockNum: 0, enoughStock: -10,
  },
]

async function mockStock(page: Page, payload: unknown, status = 200) {
  await page.route(
    (url) => url.pathname === '/api/v1/stock',
    async (route) => {
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(payload) })
    },
  )
}

test.describe('在庫一覧', () => {
  test('ST-01: ヘッダー・データ行表示', async ({ page }) => {
    await mockAllApis(page)
    await mockStock(page, MOCK_STOCK)
    await loginAndGoto(page, '/stock')

    await expect(page.getByRole('heading', { name: '在庫一覧' })).toBeVisible()
    await expect(page.getByRole('row', { name: /テスト商品A/ })).toBeVisible()
    await expect(page.getByRole('row', { name: /テスト商品B/ })).toBeVisible()
  })

  test('ST-02: 過不足 < 0 は destructive 色', async ({ page }) => {
    await mockAllApis(page)
    await mockStock(page, MOCK_STOCK)
    await loginAndGoto(page, '/stock')

    const row = page.getByRole('row', { name: /テスト商品B/ })
    await expect(row.locator('.text-destructive')).toBeVisible()
  })

  test('ST-03: エラー → リトライ表示', async ({ page }) => {
    await mockAllApis(page)
    await mockStock(page, { message: 'err' }, 500)
    await loginAndGoto(page, '/stock')

    await expect(page.getByRole('button', { name: /再試行|リトライ|retry/i })).toBeVisible({ timeout: 10000 })
  })
})
