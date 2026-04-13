import { test, expect } from '@playwright/test'
import { mockAllApis, MOCK_MAKERS, MOCK_WAREHOUSES, MOCK_PARTNERS } from './helpers/mock-api'
import { loginAndGoto } from './helpers/auth'

test.describe('メーカーCRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/masters/makers')
    await expect(page.getByText('メーカーA', { exact: true })).toBeVisible()
  })

  test('MC-001: メーカー一覧が表示される', async ({ page }) => {
    for (const m of MOCK_MAKERS) {
      await expect(page.getByText(m.makerName, { exact: true })).toBeVisible()
    }
  })

  test('MC-002: メーカーを登録できる', async ({ page }) => {
    await page.getByRole('button', { name: /新規登録/ }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('textbox').fill('新メーカー')
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('メーカーを登録しました')).toBeVisible()
  })

  test('MC-003: メーカーを編集できる', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: 'メーカーA' })
    await row.getByRole('button').first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText('メーカー編集')).toBeVisible()
    await dialog.getByRole('textbox').fill('メーカーA改')
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('メーカーを更新しました')).toBeVisible()
  })

  test('MC-004: メーカーを削除できる', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: 'メーカーA' })
    await row.getByRole('button').nth(1).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '削除' }).click()
    await expect(page.getByText('メーカーを削除しました')).toBeVisible()
  })
})

test.describe('倉庫CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/masters/warehouses')
    await expect(page.getByText('本社倉庫')).toBeVisible()
  })

  test('WH-001: 倉庫一覧が表示される', async ({ page }) => {
    for (const w of MOCK_WAREHOUSES) {
      await expect(page.getByText(w.warehouseName)).toBeVisible()
    }
  })

  test('WH-002: 倉庫を登録できる', async ({ page }) => {
    await page.getByRole('button', { name: /新規登録/ }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('textbox').fill('新倉庫')
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('倉庫を登録しました')).toBeVisible()
  })

  test('WH-003: 倉庫を削除できる', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: '本社倉庫' })
    await row.getByRole('button').nth(1).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '削除' }).click()
    await expect(page.getByText('倉庫を削除しました')).toBeVisible()
  })
})

test.describe('仕入先CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/masters/suppliers')
    await page.locator('[role="combobox"]').click()
    await page.locator('[role="option"]').filter({ hasText: '小田光' }).first().click()
    await expect(page.getByText('仕入先A')).toBeVisible()
  })

  test('SP-001: 仕入先一覧が表示される', async ({ page }) => {
    await expect(page.getByText('仕入先A')).toBeVisible()
    await expect(page.getByText('仕入先B')).toBeVisible()
  })

  test('SP-002: 仕入先を登録できる', async ({ page }) => {
    await page.getByRole('button', { name: /新規登録/ }).click()
    const dialog = page.getByRole('dialog')
    const inputs = dialog.getByRole('textbox')
    await inputs.nth(0).fill('SUP999')
    await inputs.nth(1).fill('新仕入先')
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('仕入先を登録しました')).toBeVisible()
  })
})

test.describe('得意先CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/masters/partners')
    await page.locator('[role="combobox"]').click()
    await page.locator('[role="option"]').filter({ hasText: '小田光' }).first().click()
    await expect(page.getByText('いしい記念病院')).toBeVisible()
  })

  test('PT-001: 得意先一覧が表示される', async ({ page }) => {
    for (const p of MOCK_PARTNERS) {
      await expect(page.getByText(p.partnerName)).toBeVisible()
    }
  })

  test('PT-002: 得意先を登録できる', async ({ page }) => {
    await page.getByRole('button', { name: /新規登録/ }).click()
    const dialog = page.getByRole('dialog')
    const inputs = dialog.getByRole('textbox')
    await inputs.nth(0).fill('999999')
    await inputs.nth(1).fill('新得意先')
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('得意先を登録しました')).toBeVisible()
  })

  test('PT-003: 得意先を削除できる', async ({ page }) => {
    const row = page.getByRole('row').filter({ hasText: MOCK_PARTNERS[0].partnerName })
    await row.getByRole('button').nth(1).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '削除' }).click()
    await expect(page.getByText('得意先を削除しました')).toBeVisible()
  })
})
