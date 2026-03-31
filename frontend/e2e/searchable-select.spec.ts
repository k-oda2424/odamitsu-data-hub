import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

// Helper: open a SearchableSelect by finding the combobox after a label
async function openCombobox(page: import('@playwright/test').Page, labelText: string) {
  const label = page.getByText(labelText, { exact: true })
  const container = label.locator('..')
  const trigger = container.locator('button[role="combobox"]')
  await trigger.click()
  return trigger
}

test.describe('SearchableSelect コンポーネント共通動作', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/goods')
  })

  test('SS-001: Popoverが開閉する', async ({ page }) => {
    const trigger = await openCombobox(page, 'メーカー')

    // Popover内に検索入力が表示される
    const searchInput = page.locator('[cmdk-input]')
    await expect(searchInput).toBeVisible()

    // 選択肢が表示される
    const items = page.locator('[cmdk-item]')
    await expect(items.first()).toBeVisible()
    const count = await items.count()
    expect(count).toBe(3) // メーカーA, B, C

    // Escapeで閉じる
    await page.keyboard.press('Escape')
    await expect(searchInput).toBeHidden()

    // トリガーにはプレースホルダーが表示されている
    await expect(trigger).toContainText('選択してください')
  })

  test('SS-002: テキスト入力で選択肢がフィルタされる', async ({ page }) => {
    await openCombobox(page, 'メーカー')

    const searchInput = page.locator('[cmdk-input]')
    await searchInput.fill('メーカーA')

    const items = page.locator('[cmdk-item]')
    await expect(items).toHaveCount(1)
    await expect(items.first()).toContainText('メーカーA')
  })

  test('SS-003: 検索結果0件で空メッセージが表示される', async ({ page }) => {
    await openCombobox(page, 'メーカー')

    const searchInput = page.locator('[cmdk-input]')
    await searchInput.fill('存在しないメーカー')

    const empty = page.locator('[cmdk-empty]')
    await expect(empty).toBeVisible()
    await expect(empty).toContainText('見つかりません')
  })

  test('SS-004: 選択肢をクリックすると値がセットされる', async ({ page }) => {
    const trigger = await openCombobox(page, 'メーカー')

    await page.locator('[cmdk-item]').filter({ hasText: 'メーカーB' }).click()

    // Popoverが閉じ、トリガーに選択値が表示される
    await expect(page.locator('[cmdk-input]')).toBeHidden()
    await expect(trigger).toContainText('メーカーB')
  })

  test('SS-006: clearableの場合、クリアボタンで値をリセットできる', async ({ page }) => {
    const trigger = await openCombobox(page, 'メーカー')

    // メーカーAを選択
    await page.locator('[cmdk-item]').filter({ hasText: 'メーカーA' }).click()
    await expect(trigger).toContainText('メーカーA')

    // クリアボタンをクリック
    const clearBtn = trigger.locator('..').locator('button[aria-label="選択をクリア"]')
    await clearBtn.click()

    await expect(trigger).toContainText('選択してください')
  })

  test('SS-008: Escapeで Popover を閉じる', async ({ page }) => {
    await openCombobox(page, 'メーカー')

    await expect(page.locator('[cmdk-input]')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('[cmdk-input]')).toBeHidden()
  })
})

test.describe('商品マスタ一覧 - メーカー検索', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/goods')
  })

  test('INT-013: メーカー検索が SearchableSelect として動作する', async ({ page }) => {
    const trigger = await openCombobox(page, 'メーカー')

    const items = page.locator('[cmdk-item]')
    await expect(items).toHaveCount(3)
    await expect(items.nth(0)).toContainText('メーカーA')
    await expect(items.nth(1)).toContainText('メーカーB')
    await expect(items.nth(2)).toContainText('メーカーC')

    await items.nth(0).click()
    await expect(trigger).toContainText('メーカーA')
  })

  test('REG-003: リセットで SearchableSelect もクリアされる', async ({ page }) => {
    const trigger = await openCombobox(page, 'メーカー')
    await page.locator('[cmdk-item]').filter({ hasText: 'メーカーA' }).click()
    await expect(trigger).toContainText('メーカーA')

    // リセットボタンをクリック
    await page.getByRole('button', { name: 'リセット' }).click()

    await expect(trigger).toContainText('選択してください')
  })
})

test.describe('商品マスタ詳細 - メーカー編集', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/goods')
    // 初期表示で検索しないパターン: 検索ボタンを押してテーブルを表示
    await page.getByRole('button', { name: '検索' }).click()
    const firstRow = page.locator('table tbody tr').first()
    await expect(firstRow).toBeVisible({ timeout: 10000 })
    await firstRow.click()
    await page.waitForURL(/\/goods\/\d+/, { timeout: 10000 })
    await expect(page.locator('text=商品マスタ詳細')).toBeVisible({ timeout: 10000 })
  })

  test('INT-016: 編集モードでメーカーが SearchableSelect になる', async ({ page }) => {
    // 閲覧モードではcomboboxなし
    await expect(page.locator('button[role="combobox"]')).toBeHidden()

    // 編集ボタンクリック
    await page.locator('button:has-text("編集")').click()

    // comboboxが表示される
    const combobox = page.locator('button[role="combobox"]')
    await expect(combobox).toBeVisible()
    await expect(combobox).toContainText('メーカーA')
  })

  test('INT-017: メーカーの初期値が正しく表示される', async ({ page }) => {
    await page.locator('button:has-text("編集")').click()

    const combobox = page.locator('button[role="combobox"]')
    await expect(combobox).toContainText('メーカーA')
  })
})

test.describe('販売商品ワーク一覧 - 仕入先検索', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/sales-goods/work')
  })

  test('INT-008: 仕入先検索が SearchableSelect として動作する', async ({ page }) => {
    const trigger = await openCombobox(page, '仕入先')

    const items = page.locator('[cmdk-item]')
    await expect(items.first()).toBeVisible()
    const count = await items.count()
    expect(count).toBe(2) // 仕入先A, B

    await items.filter({ hasText: '仕入先A' }).click()
    await expect(trigger).toContainText('仕入先A')
  })

  test('REG-001: リセットで仕入先もクリアされる', async ({ page }) => {
    // 仕入先を選択
    const trigger = await openCombobox(page, '仕入先')
    await page.locator('[cmdk-item]').filter({ hasText: '仕入先A' }).click()
    await expect(trigger).toContainText('仕入先A')

    // 商品名にも何か入力
    await page.getByPlaceholder('商品名を入力').fill('テスト商品')

    // リセット
    await page.getByRole('button', { name: 'リセット' }).click()

    await expect(trigger).toContainText('選択してください')
    await expect(page.getByPlaceholder('商品名を入力')).toHaveValue('')
  })
})

test.describe('販売商品マスタ一覧 - 仕入先検索', () => {
  test('INT-011: 仕入先検索が SearchableSelect として動作する', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/sales-goods')

    const trigger = await openCombobox(page, '仕入先')
    const items = page.locator('[cmdk-item]')
    await expect(items.first()).toBeVisible()
    await expect(items).toHaveCount(2)
  })
})

test.describe('販売商品ワーク詳細 - 仕入先編集', () => {
  test('INT-005: 編集モードで仕入先が SearchableSelect になる', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/sales-goods/work')

    // 初期表示で検索しないパターン: 検索ボタンを押してテーブルを表示
    await page.getByRole('button', { name: '検索' }).click()
    const firstRow = page.locator('table tbody tr').first()
    await expect(firstRow).toBeVisible({ timeout: 10000 })
    await firstRow.click()
    await page.waitForURL(/\/sales-goods\/work\/\d+\/\d+/, { timeout: 10000 })
    await expect(page.getByText('販売商品ワーク詳細')).toBeVisible({ timeout: 10000 })

    // 編集ボタンをクリック
    await page.locator('button:has-text("編集")').click()

    // 仕入先 combobox が表示される
    const combobox = page.locator('button[role="combobox"]')
    await expect(combobox).toBeVisible()
    await expect(combobox).toContainText('仕入先A')
  })
})
