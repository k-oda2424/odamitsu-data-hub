import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_RULES = [
  {
    id: 1, descriptionC: '仕入', descriptionDKeyword: null, priority: 100,
    amountSource: 'PAYMENT',
    debitAccount: '仕入高', debitSubAccount: '', debitDepartment: '', debitTaxResolver: 'PURCHASE_AUTO',
    creditAccount: '現金', creditSubAccount: '', creditSubAccountTemplate: '', creditDepartment: '', creditTaxResolver: 'OUTSIDE',
    summaryTemplate: '{D}',
    addDateTime: '2026-04-01', modifyDateTime: '2026-04-01',
  },
  {
    id: 2, descriptionC: '売上', descriptionDKeyword: null, priority: 100,
    amountSource: 'INCOME',
    debitAccount: '現金', debitSubAccount: '', debitDepartment: '', debitTaxResolver: 'OUTSIDE',
    creditAccount: '売上高', creditSubAccount: '', creditSubAccountTemplate: '', creditDepartment: '', creditTaxResolver: 'SALES_AUTO',
    summaryTemplate: '{D}',
    addDateTime: '2026-04-02', modifyDateTime: '2026-04-02',
  },
]

async function mockRules(page: Page, initial: unknown[] = MOCK_RULES) {
  const store = [...initial] as Array<Record<string, unknown>>
  await page.route(
    (url) => url.pathname === '/api/v1/finance/mf-journal-rules',
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(store) })
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() ?? '{}')
        const created = { id: store.length + 100, ...body, addDateTime: '2026-04-13', modifyDateTime: '2026-04-13' }
        store.push(created)
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(created) })
      } else {
        await route.fallback()
      }
    },
  )
  await page.route(
    (url) => /^\/api\/v1\/finance\/mf-journal-rules\/\d+$/.test(url.pathname),
    async (route) => {
      const id = Number(route.request().url().split('/').pop())
      if (route.request().method() === 'DELETE') {
        const i = store.findIndex((r) => r.id === id)
        if (i >= 0) store.splice(i, 1)
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )
  return store
}

test.describe('MF仕訳ルール', () => {
  test('MJR-01: 一覧表示', async ({ page }) => {
    await mockAllApis(page)
    await mockRules(page)
    await loginAndGoto(page, '/finance/mf-journal-rules')

    await expect(page.getByText('仕入').first()).toBeVisible()
    await expect(page.getByText('売上').first()).toBeVisible()
    await expect(page.getByText('仕入高')).toBeVisible()
    await expect(page.getByText('売上高')).toBeVisible()
  })

  test('MJR-02: 削除確認ダイアログ → 削除', async ({ page }) => {
    await mockAllApis(page)
    await mockRules(page)
    await loginAndGoto(page, '/finance/mf-journal-rules')

    const row = page.getByRole('row', { name: /仕入高/ })
    await row.locator('button').filter({ has: page.locator('svg.lucide-trash2') }).click()
    await expect(page.getByRole('dialog')).toContainText('仕入')
    await page.getByRole('button', { name: '削除' }).click()

    // 1行削除後、残りは売上のみ
    await expect(page.getByRole('row', { name: /仕入高/ })).toHaveCount(0)
    await expect(page.getByRole('row', { name: /売上高/ })).toBeVisible()
  })
})
