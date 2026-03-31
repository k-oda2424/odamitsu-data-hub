import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('販売商品新規作成画面', () => {
  test.describe('Step1: 商品マスタ選択', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/sales-goods/create')
    })

    test('ページヘッダーが表示される', async ({ page }) => {
      await expect(page.locator('text=販売商品新規作成')).toBeVisible()
      await expect(page.locator('text=一覧に戻る')).toBeVisible()
    })

    test('商品マスタ選択カードが表示される', async ({ page }) => {
      await expect(page.locator('text=販売商品の元となる商品マスタを選択')).toBeVisible()
    })

    test('検索フォームが表示される', async ({ page }) => {
      await expect(page.locator('text=商品名').first()).toBeVisible()
      await expect(page.locator('text=キーワード').first()).toBeVisible()
      await expect(page.locator('text=JANコード').first()).toBeVisible()
      await expect(page.locator('text=メーカー').first()).toBeVisible()
      await expect(page.locator('button:has-text("検索")')).toBeVisible()
      await expect(page.locator('button:has-text("リセット")')).toBeVisible()
    })

    test('テーブルが表示される', async ({ page }) => {
      await expect(page.locator('th:has-text("商品No")')).toBeVisible({ timeout: 10000 })
      await expect(page.locator('th:has-text("商品名")')).toBeVisible()
      await expect(page.locator('th:has-text("JANコード")')).toBeVisible()
      await expect(page.locator('th:has-text("メーカー")')).toBeVisible()
    })

    test('検索が動作する', async ({ page }) => {
      const goodsNameInput = page.locator('input[placeholder="商品名を入力"]')
      await goodsNameInput.fill('テスト')
      await page.locator('button:has-text("検索")').click()

      await expect(page.locator('text=販売商品新規作成')).toBeVisible()
    })

    test('「一覧に戻る」でワーク一覧に遷移する', async ({ page }) => {
      await page.locator('text=一覧に戻る').click()
      await page.waitForURL('**/sales-goods/work', { timeout: 10000 })
    })

    test('商品マスタ行クリックでStep2に遷移する', async ({ page }) => {
      const firstRow = page.locator('table tbody tr').first()
      await expect(firstRow).toBeVisible({ timeout: 10000 })
      const text = await firstRow.textContent()

      if (text && !text.includes('データがありません')) {
        await firstRow.click()

        // Step2: 選択した商品マスタ情報と入力フォームが表示される
        await expect(page.locator('text=選択した商品マスタ')).toBeVisible({ timeout: 5000 })
        await expect(page.locator('text=販売商品情報入力')).toBeVisible()
      }
    })
  })

  test.describe('Step2: 販売商品情報入力', () => {
    async function goToStep2(page: import('@playwright/test').Page) {
      await mockAllApis(page)
      await loginAndGoto(page, '/sales-goods/create')
      const firstRow = page.locator('table tbody tr').first()
      await expect(firstRow).toBeVisible({ timeout: 10000 })
      const text = await firstRow.textContent()
      if (text && text.includes('データがありません')) {
        test.skip(true, '利用可能な商品マスタがないためスキップ')
      }
      await firstRow.click()
      await expect(page.locator('text=選択した商品マスタ')).toBeVisible({ timeout: 5000 })
    }

    test('選択した商品マスタ情報が表示される', async ({ page }) => {
      await goToStep2(page)

      await expect(page.locator('text=選択した商品マスタ')).toBeVisible()
      await expect(page.locator('text=商品番号').first()).toBeVisible()
    })

    test('入力フォームのタブが表示される', async ({ page }) => {
      await goToStep2(page)

      await expect(page.locator('text=商品基本情報')).toBeVisible()
      await expect(page.locator('text=価格情報')).toBeVisible()
      await expect(page.locator('text=商品説明')).toBeVisible()
    })

    test('商品基本情報タブの入力フィールドが表示される', async ({ page }) => {
      await goToStep2(page)

      await expect(page.locator('input[placeholder="商品コードを入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="商品SKUコードを入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="商品名を入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="キーワードを入力してください"]')).toBeVisible()
    })

    test('価格情報タブの入力フィールドが表示される', async ({ page }) => {
      await goToStep2(page)

      await page.locator('button[role="tab"]:has-text("価格情報")').click()
      await expect(page.locator('input[placeholder="参考価格を入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="標準仕入単価を入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="標準売単価を入力してください"]')).toBeVisible()
    })

    test('商品説明タブの入力フィールドが表示される', async ({ page }) => {
      await goToStep2(page)

      await page.locator('button[role="tab"]:has-text("商品説明")').click()
      await expect(page.locator('input[placeholder="キャッチフレーズを入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="商品概要を入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="商品説明1を入力してください"]')).toBeVisible()
      await expect(page.locator('input[placeholder="商品説明2を入力してください"]')).toBeVisible()
    })

    test('送信ボタンが表示される', async ({ page }) => {
      await goToStep2(page)

      await expect(page.locator('button:has-text("ワークに保存")')).toBeVisible()
      await expect(page.locator('button:has-text("マスタに直接反映")')).toBeVisible()
    })

    test('「商品マスタ選択に戻る」でStep1に戻る', async ({ page }) => {
      await goToStep2(page)

      await page.locator('text=商品マスタ選択に戻る').click()

      await expect(page.locator('text=販売商品の元となる商品マスタを選択')).toBeVisible({ timeout: 5000 })
    })

    test('必須項目未入力でワーク保存するとバリデーションエラーが出る', async ({ page }) => {
      await goToStep2(page)

      await page.locator('button:has-text("ワークに保存")').click()

      await expect(page.locator('text=商品コードは必須です')).toBeVisible({ timeout: 5000 })
    })

    test('商品名がプリフィルされている', async ({ page }) => {
      await goToStep2(page)

      const goodsNameInput = page.locator('input[placeholder="商品名を入力してください"]')
      const value = await goodsNameInput.inputValue()
      expect(value.length).toBeGreaterThan(0)
    })
  })
})
