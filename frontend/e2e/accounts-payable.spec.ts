import { expect, test, type Page, type Route } from '@playwright/test'
import { mockAllApis } from './helpers/mock-api'
import { loginAndGoto } from './helpers/auth'

const TX_MONTH = '2026-03-20'

interface MockRow {
  shopNo: number
  supplierNo: number
  supplierCode: string
  supplierName: string
  transactionMonth: string
  taxRate: number
  taxIncludedAmount: number | null
  taxExcludedAmount: number | null
  taxIncludedAmountChange: number
  taxExcludedAmountChange: number
  paymentDifference: number | null
  verificationResult: 0 | 1 | null
  mfExportEnabled: boolean
  verifiedManually: boolean
  verificationNote: string | null
}

function row(overrides: Partial<MockRow> = {}): MockRow {
  return {
    shopNo: 1,
    supplierNo: 101,
    supplierCode: 'S101',
    supplierName: '○○商事',
    transactionMonth: TX_MONTH,
    taxRate: 10,
    taxIncludedAmount: 1234000,
    taxExcludedAmount: 1121818,
    taxIncludedAmountChange: 1234000,
    taxExcludedAmountChange: 1121818,
    paymentDifference: 0,
    verificationResult: 1,
    mfExportEnabled: true,
    verifiedManually: false,
    verificationNote: null,
    ...overrides,
  }
}

async function json(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data),
  })
}

async function setupApRoutes(page: Page, rows: MockRow[]) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/accounts-payable',
    async (route) => {
      await json(route, {
        content: rows,
        number: 0,
        size: 50,
        totalElements: rows.length,
        totalPages: 1,
      })
    },
  )
  await page.route(
    (url) => url.pathname === '/api/v1/finance/accounts-payable/summary',
    async (route) => {
      const unverified = rows.filter((r) => r.verificationResult === null).length
      const unmatched = rows.filter((r) => r.verificationResult === 0).length
      const matched = rows.filter((r) => r.verificationResult === 1).length
      const diffSum = rows
        .filter((r) => r.verificationResult === 0)
        .reduce((s, r) => s + (r.paymentDifference ?? 0), 0)
      await json(route, {
        transactionMonth: TX_MONTH,
        totalCount: rows.length,
        unverifiedCount: unverified,
        unmatchedCount: unmatched,
        matchedCount: matched,
        unmatchedDifferenceSum: diffSum,
      })
    },
  )
}

test.describe('買掛金一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
  })

  test('一覧に行が表示される', async ({ page }) => {
    await setupApRoutes(page, [
      row({ supplierCode: 'S101', supplierName: '○○商事', verificationResult: 1 }),
      row({
        supplierNo: 102,
        supplierCode: 'S102',
        supplierName: '△△工業',
        paymentDifference: 3210,
        verificationResult: 0,
      }),
    ])
    await loginAndGoto(page, '/finance/accounts-payable')
    await expect(page.getByText('○○商事')).toBeVisible()
    await expect(page.getByText('△△工業')).toBeVisible()
    await expect(page.getByText('一致', { exact: true })).toBeVisible()
    await expect(page.getByText('不一致', { exact: true })).toBeVisible()
  })

  test('サマリアラートで未検証・不一致件数が表示される', async ({ page }) => {
    await setupApRoutes(page, [
      row({ verificationResult: null, taxIncludedAmount: null, paymentDifference: null }),
      row({ supplierNo: 102, paymentDifference: -500, verificationResult: 0 }),
    ])
    await loginAndGoto(page, '/finance/accounts-payable')
    const alert = page.getByRole('alert').filter({ hasText: '要対応' })
    await expect(alert).toContainText('未検証 1件')
    await expect(alert).toContainText('不一致 1件')
  })

  test('検証ダイアログで検証済み金額と備考を入力して更新', async ({ page }) => {
    const target = row({
      supplierNo: 103,
      supplierCode: 'S103',
      supplierName: '□□商店',
      taxIncludedAmount: null,
      paymentDifference: null,
      verificationResult: null,
      taxIncludedAmountChange: 108000,
      taxExcludedAmountChange: 100000,
    })
    await setupApRoutes(page, [target])

    let verifyBody: Record<string, unknown> | null = null
    await page.route(
      (url) => /\/api\/v1\/finance\/accounts-payable\/\d+\/\d+\/[\d-]+\/[\d.]+\/verify$/.test(url.pathname),
      async (route) => {
        if (route.request().method() !== 'PUT') {
          await route.fallback()
          return
        }
        verifyBody = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...target, taxIncludedAmount: 108000, verificationResult: 1, verifiedManually: true, verificationNote: '請求書A-001' })
      },
    )

    await loginAndGoto(page, '/finance/accounts-payable')
    await page.getByRole('button', { name: '検証', exact: true }).first().click()
    await expect(page.getByRole('dialog')).toBeVisible()

    const amountInput = page.getByLabel('検証済み支払額(税込)')
    await amountInput.fill('108000')
    await page.getByLabel('備考（最大500字）').fill('請求書A-001')
    await page.getByRole('button', { name: '更新', exact: true }).click()

    await expect(page.getByRole('dialog')).toBeHidden()
    expect(verifyBody).not.toBeNull()
    expect(verifyBody!.verifiedAmount).toBe(108000)
    expect(verifyBody!.note).toBe('請求書A-001')
  })

  test('手動確定済み行にバッジが表示され admin が解除できる', async ({ page }) => {
    const target = row({
      supplierNo: 104,
      supplierCode: 'S104',
      supplierName: '手動確定先',
      verificationResult: 1,
      verifiedManually: true,
      verificationNote: '確認済み',
    })
    await setupApRoutes(page, [target])

    let deleted = false
    await page.route(
      (url) => /\/manual-lock$/.test(url.pathname),
      async (route) => {
        if (route.request().method() === 'DELETE') {
          deleted = true
          await json(route, { ...target, verifiedManually: false })
        } else {
          await route.fallback()
        }
      },
    )

    await loginAndGoto(page, '/finance/accounts-payable')
    await expect(page.getByText('手動', { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '詳細', exact: true }).first().click()
    await page.getByRole('button', { name: /手動確定解除/ }).click()
    await page.waitForTimeout(200)
    expect(deleted).toBe(true)
  })

  test('admin に再集計・再検証ボタンが表示される', async ({ page }) => {
    await setupApRoutes(page, [row()])
    await loginAndGoto(page, '/finance/accounts-payable')
    await expect(page.getByRole('button', { name: /再集計/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /再検証/ })).toBeVisible()
  })

  test('再集計で他バッチ実行中なら 429 エラーがトーストされる', async ({ page }) => {
    await setupApRoutes(page, [row()])
    await page.route(
      (url) => url.pathname === '/api/v1/batch/execute/accountsPayableSummary',
      async (route) => {
        await route.fulfill({
          status: 429,
          contentType: 'application/json',
          body: JSON.stringify({ message: '同時実行数の上限に達しています' }),
        })
      },
    )
    await loginAndGoto(page, '/finance/accounts-payable')
    await page.getByRole('button', { name: /再集計/ }).click()
    await expect(page.getByText(/他のバッチが実行中|同時実行数/)).toBeVisible()
  })

  test('MF出力スイッチでPATCHが呼ばれる', async ({ page }) => {
    const target = row({ mfExportEnabled: true })
    await setupApRoutes(page, [target])
    let patchBody: Record<string, unknown> | null = null
    await page.route(
      (url) => /\/mf-export$/.test(url.pathname),
      async (route) => {
        if (route.request().method() === 'PATCH') {
          patchBody = JSON.parse(route.request().postData() || '{}')
          await json(route, { ...target, mfExportEnabled: false })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/finance/accounts-payable')
    await page.getByRole('switch', { name: 'MF出力可否' }).click()
    await page.waitForTimeout(200)
    expect(patchBody).not.toBeNull()
    expect(patchBody!.enabled).toBe(false)
  })

  test('検証状態フィルタ変更でクエリに反映される', async ({ page }) => {
    let lastUrl = ''
    await page.route(
      (url) => url.pathname === '/api/v1/finance/accounts-payable',
      async (route) => {
        lastUrl = route.request().url()
        await json(route, { content: [], number: 0, size: 50, totalElements: 0, totalPages: 0 })
      },
    )
    await page.route(
      (url) => url.pathname === '/api/v1/finance/accounts-payable/summary',
      async (route) => {
        await json(route, {
          transactionMonth: TX_MONTH,
          totalCount: 0,
          unverifiedCount: 0,
          unmatchedCount: 0,
          matchedCount: 0,
          unmatchedDifferenceSum: 0,
        })
      },
    )
    await loginAndGoto(page, '/finance/accounts-payable')
    // 検証状態セレクトは shadcn Select (data-slot=select-trigger)
    await page.locator('[data-slot="select-trigger"]').click()
    await page.getByRole('option', { name: '不一致', exact: true }).click()
    await page.waitForTimeout(300)
    expect(lastUrl).toContain('verificationFilter=unmatched')
  })
})
