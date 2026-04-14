import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_LIST_RESPONSE = {
  rows: [
    {
      purchaseNo: 12345,
      purchaseCode: 'P-2026-001',
      purchaseDate: '2026-02-15',
      shopNo: 1,
      supplierNo: 21,
      supplierCode: '0021',
      supplierName: 'イトマン株式会社',
      purchaseAmount: 100000,
      includeTaxAmount: 110000,
      taxAmount: 10000,
      taxRate: 10,
      note: null,
    },
    {
      purchaseNo: 12346,
      purchaseCode: 'P-2026-002',
      purchaseDate: '2026-02-10',
      shopNo: 1,
      supplierNo: 21,
      supplierCode: '0021',
      supplierName: 'イトマン株式会社',
      purchaseAmount: 50000,
      includeTaxAmount: 54000,
      taxAmount: 4000,
      taxRate: 8,
      note: '軽減税率',
    },
  ],
  summary: {
    totalRows: 5,
    totalAmountExcTax: 150000,
    totalTaxAmount: 14000,
    totalAmountIncTax: 164000,
    byTaxRate: [
      { taxRate: 10, rows: 3, amountExcTax: 100000, taxAmount: 10000, amountIncTax: 110000 },
      { taxRate: 8,  rows: 2, amountExcTax: 50000,  taxAmount: 4000,  amountIncTax: 54000  },
    ],
  },
}

async function mockPurchasesList(page: Page, payload: unknown) {
  await page.route(
    (url) => url.pathname === '/api/v1/purchases',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(payload),
      })
    },
  )
}

async function mockPurchaseDetails(page: Page) {
  await page.route(
    (url) => /\/api\/v1\/purchases\/\d+\/details/.test(url.pathname),
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            purchaseNo: 12345, purchaseDetailNo: 1, purchaseDate: '2026-02-15',
            goodsNo: 1, goodsCode: 'A001', goodsName: 'テスト商品A',
            goodsPrice: 1000, goodsNum: 100,
            subtotal: 100000, includeTaxSubtotal: 110000,
            taxRate: 10, taxPrice: 10000, taxType: '0', note: null,
          },
        ]),
      })
    },
  )
}

test.describe('仕入一覧 drill-down', () => {
  test('PL-01: paymentSupplierNo + 期間付きで遷移 → 集計テーブル + 行表示', async ({ page }) => {
    await mockAllApis(page)
    await mockPurchasesList(page, MOCK_LIST_RESPONSE)
    await loginAndGoto(
      page,
      '/purchases?shopNo=1&paymentSupplierNo=21&fromDate=2026-01-21&toDate=2026-02-20'
        + '&supplierName=%E3%82%A4%E3%83%88%E3%83%9E%E3%83%B3%E6%A0%AA%E5%BC%8F%E4%BC%9A%E7%A4%BE&transactionMonth=2026-02-20',
    )

    await expect(page.getByRole('heading', { name: '仕入一覧' })).toBeVisible()

    // 絞り込みバッジ
    await expect(page.getByText(/絞り込み中/)).toBeVisible()
    await expect(page.getByText('イトマン株式会社').first()).toBeVisible()
    await expect(page.getByText(/買掛取引月/)).toBeVisible()

    // 集計テーブル
    await expect(page.getByText('集計（明細単位）')).toBeVisible()
    await expect(page.getByRole('cell', { name: '10%' }).first()).toBeVisible()
    await expect(page.getByRole('cell', { name: '8%' }).first()).toBeVisible()
    // 合計行
    await expect(page.getByRole('cell', { name: '合計' })).toBeVisible()

    // データ行
    await expect(page.getByText('P-2026-001')).toBeVisible()
    await expect(page.getByText('P-2026-002')).toBeVisible()
  })

  test('PL-02: 行クリックで明細ダイアログ', async ({ page }) => {
    await mockAllApis(page)
    await mockPurchasesList(page, MOCK_LIST_RESPONSE)
    await mockPurchaseDetails(page)
    await loginAndGoto(
      page,
      '/purchases?shopNo=1&paymentSupplierNo=21&fromDate=2026-01-21&toDate=2026-02-20',
    )

    await page.getByText('P-2026-001').first().click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText('テスト商品A')).toBeVisible()
  })
})
