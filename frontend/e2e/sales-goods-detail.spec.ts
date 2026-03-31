import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('販売商品ワーク詳細画面', () => {
  async function navigateToFirstWorkDetail(page: import('@playwright/test').Page) {
    await mockAllApis(page)
    await loginAndGoto(page, '/sales-goods/work')
    const firstRow = page.locator('table tbody tr').first()
    await expect(firstRow).toBeVisible({ timeout: 10000 })
    const text = await firstRow.textContent()
    if (text && text.includes('データがありません')) {
      test.skip(true, 'ワークデータが存在しないためスキップ')
    }
    await firstRow.click()
    await page.waitForURL(/\/sales-goods\/work\/\d+\/\d+/, { timeout: 10000 })
  }

  test('ページヘッダーとボタンが正しく表示される', async ({ page }) => {
    await navigateToFirstWorkDetail(page)

    await expect(page.locator('text=販売商品ワーク詳細')).toBeVisible()
    await expect(page.locator('button:has-text("編集")')).toBeVisible()
    await expect(page.locator('button:has-text("マスタに反映")')).toBeVisible()
    await expect(page.locator('text=一覧に戻る')).toBeVisible()
  })

  test('タブが表示される', async ({ page }) => {
    await navigateToFirstWorkDetail(page)

    await expect(page.locator('text=商品基本情報')).toBeVisible()
    await expect(page.locator('text=価格情報')).toBeVisible()
    await expect(page.locator('text=商品説明')).toBeVisible()
  })

  test('商品基本情報タブのフィールドが表示される', async ({ page }) => {
    await navigateToFirstWorkDetail(page)

    await expect(page.locator('text=商品番号')).toBeVisible()
    await expect(page.locator('text=商品コード').first()).toBeVisible()
    await expect(page.locator('text=商品名').first()).toBeVisible()
    await expect(page.locator('text=JANコード')).toBeVisible()
    await expect(page.getByText('メーカー', { exact: true })).toBeVisible()
  })

  test('価格情報タブに切り替えできる', async ({ page }) => {
    await navigateToFirstWorkDetail(page)

    await page.locator('button[role="tab"]:has-text("価格情報")').click()
    await expect(page.locator('text=参考価格')).toBeVisible()
    await expect(page.locator('text=標準仕入単価')).toBeVisible()
    await expect(page.locator('text=標準売単価')).toBeVisible()
  })

  test('商品説明タブに切り替えできる', async ({ page }) => {
    await navigateToFirstWorkDetail(page)

    await page.locator('button[role="tab"]:has-text("商品説明")').click()
    await expect(page.getByText('キャッチフレーズ', { exact: true })).toBeVisible()
    await expect(page.getByText('商品概要', { exact: true })).toBeVisible()
    await expect(page.getByText('商品説明1', { exact: true })).toBeVisible()
    await expect(page.getByText('商品説明2', { exact: true })).toBeVisible()
  })

  test('編集モードの切り替えが動作する', async ({ page }) => {
    await navigateToFirstWorkDetail(page)

    await page.locator('button:has-text("編集")').click()

    await expect(page.locator('button:has-text("保存")')).toBeVisible()
    await expect(page.locator('button:has-text("キャンセル")')).toBeVisible()
    await expect(page.locator('button:has-text("マスタに反映")')).toBeHidden()

    await page.locator('button:has-text("キャンセル")').click()
    await expect(page.locator('button:has-text("編集")')).toBeVisible()
    await expect(page.locator('button:has-text("マスタに反映")')).toBeVisible()
  })

  test('編集モードで入力フィールドが表示される', async ({ page }) => {
    await navigateToFirstWorkDetail(page)
    await page.locator('button:has-text("編集")').click()

    const inputs = page.locator('[role="tabpanel"] input')
    const inputCount = await inputs.count()
    expect(inputCount).toBeGreaterThanOrEqual(3)
  })

  test('「一覧に戻る」でワーク一覧に遷移する', async ({ page }) => {
    await navigateToFirstWorkDetail(page)
    await page.locator('text=一覧に戻る').click()
    await page.waitForURL('**/sales-goods/work', { timeout: 10000 })
  })
})

test.describe('販売商品マスタ詳細画面', () => {
  async function navigateToFirstMasterDetail(page: import('@playwright/test').Page) {
    await mockAllApis(page)
    await loginAndGoto(page, '/sales-goods')
    const firstRow = page.locator('table tbody tr').first()
    await expect(firstRow).toBeVisible({ timeout: 10000 })
    const text = await firstRow.textContent()
    if (text && text.includes('データがありません')) {
      test.skip(true, 'マスタデータが存在しないためスキップ')
    }
    await firstRow.click()
    await page.waitForURL(/\/sales-goods\/\d+\/\d+/, { timeout: 10000 })
  }

  test('ページヘッダーが正しく表示される', async ({ page }) => {
    await navigateToFirstMasterDetail(page)

    await expect(page.locator('text=販売商品マスタ詳細')).toBeVisible()
    await expect(page.locator('button:has-text("編集")')).toBeVisible()
    await expect(page.locator('button:has-text("マスタに反映")')).toBeHidden()
    await expect(page.locator('text=一覧に戻る')).toBeVisible()
  })

  test('タブが表示される', async ({ page }) => {
    await navigateToFirstMasterDetail(page)

    await expect(page.locator('text=商品基本情報')).toBeVisible()
    await expect(page.locator('text=価格情報')).toBeVisible()
    await expect(page.locator('text=商品説明')).toBeVisible()
  })

  test('編集モードの切り替えが動作する', async ({ page }) => {
    await navigateToFirstMasterDetail(page)

    await page.locator('button:has-text("編集")').click()
    await expect(page.locator('button:has-text("保存")')).toBeVisible()
    await expect(page.locator('button:has-text("キャンセル")')).toBeVisible()

    await page.locator('button:has-text("キャンセル")').click()
    await expect(page.locator('button:has-text("編集")')).toBeVisible()
  })

  test('「一覧に戻る」でマスタ一覧に遷移する', async ({ page }) => {
    await navigateToFirstMasterDetail(page)
    await page.locator('text=一覧に戻る').click()
    await page.waitForURL('**/sales-goods', { timeout: 10000 })
    expect(page.url()).not.toContain('/work')
  })
})
