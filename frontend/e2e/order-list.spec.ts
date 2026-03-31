import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('受注一覧画面', () => {
  test.describe('初期表示', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/orders')
    })

    test('ページタイトルが表示される', async ({ page }) => {
      await expect(page.getByRole('heading', { name: '受注一覧' })).toBeVisible()
    })

    test('検索フォームが表示される', async ({ page }) => {
      await expect(page.getByText('伝票番号', { exact: true })).toBeVisible()
      await expect(page.getByText('商品名', { exact: true })).toBeVisible()
      await expect(page.getByText('商品コード', { exact: true })).toBeVisible()
      await expect(page.getByText('注文ステータス', { exact: true })).toBeVisible()
      await expect(page.getByText('注文日時', { exact: true })).toBeVisible()
      await expect(page.getByText('伝票日付', { exact: true })).toBeVisible()
    })

    test('案内メッセージが表示され、テーブルは非表示', async ({ page }) => {
      await expect(page.locator('text=検索条件を入力して')).toBeVisible()
      await expect(page.locator('table')).not.toBeVisible()
    })

    test('検索・リセットボタンが表示される', async ({ page }) => {
      await expect(page.locator('button:has-text("検索")')).toBeVisible()
      await expect(page.locator('button:has-text("リセット")')).toBeVisible()
    })

    test('注文日時FROMにデフォルト値が設定されている', async ({ page }) => {
      const dateInputs = page.locator('input[type="datetime-local"]')
      const fromValue = await dateInputs.first().inputValue()
      expect(fromValue).not.toBe('')
      expect(fromValue).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/)
    })
  })

  test.describe('検索機能', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/orders')
    })

    test('検索ボタンでテーブルが表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })
    })

    test('テーブルヘッダーが正しく表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.locator('th:has-text("注文番号")')).toBeVisible()
      await expect(page.locator('th:has-text("受注日時")')).toBeVisible()
      await expect(page.locator('th:has-text("伝票日付")')).toBeVisible()
      await expect(page.locator('th:has-text("得意先")')).toBeVisible()
      await expect(page.locator('th:has-text("商品コード")')).toBeVisible()
      await expect(page.locator('th:has-text("商品名")')).toBeVisible()
      await expect(page.locator('th:has-text("単価")')).toBeVisible()
      await expect(page.locator('th:has-text("数量")')).toBeVisible()
      await expect(page.locator('th:has-text("小計")')).toBeVisible()
      await expect(page.locator('th:has-text("ステータス")')).toBeVisible()
    })

    test('検索結果にデータが表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.locator('text=1001-1')).toBeVisible()
      await expect(page.locator('text=1001-2')).toBeVisible()
    })

    test('得意先名が表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.getByText('いしい記念病院').first()).toBeVisible()
    })

    test('ステータスがバッジで表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.getByText('注文受付', { exact: true }).first()).toBeVisible()
      await expect(page.getByText('納品済', { exact: true }).first()).toBeVisible()
    })

    test('リセットでテーブルが非表示になる', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await page.locator('button:has-text("リセット")').click()
      await expect(page.locator('text=検索条件を入力して')).toBeVisible()
      await expect(page.locator('table')).not.toBeVisible()
    })

    test('商品名で検索できる', async ({ page }) => {
      await page.locator('input[placeholder="商品名を入力"]').fill('テスト')
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('text=検索条件を入力して')).not.toBeVisible()
    })

    test('伝票番号で検索できる', async ({ page }) => {
      await page.locator('input[placeholder="伝票番号を入力"]').fill('SLIP-001')
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('text=検索条件を入力して')).not.toBeVisible()
    })
  })
})
