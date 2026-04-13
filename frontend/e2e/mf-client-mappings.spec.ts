import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_MAPPINGS = [
  { id: 1, alias: 'ABC病院', mfClientName: '（医）ABC病院', addDateTime: '2026-04-01', modifyDateTime: '2026-04-01' },
  { id: 2, alias: 'XYZ商事', mfClientName: '（株）XYZ', addDateTime: '2026-04-02', modifyDateTime: '2026-04-02' },
]

async function mockMappings(page: Page, initial: unknown[] = MOCK_MAPPINGS) {
  const store = [...initial] as Array<Record<string, unknown>>
  await page.route(
    (url) => url.pathname === '/api/v1/finance/mf-client-mappings',
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
    (url) => /^\/api\/v1\/finance\/mf-client-mappings\/\d+$/.test(url.pathname),
    async (route) => {
      const id = Number(route.request().url().split('/').pop())
      if (route.request().method() === 'DELETE') {
        const i = store.findIndex((m) => m.id === id)
        if (i >= 0) store.splice(i, 1)
        await route.fulfill({ status: 204 })
      } else if (route.request().method() === 'PUT') {
        const body = JSON.parse(route.request().postData() ?? '{}')
        const i = store.findIndex((m) => m.id === id)
        if (i >= 0) store[i] = { ...store[i], ...body }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(store[i] ?? {}) })
      } else {
        await route.fallback()
      }
    },
  )
  return store
}

test.describe('MF得意先マッピング', () => {
  test('MCM-01: 一覧表示・検索フィルタ', async ({ page }) => {
    await mockAllApis(page)
    await mockMappings(page)
    await loginAndGoto(page, '/finance/mf-client-mappings')

    await expect(page.getByRole('heading', { name: 'MF得意先マッピング' })).toBeVisible()
    await expect(page.getByText('ABC病院', { exact: true })).toBeVisible()
    await expect(page.getByText('XYZ商事', { exact: true })).toBeVisible()

    await page.getByPlaceholder(/alias.*MF名で検索/).fill('XYZ')
    await expect(page.getByText('ABC病院', { exact: true })).toHaveCount(0)
    await expect(page.getByText('XYZ商事', { exact: true })).toBeVisible()
  })

  test('MCM-02: 新規追加 → 一覧に反映', async ({ page }) => {
    await mockAllApis(page)
    await mockMappings(page)
    await loginAndGoto(page, '/finance/mf-client-mappings')

    await page.getByRole('button', { name: /新規追加/ }).click()
    await expect(page.getByRole('dialog')).toBeVisible()

    const dialog = page.getByRole('dialog')
    await dialog.locator('input').nth(0).fill('新規病院')
    await dialog.locator('input').nth(1).fill('（医）新規病院')
    await page.getByRole('button', { name: '追加', exact: true }).click()

    await expect(page.getByText('新規病院', { exact: true })).toBeVisible()
  })

  test('MCM-03: 削除確認ダイアログ → 削除', async ({ page }) => {
    await mockAllApis(page)
    await mockMappings(page)
    await loginAndGoto(page, '/finance/mf-client-mappings')

    // ABC病院行の削除ボタン
    const row = page.getByRole('row', { name: /ABC病院/ })
    await row.locator('button').filter({ has: page.locator('svg.lucide-trash2') }).click()

    await expect(page.getByRole('dialog')).toContainText('ABC病院')
    await page.getByRole('button', { name: '削除' }).click()

    await expect(page.getByText('ABC病院')).toHaveCount(0)
  })
})
