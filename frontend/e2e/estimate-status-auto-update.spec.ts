import { test, expect, type Page, type Route } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis, MOCK_USER, MOCK_ESTIMATES, MOCK_ESTIMATE_COMPARISON_DETAIL, MOCK_ESTIMATE_COMPARISON_SUBMITTED } from './helpers/mock-api'

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

// ==================== getNotifiedStatus ユニットテスト ====================

// getNotifiedStatus を直接インポートしてテスト（Playwright Node.js コンテキスト）
import { getNotifiedStatus } from '../types/estimate'

test.describe('getNotifiedStatus ユニットテスト', () => {
  const cases: [string | null, string | null][] = [
    ['00', '10'],     // U-01: 作成 → 提出済
    ['10', '10'],     // U-02: 提出済 → 変化なし
    ['20', '30'],     // U-03: 修正 → 修正後提出済
    ['30', '30'],     // U-04: 修正後提出済 → 変化なし
    ['40', null],     // U-05: 他同グループ提出済 → 自動更新対象外
    ['50', null],     // U-06: 削除 → 自動更新対象外
    ['60', null],     // U-07: 都度見積のため不要 → 自動更新対象外
    ['70', null],     // U-08: 価格反映済 → 自動更新対象外
    ['90', null],     // U-09: 入札関係のため不要 → 自動更新対象外
    ['99', null],     // U-10: 取引なし → 自動更新対象外
    [null, null],     // U-11: null → 自動更新対象外
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

    let dialogMessage = ''
    page.on('dialog', async (dialog) => {
      dialogMessage = dialog.message()
      await dialog.accept()
    })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(1000)
    expect(dialogMessage).toContain('提出済')
    expect(statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ estimateStatus: '10' })
  })

  test('E-02: ステータス 20 で印刷→確認ダイアログに「修正後提出済」→ステータス30に更新', async ({ page }) => {
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

    let dialogMessage = ''
    page.on('dialog', async (dialog) => {
      dialogMessage = dialog.message()
      await dialog.accept()
    })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(1000)
    expect(dialogMessage).toContain('修正後提出済')
    expect(statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ estimateStatus: '30' })
  })

  test('E-03: ステータス 10 で印刷→確認ダイアログなし、ステータス更新なし', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '10')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    let dialogShown = false
    page.on('dialog', async (dialog) => { dialogShown = true; await dialog.accept() })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(500)
    expect(dialogShown).toBe(false)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-04: ステータス 30 で印刷→確認ダイアログなし、ステータス更新なし', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '30')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    let dialogShown = false
    page.on('dialog', async (dialog) => { dialogShown = true; await dialog.accept() })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(500)
    expect(dialogShown).toBe(false)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-05: ステータス 70 で印刷→確認ダイアログなし、ステータス更新なし（自動更新対象外）', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '70')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    let dialogShown = false
    page.on('dialog', async (dialog) => { dialogShown = true; await dialog.accept() })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(500)
    expect(dialogShown).toBe(false)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-06: 印刷キャンセル→ステータス更新なし', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '00')

    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimates/570')

    page.on('dialog', (dialog) => dialog.dismiss())
    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(500)
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

    let dialogMessage = ''
    page.on('dialog', async (dialog) => {
      dialogMessage = dialog.message()
      await dialog.accept()
    })

    await page.locator('button:has-text("PDF")').click()
    await page.waitForTimeout(2000)
    expect(dialogMessage).toContain('提出済')
    expect(statusUpdateBody).not.toBeNull()
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

    let dialogShown = false
    page.on('dialog', async (dialog) => { dialogShown = true; await dialog.accept() })

    await page.locator('button:has-text("PDF")').click()
    await page.waitForTimeout(1000)
    expect(dialogShown).toBe(false)
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

    page.on('dialog', (dialog) => dialog.dismiss())
    await page.locator('button:has-text("PDF")').click()
    await page.waitForTimeout(500)
    expect(statusUpdateCalled).toBe(false)
  })
})

// ==================== 比較見積詳細 — 印刷時ステータス自動更新 ====================

test.describe('比較見積印刷時ステータス自動更新', () => {
  test('E-10: ステータス 00 で印刷→確認OK→ステータス10に更新', async ({ page }) => {
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

    let dialogMessage = ''
    page.on('dialog', async (dialog) => {
      dialogMessage = dialog.message()
      await dialog.accept()
    })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(1000)
    expect(dialogMessage).toContain('提出済')
    expect(statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ comparisonStatus: '10' })
  })

  test('E-11: ステータス 10 で印刷→確認ダイアログなし', async ({ page }) => {
    await mockAllApis(page)
    await mockComparisonWithStatus(page, '10')

    let dialogShown = false
    let statusUpdateCalled = false
    await page.route(
      (url) => /^\/api\/v1\/estimate-comparisons\/\d+\/status$/.test(url.pathname),
      async (route) => { statusUpdateCalled = true; await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }) },
    )

    await loginAndGoto(page, '/estimate-comparisons/1')

    page.on('dialog', async (dialog) => { dialogShown = true; await dialog.accept() })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(500)
    expect(dialogShown).toBe(false)
    expect(statusUpdateCalled).toBe(false)
  })

  test('E-12: ステータス 20 で印刷→確認OK→ステータス30に更新', async ({ page }) => {
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

    let dialogMessage = ''
    page.on('dialog', async (dialog) => {
      dialogMessage = dialog.message()
      await dialog.accept()
    })

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(1000)
    expect(dialogMessage).toContain('修正後提出済')
    expect(statusUpdateBody).not.toBeNull()
    expect(JSON.parse(statusUpdateBody!)).toEqual({ comparisonStatus: '30' })
  })
})

// ==================== エラー処理 ====================

test.describe('ステータス更新エラー処理', () => {
  test('E-13: ステータス更新API失敗→エラートースト表示', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '00')

    // Mock status update to fail
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
      async (route) => {
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Internal Server Error' }) })
      },
    )

    await loginAndGoto(page, '/estimates/570')

    page.on('dialog', (dialog) => dialog.accept())
    await page.locator('button:has-text("印刷")').click()
    await expect(page.getByText('ステータスの更新に失敗しました')).toBeVisible({ timeout: 5000 })
  })

  test('E-14: ステータス変化なし→トースト非表示', async ({ page }) => {
    await mockAllApis(page)
    await mockEstimateWithStatus(page, '10')

    await loginAndGoto(page, '/estimates/570')

    await page.locator('button:has-text("印刷")').click()
    await page.waitForTimeout(1000)
    // No success or error toast should appear
    await expect(page.getByText('ステータスを')).not.toBeVisible()
    await expect(page.getByText('ステータスの更新に失敗')).not.toBeVisible()
  })
})
