import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('請求書一覧画面', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/finance/invoices')
  })

  test('ページヘッダーが表示される', async ({ page }) => {
    await expect(page.getByText('請求書一覧', { exact: true })).toBeVisible()
  })

  test('検索フォームが表示される', async ({ page }) => {
    await expect(page.getByText('締月', { exact: true })).toBeVisible()
    await expect(page.getByText('得意先コード', { exact: true })).toBeVisible()
    await expect(page.getByText('得意先名', { exact: true })).toBeVisible()
  })

  test('初期状態ではテーブルが表示されない', async ({ page }) => {
    await expect(
      page.getByText('検索条件を入力して「検索」ボタンを押してください'),
    ).toBeVisible()
  })

  test('検索実行で結果テーブルが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('いしい記念病院')).toBeVisible()
    await expect(page.getByText('クローバーハウス')).toBeVisible()
  })

  test('請求額が表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('130,000').first()).toBeVisible()
    await expect(page.getByText('55,000').first()).toBeVisible()
  })

  test('行クリックで詳細Dialogが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await page.getByText('いしい記念病院').click()
    await expect(page.getByText('請求詳細')).toBeVisible()
    await expect(page.getByText('前回請求残高')).toBeVisible()
    await expect(page.getByText('入金日を更新')).toBeVisible()
  })

  test('リセットでテーブルが非表示になる', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('いしい記念病院')).toBeVisible()
    await page.getByRole('button', { name: 'リセット' }).click()
    await expect(
      page.getByText('検索条件を入力して「検索」ボタンを押してください'),
    ).toBeVisible()
  })
})
