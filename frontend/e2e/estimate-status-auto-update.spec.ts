import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis, MOCK_ESTIMATES, MOCK_ESTIMATE_COMPARISON_DETAIL } from './helpers/mock-api'

// ==================== ヘルパー ====================

async function mockEstimateWithStatus(page: Page, status: string) {
  await page.route(
    (url) => /^\/api\/v1\/estimates\/\d+$/.test(url.pathname) && !url.pathname.includes('/status') && !url.pathname.includes('/pdf'),
    async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...MOCK_ESTIMATES[0], estimateStatus: status, details: [] }),
        })
      } else {
        await route.fallback()
      }
    },
  )
}

async function mockComparisonWithStatus(page: Page, status: string) {
  await page.route(
    (url) => /^\/api\/v1\/estimate-comparisons\/\d+$/.test(url.pathname) && !url.pathname.includes('/status'),
    async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...MOCK_ESTIMATE_COMPARISON_DETAIL, comparisonStatus: status }),
        })
      } else {
        await route.fallback()
      }
    },
  )
}

// 印刷ダイアログ（OSネイティブ）をサイレントに処理するため window.print をスタブ
async function stubWindowPrint(page: Page) {
  await page.addInitScript(() => {
    window.print = () => {}
  })
}

// ==================== getNotifiedStatus ユニットテスト ====================

import { getNotifiedStatus } from '../types/estimate'

test.describe('getNotifiedStatus ユニットテスト', () => {
  const cases: [string | null, string | null][] = [
    ['00', '10'],
    ['10', '10'],
    ['20', '30'],
    ['30', '30'],
    ['40', null],
    ['50', null],
    ['60', null],
    ['70', null],
    ['90', null],
    ['99', null],
    [null, null],
  ]

  for (const [input, expected] of cases) {
    test(`getNotifiedStatus('${input}') → ${expected === null ? 'null' : `'${expected}'`}`, () => {
      expect(getNotifiedStatus(input)).toBe(expected)
    })
  }
})

// ==================== 見積詳細 — 印刷時ステータス自動更新 ====================

test.describe('見積印刷時ステータス自動更新', () => {
  test('E-01: ステータス 00 で印刷→確認ダイアログ表示→ステータス10に更新', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '00')

    let statusUpdateBody: string | null = null
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => {
        statusUpdateBody = route.request().postData()
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...MOCK_ESTIMATES[0], estimateStatus: '10' }) })
      },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toContainText('提出済')
    await page.getByRole('button', { name: '確認' }).click()

    await expect.poll(() => statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ estimateStatus: '10' })
  })

  test('E-02: ステータス 20 で印刷→確認ダイアログに「修正後提出済」→ステータス30に更新', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '20')

    let statusUpdateBody: string | null = null
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => {
        statusUpdateBody = route.request().postData()
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...MOCK_ESTIMATES[0], estimateStatus: '30' }) })
      },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toContainText('修正後提出済')
    await page.getByRole('button', { name: '確認' }).click()

    await expect.poll(() => statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ estimateStatus: '30' })
  })

  test('E-03: ステータス 10 で印刷→確認ダイアログなし、ステータス更新なし', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '10')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-04: ステータス 30 で印刷→確認ダイアログなし、ステータス更新なし', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '30')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-05: ステータス 70 で印刷→確認ダイアログなし、ステータス更新なし（自動更新対象外）', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '70')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-06: 印刷キャンセル→ステータス更新なし', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '00')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: 'キャンセル' }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(statusUpdateCalled).toBe(false)
  })
})

// ==================== 見積詳細 — PDF時ステータス自動更新 ====================

test.describe('見積PDF時ステータス自動更新', () => {
  test('E-07: ステータス 00 でPDF→確認OK→ステータス10に更新', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '00')

    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/pdf/.test(url.pathname),
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/pdf',
          headers: { 'Content-Disposition': 'attachment; filename="test.pdf"' },
          body: Buffer.from('fake-pdf'),
        })
      },
    )

    let statusUpdateBody: string | null = null
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => {
        statusUpdateBody = route.request().postData()
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...MOCK_ESTIMATES[0], estimateStatus: '10' }) })
      },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("PDF")').click()
    await expect(page.getByRole('dialog')).toContainText('提出済')
    await page.getByRole('button', { name: '確認' }).click()

    await expect.poll(() => statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ estimateStatus: '10' })
  })

  test('E-08: ステータス 10 でPDF→確認ダイアログなし', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '10')

    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/pdf/.test(url.pathname),
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/pdf',
          headers: { 'Content-Disposition': 'attachment; filename="test.pdf"' },
          body: Buffer.from('fake-pdf'),
        })
      },
    )

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("PDF")').click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-09: PDFキャンセル→ステータス更新なし', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '00')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("PDF")').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: 'キャンセル' }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(statusUpdateCalled).toBe(false)
  })
})

// ==================== 比較見積詳細 — 印刷時ステータス自動更新 ====================

test.describe('比較見積印刷時ステータス自動更新', () => {
  test('E-10: ステータス 00 で印刷→確認OK→ステータス10に更新', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)

    let statusUpdateBody: string | null = null
    await page.route(
      (url) => /^\/api\/v1\/estimate-comparisons\/\d+\/status$/.test(url.pathname),
      async (route) => {
        statusUpdateBody = route.request().postData()
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...MOCK_ESTIMATE_COMPARISON_DETAIL, comparisonStatus: '10' }) })
      },
    )

    await loginAndGoto(page, '/estimate-comparisons/1')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toContainText('提出済')
    await page.getByRole('button', { name: '確認' }).click()

    await expect.poll(() => statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ comparisonStatus: '10' })
  })

  test('E-11: ステータス 10 で印刷→確認ダイアログなし', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockComparisonWithStatus(page, '10')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimate-comparisons\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimate-comparisons/1')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-12: ステータス 20 で印刷→確認OK→ステータス30に更新', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockComparisonWithStatus(page, '20')

    let statusUpdateBody: string | null = null
    await page.route(
      (url) => /^\/api\/v1\/estimate-comparisons\/\d+\/status$/.test(url.pathname),
      async (route) => {
        statusUpdateBody = route.request().postData()
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...MOCK_ESTIMATE_COMPARISON_DETAIL, comparisonStatus: '30' }) })
      },
    )

    await loginAndGoto(page, '/estimate-comparisons/1')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toContainText('修正後提出済')
    await page.getByRole('button', { name: '確認' }).click()

    await expect.poll(() => statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ comparisonStatus: '30' })
  })
})

// ==================== エラー処理 ====================

test.describe('ステータス更新エラー処理', () => {
  test('E-13: ステータス更新API失敗→エラートースト表示', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '00')

    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => {
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Internal Server Error' }) })
      },
    )

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await page.getByRole('button', { name: '確認' }).click()
    await expect(page.getByText('ステータスの更新に失敗しました')).toBeVisible({ timeout: 5000 })
  })

  test('E-14: ステータス変化なし→トースト非表示', async ({ page }) => {
    await stubWindowPrint(page)
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '10')

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(page.getByText('ステータスを')).not.toBeVisible()
    await expect(page.getByText('ステータスの更新に失敗')).not.toBeVisible()
  })
})
