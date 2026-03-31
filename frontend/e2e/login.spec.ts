import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:3000'

test.describe('ログイン画面', () => {
  test('ログインページが正しく表示される', async ({ page }) => {
    await page.goto(`${BASE}/login`)

    // タイトルが表示される
    await expect(page.locator('text=OdaMitsu Data Hub')).toBeVisible()

    // ログインフォームの要素が存在する
    await expect(page.locator('#loginId')).toBeVisible()
    await expect(page.locator('#password')).toBeVisible()
    await expect(page.locator('button[type="submit"]')).toBeVisible()
    await expect(page.locator('button[type="submit"]')).toHaveText('ログイン')
  })

  test('空のフォームでsubmitするとHTML5バリデーションが効く', async ({ page }) => {
    await page.goto(`${BASE}/login`)

    // required属性によりsubmitがブロックされる
    await page.locator('button[type="submit"]').click()

    // ページが/loginのまま（リダイレクトしない）
    expect(page.url()).toContain('/login')
  })

  test('不正な認証情報でエラーメッセージが表示される', async ({ page }) => {
    await page.goto(`${BASE}/login`)

    // フォームに入力
    await page.locator('#loginId').fill('invalid_user')
    await page.locator('#password').fill('wrong_password')

    // ログインボタン押下
    await page.locator('button[type="submit"]').click()

    // エラーメッセージが表示される
    await expect(page.locator('text=ログインIDまたはパスワードが正しくありません')).toBeVisible({ timeout: 10000 })

    // ボタンが「ログイン」に戻っている（ローディング解除）
    await expect(page.locator('button[type="submit"]')).toHaveText('ログイン')

    // ページが/loginのまま
    expect(page.url()).toContain('/login')
  })

  test('ルートにアクセスすると/dashboardにリダイレクトされる', async ({ page }) => {
    await page.goto(`${BASE}/`)
    await page.waitForURL('**/dashboard')
    expect(page.url()).toContain('/dashboard')
  })

  test('未認証で/dashboardにアクセスすると/loginにリダイレクトされる', async ({ page }) => {
    // localStorageにトークンがない状態で/dashboardにアクセス
    await page.goto(`${BASE}/dashboard`)
    await page.waitForURL('**/login', { timeout: 10000 })
    expect(page.url()).toContain('/login')
  })
})
