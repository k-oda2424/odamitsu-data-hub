import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('見積一覧画面', () => {
  test.describe('初期表示', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates')
    })

    test('ページヘッダーと見積作成ボタンが表示される', async ({ page }) => {
      await expect(page.getByRole('heading', { name: '見積一覧' })).toBeVisible()
      await expect(page.locator('text=見積作成')).toBeVisible()
    })

    test('検索フォームが表示される', async ({ page }) => {
      await expect(page.getByText('見積番号', { exact: true })).toBeVisible()
      await expect(page.getByText('商品名', { exact: true })).toBeVisible()
      await expect(page.getByText('商品コード', { exact: true })).toBeVisible()
      await expect(page.getByText('見積日', { exact: true })).toBeVisible()
      await expect(page.getByText('価格変更日', { exact: true })).toBeVisible()
      await expect(page.getByText('見積ステータス', { exact: true })).toBeVisible()
    })

    test('デフォルトで「作成」と「修正」ステータスがチェックされている', async ({ page }) => {
      const createCheckbox = page.locator('label').filter({ hasText: /^作成$/ }).locator('button[role="checkbox"]')
      const modifiedCheckbox = page.locator('label').filter({ hasText: /^修正$/ }).locator('button[role="checkbox"]')
      await expect(createCheckbox).toHaveAttribute('data-state', 'checked')
      await expect(modifiedCheckbox).toHaveAttribute('data-state', 'checked')
    })

    test('案内メッセージが表示され、テーブルは非表示', async ({ page }) => {
      await expect(page.locator('text=検索条件を入力して')).toBeVisible()
      await expect(page.locator('table')).not.toBeVisible()
    })

    test('検索・リセットボタンが表示される', async ({ page }) => {
      await expect(page.locator('button:has-text("検索")')).toBeVisible()
      await expect(page.locator('button:has-text("リセット")')).toBeVisible()
    })
  })

  test.describe('検索機能', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates')
    })

    test('検索ボタンでテーブルが表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })
    })

    test('テーブルヘッダーが正しく表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.locator('th:has-text("見積番号")')).toBeVisible()
      await expect(page.locator('th:has-text("見積日")')).toBeVisible()
      await expect(page.locator('th:has-text("価格変更日")')).toBeVisible()
      await expect(page.locator('th:has-text("得意先")')).toBeVisible()
      await expect(page.locator('th:has-text("見積ステータス")')).toBeVisible()
    })

    test('検索結果にデータが表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      // モックデータの見積番号が表示される
      await expect(page.locator('text=570')).toBeVisible()
      await expect(page.locator('text=2341')).toBeVisible()
    })

    test('得意先がコード付きで表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.locator('text=【022000】いしい記念病院')).toBeVisible()
    })

    test('見積ステータスがバッジで表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.getByText('修正', { exact: true }).first()).toBeVisible()
      await expect(page.getByText('作成', { exact: true }).first()).toBeVisible()
    })

    test('リセットでテーブルが非表示になる', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await page.locator('button:has-text("リセット")').click()
      await expect(page.locator('text=検索条件を入力して')).toBeVisible()
      await expect(page.locator('table')).not.toBeVisible()
    })

    test('見積番号で検索できる', async ({ page }) => {
      await page.locator('input[placeholder="見積番号を入力"]').fill('570')
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })
    })

    test('商品名で検索できる', async ({ page }) => {
      await page.locator('input[placeholder="商品名を入力"]').fill('テスト')
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('text=検索条件を入力して')).not.toBeVisible()
    })
  })

  test.describe('ナビゲーション', () => {
    test('「見積作成」ボタンで作成画面に遷移する', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates')

      await page.locator('text=見積作成').click()
      await page.waitForURL('**/estimates/create', { timeout: 10000 })
      expect(page.url()).toContain('/estimates/create')
    })

    test('行クリックで見積詳細に遷移する', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates')

      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      const firstRow = page.locator('table tbody tr').first()
      await firstRow.click()
      await page.waitForURL(/\/estimates\/\d+/, { timeout: 10000 })
    })
  })
})
