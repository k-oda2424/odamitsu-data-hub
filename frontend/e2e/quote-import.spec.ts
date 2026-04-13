import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('AI取込一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/purchase-prices/imports')
  })

  test('ページが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'AI見積取込一覧' })).toBeVisible()
  })

  test('取込データが表示される', async ({ page }) => {
    await expect(page.getByText('小田光様_26年5月')).toBeVisible()
  })

  test('突合画面へボタンで遷移する', async ({ page }) => {
    await page.getByRole('button', { name: '突合画面へ' }).click()
    await page.waitForURL('**/purchase-prices/imports/1', { timeout: 10000 })
  })
})

test.describe('商品突合画面（仕入先未確定）', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/purchase-prices/imports/1')
  })

  test('ページヘッダーが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '商品突合' })).toBeVisible()
  })

  test('仕入先突合セクションが表示される', async ({ page }) => {
    await expect(page.getByText('仕入先の突合')).toBeVisible()
    await expect(page.getByRole('button', { name: '確定' })).toBeVisible()
  })

  test('仕入先未確定で商品突合がロックされている', async ({ page }) => {
    await expect(page.getByText('仕入先を確定してから商品の突合を行ってください')).toBeVisible()
  })
})

test.describe('商品突合画面（仕入先確定済み）', () => {
  const MOCK_CONFIRMED_DATA = {
    header: {
      quoteImportId: 1,
      shopNo: 1,
      supplierName: '花王',
      supplierCode: 'SUP001',
      supplierNo: 1,
      fileName: '小田光様_26年5月価格改定御見積書.pdf',
      effectiveDate: '2026-05-01',
      changeReason: 'PU',
      priceType: '税抜',
      totalCount: 3,
      remainingCount: 2,
    },
    details: [
      {
        quoteImportDetailId: 1,
        rowNo: 1,
        janCode: '4901301508034',
        quoteGoodsName: 'クリーン&クリーンF1 ボトル',
        specification: '700mL',
        quantityPerCase: 6,
        oldPrice: 661,
        newPrice: 726,
      },
      {
        quoteImportDetailId: 2,
        rowNo: 3,
        janCode: null,
        quoteGoodsName: 'マジックリン 除菌プラス 業務用',
        specification: '5Kg',
        quantityPerCase: 3,
        oldPrice: 2976,
        newPrice: 3274,
      },
    ],
  }

  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    // mockAllApis の後に特定ルートを上書き登録（LIFOで優先される）
    await page.route(
      (url) => /^\/api\/v1\/quote-imports\/1$/.test(url.pathname),
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_CONFIRMED_DATA),
        })
      },
    )
    await loginAndGoto(page, '/purchase-prices/imports/1')
  })

  test('仕入先確定済みBadgeが表示される', async ({ page }) => {
    await expect(page.getByText('確定済み')).toBeVisible()
  })

  test('商品明細テーブルが表示される', async ({ page }) => {
    await expect(page.getByText('クリーン&クリーンF1 ボトル')).toBeVisible()
    await expect(page.getByText('マジックリン 除菌プラス 業務用')).toBeVisible()
  })

  test('JANコードなしの明細に(なし)が表示される', async ({ page }) => {
    await expect(page.getByText('(なし)')).toBeVisible()
  })

  test('各行に突合・新規作成・スキップボタンが表示される', async ({ page }) => {
    // テーブル内のボタンを確認
    const rows = page.locator('table tbody tr')
    await expect(rows).toHaveCount(2)
    await expect(rows.first().getByText('突合')).toBeVisible()
    await expect(rows.first().getByText('新規作成')).toBeVisible()
    await expect(rows.first().getByText('スキップ')).toBeVisible()
  })

  test('新規作成ボタンでDialogが表示される', async ({ page }) => {
    const rows = page.locator('table tbody tr')
    await rows.first().getByText('新規作成').click()
    await expect(page.getByRole('heading', { name: '新規商品登録' })).toBeVisible()
    await expect(page.getByText('商品マスタ情報')).toBeVisible()
    await expect(page.getByText('販売商品情報')).toBeVisible()
    await expect(page.getByPlaceholder('商品コードを入力')).toBeVisible()
  })
})

test.describe('サイドバーメニュー', () => {
  test('AI見積取込メニューが表示される', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/dashboard')
    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await expect(sidebar.getByText('AI見積取込')).toBeVisible()
  })
})
