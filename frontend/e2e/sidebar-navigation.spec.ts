import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('サイドバーナビゲーション', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/dashboard')
  })

  test('商品・在庫グループにメニュー項目が表示される', async ({ page }) => {
    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await expect(sidebar.getByRole('link', { name: '商品マスタ', exact: true })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: '販売商品マスタ', exact: true })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: '販売商品ワーク', exact: true })).toBeVisible()
  })

  test('「商品マスタ」クリックで /goods に遷移する', async ({ page }) => {
    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await sidebar.getByRole('link', { name: '商品マスタ', exact: true }).click()
    await page.waitForURL('**/goods', { timeout: 10000 })
    expect(page.url()).toContain('/goods')
  })

  test('「販売商品マスタ」クリックで /sales-goods に遷移する', async ({ page }) => {
    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await sidebar.getByRole('link', { name: '販売商品マスタ', exact: true }).click()
    await page.waitForURL('**/sales-goods', { timeout: 10000 })
    expect(page.url()).toContain('/sales-goods')
  })

  test('「販売商品ワーク」クリックで /sales-goods/work に遷移する', async ({ page }) => {
    const sidebar = page.locator('[data-sidebar="sidebar"]')
    await sidebar.getByRole('link', { name: '販売商品ワーク', exact: true }).click()
    await page.waitForURL('**/sales-goods/work', { timeout: 10000 })
    expect(page.url()).toContain('/sales-goods/work')
  })

  test('/goods 配下のサブページでも「商品マスタ」がアクティブになる', async ({ page }) => {
    await page.goto('http://localhost:3000/goods/create')
    await page.waitForLoadState('networkidle')
    const sidebar = page.locator('[data-sidebar="sidebar"]')
    const goodsLink = sidebar.getByRole('link', { name: '商品マスタ', exact: true })

    // data-active は SidebarMenuButton (asChild) 経由で link 要素に設定される
    await expect(goodsLink).toHaveAttribute('data-active', 'true')
  })

  test('/sales-goods/work 配下でも「販売商品ワーク」がアクティブになる', async ({ page }) => {
    await page.goto('http://localhost:3000/sales-goods/work')
    await page.waitForLoadState('networkidle')
    const sidebar = page.locator('[data-sidebar="sidebar"]')
    const workLink = sidebar.getByRole('link', { name: '販売商品ワーク', exact: true })

    await expect(workLink).toHaveAttribute('data-active', 'true')
  })
})
