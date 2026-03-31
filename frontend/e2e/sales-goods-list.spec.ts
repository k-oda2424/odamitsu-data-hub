import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('販売商品ワーク一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/sales-goods/work')
  })

  test('ページが正しく表示される', async ({ page }) => {
    await expect(page.locator('text=販売商品ワーク一覧')).toBeVisible()
    await expect(page.locator('text=新規作成')).toBeVisible()
  })

  test('検索フォームが表示される', async ({ page }) => {
    await expect(page.locator('text=商品名').first()).toBeVisible()
    await expect(page.locator('text=商品コード').first()).toBeVisible()
    await expect(page.locator('text=キーワード').first()).toBeVisible()
    await expect(page.locator('text=仕入先').first()).toBeVisible()
    await expect(page.locator('button:has-text("検索")')).toBeVisible()
    await expect(page.locator('button:has-text("リセット")')).toBeVisible()
  })

  test('テーブルヘッダーが正しく表示される', async ({ page }) => {
    await page.locator('button:has-text("検索")').click()
    await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

    await expect(page.locator('th:has-text("商品No")')).toBeVisible()
    await expect(page.locator('th:has-text("商品コード")')).toBeVisible()
    await expect(page.locator('th:has-text("商品名")')).toBeVisible()
    await expect(page.locator('th:has-text("仕入先")')).toBeVisible()
    await expect(page.locator('th:has-text("仕入単価")')).toBeVisible()
    await expect(page.locator('th:has-text("売単価")')).toBeVisible()
  })

  test('「新規作成」ボタンで作成画面に遷移する', async ({ page }) => {
    await page.locator('text=新規作成').click()
    await page.waitForURL('**/sales-goods/create', { timeout: 10000 })
    expect(page.url()).toContain('/sales-goods/create')
  })

  test('検索フォームのリセットが動作する', async ({ page }) => {
    const goodsNameInput = page.locator('input[placeholder="商品名を入力"]')
    await goodsNameInput.fill('テスト商品')
    expect(await goodsNameInput.inputValue()).toBe('テスト商品')

    await page.locator('button:has-text("リセット")').click()
    expect(await goodsNameInput.inputValue()).toBe('')
  })

  test('検索ボタンでクエリが実行される', async ({ page }) => {
    const goodsNameInput = page.locator('input[placeholder="商品名を入力"]')
    await goodsNameInput.fill('テスト')

    await page.locator('button:has-text("検索")').click()

    // ページがクラッシュしないことを確認
    await expect(page.locator('text=販売商品ワーク一覧')).toBeVisible()
  })

  test('データが存在する場合、行クリックで詳細画面に遷移する', async ({ page }) => {
    await page.locator('button:has-text("検索")').click()
    await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

    const firstRow = page.locator('table tbody tr').first()
    const text = await firstRow.textContent()
    if (text && !text.includes('データがありません')) {
      await firstRow.click()
      await page.waitForURL(/\/sales-goods\/work\/\d+\/\d+/, { timeout: 10000 })
    }
  })
})

test.describe('販売商品マスタ一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/sales-goods')
  })

  test('ページが正しく表示される', async ({ page }) => {
    await expect(page.locator('text=販売商品マスタ一覧')).toBeVisible()
  })

  test('新規作成ボタンが存在しない', async ({ page }) => {
    await expect(page.locator('button:has-text("新規作成")')).toBeHidden()
  })

  test('検索フォームが表示される', async ({ page }) => {
    await expect(page.locator('text=商品名').first()).toBeVisible()
    await expect(page.locator('text=商品コード').first()).toBeVisible()
    await expect(page.locator('text=キーワード').first()).toBeVisible()
    await expect(page.locator('text=仕入先').first()).toBeVisible()
  })

  test('テーブルヘッダーが正しく表示される', async ({ page }) => {
    await page.locator('button:has-text("検索")').click()
    await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

    await expect(page.locator('th:has-text("商品No")')).toBeVisible()
    await expect(page.locator('th:has-text("商品コード")')).toBeVisible()
    await expect(page.locator('th:has-text("商品名")')).toBeVisible()
    await expect(page.locator('th:has-text("仕入先")')).toBeVisible()
    await expect(page.locator('th:has-text("仕入単価")')).toBeVisible()
    await expect(page.locator('th:has-text("売単価")')).toBeVisible()
  })

  test('検索フォームのリセットが動作する', async ({ page }) => {
    const goodsNameInput = page.locator('input[placeholder="商品名を入力"]')
    await goodsNameInput.fill('テスト商品')
    expect(await goodsNameInput.inputValue()).toBe('テスト商品')

    await page.locator('button:has-text("リセット")').click()
    expect(await goodsNameInput.inputValue()).toBe('')
  })

  test('データが存在する場合、行クリックで詳細画面に遷移する', async ({ page }) => {
    await page.locator('button:has-text("検索")').click()
    await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

    const firstRow = page.locator('table tbody tr').first()
    const text = await firstRow.textContent()
    if (text && !text.includes('データがありません')) {
      await firstRow.click()
      await page.waitForURL(/\/sales-goods\/\d+\/\d+/, { timeout: 10000 })
    }
  })
})
