import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_AP = [
  {
    shopNo: 1,
    supplierNo: 101,
    supplierName: '仕入先A',
    transactionMonth: '2026-03-01',
    taxRate: 10,
    purchaseAmount: 100000,
    taxAmount: 10000,
    totalAmount: 110000,
  },
  {
    shopNo: 1,
    supplierNo: 102,
    supplierName: '仕入先B',
    transactionMonth: '2026-03-01',
    taxRate: 8,
    purchaseAmount: 50000,
    taxAmount: 4000,
    totalAmount: 54000,
  },
]

function toPage<T>(list: T[]) {
  return {
    content: list,
    totalElements: list.length,
    totalPages: Math.max(1, Math.ceil(list.length / 50)),
    number: 0,
    size: 50,
    first: true,
    last: true,
    numberOfElements: list.length,
    empty: list.length === 0,
  }
}

async function mockAccountsPayable(page: Page, payload: unknown, status = 200) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/accounts-payable',
    async (route) => {
      const body = status === 200 && Array.isArray(payload) ? toPage(payload) : payload
      await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    },
  )
}

test.describe('買掛金一覧', () => {
  test('AP-01: ヘッダー表示・データ行表示', async ({ page }) => {
    await mockAllApis(page)
    await mockAccountsPayable(page, MOCK_AP)
    await loginAndGoto(page, '/finance/accounts-payable')

    await expect(page.getByRole('heading', { name: '買掛金一覧' })).toBeVisible()
    await expect(page.getByRole('row', { name: /仕入先A/ })).toBeVisible()
    await expect(page.getByRole('row', { name: /仕入先B/ })).toBeVisible()
    await expect(page.getByText('110,000')).toBeVisible()
  })

  test('AP-02: 0件 → 空テーブル', async ({ page }) => {
    await mockAllApis(page)
    await mockAccountsPayable(page, [])
    await loginAndGoto(page, '/finance/accounts-payable')

    await expect(page.getByRole('heading', { name: '買掛金一覧' })).toBeVisible()
    // データ行は無し（"データがありません" プレースホルダが代わりに入る）
    await expect(page.getByText(/データがありません|データなし|No data/i)).toBeVisible()
  })

  test('AP-03: API エラー → エラーメッセージ＋リトライボタン', async ({ page }) => {
    await mockAllApis(page)
    await mockAccountsPayable(page, { message: 'Server Error' }, 500)
    await loginAndGoto(page, '/finance/accounts-payable')

    await expect(page.getByRole('button', { name: /再試行|リトライ|retry/i })).toBeVisible({ timeout: 10000 })
  })

  // AP-04 (旧: 検索ボックスでフィルタ): サーバーサイドページネーション導入により
  // DataTable のクライアントサイド検索ボックスは非表示。サーバー検索は buyer検索APIで
  // 別途対応が必要（現状は shopNo/supplierNo のみ対応）。
})
