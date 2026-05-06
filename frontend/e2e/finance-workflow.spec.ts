import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'
// NOTE: backend SF-H06 で AccountingStatusResponse record が新設されたら
// `import type { AccountingStatusResponse } from '@/types/finance'` に切り替える。
// 当面は components 側で export している型を参照して shape の同期を保つ。
import type { AccountingStatus } from '@/components/pages/finance/accounting-workflow'

const MOCK_STATUS: AccountingStatus = {
  cashbookHistory: [
    {
      periodLabel: '2026-03',
      fileName: '現金出納帳_202603.xlsx',
      processedAt: '2026-04-05T10:00:00',
      rowCount: 42,
      totalIncome: 500000,
      totalPayment: 300000,
    },
  ],
  smilePurchaseLatestDate: '2026-04-10',
  smilePaymentLatestDate: '2026-04-10',
  invoiceLatest: [{ shopNo: 1, closingDate: '2026-03-31', count: 15 }],
  accountsPayableLatestMonth: '2026-03',
  batchJobs: [
    { jobName: 'purchaseFileImport', status: 'COMPLETED', exitCode: 'COMPLETED', startTime: '2026-04-10T09:00:00', endTime: '2026-04-10T09:05:00' },
    { jobName: 'accountsPayableAggregation', status: 'COMPLETED', exitCode: 'COMPLETED', startTime: '2026-04-10T09:30:00', endTime: '2026-04-10T09:32:00' },
  ],
}

async function mockAccountingStatus(page: Page, payload: unknown, status = 200) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/accounting-status',
    async (route) => {
      await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(payload),
      })
    },
  )
}

test.describe('経理業務フロー', () => {
  test('WF-01: ヘッダー・ワークフローステップ表示', async ({ page }) => {
    await mockAllApis(page)
    await mockAccountingStatus(page, MOCK_STATUS)
    await loginAndGoto(page, '/finance/workflow')

    await expect(page.getByRole('heading', { name: '経理業務フロー' })).toBeVisible()
    // ステップが複数存在する
    await expect(page.locator('h3').first()).toBeVisible()
  })

  test('WF-02: 取込履歴ステータス表示', async ({ page }) => {
    await mockAllApis(page)
    await mockAccountingStatus(page, MOCK_STATUS)
    await loginAndGoto(page, '/finance/workflow')

    // バッチステータスチップ（完了表示）
    await expect(page.getByText('仕入ファイル').first()).toBeVisible()
    await expect(page.getByText('買掛集計').first()).toBeVisible()
  })

  test('WF-03: アクションボタンでルーティング', async ({ page }) => {
    await mockAllApis(page)
    await mockAccountingStatus(page, MOCK_STATUS)
    await loginAndGoto(page, '/finance/workflow')

    // 買掛金画面へのリンクがあればクリックして遷移を確認
    const apButton = page.getByRole('button', { name: /買掛金/ }).first()
    if (await apButton.count() > 0) {
      await apButton.click()
      await expect(page).toHaveURL(/\/finance\/accounts-payable/)
    }
  })

  test('WF-04: ステータス未取得でもヘッダー表示', async ({ page }) => {
    await mockAllApis(page)
    await mockAccountingStatus(page, {
      cashbookHistory: [],
      smilePurchaseLatestDate: null,
      smilePaymentLatestDate: null,
      invoiceLatest: [],
      accountsPayableLatestMonth: null,
      batchJobs: [],
    })
    await loginAndGoto(page, '/finance/workflow')

    await expect(page.getByRole('heading', { name: '経理業務フロー' })).toBeVisible()
  })
})
