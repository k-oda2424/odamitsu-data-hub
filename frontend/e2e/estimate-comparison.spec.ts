import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

/** Open a SearchableSelect by label and select an option */
async function selectFromCombobox(page: Page, labelText: string, optionText: string) {
  const label = page.getByText(labelText, { exact: true })
  const container = label.locator('..')
  const trigger = container.locator('button[role="combobox"]')
  await trigger.click()
  await page.getByRole('option', { name: optionText, exact: true }).click()
}

/** Admin (shopNo=0) needs to select a shop before the add button is enabled */
async function selectShop(page: Page) {
  await selectFromCombobox(page, '店舗', '小田光')
}

/** Add a goods via search dialog. Returns after dialog closes. */
async function addGoodsViaDialog(page: Page, rowIndex = 0) {
  await page.getByRole('button', { name: /商品を追加/ }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '検索' }).click()
  await expect(dialog.locator('tbody tr').first()).toBeVisible()
  await dialog.locator('tbody tr').nth(rowIndex).click()
  await expect(dialog).not.toBeVisible()
}

test.describe('比較見積画面', () => {
  test.describe('初期表示', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
    })

    test('ページヘッダーが表示される', async ({ page }) => {
      await expect(page.getByRole('heading', { name: '比較見積' })).toBeVisible()
    })

    test('見積一覧への戻りリンクが表示される', async ({ page }) => {
      await expect(page.getByRole('button', { name: /見積一覧/ })).toBeVisible()
    })

    test('商品追加ボタンが表示される', async ({ page }) => {
      await expect(page.getByRole('button', { name: /商品を追加/ })).toBeVisible()
    })

    test('商品未追加の案内メッセージが表示される', async ({ page }) => {
      await expect(page.getByText('比較商品 (0/10)')).toBeVisible()
    })

    test('admin ユーザーで店舗セレクトが表示される', async ({ page }) => {
      await expect(page.getByText('店舗', { exact: true }).first()).toBeVisible()
    })
  })

  test.describe('商品追加', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
      await selectShop(page)
    })

    test('商品追加ボタンで検索ダイアログが開く', async ({ page }) => {
      await page.getByRole('button', { name: /商品を追加/ }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await expect(page.getByRole('dialog').getByText('商品検索')).toBeVisible()
    })

    test('検索結果から商品を選択して追加できる', async ({ page }) => {
      await addGoodsViaDialog(page)
      await expect(page.getByText('比較商品 (1/10)')).toBeVisible()
    })

    test('最初に追加した商品が基準品になる', async ({ page }) => {
      await addGoodsViaDialog(page)
      await expect(page.getByText('基準品', { exact: true })).toBeVisible()
    })
  })

  test.describe('シミュレーション計算', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
      await selectShop(page)
      await addGoodsViaDialog(page)
    })

    test('販売単価を入力できる', async ({ page }) => {
      const priceInput = page.getByLabel(/販売単価/).first()
      await priceInput.fill('1800')
      await expect(priceInput).toHaveValue('1800')
    })

    test('数量を入力できる', async ({ page }) => {
      const qtyInput = page.getByLabel(/数量$/).first()
      await qtyInput.fill('10')
      await expect(qtyInput).toHaveValue('10')
    })
  })

  test.describe('基準品操作', () => {
    test('基準品を変更できる', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
      await selectShop(page)
      await addGoodsViaDialog(page, 0)
      await addGoodsViaDialog(page, 1)

      // 初期: 1個目=基準品(ボタンなし), 2個目=「基準品にする」ボタン1つ
      await expect(page.getByRole('button', { name: /基準品にする/ })).toHaveCount(1)
      await page.getByRole('button', { name: /基準品にする/ }).first().click()
      // 切替後も「基準品にする」は別の商品（旧基準品）に1つ表示される
      await expect(page.getByRole('button', { name: /基準品にする/ })).toHaveCount(1)
      // 「基準品」テキストも1つだけ
      await expect(page.getByText('基準品', { exact: true })).toHaveCount(1)
    })
  })

  test.describe('見積作成遷移', () => {
    test('見積作成ボタンで遷移し sessionStorage にデータ格納', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
      await selectShop(page)
      await addGoodsViaDialog(page, 0)
      await addGoodsViaDialog(page, 1)

      await page.getByRole('button', { name: /見積作成/ }).click()

      const prefill = await page.evaluate(() => sessionStorage.getItem('estimate-prefill'))
      expect(prefill).toBeTruthy()
      const data = JSON.parse(prefill!)
      expect(data.details).toBeDefined()
      expect(data.details.length).toBeGreaterThan(0)
    })
  })

  test.describe('除外操作', () => {
    test('商品を除外できる', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
      await selectShop(page)
      await addGoodsViaDialog(page)
      await expect(page.getByText('比較商品 (1/10)')).toBeVisible()

      // 削除ボタン（aria-label で堅牢に選択）
      const removeBtn = page.getByRole('button', { name: /を削除$/ }).first()
      await removeBtn.click()
      await expect(page.getByText('比較商品 (0/10)')).toBeVisible()
    })
  })

  test.describe('権限制御', () => {
    test('admin で仕入価格が表示される', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
      await selectShop(page)
      await addGoodsViaDialog(page)
      await expect(page.getByText('仕入価格', { exact: true })).toBeVisible()
    })
  })

  test.describe('印刷機能', () => {
    test('得意先向け表示で仕入情報が非表示になる', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
      await selectShop(page)
      await addGoodsViaDialog(page)

      await expect(page.getByText('仕入価格', { exact: true })).toBeVisible()
      await page.getByText('得意先向け表示', { exact: false }).click()
      await expect(page.getByText('仕入価格', { exact: true })).not.toBeVisible()
    })
  })
})
