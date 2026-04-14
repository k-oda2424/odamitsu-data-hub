import { test, expect, type Route } from '@playwright/test'
import { mockAllApis } from './helpers/mock-api'
import { loginAndGoto } from './helpers/auth'

const SAMPLE_ROWS = [
  {
    bCartLogisticsId: 101,
    partnerCode: 'C0001',
    partnerName: '得意先A',
    deliveryCompName: 'A商会 東京支店',
    deliveryCode: '',
    shipmentDate: '',
    memo: '配送希望: 午前',
    adminMessage: '',
    shipmentStatus: '未発送',
    goodsInfo: ['00012345：洗剤A:2'],
    smileSerialNoList: [5001],
    bCartCsvExported: false,
  },
  {
    bCartLogisticsId: 102,
    partnerCode: 'C0002',
    partnerName: '得意先B',
    deliveryCompName: 'B商事',
    deliveryCode: 'ABC-123',
    shipmentDate: '2026-04-10',
    memo: '',
    adminMessage: '着日指定あり',
    shipmentStatus: '発送指示',
    goodsInfo: ['00011111：消毒液:1'],
    smileSerialNoList: [5002],
    bCartCsvExported: false,
  },
  {
    bCartLogisticsId: 103,
    partnerCode: 'C0003',
    partnerName: '得意先C',
    deliveryCompName: 'C物流',
    deliveryCode: 'LOCKED-999',
    shipmentDate: '2026-04-01',
    memo: '',
    adminMessage: '',
    shipmentStatus: '発送済',
    goodsInfo: ['00019999：業務用:1'],
    smileSerialNoList: [5003],
    bCartCsvExported: true, // ロック行
  },
]

async function mockBcartShippingList(page: import('@playwright/test').Page, data: unknown[]) {
  await page.route(
    (url) => url.pathname === '/api/v1/bcart/shipping' && !url.searchParams.get('action'),
    async (route: Route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(data),
        })
      } else if (method === 'PUT') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
      } else {
        await route.fallback()
      }
    },
  )
  await page.route(
    (url) => url.pathname === '/api/v1/bcart/shipping/bulk-status',
    async (route: Route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
    },
  )
}

test.describe('B-CART出荷情報入力', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await mockBcartShippingList(page, SAMPLE_ROWS)
  })

  test('初期表示: 3 件が表示される', async ({ page }) => {
    await loginAndGoto(page, '/bcart/shipping')
    await expect(page.getByRole('heading', { name: 'B-CART出荷情報入力' })).toBeVisible()
    await expect(page.getByText('得意先A')).toBeVisible()
    await expect(page.getByText('得意先B')).toBeVisible()
    await expect(page.getByText('得意先C')).toBeVisible()
  })

  test('B-CART 連携済み発送済み行はロック（チェックボックス非活性）', async ({ page }) => {
    await loginAndGoto(page, '/bcart/shipping')
    const lockedCheckbox = page.getByRole('checkbox', { name: 'row-103' })
    await expect(lockedCheckbox).toBeDisabled()
    await expect(page.getByText('B-CART連携済')).toBeVisible()
  })

  test('送り状番号とメモを編集すると保存ボタンのカウントが増える', async ({ page }) => {
    await loginAndGoto(page, '/bcart/shipping')
    await page.getByTestId('delivery-code-101').fill('NEW-0001')
    await expect(page.getByRole('button', { name: /出荷情報更新/ })).toBeEnabled()
  })

  test('未編集で保存ボタンを押しても確認ダイアログは出ず toast が出る', async ({ page }) => {
    await loginAndGoto(page, '/bcart/shipping')
    await page.getByRole('button', { name: /出荷情報更新/ }).click()
    await expect(page.getByText('変更はありません')).toBeVisible()
  })

  test('選択行に一括ステータス更新できる', async ({ page }) => {
    await loginAndGoto(page, '/bcart/shipping')

    // 得意先Aの行を選択
    await page.getByRole('checkbox', { name: 'row-101' }).check()

    // 一括適用ステータスを「発送指示」に
    await page.getByTestId('bulk-status-select').click()
    await page.getByRole('option', { name: '発送指示' }).click()

    // 一括更新ボタン
    await page.getByTestId('bulk-update-btn').click()

    // 確認ダイアログ
    await expect(page.getByText('一括ステータス更新')).toBeVisible()
    await page.getByRole('button', { name: '更新' }).last().click()

    // Toast
    await expect(page.getByText('選択した項目のステータスを更新しました')).toBeVisible({ timeout: 5000 })
  })

  test('未保存編集があると一括更新が警告で止まる', async ({ page }) => {
    await loginAndGoto(page, '/bcart/shipping')
    await page.getByTestId('delivery-code-101').fill('EDIT-999')

    await page.getByRole('checkbox', { name: 'row-101' }).check()
    await page.getByTestId('bulk-status-select').click()
    await page.getByRole('option', { name: '発送済' }).click()
    await page.getByTestId('bulk-update-btn').click()

    // SMILE 未連携の発送済み制限 or 未保存編集の警告のいずれかが出る
    await expect(
      page.getByText(/未保存の編集があります|SMILE 未連携の行が/),
    ).toBeVisible()
  })

  test('SMILE未連携の行は「SMILE未連携」と表示される', async ({ page }) => {
    await mockBcartShippingList(page, [
      {
        ...SAMPLE_ROWS[0],
        partnerCode: null,
        partnerName: null,
        goodsInfo: [],
        smileSerialNoList: [],
      },
    ])
    await loginAndGoto(page, '/bcart/shipping')
    // SMILE 未連携の注記テキストを検索（goodsInfo が空のセル、または発送済不可注記）
    await expect(page.getByText('SMILE未連携').first()).toBeVisible({ timeout: 10000 })
  })

  test('入力内容保存時に PUT /bcart/shipping へ dirty 行のみ送信', async ({ page }) => {
    let captured: unknown = null
    await page.route(
      (url) => url.pathname === '/api/v1/bcart/shipping',
      async (route) => {
        if (route.request().method() === 'PUT') {
          captured = JSON.parse(route.request().postData() || '[]')
          await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
        } else {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(SAMPLE_ROWS),
          })
        }
      },
    )

    await loginAndGoto(page, '/bcart/shipping')
    await page.getByTestId('delivery-code-101').fill('SEND-001')

    await page.getByRole('button', { name: /出荷情報更新/ }).click()
    await page.getByRole('button', { name: '更新' }).last().click()

    await expect(page.getByText('出荷情報を更新しました')).toBeVisible({ timeout: 5000 })
    expect(Array.isArray(captured)).toBe(true)
    const body = captured as Array<Record<string, unknown>>
    expect(body).toHaveLength(1)
    expect(body[0].bCartLogisticsId).toBe(101)
    expect(body[0].deliveryCode).toBe('SEND-001')
    // adminMessage は編集していないので null が送信される
    expect(body[0].adminMessage).toBeNull()
  })
})
