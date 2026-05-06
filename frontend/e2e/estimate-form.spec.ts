import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis, MOCK_ESTIMATES } from './helpers/mock-api'

// 店舗（admin）→クローバーハウス→日付 を埋める共通ヘルパー
async function fillRequiredHeader(page: Page) {
  // 店舗: "小田光" を選ぶ（admin の場合表示される）
  const shopTrigger = page.getByRole('combobox').first()
  await shopTrigger.click()
  await page.getByRole('option').first().click()

  // 得意先: SearchableSelect
  await page.locator('button:has-text("得意先を選択")').click()
  await page.getByRole('option', { name: /クローバーハウス/ }).click()

  // 見積日・価格改定日（デフォルトは見積日=今日、価格改定日=空）
  const priceChangeDate = page.locator('input[type="date"]').nth(1)
  await priceChangeDate.fill('2026-05-01')
}

async function mockGoodsSearch(page: Page, payload: Record<string, unknown> | null) {
  await page.route(
    (url) => url.pathname === '/api/v1/estimates/goods-search',
    async (route) => {
      if (payload === null) {
        await route.fulfill({ status: 404, contentType: 'application/json', body: '{"message":"not found"}' })
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
      }
    },
  )
}

test.describe('見積フォーム — 新規作成', () => {
  test('F-01: 初期表示 — ヘッダー、必須ラベル、空明細行1つ', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/estimates/create')

    await expect(page.getByRole('heading', { name: '見積作成' })).toBeVisible()
    await expect(page.getByText('店舗', { exact: false }).first()).toBeVisible()
    await expect(page.getByText('得意先', { exact: false }).first()).toBeVisible()
    await expect(page.getByText('見積日', { exact: false }).first()).toBeVisible()
    await expect(page.getByText('価格改定日', { exact: false }).first()).toBeVisible()

    // 明細テーブルヘッダ
    await expect(page.getByRole('columnheader', { name: '商品コード' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '商品名' })).toBeVisible()

    // 初期明細行1つ（行追加ボタンが見える）
    await expect(page.getByRole('button', { name: '行追加' })).toBeVisible()
  })

  test('F-02: 必須項目未入力で保存 → エラートースト', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/estimates/create')

    await page.getByRole('button', { name: /保存/ }).click()
    await expect(page.getByText('店舗を選択してください')).toBeVisible()
  })

  test('F-03: 明細空で保存 → 「有効な明細を1件以上」エラー', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/estimates/create')

    await fillRequiredHeader(page)
    await page.getByRole('button', { name: /保存/ }).click()
    await expect(page.getByText('有効な明細を1件以上入力してください')).toBeVisible()
  })

  test('F-04: 行追加・削除', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/estimates/create')

    // 初期1行
    const rows = page.locator('tbody tr')
    await expect(rows).toHaveCount(1)

    // 追加
    await page.getByRole('button', { name: '行追加' }).click()
    await expect(rows).toHaveCount(2)
    await page.getByRole('button', { name: '行追加' }).click()
    await expect(rows).toHaveCount(3)

    // 削除（1行目のゴミ箱をクリック）
    const deleteButtons = page.locator('tbody tr button').filter({ has: page.locator('svg.lucide-trash2') })
    await deleteButtons.first().click()
    await expect(rows).toHaveCount(2)
  })

  test('F-05: 商品コード入力 → goods-search → 明細自動入力', async ({ page }) => {
    await mockAllApis(page)
    await mockGoodsSearch(page, {
      goodsNo: 1001,
      goodsCode: 'A001',
      goodsName: 'テスト商品A',
      specification: '500ml',
      purchasePrice: 100,
      containNum: 20,
      changeContainNum: null,
      pricePlanInfo: '',
      supplierNo: 1,
      currentSalesPrice: 150,
    })
    await loginAndGoto(page, '/estimates/create')

    await fillRequiredHeader(page)

    // 明細1行目の商品コード欄に入力 → Enter で検索
    const codeInput = page.locator('tbody tr').first().locator('input[placeholder="コード/JAN"]')
    await codeInput.fill('A001')
    await codeInput.press('Enter')

    // 自動入力の結果を確認（商品名は input 内の value）
    const goodsNameInput = page.locator('tbody tr').first().locator('input[placeholder="商品名"]')
    await expect(goodsNameInput).toHaveValue('テスト商品A')
  })

  test('F-06: 商品コード未ヒット → info トースト', async ({ page }) => {
    await mockAllApis(page)
    await mockGoodsSearch(page, null)
    await loginAndGoto(page, '/estimates/create')

    await fillRequiredHeader(page)

    const codeInput = page.locator('tbody tr').first().locator('input[placeholder="コード/JAN"]')
    await codeInput.fill('UNKNOWN')
    await codeInput.press('Enter')

    await expect(page.getByText(/商品コード「UNKNOWN」が見つかりません/)).toBeVisible()
  })

  test('F-07: 正常系保存 → POST /estimates → 詳細画面遷移', async ({ page }) => {
    await mockAllApis(page)
    await mockGoodsSearch(page, {
      goodsNo: 1001,
      goodsCode: 'A001',
      goodsName: 'テスト商品A',
      specification: '',
      purchasePrice: 100,
      containNum: 20,
      changeContainNum: null,
      pricePlanInfo: '',
      supplierNo: 1,
      currentSalesPrice: null,
    })

    let postBody: string | null = null
    await page.route(
      (url) => url.pathname === '/api/v1/estimates' && !url.pathname.endsWith('/compare-goods'),
      async (route) => {
        if (route.request().method() === 'POST') {
          postBody = route.request().postData()
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ ...MOCK_ESTIMATES[1], estimateNo: 9999 }),
          })
        } else {
          await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ESTIMATES) })
        }
      },
    )

    await loginAndGoto(page, '/estimates/create')
    await fillRequiredHeader(page)

    // 明細入力
    const firstRow = page.locator('tbody tr').first()
    await firstRow.locator('input[placeholder="コード/JAN"]').fill('A001')
    await firstRow.locator('input[placeholder="コード/JAN"]').press('Enter')
    await expect(firstRow.locator('input[placeholder="商品名"]')).toHaveValue('テスト商品A')

    // 見積単価入力（admin表示: 原価, 原価改定予定, 見積単価 の順）
    const goodsPriceInput = firstRow.locator('input[type="number"]').nth(1) // 0:原価, 1:見積単価
    await goodsPriceInput.fill('200')

    await page.getByRole('button', { name: /保存/ }).click()

    await expect.poll(() => postBody).not.toBeNull()
    const body = JSON.parse(postBody!)
    expect(body.details).toHaveLength(1)
    expect(body.details[0].goodsPrice).toBe(200)

    // 遷移先
    await expect(page).toHaveURL(/\/estimates\/9999/)
  })
})

test.describe('見積フォーム — 編集', () => {
  test('F-08: 既存データ読込 → 編集 → PUT /estimates/{no}', async ({ page }) => {
    await mockAllApis(page)

    // GET /estimates/570 を details 付きで返す
    const estimateWithDetails = {
      ...MOCK_ESTIMATES[0],
      details: [
        {
          estimateDetailNo: 1,
          goodsNo: 1001,
          goodsCode: 'A001',
          goodsName: 'テスト商品A',
          specification: '',
          purchasePrice: 100,
          pricePlanInfo: '',
          goodsPrice: 200,
          containNum: 20,
          changeContainNum: null,
          profitRate: 50,
          detailNote: '',
          displayOrder: 1,
        },
      ],
    }
    await page.route(
      (url) => url.pathname === '/api/v1/estimates/570',
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(estimateWithDetails) })
        } else {
          await route.fallback()
        }
      },
    )

    let putBody: string | null = null
    await page.route(
      (url) => url.pathname === '/api/v1/estimates/570',
      async (route) => {
        if (route.request().method() === 'PUT') {
          putBody = route.request().postData()
          await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...estimateWithDetails, estimateNo: 570 }) })
        } else {
          await route.fallback()
        }
      },
    )

    await loginAndGoto(page, '/estimates/570/edit')

    // プリフィル: 明細行に既存データ
    const firstRow = page.locator('tbody tr').first()
    await expect(firstRow.locator('input[placeholder="商品名"]')).toHaveValue('テスト商品A')

    // 見積単価を変更
    const goodsPriceInput = firstRow.locator('input[type="number"]').nth(1)
    await goodsPriceInput.fill('300')

    await page.getByRole('button', { name: /保存/ }).click()

    await expect.poll(() => putBody).not.toBeNull()
    const body = JSON.parse(putBody!)
    expect(body.details[0].goodsPrice).toBe(300)

    await expect(page).toHaveURL(/\/estimates\/570$/)
  })

  test('F-09: 編集モード — 保存済み納品先がプリセレクト表示される (シナリオA: destinations APIに含まれる)', async ({ page }) => {
    await mockAllApis(page)
    // partnerNo=200 用の destinations を上書き（mockAllApisより後に登録: LIFOで優先）
    await page.route(
      (url) => url.pathname === '/api/v1/masters/destinations',
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { destinationNo: 1, destinationName: '本社', destinationCode: 'DEST001', partnerNo: 200 },
            { destinationNo: 2, destinationName: '第二倉庫', destinationCode: 'DEST002', partnerNo: 200 },
          ]),
        })
      },
    )
    const estimateWithDest = {
      ...MOCK_ESTIMATES[1],
      destinationNo: 1,
      destinationName: '本社',
      destinationCode: 'DEST001',
      details: [],
    }
    await page.route(
      (url) => url.pathname === '/api/v1/estimates/2341',
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(estimateWithDest) })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/estimates/2341/edit')

    const destButton = page.locator('button:has-text("DEST001 本社")')
    await expect(destButton).toBeVisible()
  })

  test('F-10: 編集モード — destinations API に含まれない納品先 (シナリオB: 削除済み・partner変更) でも fallback option で表示', async ({ page }) => {
    await mockAllApis(page)
    // 999 を含まない destinations を返す（mockAllApisより後）
    await page.route(
      (url) => url.pathname === '/api/v1/masters/destinations',
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { destinationNo: 1, destinationName: '本社', destinationCode: 'DEST001', partnerNo: 200 },
            { destinationNo: 2, destinationName: '第二倉庫', destinationCode: 'DEST002', partnerNo: 200 },
          ]),
        })
      },
    )
    const estimateWithDeletedDest = {
      ...MOCK_ESTIMATES[1],
      destinationNo: 999,
      destinationName: '旧倉庫(削除済)',
      destinationCode: 'DEST999',
      details: [],
    }
    await page.route(
      (url) => url.pathname === '/api/v1/estimates/2341',
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(estimateWithDeletedDest) })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/estimates/2341/edit')

    const destButton = page.locator('button:has-text("DEST999 旧倉庫(削除済)")')
    await expect(destButton).toBeVisible()
  })

  test('F-11: 編集モード — destinationNo=null の見積では fallback 注入されず placeholder 表示', async ({ page }) => {
    await mockAllApis(page)
    const estimateWithoutDest = {
      ...MOCK_ESTIMATES[1],
      destinationNo: null,
      destinationName: null,
      destinationCode: null,
      details: [],
    }
    await page.route(
      (url) => url.pathname === '/api/v1/estimates/2341',
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(estimateWithoutDest) })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/estimates/2341/edit')

    const placeholderButton = page.locator('button:has-text("納品先を選択")')
    await expect(placeholderButton).toBeVisible()
  })
})
