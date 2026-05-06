import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

/**
 * MF 連携状況画面 E2E（モック）。
 * admin 権限 (shopNo=0) 前提。接続/未接続の状態、タブ切替、勘定科目同期プレビューを確認。
 */

const CONNECTED_STATUS = {
  configured: true,
  connected: true,
  expiresAt: '2026-05-01T09:00:00Z',
  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read',
  lastRefreshedAt: '2026-04-21T09:00:00Z',
  reAuthRequired: false,
}

const CONFIGURED_NOT_CONNECTED_STATUS = {
  configured: true,
  connected: false,
  expiresAt: null,
  scope: null,
  lastRefreshedAt: null,
  reAuthRequired: false,
}

const UNCONFIGURED_STATUS = {
  configured: false,
  connected: false,
  expiresAt: null,
  scope: null,
  lastRefreshedAt: null,
  reAuthRequired: false,
}

const DEFAULT_CLIENT = {
  id: 1,
  clientId: 'test-client-id',
  clientSecretConfigured: true,
  redirectUri: 'http://localhost:3000/finance/mf-integration/callback',
  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/offices.read mfc/accounting/taxes.read',
  authorizeUrl: 'https://api.biz.moneyforward.com/authorize',
  tokenUrl: 'https://api.biz.moneyforward.com/token',
  apiBaseUrl: 'https://api-accounting.moneyforward.com',
}

const EMPTY_CLIENT = {
  id: null,
  clientId: null,
  clientSecretConfigured: false,
  redirectUri: null,
  scope: null,
  authorizeUrl: null,
  tokenUrl: null,
  apiBaseUrl: null,
}

async function mockStatus(page: Page, status: unknown) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/mf-integration/oauth/status',
    async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(status) })
    },
  )
}

async function mockClient(page: Page, client: unknown) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/mf-integration/oauth/client',
    async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(client) })
      } else {
        await route.fallback()
      }
    },
  )
}

test.describe('MF連携状況画面', () => {
  test('MFI-01: 未設定状態ではフォーム入力を促すバッジが出る', async ({ page }) => {
    await mockAllApis(page)
    await mockStatus(page, UNCONFIGURED_STATUS)
    await mockClient(page, EMPTY_CLIENT)
    await loginAndGoto(page, '/finance/mf-integration')

    await expect(page.getByRole('heading', { name: 'MF 連携状況' })).toBeVisible()
    await expect(page.getByText('未設定', { exact: true })).toBeVisible()
    // 接続ボタンが無効
    await expect(page.getByRole('button', { name: /^接続$/ })).toBeDisabled()
  })

  test('MFI-02: 設定済・未接続では「接続」ボタンが有効', async ({ page }) => {
    await mockAllApis(page)
    await mockStatus(page, CONFIGURED_NOT_CONNECTED_STATUS)
    await mockClient(page, DEFAULT_CLIENT)
    await loginAndGoto(page, '/finance/mf-integration')

    await expect(page.getByText('未接続', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /^接続$/ })).toBeEnabled()
  })

  test('MFI-03: 接続中バッジ表示 + 他タブが有効化される', async ({ page }) => {
    await mockAllApis(page)
    await mockStatus(page, CONNECTED_STATUS)
    await mockClient(page, DEFAULT_CLIENT)
    await loginAndGoto(page, '/finance/mf-integration')

    await expect(page.getByText('接続中', { exact: true })).toBeVisible()
    await expect(page.getByRole('tab', { name: 'enum 翻訳辞書' })).toBeEnabled()
    await expect(page.getByRole('tab', { name: '勘定科目同期' })).toBeEnabled()
  })

  test('MFI-04: enum 翻訳辞書タブに遷移 + 一覧表示', async ({ page }) => {
    await mockAllApis(page)
    await mockStatus(page, CONNECTED_STATUS)
    await mockClient(page, DEFAULT_CLIENT)
    await page.route(
      (url) => url.pathname === '/api/v1/finance/mf-integration/enum-translations',
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([
              { id: 1, enumKind: 'FINANCIAL_STATEMENT', englishCode: 'BALANCE_SHEET', japaneseName: '貸借対照表' },
              { id: 2, enumKind: 'CATEGORY', englishCode: 'CASH_AND_DEPOSITS', japaneseName: '現金及び預金' },
            ]),
          })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/finance/mf-integration')
    await page.getByRole('tab', { name: 'enum 翻訳辞書' }).click()

    await expect(page.getByText('MF 英語 enum → 日本語 翻訳辞書')).toBeVisible()
    // 英語 enum 欄は font-mono、日本語欄は通常の入力。値が入っていることを確認。
    const englishInputs = page.locator('input.font-mono')
    await expect(englishInputs).toHaveCount(2) // FINANCIAL_STATEMENT 1 件 + CATEGORY 1 件
    await expect(englishInputs.nth(0)).toHaveValue('BALANCE_SHEET')
    await expect(englishInputs.nth(1)).toHaveValue('CASH_AND_DEPOSITS')
  })

  test('MFI-05: 勘定科目同期プレビューダイアログに差分件数が出る', async ({ page }) => {
    await mockAllApis(page)
    await mockStatus(page, CONNECTED_STATUS)
    await mockClient(page, DEFAULT_CLIENT)
    await page.route(
      (url) => url.pathname === '/api/v1/finance/mf-integration/account-sync/preview',
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            applied: false,
            insertCount: 3,
            updateCount: 10,
            deleteCount: 2,
            insertSamples: [
              { accountName: '仮想通貨', subAccountName: null, category: '投資その他の資産', taxClassification: '対象外' },
            ],
            updateSamples: [
              { accountName: '現金', subAccountName: null, changes: 'order: 5 → 0' },
            ],
            deleteSamples: [
              { accountName: '廃止勘定', subAccountName: null, category: '仕入債務', taxClassification: '対象外' },
            ],
            unknownEnums: [],
          }),
        })
      },
    )
    await loginAndGoto(page, '/finance/mf-integration')
    await page.getByRole('tab', { name: '勘定科目同期' }).click()
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText('追加 3')).toBeVisible()
    await expect(page.getByText('更新 10')).toBeVisible()
    await expect(page.getByText('削除 2')).toBeVisible()
    await expect(page.getByText('仮想通貨')).toBeVisible()
    await expect(page.getByText(/order: 5 → 0/)).toBeVisible()
    await expect(page.getByRole('button', { name: /^適用$/ })).toBeEnabled()
  })

  test('MFI-06: プレビューで差分ゼロなら「適用」ボタンが無効', async ({ page }) => {
    await mockAllApis(page)
    await mockStatus(page, CONNECTED_STATUS)
    await mockClient(page, DEFAULT_CLIENT)
    await page.route(
      (url) => url.pathname === '/api/v1/finance/mf-integration/account-sync/preview',
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            applied: false,
            insertCount: 0,
            updateCount: 0,
            deleteCount: 0,
            insertSamples: [],
            updateSamples: [],
            deleteSamples: [],
            unknownEnums: [],
          }),
        })
      },
    )
    await loginAndGoto(page, '/finance/mf-integration')
    await page.getByRole('tab', { name: '勘定科目同期' }).click()
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await expect(page.getByText('差分なし')).toBeVisible()
    await expect(page.getByRole('button', { name: /^適用$/ })).toBeDisabled()
  })
})
