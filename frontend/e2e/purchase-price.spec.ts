import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('仕入価格一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/purchase-prices')
  })

  test('ページが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '仕入価格一覧' })).toBeVisible()
    await expect(page.getByRole('button', { name: '検索' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'リセット' })).toBeVisible()
  })

  test('検索フォームが表示される', async ({ page }) => {
    await expect(page.getByText('商品名')).toBeVisible()
    await expect(page.getByText('商品コード')).toBeVisible()
    await expect(page.getByText('仕入先', { exact: true })).toBeVisible()
  })

  test('検索実行でテーブルが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()

    const firstRow = page.locator('table tbody tr').first()
    await expect(firstRow).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('テスト商品A')).toBeVisible()
    await expect(page.getByText('仕入先A')).toBeVisible()
  })

  test('行クリックで変更予定Dialogが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()

    const firstRow = page.locator('table tbody tr').first()
    await expect(firstRow).toBeVisible({ timeout: 10000 })
    await firstRow.click()

    await expect(page.getByText('仕入価格変更予定入力')).toBeVisible()
    await expect(page.getByText('変更前価格')).toBeVisible()
    await expect(page.getByText('変更後価格')).toBeVisible()
    await expect(page.getByText('変更予定日')).toBeVisible()
    await expect(page.getByText('変更理由')).toBeVisible()
  })

  test('リセットでフォームがクリアされる', async ({ page }) => {
    await page.getByPlaceholder('商品名を入力').fill('テスト')
    await page.getByRole('button', { name: 'リセット' }).click()
    await expect(page.getByPlaceholder('商品名を入力')).toHaveValue('')
  })
})

test.describe('仕入価格変更一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/purchase-prices/changes')
  })

  test('ページが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '仕入価格変更一覧' })).toBeVisible()
    await expect(page.getByRole('button', { name: '一括入力' })).toBeVisible()
  })

  test('検索フォームが表示される', async ({ page }) => {
    await expect(page.getByText('仕入先コード')).toBeVisible()
    await expect(page.getByText('商品コード')).toBeVisible()
    await expect(page.getByText('変更理由')).toBeVisible()
  })

  test('検索実行でテーブルが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()

    const firstRow = page.locator('table tbody tr').first()
    await expect(firstRow).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('テスト商品A')).toBeVisible()
    await expect(page.getByText('仕入先A')).toBeVisible()
  })

  test('反映ステータスBadgeが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()

    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('未反映')).toBeVisible()
    await expect(page.getByText('反映済')).toBeVisible()
  })

  test('一括入力ボタンで遷移する', async ({ page }) => {
    await page.getByRole('button', { name: '一括入力' }).click()
    await page.waitForURL('**/purchase-prices/changes/bulk-input', { timeout: 10000 })
  })
})

test.describe('仕入価格変更一括入力', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/purchase-prices/changes/bulk-input')
  })

  test('ページが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '仕入価格変更一括入力' })).toBeVisible()
    await expect(page.getByText('ヘッダー情報')).toBeVisible()
    await expect(page.getByText('明細入力')).toBeVisible()
  })

  test('ヘッダーフォームが表示される', async ({ page }) => {
    await expect(page.locator('text=仕入先').first()).toBeVisible()
    await expect(page.getByText('変更予定日')).toBeVisible()
    await expect(page.getByText('変更理由')).toBeVisible()
  })

  test('行追加ボタンで明細行が増える', async ({ page }) => {
    const initialRows = await page.locator('table tbody tr').count()
    await page.getByRole('button', { name: '行追加' }).click()
    const afterRows = await page.locator('table tbody tr').count()
    expect(afterRows).toBe(initialRows + 1)
  })

  test('変更一覧に戻るボタンで遷移する', async ({ page }) => {
    await page.getByText('変更一覧に戻る').click()
    await page.waitForURL('**/purchase-prices/changes', { timeout: 10000 })
  })
})

test.describe('サイドバーメニュー', () => {
  test('仕入価格関連メニューが表示される', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/dashboard')

    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await expect(sidebar.getByText('仕入価格一覧')).toBeVisible()
    await expect(sidebar.getByText('仕入価格変更一覧')).toBeVisible()
    await expect(sidebar.getByText('仕入価格変更一括入力')).toBeVisible()
  })
})
