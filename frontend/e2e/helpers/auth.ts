import { type Page } from '@playwright/test'

const BASE = 'http://localhost:3000'

/**
 * ログインしてダッシュボードまで遷移する。
 * 事前に mockAllApis(page) で API モックをセットアップしておくこと。
 */
export async function login(page: Page) {
  await page.goto(`${BASE}/login`)
  await page.locator('#loginId').fill('admin')
  await page.locator('#password').fill('password')
  await page.locator('button[type="submit"]').click()
  await page.waitForURL('**/dashboard', { timeout: 15000 })
}

/**
 * 認証済み状態で指定URLに遷移する。
 * 事前に mockAllApis(page) で API モックをセットアップしておくこと。
 */
export async function loginAndGoto(page: Page, path: string) {
  await login(page)
  await page.goto(`${BASE}${path}`)
  await page.waitForLoadState('networkidle')
}
