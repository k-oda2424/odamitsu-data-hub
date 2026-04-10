import { test, expect } from '@playwright/test'
import { mockAllApis, MOCK_USERS } from './helpers/mock-api'
import { loginAndGoto } from './helpers/auth'

test.describe('ユーザー管理', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/masters/users')
  })

  test('UM-001: ページタイトルが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'ユーザー管理', level: 1 })).toBeVisible()
  })

  test('UM-003: ユーザー一覧が表示される', async ({ page }) => {
    // テーブル内の各行を確認
    await expect(page.getByRole('row').filter({ hasText: 'テスト管理者' }).filter({ hasText: 'admin' })).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: '一般ユーザー' }).filter({ hasText: 'user1' })).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: 'パートナーユーザー' }).filter({ hasText: 'partner1' })).toBeVisible()
  })

  test('UM-004: 新規登録ボタンが表示される', async ({ page }) => {
    await expect(page.getByRole('button', { name: '新規登録' })).toBeVisible()
  })

  test('UM-020: 新規登録ダイアログが開く', async ({ page }) => {
    await page.getByRole('button', { name: '新規登録' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('dialog').getByText('ユーザー新規登録')).toBeVisible()
  })

  test('UM-021: ユーザーを正常に登録できる', async ({ page }) => {
    await page.getByRole('button', { name: '新規登録' }).click()
    const dialog = page.getByRole('dialog')
    const inputs = dialog.getByRole('textbox')
    await inputs.nth(0).fill('newuser')       // ログインID
    await inputs.nth(1).fill('新規ユーザー')    // ユーザー名
    await inputs.nth(2).fill('pass12345')     // パスワード
    await inputs.nth(3).fill('pass12345')     // パスワード確認
    await dialog.getByRole('button', { name: '登録', exact: true }).click()

    await expect(page.getByText('ユーザーを登録しました')).toBeVisible()
  })

  test('UM-040: 編集ダイアログが開き、既存値が入っている', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: 'テスト管理者' }).filter({ hasText: 'admin' })
    await row.getByRole('button').first().click()

    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText('ユーザー編集')).toBeVisible()
    const inputs = dialog.getByRole('textbox')
    await expect(inputs.nth(0)).toHaveValue('admin')
    await expect(inputs.nth(1)).toHaveValue('テスト管理者')
  })

  test('UM-042: ユーザー名のみ更新できる', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: 'テスト管理者' }).filter({ hasText: 'admin' })
    await row.getByRole('button').first().click()

    const dialog = page.getByRole('dialog')
    await dialog.getByRole('textbox').nth(1).fill('更新ユーザー')
    await dialog.getByRole('button', { name: '更新' }).click()

    await expect(page.getByText('ユーザーを更新しました')).toBeVisible()
  })

  test('UM-060: 削除確認ダイアログが表示される', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: '一般ユーザー' })
    await row.getByRole('button').nth(1).click()

    const dialog = page.getByRole('alertdialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText('ユーザーの削除')).toBeVisible()
  })

  test('UM-061: 削除を実行できる', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: '一般ユーザー' })
    await row.getByRole('button').nth(1).click()

    const dialog = page.getByRole('alertdialog')
    await dialog.getByRole('button', { name: '削除' }).click()
    await expect(page.getByText('ユーザーを削除しました')).toBeVisible()
  })

  test('UM-083: 自分自身の削除ボタンは無効', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: 'テスト管理者' }).filter({ hasText: 'admin' })
    await expect(row.getByRole('button').nth(1)).toBeDisabled()
  })
})
