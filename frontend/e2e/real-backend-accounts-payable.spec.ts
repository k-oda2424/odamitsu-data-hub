import { test, expect, type Page } from '@playwright/test'

/**
 * 実バックエンド接続のE2E（モックなし）。
 * 前提: backend が http://localhost:8090, frontend が http://localhost:3000 で起動済み。
 * 認証情報は環境変数で注入:
 *   E2E_ADMIN_ID / E2E_ADMIN_PW （未設定時はテストをスキップ）
 * 実行: `E2E_ADMIN_ID=xxx E2E_ADMIN_PW=yyy npx playwright test real-backend-accounts-payable.spec.ts`
 */

const ADMIN_ID = process.env.E2E_ADMIN_ID
const ADMIN_PW = process.env.E2E_ADMIN_PW

// 対象月: 実行日から前月を算出（例: 2026-04-15 実行 → targetMonth=2026-03, targetDate=20260320）
function computeTargetMonth(today = new Date()) {
  const d = new Date(today.getFullYear(), today.getMonth() - 1, 1)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return { yyyyMm: `${y}-${m}`, yyyyMmDd: `${y}${m}20` }
}

async function realLogin(page: Page) {
  if (!ADMIN_ID || !ADMIN_PW) throw new Error('E2E_ADMIN_ID / E2E_ADMIN_PW 未設定')
  await page.goto('/login')
  await page.locator('#loginId').fill(ADMIN_ID)
  await page.locator('#password').fill(ADMIN_PW)
  await page.locator('button[type="submit"]').click()
  await page.waitForURL('**/dashboard', { timeout: 15000 })
}

test.describe('買掛金一覧 / 再集計 (real backend)', () => {
  // F-W13: CI で silent skip を防ぐため、CI=true かつ env 未設定のときは失敗させる。
  // 開発者のローカル実行では CI 未設定なら従来通り skip で迷惑をかけない。
  if (process.env.CI === 'true' && (!ADMIN_ID || !ADMIN_PW)) {
    test('E2E admin credentials required on CI', () => {
      throw new Error('CI 実行では E2E_ADMIN_ID / E2E_ADMIN_PW の設定が必須です (silent skip 防止)')
    })
    return
  }
  test.skip(!ADMIN_ID || !ADMIN_PW, 'E2E_ADMIN_ID / E2E_ADMIN_PW 未設定のためスキップ (ローカル実行のみ)')

  test('admin で前月分の再集計が成功する', async ({ page }) => {
    const { yyyyMm, yyyyMmDd } = computeTargetMonth()
    await realLogin(page)

    // 再集計バッチ起動のレスポンスを捕捉
    const launchResp = page.waitForResponse((r) =>
      r.url().includes('/api/v1/batch/execute/accountsPayableAggregation') &&
      r.request().method() === 'POST'
    )

    await page.goto('/finance/accounts-payable')

    // 取引月を前月に設定（<input type="month"> は YYYY-MM 形式）
    await page.locator('#ap-month').fill(yyyyMm)

    // 再集計クリック
    await page.getByRole('button', { name: /再集計/ }).click()

    const resp = await launchResp
    expect(resp.status(), 'バッチ起動は 202 Accepted').toBe(202)
    const body = await resp.json()
    expect(body.message).toContain('accountsPayableAggregation')

    // targetDate が前月20日で送信されていることを検証
    const url = new URL(resp.request().url())
    expect(url.searchParams.get('targetDate')).toBe(yyyyMmDd)

    // トースト表示を確認（"ジョブを起動しました" または "accountsPayable" を含む）
    await expect(page.getByText(/起動|accountsPayable/i).first()).toBeVisible({ timeout: 5000 })

    // バッチが終端ステータスに到達するまで expect.poll で最大 20 秒待機
    let lastRaw: unknown = null
    await expect
      .poll(
        async () => {
          const status = await page.evaluate(async () => {
            const token = localStorage.getItem('token')
            const r = await fetch('/api/v1/batch/status/accountsPayableAggregation', {
              headers: token ? { Authorization: `Bearer ${token}` } : {},
            })
            if (!r.ok) return { httpStatus: r.status }
            return await r.json()
          })
          lastRaw = status
          const s = (status as { status?: string })?.status
          return s === 'COMPLETED' || s === 'FAILED' ? s : 'PENDING'
        },
        { timeout: 20_000, intervals: [1000] },
      )
      .toBe('COMPLETED')
    expect((lastRaw as { status?: string })?.status, `バッチ最終ステータス: ${JSON.stringify(lastRaw)}`).toBe('COMPLETED')
  })
})
