import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('発注一覧画面', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/send-orders')
  })

  test('ページヘッダーが表示される', async ({ page }) => {
    await expect(page.getByText('発注一覧', { exact: true }).first()).toBeVisible()
  })

  test('発注入力ボタンが表示される', async ({ page }) => {
    await expect(page.getByRole('button', { name: '発注入力' })).toBeVisible()
  })

  test('初期状態ではテーブルが表示されない', async ({ page }) => {
    await expect(
      page.getByText('検索条件を入力して「検索」ボタンを押してください'),
    ).toBeVisible()
  })

  test('検索実行で結果テーブルが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('テスト商品A')).toBeVisible()
    await expect(page.getByText('テスト商品B')).toBeVisible()
  })

  test('ステータスバッジが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('発注済', { exact: true })).toBeVisible()
    await expect(page.getByText('納期回答', { exact: true })).toBeVisible()
  })

  test('リセットでテーブルが非表示になる', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('テスト商品A')).toBeVisible()
    await page.getByRole('button', { name: 'リセット' }).click()
    await expect(
      page.getByText('検索条件を入力して「検索」ボタンを押してください'),
    ).toBeVisible()
  })

  test('発注入力ボタンで入力画面に遷移する', async ({ page }) => {
    await page.getByRole('button', { name: '発注入力' }).click()
    await page.waitForURL('**/send-orders/create')
  })
})

test.describe('発注入力画面', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/send-orders/create')
  })

  test('ページヘッダーが表示される', async ({ page }) => {
    await expect(page.getByText('発注入力', { exact: true }).first()).toBeVisible()
  })

  test('発注情報フォームが表示される', async ({ page }) => {
    await expect(page.getByText('発注情報', { exact: true })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '倉庫' })).toBeVisible()
    await expect(page.locator('label').filter({ hasText: '仕入先' })).toBeVisible()
  })

  test('仕入先選択前は明細に案内メッセージが表示される', async ({ page }) => {
    await expect(page.getByText('仕入先を選択してください')).toBeVisible()
  })

  test('確認画面へボタンが表示される', async ({ page }) => {
    await expect(page.getByRole('button', { name: '確認画面へ' })).toBeVisible()
  })

  test('行追加ボタンが表示される', async ({ page }) => {
    await expect(page.getByRole('button', { name: '行追加' })).toBeVisible()
  })

  test('発注一覧ボタンで一覧に遷移する', async ({ page }) => {
    await page.getByRole('button', { name: '発注一覧' }).click()
    await page.waitForURL('**/send-orders')
  })

  test('仕入先選択後に商品が選択できる', async ({ page }) => {
    // 仕入先を選択する（SearchableSelect: role="combobox"）
    const supplierCombobox = page.locator('label:has-text("仕入先")').locator('..').getByRole('combobox')
    await supplierCombobox.click()
    await page.getByText('仕入先A', { exact: true }).click()

    // 案内メッセージが消えて明細が表示される
    await expect(page.getByText('仕入先を選択してください')).not.toBeVisible()

    // 商品SearchableSelectをクリック（明細行の最初の商品）
    const goodsCombobox = page.locator('label:has-text("商品")').first().locator('..').getByRole('combobox')
    await goodsCombobox.click()

    // 仕入先Aの商品が表示される
    await expect(page.getByText('MS001 マスタ販売商品A')).toBeVisible()
    await expect(page.getByText('MS002 マスタ販売商品B')).toBeVisible()
  })

  test('商品選択で仕入単価が自動設定される', async ({ page }) => {
    // 仕入先を選択
    const supplierCombobox = page.locator('label:has-text("仕入先")').locator('..').getByRole('combobox')
    await supplierCombobox.click()
    await page.getByText('仕入先A', { exact: true }).click()

    // 商品を選択
    const goodsCombobox = page.locator('label:has-text("商品")').first().locator('..').getByRole('combobox')
    await goodsCombobox.click()
    await page.getByText('MS001 マスタ販売商品A').click()

    // 仕入単価が自動入力される（purchasePrice: 120）
    const priceInput = page.locator('label:has-text("仕入単価")').first().locator('..').getByRole('spinbutton')
    await expect(priceInput).toHaveValue('120')
  })
})

test.describe('サイドバー - 発注メニュー', () => {
  test('発注一覧と発注入力が別メニューとして表示される', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/send-orders')

    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await expect(sidebar.getByRole('link', { name: '発注一覧', exact: true })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: '発注入力', exact: true })).toBeVisible()
  })

  test('発注一覧ページでは発注一覧メニューがアクティブ', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/send-orders')

    const sidebar = page.locator('[data-sidebar="sidebar"]')
    const listLink = sidebar.getByRole('link', { name: '発注一覧', exact: true })
    const createLink = sidebar.getByRole('link', { name: '発注入力', exact: true })

    await expect(listLink).toHaveAttribute('data-active', 'true')
    await expect(createLink).not.toHaveAttribute('data-active', 'true')
  })

  test('発注入力ページでは発注入力メニューがアクティブ', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/send-orders/create')

    const sidebar = page.locator('[data-sidebar="sidebar"]')
    const listLink = sidebar.getByRole('link', { name: '発注一覧', exact: true })
    const createLink = sidebar.getByRole('link', { name: '発注入力', exact: true })

    await expect(createLink).toHaveAttribute('data-active', 'true')
    await expect(listLink).not.toHaveAttribute('data-active', 'true')
  })

  test('発注一覧メニュークリックで一覧ページに遷移する', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/send-orders/create')

    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await sidebar.getByRole('link', { name: '発注一覧', exact: true }).click()
    await page.waitForURL('**/send-orders')
    await expect(page.getByText('発注一覧', { exact: true }).first()).toBeVisible()
  })

  test('発注入力メニュークリックで入力ページに遷移する', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/send-orders')

    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await sidebar.getByRole('link', { name: '発注入力', exact: true }).click()
    await page.waitForURL('**/send-orders/create')
    await expect(page.getByText('発注入力', { exact: true }).first()).toBeVisible()
  })
})
