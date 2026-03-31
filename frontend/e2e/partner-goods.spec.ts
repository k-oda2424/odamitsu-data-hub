import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('得意先商品マスタ一覧画面', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/partner-goods')
  })

  test('ページヘッダーが表示される', async ({ page }) => {
    await expect(page.getByText('得意先商品マスタ')).toBeVisible()
  })

  test('検索フォームが表示される', async ({ page }) => {
    await expect(page.getByText('得意先', { exact: true })).toBeVisible()
    await expect(page.getByText('商品名', { exact: true })).toBeVisible()
    await expect(page.getByText('商品コード', { exact: true })).toBeVisible()
    await expect(page.getByText('キーワード', { exact: true })).toBeVisible()
  })

  test('初期状態ではテーブルが表示されない', async ({ page }) => {
    await expect(
      page.getByText('検索条件を入力して「検索」ボタンを押してください'),
    ).toBeVisible()
  })

  test('検索実行で結果テーブルが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('得意先商品A')).toBeVisible()
    await expect(page.getByText('得意先商品B')).toBeVisible()
  })

  test('検索結果に正しいカラムが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('いしい記念病院')).toBeVisible()
    await expect(page.getByText('PG001')).toBeVisible()
    await expect(page.getByText('1,500')).toBeVisible()
    await expect(page.getByText('2025-12-01')).toBeVisible()
  })

  test('見積リンクが表示される', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('見積明細')).toBeVisible()
  })

  test('リセットボタンで検索結果がクリアされる', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await expect(page.getByText('得意先商品A')).toBeVisible()

    await page.getByRole('button', { name: 'リセット' }).click()
    await expect(
      page.getByText('検索条件を入力して「検索」ボタンを押してください'),
    ).toBeVisible()
  })

  test('行クリックで詳細画面に遷移する', async ({ page }) => {
    await page.getByRole('button', { name: '検索' }).click()
    await page.getByText('得意先商品A').click()
    await page.waitForURL('**/partner-goods/108/1/1')
  })
})

test.describe('得意先商品詳細画面', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/partner-goods/108/1/1')
  })

  test('ページヘッダーが表示される', async ({ page }) => {
    await expect(page.getByText('得意先商品詳細')).toBeVisible()
  })

  test('得意先商品情報が表示される', async ({ page }) => {
    await expect(page.getByText('いしい記念病院')).toBeVisible()
    // 商品コードは情報欄と注文履歴テーブルの両方に表示される
    await expect(page.locator('.grid').getByText('PG001')).toBeVisible()
    await expect(page.getByText('得意先商品A').first()).toBeVisible()
    await expect(page.getByText('1,500').first()).toBeVisible()
  })

  test('注文履歴が表示される', async ({ page }) => {
    await expect(page.getByText('注文履歴')).toBeVisible()
    // 注文: 数量10
    await expect(page.getByText('10', { exact: true })).toBeVisible()
    // 返品: 数量-3（赤文字表示）
    await expect(page.getByText('-3', { exact: true })).toBeVisible()
  })

  test('編集ボタンで編集モードに切り替わる', async ({ page }) => {
    await page.getByRole('button', { name: '編集' }).click()
    await expect(page.getByRole('button', { name: /保存/ })).toBeVisible()
    await expect(page.getByRole('button', { name: 'キャンセル' })).toBeVisible()
  })

  test('キャンセルで編集モードを終了する', async ({ page }) => {
    await page.getByRole('button', { name: '編集' }).click()
    await page.getByRole('button', { name: 'キャンセル' }).click()
    await expect(page.getByRole('button', { name: '編集' })).toBeVisible()
  })

  test('保存で更新が成功する', async ({ page }) => {
    await page.getByRole('button', { name: '編集' }).click()

    const priceInput = page.locator('input[type="number"]')
    await priceInput.clear()
    await priceInput.fill('1600')

    await page.getByRole('button', { name: /保存/ }).click()
    await expect(page.getByText('得意先商品を更新しました')).toBeVisible()
  })

  test('一覧に戻るボタンで一覧画面に遷移する', async ({ page }) => {
    await page.getByRole('button', { name: '一覧に戻る' }).click()
    await page.waitForURL('**/partner-goods')
  })
})
