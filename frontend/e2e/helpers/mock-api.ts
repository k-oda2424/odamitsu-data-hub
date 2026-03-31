import { type Page, type Route } from '@playwright/test'

// ==================== Mock Data ====================

export const MOCK_TOKEN = 'mock-jwt-token-for-e2e-testing'

export const MOCK_USER = {
  loginUserNo: 1,
  userName: 'テスト管理者',
  loginId: 'admin',
  companyNo: 1,
  companyType: 'ADMIN',
  shopNo: 1,
}

export const MOCK_MAKERS = [
  { makerNo: 1, makerName: 'メーカーA' },
  { makerNo: 2, makerName: 'メーカーB' },
  { makerNo: 3, makerName: 'メーカーC' },
]

export const MOCK_SUPPLIERS = [
  { supplierNo: 1, supplierName: '仕入先A' },
  { supplierNo: 2, supplierName: '仕入先B' },
]

export const MOCK_GOODS_LIST = [
  {
    goodsNo: 1,
    goodsName: 'テスト商品A',
    janCode: '4901234567890',
    makerNo: 1,
    makerName: 'メーカーA',
    keyword: 'テスト',
    specification: '500ml',
    caseContainNum: 24,
    applyReducedTaxRate: false,
    discontinuedFlg: '0',
  },
  {
    goodsNo: 2,
    goodsName: 'テスト商品B',
    janCode: '4901234567891',
    makerNo: 2,
    makerName: 'メーカーB',
    keyword: 'サンプル',
    specification: '1L',
    caseContainNum: 12,
    applyReducedTaxRate: true,
    discontinuedFlg: '0',
  },
]

export const MOCK_GOODS_DETAIL = {
  goodsNo: 1,
  goodsName: 'テスト商品A',
  janCode: '4901234567890',
  makerNo: 1,
  makerName: 'メーカーA',
  keyword: 'テスト',
  specification: '500ml',
  caseContainNum: 24,
  applyReducedTaxRate: false,
  discontinuedFlg: '0',
  salesGoodsList: [
    {
      shopNo: 1,
      goodsNo: 1,
      goodsCode: 'SG001',
      goodsName: '販売テスト商品A',
      supplierNo: 1,
      supplierName: '仕入先A',
      purchasePrice: 100,
      goodsPrice: 150,
      isWork: false,
    },
    {
      shopNo: 1,
      goodsNo: 2,
      goodsCode: 'SG002',
      goodsName: '販売テスト商品B(ワーク)',
      supplierNo: 2,
      supplierName: '仕入先B',
      purchasePrice: 200,
      goodsPrice: 300,
      isWork: true,
    },
  ],
}

export const MOCK_SALES_GOODS_WORK_LIST = [
  {
    shopNo: 1,
    goodsNo: 1,
    goodsCode: 'WK001',
    goodsSkuCode: 'SKU-WK001',
    goodsName: 'ワーク販売商品A',
    supplierNo: 1,
    supplierName: '仕入先A',
    purchasePrice: 100,
    goodsPrice: 150,
    keyword: 'テスト',
    isWork: true,
  },
  {
    shopNo: 1,
    goodsNo: 2,
    goodsCode: 'WK002',
    goodsSkuCode: 'SKU-WK002',
    goodsName: 'ワーク販売商品B',
    supplierNo: 2,
    supplierName: '仕入先B',
    purchasePrice: 200,
    goodsPrice: 300,
    keyword: 'サンプル',
    isWork: true,
  },
]

export const MOCK_SALES_GOODS_MASTER_LIST = [
  {
    shopNo: 1,
    goodsNo: 3,
    goodsCode: 'MS001',
    goodsSkuCode: 'SKU-MS001',
    goodsName: 'マスタ販売商品A',
    supplierNo: 1,
    supplierName: '仕入先A',
    purchasePrice: 120,
    goodsPrice: 180,
    keyword: 'マスタ',
    isWork: false,
  },
]

export const MOCK_SALES_GOODS_WORK_DETAIL = {
  shopNo: 1,
  goodsNo: 1,
  goodsCode: 'WK001',
  goodsSkuCode: 'SKU-WK001',
  goodsName: 'ワーク販売商品A',
  keyword: 'テスト',
  supplierNo: 1,
  supplierName: '仕入先A',
  referencePrice: 120,
  purchasePrice: 100,
  goodsPrice: 150,
  catchphrase: 'テストキャッチフレーズ',
  goodsIntroduction: 'テスト商品概要',
  goodsDescription1: 'テスト商品説明1',
  goodsDescription2: 'テスト商品説明2',
  janCode: '4901234567890',
  makerName: 'メーカーA',
  isWork: true,
}

export const MOCK_SALES_GOODS_MASTER_DETAIL = {
  shopNo: 1,
  goodsNo: 3,
  goodsCode: 'MS001',
  goodsSkuCode: 'SKU-MS001',
  goodsName: 'マスタ販売商品A',
  keyword: 'マスタ',
  supplierNo: 1,
  supplierName: '仕入先A',
  referencePrice: 150,
  purchasePrice: 120,
  goodsPrice: 180,
  catchphrase: 'マスタキャッチフレーズ',
  goodsIntroduction: 'マスタ商品概要',
  goodsDescription1: 'マスタ商品説明1',
  goodsDescription2: 'マスタ商品説明2',
  janCode: '4901234567891',
  makerName: 'メーカーB',
  isWork: false,
}

export const MOCK_SHOPS = [
  { shopNo: 1, shopName: '小田光' },
  { shopNo: 2, shopName: '小田光 第2事業部' },
]

export const MOCK_ESTIMATES = [
  {
    estimateNo: 570,
    shopNo: 1,
    partnerNo: 108,
    partnerCode: '022000',
    partnerName: 'いしい記念病院',
    destinationNo: 0,
    estimateDate: '2022-10-26',
    priceChangeDate: '2022-11-21',
    estimateStatus: '20',
    note: '',
  },
  {
    estimateNo: 2341,
    shopNo: 1,
    partnerNo: 200,
    partnerCode: '148013',
    partnerName: 'クローバーハウス',
    destinationNo: 0,
    estimateDate: '2024-01-08',
    priceChangeDate: '2024-02-01',
    estimateStatus: '00',
    note: 'テスト備考',
  },
]

export const MOCK_PARTNERS = [
  { partnerNo: 108, partnerName: 'いしい記念病院', partnerCode: '022000', shopNo: 1, companyNo: 5 },
  { partnerNo: 200, partnerName: 'クローバーハウス', partnerCode: '148013', shopNo: 1, companyNo: 10 },
]

export const MOCK_DESTINATIONS = [
  { destinationNo: 1, destinationName: '本社', destinationCode: 'DEST001', partnerNo: 108 },
  { destinationNo: 2, destinationName: '第二倉庫', destinationCode: 'DEST002', partnerNo: 108 },
]

export const MOCK_PARTNER_GOODS_LIST = [
  {
    partnerNo: 108,
    goodsNo: 1,
    destinationNo: 1,
    shopNo: 1,
    companyNo: 5,
    shopName: '小田光',
    companyName: 'いしい記念病院',
    goodsCode: 'PG001',
    goodsName: '得意先商品A',
    goodsPrice: 1500,
    keyword: 'テスト',
    reflectedEstimateNo: 570,
    lastSalesDate: '2025-12-01',
    lastPriceUpdateDate: '2025-11-15',
  },
  {
    partnerNo: 200,
    goodsNo: 2,
    destinationNo: 1,
    shopNo: 1,
    companyNo: 10,
    shopName: '小田光',
    companyName: 'クローバーハウス',
    goodsCode: 'PG002',
    goodsName: '得意先商品B',
    goodsPrice: 2000,
    keyword: 'サンプル',
    reflectedEstimateNo: null,
    lastSalesDate: '2025-10-15',
    lastPriceUpdateDate: null,
  },
]

export const MOCK_PARTNER_GOODS_DETAIL = {
  ...MOCK_PARTNER_GOODS_LIST[0],
  orderHistory: [
    {
      orderDateTime: '2025-11-15T10:30:00',
      goodsCode: 'PG001',
      goodsName: '得意先商品A',
      goodsPrice: 1500,
      goodsNum: 10,
    },
    {
      orderDateTime: '2025-10-01T09:00:00',
      goodsCode: 'PG001',
      goodsName: '得意先商品A',
      goodsPrice: 1500,
      goodsNum: -3,
    },
  ],
}

export const MOCK_ORDER_DETAILS = [
  {
    orderNo: 1001, orderDetailNo: 1, shopNo: 1,
    companyName: 'いしい記念病院', partnerCode: '022000', partnerNo: 108,
    orderDetailStatus: '00', orderDateTime: '2026-03-15T10:30:00',
    goodsCode: 'SG001', goodsName: 'テスト商品A', goodsPrice: 1500,
    orderNum: 10, cancelNum: 0, returnNum: 0,
    unitContainNum: 24, unitNum: 1, subtotal: 15000,
    slipNo: 'SLIP-001', slipDate: '2026-03-16', deliveryNo: 1,
  },
  {
    orderNo: 1001, orderDetailNo: 2, shopNo: 1,
    companyName: 'いしい記念病院', partnerCode: '022000', partnerNo: 108,
    orderDetailStatus: '20', orderDateTime: '2026-03-15T10:30:00',
    goodsCode: 'SG002', goodsName: 'テスト商品B', goodsPrice: 800,
    orderNum: 5, cancelNum: 0, returnNum: 0,
    unitContainNum: 12, unitNum: 1, subtotal: 4000,
    slipNo: 'SLIP-001', slipDate: '2026-03-16', deliveryNo: 1,
  },
]

export const MOCK_PURCHASE_PRICES = [
  {
    purchasePriceNo: 1,
    goodsNo: 1,
    goodsCode: 'SG001',
    goodsName: 'テスト商品A',
    makerName: 'メーカーA',
    supplierNo: 1,
    supplierName: '仕入先A',
    supplierCode: 'SUP001',
    shopNo: 1,
    partnerNo: 0,
    destinationNo: 0,
    goodsPrice: 100,
    includeTaxGoodsPrice: 110,
    taxRate: 10,
    taxCategory: 0,
    lastPurchaseDate: '2026-03-01',
  },
  {
    purchasePriceNo: 2,
    goodsNo: 2,
    goodsCode: 'SG002',
    goodsName: 'テスト商品B',
    makerName: 'メーカーB',
    supplierNo: 2,
    supplierName: '仕入先B',
    supplierCode: 'SUP002',
    shopNo: 1,
    partnerNo: 0,
    destinationNo: 0,
    goodsPrice: 200,
    includeTaxGoodsPrice: 220,
    taxRate: 10,
    taxCategory: 0,
    lastPurchaseDate: '2026-03-15',
  },
]

export const MOCK_PURCHASE_PRICE_CHANGES = [
  {
    purchasePriceChangePlanNo: 1,
    shopNo: 1,
    goodsCode: 'SG001',
    goodsName: 'テスト商品A',
    janCode: '4901234567890',
    supplierCode: 'SUP001',
    supplierName: '仕入先A',
    beforePrice: 100,
    afterPrice: 120,
    changePlanDate: '2026-04-01',
    changeReason: 'PU',
    changeContainNum: null,
    partnerNo: 0,
    destinationNo: 0,
    partnerPriceChangePlanCreated: false,
    purchasePriceReflect: false,
  },
  {
    purchasePriceChangePlanNo: 2,
    shopNo: 1,
    goodsCode: 'SG002',
    goodsName: 'テスト商品B',
    janCode: '4901234567891',
    supplierCode: 'SUP002',
    supplierName: '仕入先B',
    beforePrice: 200,
    afterPrice: 180,
    changePlanDate: '2026-03-15',
    changeReason: 'PD',
    changeContainNum: null,
    partnerNo: 0,
    destinationNo: 0,
    partnerPriceChangePlanCreated: true,
    purchasePriceReflect: true,
  },
]

// ==================== Helpers ====================

async function json(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data),
  })
}

// ==================== Mock Setup ====================

/**
 * 全APIをモックする。
 * テスト用のbeforeEachで loginAndGoto の前に呼び出すこと。
 *
 * Playwright の page.route() はブラウザレベルでリクエストを捕捉するため、
 * Next.js の rewrite（/api/* → localhost:8090）を経由せずレスポンスを返せる。
 */
export async function mockAllApis(page: Page) {
  // ---- Catch-all fallback（最初に登録 = LIFO で最後にチェック） ----
  await page.route(
    (url) => url.pathname.startsWith('/api/v1/'),
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, [])
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await json(route, {})
      }
    },
  )

  // ---- Auth ----
  await page.route(
    (url) => url.pathname === '/api/v1/auth/login',
    async (route) => {
      if (route.request().method() !== 'POST') {
        await route.fallback()
        return
      }
      const body = JSON.parse(route.request().postData() || '{}')
      if (body.loginId && body.password) {
        await json(route, { token: MOCK_TOKEN, user: MOCK_USER })
      } else {
        await json(route, { message: 'Unauthorized' }, 401)
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/auth/me',
    async (route) => {
      await json(route, MOCK_USER)
    },
  )

  // ---- Masters ----
  await page.route(
    (url) => url.pathname === '/api/v1/masters/makers',
    async (route) => {
      await json(route, MOCK_MAKERS)
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/masters/suppliers',
    async (route) => {
      await json(route, MOCK_SUPPLIERS)
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/masters/shops',
    async (route) => {
      await json(route, MOCK_SHOPS)
    },
  )

  // ---- Goods ----
  await page.route(
    (url) => url.pathname === '/api/v1/goods/available-for-sales',
    async (route) => {
      await json(route, MOCK_GOODS_LIST)
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/goods\/\d+\/detail$/.test(url.pathname),
    async (route) => {
      await json(route, MOCK_GOODS_DETAIL)
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/goods\/\d+\/sales-goods$/.test(url.pathname),
    async (route) => {
      if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { shopNo: 1, goodsNo: 1, ...body })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/goods\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_GOODS_LIST[0])
      } else if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...MOCK_GOODS_LIST[0], ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/goods',
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_GOODS_LIST)
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { goodsNo: 99, ...body })
      } else {
        await route.fallback()
      }
    },
  )

  // ---- Sales Goods ----
  await page.route(
    (url) => /^\/api\/v1\/sales-goods\/work\/\d+\/\d+\/reflect$/.test(url.pathname),
    async (route) => {
      await json(route, MOCK_SALES_GOODS_MASTER_DETAIL)
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/sales-goods\/work\/\d+\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_SALES_GOODS_WORK_DETAIL)
      } else if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...MOCK_SALES_GOODS_WORK_DETAIL, ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/sales-goods\/master\/\d+\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_SALES_GOODS_MASTER_DETAIL)
      } else if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...MOCK_SALES_GOODS_MASTER_DETAIL, ...body })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/sales-goods/work',
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_SALES_GOODS_WORK_LIST)
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...MOCK_SALES_GOODS_WORK_DETAIL, ...body })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/sales-goods/master',
    async (route) => {
      await json(route, MOCK_SALES_GOODS_MASTER_LIST)
    },
  )

  // ---- Partners & Destinations ----
  await page.route(
    (url) => url.pathname === '/api/v1/masters/partners',
    async (route) => {
      await json(route, MOCK_PARTNERS)
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/masters/destinations',
    async (route) => {
      await json(route, MOCK_DESTINATIONS)
    },
  )

  // ---- Partner Goods ----
  await page.route(
    (url) => /^\/api\/v1\/partner-goods\/\d+\/\d+\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_PARTNER_GOODS_DETAIL)
      } else if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...MOCK_PARTNER_GOODS_LIST[0], ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/partner-goods',
    async (route) => {
      if (route.request().method() === 'GET') {
        await json(route, MOCK_PARTNER_GOODS_LIST)
      } else {
        await route.fallback()
      }
    },
  )

  // ---- Orders ----
  await page.route(
    (url) => url.pathname === '/api/v1/orders/details',
    async (route) => {
      await json(route, MOCK_ORDER_DETAILS)
    },
  )

  // ---- Purchase Prices ----
  await page.route(
    (url) => url.pathname === '/api/v1/purchase-prices',
    async (route) => {
      await json(route, MOCK_PURCHASE_PRICES)
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/purchase-price-changes/bulk',
    async (route) => {
      if (route.request().method() === 'POST') {
        await json(route, [], 201)
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/purchase-price-changes',
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_PURCHASE_PRICE_CHANGES)
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { purchasePriceChangePlanNo: 99, ...body }, 201)
      } else {
        await route.fallback()
      }
    },
  )

  // ---- Estimates ----
  await page.route(
    (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
    async (route) => {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { ...MOCK_ESTIMATES[0], ...body })
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/estimates\/\d+$/.test(url.pathname),
    async (route) => {
      await json(route, MOCK_ESTIMATES[0])
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/estimates',
    async (route) => {
      await json(route, MOCK_ESTIMATES)
    },
  )
}
