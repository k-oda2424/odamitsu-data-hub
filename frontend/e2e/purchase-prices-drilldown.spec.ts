import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

async function mockPurchasePricesEmpty(page: Page) {
  await page.route(
    (url) => url.pathname === '/api/v1/purchase-prices',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    },
  )
}

async function mockShops(page: Page) {
  await page.route(
    (url) => url.pathname === '/api/v1/masters/shops',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { shopNo: 1, shopName: '本社' },
          { shopNo: 2, shopName: '第２事業部' },
        ]),
      })
    },
  )
}

test.describe('仕入一覧 drill-down from 買掛金一覧', () => {
  test('paymentSupplierNo + shopNo 付きURLで遷移 → 店舗と絞り込みバッジが出る', async ({ page }) => {
    await mockAllApis(page)
    await mockShops(page)
    await mockPurchasePricesEmpty(page)
    await loginAndGoto(
      page,
      '/purchase-prices?shopNo=1&paymentSupplierNo=21&supplierName=%E3%82%A4%E3%83%88%E3%83%9E%E3%83%B3',
    )

    await expect(page.getByRole('heading', { name: '仕入価格一覧' })).toBeVisible()

    // 店舗 Select トリガーに "本社" が出ている
    await expect(page.locator('[role="combobox"]').first()).toContainText('本社')

    // 絞り込みバッジ表示
    await expect(page.getByText(/支払先で絞り込み中/)).toBeVisible()
    await expect(page.getByRole('button', { name: '絞り込み解除' })).toBeVisible()

    // 仕入先 SearchableSelect トリガーに "イトマン" の疑似オプションが選択表示
    await expect(page.getByText(/イトマン.*支払先配下の全仕入先/)).toBeVisible()
  })
})
