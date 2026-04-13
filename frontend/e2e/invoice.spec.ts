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

  test('入金日のインライン入力欄が表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    const dateInputs = page.locator('input[type="date"]')
    await expect(dateInputs.first()).toBeVisible()
  })

  test('一括操作バーにチェックボックスとグループ選択が表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('全選択')).toBeVisible()
    await expect(page.getByText('グループ管理')).toBeVisible()
  })

  test('リセットでテーブルが非表示になる', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('いしい記念病院')).toBeVisible()
    await page.getByRole('button', { name: 'リセット' }).click()
    await expect(
      page.getByText('検索条件を入力して「検索」ボタンを押してください'),
    ).toBeVisible()
  })

  test('インポートボタンが表示される', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'インポート' })).toBeVisible()
  })

  test('インポートボタンクリックでDialogが表示される', async ({ page }) => {
    await page.getByRole('button', { name: 'インポート' }).click()
    await expect(page.getByText('請求実績インポート')).toBeVisible()
    await expect(page.getByText('Excelファイル（.xlsx）')).toBeVisible()
    await expect(page.getByRole('button', { name: '取込実行' })).toBeVisible()
  })

  test('ファイルアップロード後に結果が表示される', async ({ page }) => {
    await page.getByRole('button', { name: 'インポート' }).click()

    // Create a mock .xlsx file
    const buffer = Buffer.from('mock xlsx content')
    await page.locator('input[type="file"]').setInputFiles({
      name: '請求実績20260228分.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer,
    })

    await page.getByRole('button', { name: '取込実行' }).click()

    // Wait for result display
    await expect(page.getByText('第1事業部')).toBeVisible()
    await expect(page.getByText('2026/02/末')).toBeVisible()
    await expect(page.getByText('150件', { exact: true })).toBeVisible()
    await expect(page.getByText('34件', { exact: true })).toBeVisible()
  })
})
