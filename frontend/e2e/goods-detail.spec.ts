import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('商品マスタ詳細画面', () => {
  test.describe('一覧からの遷移', () => {
    test('商品マスタ一覧の行クリックで詳細画面に遷移する', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/goods')

      // 検索を実行してテーブルを表示
      await page.locator('button:has-text("検索")').click()
      const firstRow = page.locator('table tbody tr').first()
      await expect(firstRow).toBeVisible({ timeout: 10000 })
      await firstRow.click()

      // URLが /goods/{goodsNo} の形式になっている
      await page.waitForURL(/\/goods\/\d+/, { timeout: 10000 })
      expect(page.url()).toMatch(/\/goods\/\d+$/)
    })
  })

  test.describe('詳細画面の表示', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/goods')
      await page.locator('button:has-text("検索")').click()
      const firstRow = page.locator('table tbody tr').first()
      await expect(firstRow).toBeVisible({ timeout: 10000 })
      await firstRow.click()
      await page.waitForURL(/\/goods\/\d+/, { timeout: 10000 })
      // 詳細ページが描画されるのを待つ
      await expect(page.locator('text=商品マスタ詳細')).toBeVisible({ timeout: 10000 })
    })

    test('ページヘッダーとボタンが表示される', async ({ page }) => {
      await expect(page.locator('text=商品マスタ詳細')).toBeVisible()
      await expect(page.locator('text=編集')).toBeVisible()
      await expect(page.locator('text=一覧に戻る')).toBeVisible()
    })

    test('商品マスタ情報カードが表示される', async ({ page }) => {
      await expect(page.locator('text=商品マスタ情報')).toBeVisible()
      await expect(page.locator('text=商品番号')).toBeVisible()
      await expect(page.locator('text=商品名').first()).toBeVisible()
      await expect(page.locator('text=JANコード')).toBeVisible()
      await expect(page.getByText('メーカー', { exact: true })).toBeVisible()
      await expect(page.locator('text=入数')).toBeVisible()
      await expect(page.locator('text=規格')).toBeVisible()
      await expect(page.locator('text=キーワード').first()).toBeVisible()
      await expect(page.locator('text=軽減税率')).toBeVisible()
      await expect(page.locator('text=廃番')).toBeVisible()
    })

    test('関連販売商品一覧カードが表示される', async ({ page }) => {
      await expect(page.locator('text=関連販売商品一覧')).toBeVisible()
    })

    test('「一覧に戻る」ボタンで商品マスタ一覧に戻る', async ({ page }) => {
      await page.locator('text=一覧に戻る').click()
      await page.waitForURL('**/goods', { timeout: 10000 })
      expect(page.url()).toContain('/goods')
    })
  })

  test.describe('インライン編集', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/goods')
      await page.locator('button:has-text("検索")').click()
      const firstRow = page.locator('table tbody tr').first()
      await expect(firstRow).toBeVisible({ timeout: 10000 })
      await firstRow.click()
      await page.waitForURL(/\/goods\/\d+/, { timeout: 10000 })
      await expect(page.locator('text=商品マスタ詳細')).toBeVisible({ timeout: 10000 })
    })

    test('編集ボタンで編集モードに切り替わる', async ({ page }) => {
      const cardContent = page.locator('text=商品マスタ情報').locator('..')
      await expect(cardContent).toBeVisible()

      await page.locator('text=編集').click()

      // 編集モードに入ると「保存」「キャンセル」ボタンが表示される
      await expect(page.locator('text=保存')).toBeVisible()
      await expect(page.locator('text=キャンセル')).toBeVisible()

      // 「編集」ボタンは非表示になる
      await expect(page.locator('button:has-text("編集")')).toBeHidden()
    })

    test('キャンセルで編集モードが解除される', async ({ page }) => {
      await page.locator('text=編集').click()
      await expect(page.locator('text=保存')).toBeVisible()

      await page.locator('text=キャンセル').click()

      // 編集ボタンが再表示される
      await expect(page.locator('button:has-text("編集")')).toBeVisible()
      // 保存ボタンは非表示
      await expect(page.locator('button:has-text("保存")')).toBeHidden()
    })

    test('編集モードではフォーム入力フィールドが表示される', async ({ page }) => {
      await page.locator('text=編集').click()

      // input要素がカード内に存在する
      const inputs = page.locator('.space-y-6 >> nth=0').locator('input')
      const inputCount = await inputs.count()
      expect(inputCount).toBeGreaterThanOrEqual(3)
    })
  })
})
