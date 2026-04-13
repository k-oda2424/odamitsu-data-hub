import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_JOBS = [
  { jobName: 'goodsFileImport', category: 'マスタ取込', description: 'SMILE商品マスタCSV取込', available: true, requiresShopNo: 'true' },
  { jobName: 'purchaseFileImport', category: 'マスタ取込', description: 'SMILE仕入ファイル取込', available: true, requiresShopNo: 'true' },
  { jobName: 'salesAggregation', category: '集計', description: '売上集計', available: true, requiresShopNo: 'false' },
  { jobName: 'deprecatedJob', category: '集計', description: '未実装ジョブ', available: false, requiresShopNo: 'false' },
]

async function mockBatchJobs(page: Page, payload: unknown, status = 200) {
  await page.route(
    (url) => url.pathname === '/api/v1/batch/jobs',
    async (route) => {
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(payload) })
    },
  )
}

async function mockBatchExecute(page: Page, captureTo: { url?: string }) {
  await page.route(
    (url) => /^\/api\/v1\/batch\/execute\//.test(url.pathname),
    async (route) => {
      captureTo.url = route.request().url()
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ message: 'ジョブを実行しました' }) })
    },
  )
}

test.describe('バッチ管理', () => {
  test('BM-01: ヘッダー・ショップ選択・ジョブカテゴリ表示', async ({ page }) => {
    await mockAllApis(page)
    await mockBatchJobs(page, MOCK_JOBS)
    await loginAndGoto(page, '/batch')

    await expect(page.getByRole('heading', { name: 'バッチ管理' })).toBeVisible()
    await expect(page.getByText('実行対象ショップ')).toBeVisible()
    // カテゴリカード（CardTitle は div, data-slot="card-title"）
    await expect(page.locator('[data-slot="card-title"]', { hasText: 'マスタ取込' })).toBeVisible()
    await expect(page.locator('[data-slot="card-title"]', { hasText: '集計' })).toBeVisible()
  })

  test('BM-02: ジョブ一覧表示（available=false は「未実装」バッジ）', async ({ page }) => {
    await mockAllApis(page)
    await mockBatchJobs(page, MOCK_JOBS)
    await loginAndGoto(page, '/batch')

    await expect(page.getByText('SMILE商品マスタCSV取込')).toBeVisible()
    await expect(page.getByText('SMILE仕入ファイル取込')).toBeVisible()
    await expect(page.getByText('売上集計')).toBeVisible()
    await expect(page.getByText('未実装ジョブ')).toBeVisible()
    await expect(page.getByText('未実装', { exact: true })).toBeVisible()
  })

  test('BM-03: ジョブ実行 → API呼出 → info トースト', async ({ page }) => {
    await mockAllApis(page)
    await mockBatchJobs(page, MOCK_JOBS)
    const captured: { url?: string } = {}
    await mockBatchExecute(page, captured)

    await loginAndGoto(page, '/batch')

    // 「実行」ボタンを押下（goodsFileImport = requiresShopNo:true）
    // ジョブ行は div.divide-y > div.flex — span内の description テキストで特定
    const row = page.locator('.divide-y > div').filter({ hasText: 'SMILE商品マスタCSV取込' })
    await row.getByRole('button', { name: /実行/ }).click()

    await expect.poll(() => captured.url).toBeDefined()
    expect(captured.url).toContain('/batch/execute/goodsFileImport')
    expect(captured.url).toContain('shopNo=')
    await expect(page.getByText(/goodsFileImport を起動しました/)).toBeVisible()
  })

  test('BM-04: ジョブ一覧取得エラー → エラーメッセージ', async ({ page }) => {
    await mockAllApis(page)
    await mockBatchJobs(page, { message: 'Server Error' }, 500)
    await loginAndGoto(page, '/batch')

    await expect(page.getByRole('button', { name: /再試行|リトライ|retry/i })).toBeVisible({ timeout: 10000 })
  })
})
