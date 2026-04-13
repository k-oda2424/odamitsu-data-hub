import { type Page, type Route } from '@playwright/test'

// ==================== Mock Data ====================

export const MOCK_TOKEN = 'mock-jwt-token-for-e2e-testing'

/**
 * E2E テスト用のデフォルトユーザー（admin 権限）。
 * shopNo=0 は admin (全店舗アクセス可) を意味する。
 * 非 admin ユーザーをテストする場合は、テストごとに `/api/v1/auth/me` を route.fulfill で
 * 上書きして shopNo を 1 などの実店舗番号に差し替えること。
 */
export const MOCK_USER = {
  loginUserNo: 1,
  userName: 'テスト管理者',
  loginId: 'admin',
  companyNo: 1,
  companyType: 'ADMIN',
  shopNo: 0,
}

export const MOCK_USERS = [
  { loginUserNo: 1, loginId: 'admin', userName: 'テスト管理者', companyNo: 1, companyType: 'ADMIN', addDateTime: '2024-01-01T00:00:00' },
  { loginUserNo: 2, loginId: 'user1', userName: '一般ユーザー', companyNo: 1, companyType: 'SHOP', addDateTime: '2024-06-01T00:00:00' },
  { loginUserNo: 3, loginId: 'partner1', userName: 'パートナーユーザー', companyNo: 2, companyType: 'PARTNER', addDateTime: '2025-01-15T00:00:00' },
]

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
  {
    shopNo: 1,
    goodsNo: 4,
    goodsCode: 'MS002',
    goodsSkuCode: 'SKU-MS002',
    goodsName: 'マスタ販売商品B',
    supplierNo: 1,
    supplierName: '仕入先A',
    purchasePrice: 250,
    goodsPrice: 350,
    keyword: 'マスタ',
    isWork: false,
  },
  {
    shopNo: 1,
    goodsNo: 5,
    goodsCode: 'MS003',
    goodsSkuCode: 'SKU-MS003',
    goodsName: 'マスタ販売商品C',
    supplierNo: 2,
    supplierName: '仕入先B',
    purchasePrice: 300,
    goodsPrice: 450,
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

export const MOCK_QUOTE_IMPORT_HEADERS = [
  {
    quoteImportId: 1,
    shopNo: 1,
    supplierName: '花王',
    supplierCode: null,
    supplierNo: null,
    fileName: '小田光様_26年5月価格改定御見積書.pdf',
    quoteDate: '2026-01-30',
    effectiveDate: '2026-05-01',
    changeReason: 'PU',
    priceType: '税抜',
    totalCount: 3,
    remainingCount: 3,
    addDateTime: '2026-04-01T10:00:00',
  },
]

export const MOCK_QUOTE_IMPORT_DETAILS = [
  {
    quoteImportDetailId: 1,
    rowNo: 1,
    janCode: '4901301508034',
    quoteGoodsName: 'クリーン&クリーンF1 ボトル',
    quoteGoodsCode: null,
    specification: '700mL',
    quantityPerCase: 6,
    oldPrice: 661,
    newPrice: 726,
    oldBoxPrice: 3966,
    newBoxPrice: 4356,
  },
  {
    quoteImportDetailId: 2,
    rowNo: 3,
    janCode: null,
    quoteGoodsName: 'マジックリン 除菌プラス 業務用',
    quoteGoodsCode: null,
    specification: '5Kg',
    quantityPerCase: 3,
    oldPrice: 2976,
    newPrice: 3274,
    oldBoxPrice: 5952,
    newBoxPrice: 6548,
  },
  {
    quoteImportDetailId: 3,
    rowNo: 5,
    janCode: null,
    quoteGoodsName: 'ハンドスキッシュEX スプレー',
    quoteGoodsCode: null,
    specification: '150mL',
    quantityPerCase: 24,
    oldPrice: 362,
    newPrice: 398,
    oldBoxPrice: 8688,
    newBoxPrice: 9552,
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

export const MOCK_WAREHOUSES = [
  { warehouseNo: 1, warehouseName: '本社倉庫', shopNo: 1 },
  { warehouseNo: 2, warehouseName: '第二倉庫', shopNo: 1 },
  { warehouseNo: 3, warehouseName: '第2事業部倉庫', shopNo: 2 },
]

export const MOCK_SEND_ORDER_DETAILS = [
  {
    sendOrderNo: 5001,
    sendOrderDetailNo: 1,
    shopNo: 1,
    warehouseNo: 1,
    warehouseName: '本社倉庫',
    supplierNo: 1,
    supplierName: '仕入先A',
    sendOrderDateTime: '2026-03-20T10:00:00',
    desiredDeliveryDate: '2026-03-25',
    goodsNo: 1,
    goodsCode: 'SG001',
    goodsName: 'テスト商品A',
    goodsPrice: 100,
    sendOrderNum: 10,
    sendOrderCaseNum: null,
    containNum: null,
    subtotal: 1000,
    arrivePlanDate: null,
    arrivedDate: null,
    arrivedNum: null,
    sendOrderDetailStatus: '00',
  },
  {
    sendOrderNo: 5001,
    sendOrderDetailNo: 2,
    shopNo: 1,
    warehouseNo: 1,
    warehouseName: '本社倉庫',
    supplierNo: 1,
    supplierName: '仕入先A',
    sendOrderDateTime: '2026-03-20T10:00:00',
    desiredDeliveryDate: '2026-03-25',
    goodsNo: 2,
    goodsCode: 'SG002',
    goodsName: 'テスト商品B',
    goodsPrice: 200,
    sendOrderNum: 5,
    sendOrderCaseNum: null,
    containNum: null,
    subtotal: 1000,
    arrivePlanDate: '2026-03-28',
    arrivedDate: null,
    arrivedNum: null,
    sendOrderDetailStatus: '10',
  },
]

export const MOCK_SEND_ORDER_CREATED = {
  sendOrderNo: 5002,
  shopNo: 1,
  warehouseNo: 1,
  supplierNo: 1,
  supplierName: '仕入先A',
  warehouseName: '本社倉庫',
  sendOrderDateTime: '2026-04-01T10:00:00',
  desiredDeliveryDate: '2026-04-05',
  sendOrderStatus: '00',
  details: [],
}

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

export const MOCK_COMPARE_GOODS = [
  {
    goodsNo: 1,
    goodsCode: 'KAO-001',
    goodsName: '花王 除菌洗浄剤',
    specification: '5L',
    janCode: '4901234567890',
    makerName: 'メーカーA',
    supplierName: '仕入先A',
    supplierNo: 1,
    purchasePrice: 1200,
    nowGoodsPrice: 1800,
    containNum: 3,
    changeContainNum: null,
    pricePlanInfo: '2026-05-01より1200→1300',
    planAfterPrice: 1300,
  },
  {
    goodsNo: 2,
    goodsCode: 'LION-001',
    goodsName: 'ライオン 除菌洗浄剤',
    specification: '4.5L',
    janCode: '4901234567891',
    makerName: 'メーカーB',
    supplierName: '仕入先B',
    supplierNo: 2,
    purchasePrice: 1050,
    nowGoodsPrice: 1700,
    containNum: 3,
    changeContainNum: null,
    pricePlanInfo: null,
    planAfterPrice: null,
  },
]

// ==================== Estimate Comparisons ====================

export const MOCK_ESTIMATE_COMPARISONS = [
  {
    comparisonNo: 1,
    shopNo: 1,
    partnerNo: 108,
    partnerName: 'いしい記念病院',
    destinationNo: null,
    destinationName: null,
    comparisonDate: '2026-04-01',
    comparisonStatus: '00',
    sourceEstimateNo: 570,
    title: '除菌洗浄剤 比較提案',
    note: null,
    groupCount: 2,
    groups: [],
  },
  {
    comparisonNo: 2,
    shopNo: 1,
    partnerNo: 200,
    partnerName: 'クローバーハウス',
    destinationNo: 1,
    destinationName: '本社',
    comparisonDate: '2026-04-05',
    comparisonStatus: '10',
    sourceEstimateNo: null,
    title: '衛生用品 切替提案',
    note: 'テスト備考',
    groupCount: 3,
    groups: [],
  },
  {
    comparisonNo: 3,
    shopNo: 1,
    partnerNo: 108,
    partnerName: 'いしい記念病院',
    destinationNo: null,
    destinationName: null,
    comparisonDate: '2026-03-20',
    comparisonStatus: '20',
    sourceEstimateNo: 570,
    title: '修正版 除菌洗浄剤',
    note: null,
    groupCount: 1,
    groups: [],
  },
]

export const MOCK_ESTIMATE_COMPARISON_DETAIL = {
  comparisonNo: 1,
  shopNo: 1,
  partnerNo: 108,
  partnerName: 'いしい記念病院',
  destinationNo: null,
  destinationName: null,
  comparisonDate: '2026-04-01',
  comparisonStatus: '00',
  sourceEstimateNo: 570,
  title: '除菌洗浄剤 比較提案',
  note: 'テストメモ',
  groupCount: 2,
  groups: [
    {
      groupNo: 1,
      baseGoodsNo: 1,
      baseGoodsCode: 'KAO-001',
      baseGoodsName: '花王 除菌洗浄剤',
      baseSpecification: '5L',
      basePurchasePrice: 1200,
      baseGoodsPrice: 1800,
      baseContainNum: 3,
      displayOrder: 1,
      groupNote: null,
      details: [
        {
          detailNo: 1,
          goodsNo: 2,
          goodsCode: 'LION-001',
          goodsName: 'ライオン 除菌洗浄剤',
          specification: '4.5L',
          purchasePrice: 1050,
          proposedPrice: 1700,
          containNum: 3,
          profitRate: 38.2,
          detailNote: null,
          displayOrder: 1,
          supplierNo: 2,
        },
        {
          detailNo: 2,
          goodsNo: null,
          goodsCode: null,
          goodsName: 'サラヤ 除菌洗浄剤',
          specification: '5L',
          purchasePrice: 980,
          proposedPrice: 1600,
          containNum: 3,
          profitRate: 38.8,
          detailNote: '未登録商品',
          displayOrder: 2,
          supplierNo: null,
        },
      ],
    },
    {
      groupNo: 2,
      baseGoodsNo: 3,
      baseGoodsCode: 'KAO-002',
      baseGoodsName: '花王 ハンドソープ',
      baseSpecification: '2L',
      basePurchasePrice: 800,
      baseGoodsPrice: 1200,
      baseContainNum: 6,
      displayOrder: 2,
      groupNote: 'ハンドソープ切替検討',
      details: [],
    },
  ],
}

export const MOCK_ESTIMATE_COMPARISON_SUBMITTED = {
  ...MOCK_ESTIMATE_COMPARISON_DETAIL,
  comparisonNo: 2,
  comparisonStatus: '10',
  sourceEstimateNo: null,
}

// ==================== Helpers ====================

async function json(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data),
  })
}

/**
 * リスト配列を Spring Data Page 形式に変換。page/size は url.searchParams から読み取る。
 */
function toPage<T>(list: T[], url: URL, defaultSize = 50) {
  const page = Number(url.searchParams.get('page') ?? '0')
  const size = Number(url.searchParams.get('size') ?? String(defaultSize))
  const start = page * size
  const content = list.slice(start, start + size)
  const totalPages = Math.max(1, Math.ceil(list.length / size))
  return {
    content,
    totalElements: list.length,
    totalPages,
    number: page,
    size,
    first: page === 0,
    last: page >= totalPages - 1,
    numberOfElements: content.length,
    empty: content.length === 0,
  }
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

  // ---- Users ----
  await page.route(
    (url) => /^\/api\/v1\/users\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_USERS[0])
      } else if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...MOCK_USERS[0], ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/users',
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_USERS)
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { loginUserNo: 99, ...body, companyType: 'ADMIN', addDateTime: new Date().toISOString() })
      } else {
        await route.fallback()
      }
    },
  )

  // ---- Masters (CRUD) ----
  await page.route(
    (url) => /^\/api\/v1\/masters\/makers\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { makerNo: 1, ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/masters/makers',
    async (route) => {
      const method = route.request().method()
      if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { makerNo: 99, ...body })
      } else {
        await json(route, MOCK_MAKERS)
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/masters\/suppliers\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { supplierNo: 1, ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/masters/suppliers',
    async (route) => {
      const method = route.request().method()
      if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { supplierNo: 99, ...body })
      } else {
        await json(route, MOCK_SUPPLIERS)
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/masters\/warehouses\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { warehouseNo: 1, ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/masters\/partners\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { partnerNo: 1, ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/masters/partners',
    async (route) => {
      const method = route.request().method()
      if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { partnerNo: 99, ...body })
      } else {
        await json(route, MOCK_PARTNERS)
      }
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
        await json(route, toPage(MOCK_SALES_GOODS_WORK_LIST, new URL(route.request().url())))
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
      await json(route, toPage(MOCK_SALES_GOODS_MASTER_LIST, new URL(route.request().url())))
    },
  )

  // ---- Destinations ----
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
        await json(route, toPage(MOCK_PARTNER_GOODS_LIST, new URL(route.request().url())))
      } else {
        await route.fallback()
      }
    },
  )

  // ---- Orders ----
  await page.route(
    (url) => url.pathname === '/api/v1/orders/details',
    async (route) => {
      await json(route, toPage(MOCK_ORDER_DETAILS, new URL(route.request().url())))
    },
  )

  // ---- Quote Imports ----
  await page.route(
    (url) => /^\/api\/v1\/quote-imports\/\d+\/supplier$/.test(url.pathname),
    async (route) => {
      await route.fulfill({ status: 200 })
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/quote-imports\/\d+\/details\/\d+\/match$/.test(url.pathname),
    async (route) => {
      await route.fulfill({ status: 200 })
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/quote-imports\/\d+\/details\/\d+\/create-new$/.test(url.pathname),
    async (route) => {
      await json(route, {}, 201)
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/quote-imports\/\d+\/details\/\d+$/.test(url.pathname),
    async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/quote-imports\/\d+$/.test(url.pathname),
    async (route) => {
      await json(route, {
        header: MOCK_QUOTE_IMPORT_HEADERS[0],
        details: MOCK_QUOTE_IMPORT_DETAILS,
      })
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/quote-imports',
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_QUOTE_IMPORT_HEADERS)
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { quoteImportId: 99, ...body }, 201)
      } else {
        await route.fallback()
      }
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
        await json(route, toPage(MOCK_PURCHASE_PRICE_CHANGES, new URL(route.request().url())))
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { purchasePriceChangePlanNo: 99, ...body }, 201)
      } else {
        await route.fallback()
      }
    },
  )

  // ---- Warehouses ----
  await page.route(
    (url) => url.pathname === '/api/v1/masters/warehouses',
    async (route) => {
      await json(route, MOCK_WAREHOUSES)
    },
  )

  // ---- Send Orders ----
  await page.route(
    (url) => /^\/api\/v1\/send-orders\/\d+\/details\/\d+\/status$/.test(url.pathname),
    async (route) => {
      await json(route, { message: '更新しました' })
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/send-orders\/\d+$/.test(url.pathname),
    async (route) => {
      await json(route, MOCK_SEND_ORDER_CREATED)
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/send-orders/details',
    async (route) => {
      await json(route, MOCK_SEND_ORDER_DETAILS)
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/send-orders',
    async (route) => {
      const method = route.request().method()
      if (method === 'POST') {
        await json(route, MOCK_SEND_ORDER_CREATED)
      } else {
        await json(route, [])
      }
    },
  )

  // ---- Batch Jobs ----
  await page.route(
    (url) => url.pathname === '/api/v1/batch/jobs',
    async (route) => {
      await json(route, [
        { jobName: 'goodsFileImport', category: 'マスタ取込', description: 'SMILE商品マスタCSV取込', available: true },
        { jobName: 'purchaseFileImport', category: 'マスタ取込', description: 'SMILE仕入ファイル取込', available: true },
      ])
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/batch\/execute\//.test(url.pathname),
    async (route) => {
      await json(route, { message: 'ジョブを実行しました' })
    },
  )

  // ---- Partner Groups ----
  await page.route(
    (url) => url.pathname === '/api/v1/finance/partner-groups',
    async (route) => {
      if (route.request().method() === 'GET') {
        await json(route, [
          { partnerGroupId: 1, groupName: 'イズミグループ', shopNo: 1, partnerCodes: ['000231', '000232'] },
        ])
      } else {
        await json(route, { partnerGroupId: 2, groupName: 'test', shopNo: 1, partnerCodes: [] })
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/finance\/partner-groups\/\d+$/.test(url.pathname),
    async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await json(route, { partnerGroupId: 1, groupName: 'updated', shopNo: 1, partnerCodes: [] })
      }
    },
  )

  // ---- Invoices ----
  await page.route(
    (url) => url.pathname === '/api/v1/finance/invoices/bulk-payment-date',
    async (route) => {
      await json(route, { updatedCount: 2 })
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/finance/invoices/import',
    async (route) => {
      await json(route, {
        closingDate: '2026/02/末',
        shopNo: 1,
        totalRows: 184,
        insertedRows: 150,
        updatedRows: 34,
        skippedRows: 0,
        errors: [],
      })
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/finance\/invoices\/\d+\/payment-date$/.test(url.pathname),
    async (route) => {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { invoiceId: 1, ...body })
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/finance/invoices',
    async (route) => {
      await json(route, [
        {
          invoiceId: 1,
          partnerCode: '022000',
          partnerName: 'いしい記念病院',
          closingDate: '2026/03/末',
          previousBalance: 50000,
          totalPayment: 30000,
          carryOverBalance: 20000,
          netSales: 100000,
          taxPrice: 10000,
          netSalesIncludingTax: 110000,
          currentBillingAmount: 130000,
          shopNo: 1,
          paymentDate: '2026-04-15',
        },
        {
          invoiceId: 2,
          partnerCode: '148013',
          partnerName: 'クローバーハウス',
          closingDate: '2026/03/末',
          previousBalance: 0,
          totalPayment: 0,
          carryOverBalance: 0,
          netSales: 50000,
          taxPrice: 5000,
          netSalesIncludingTax: 55000,
          currentBillingAmount: 55000,
          shopNo: 1,
          paymentDate: null,
        },
      ])
    },
  )

  // ---- Estimate Comparisons ----
  await page.route(
    (url) => /^\/api\/v1\/estimate-comparisons\/from-estimate\/\d+$/.test(url.pathname),
    async (route) => {
      if (route.request().method() === 'POST') {
        await json(route, { ...MOCK_ESTIMATE_COMPARISON_DETAIL, comparisonNo: 99 }, 201)
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/estimate-comparisons\/\d+\/status$/.test(url.pathname),
    async (route) => {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { ...MOCK_ESTIMATE_COMPARISON_DETAIL, ...body })
    },
  )

  await page.route(
    (url) => /^\/api\/v1\/estimate-comparisons\/\d+$/.test(url.pathname),
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_ESTIMATE_COMPARISON_DETAIL)
      } else if (method === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { ...MOCK_ESTIMATE_COMPARISON_DETAIL, ...body })
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 })
      } else {
        await route.fallback()
      }
    },
  )

  await page.route(
    (url) => url.pathname === '/api/v1/estimate-comparisons',
    async (route) => {
      const method = route.request().method()
      if (method === 'GET') {
        await json(route, MOCK_ESTIMATE_COMPARISONS)
      } else if (method === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}')
        await json(route, { comparisonNo: 99, ...body, groupCount: body.groups?.length ?? 0, groups: [] }, 201)
      } else {
        await route.fallback()
      }
    },
  )

  // ---- Estimate Compare Goods ----
  await page.route(
    (url) => url.pathname === '/api/v1/estimates/compare-goods',
    async (route) => {
      const url = new URL(route.request().url())
      const goodsNoList = url.searchParams.getAll('goodsNoList').map(Number)
      const filtered = MOCK_COMPARE_GOODS.filter((g) => goodsNoList.includes(g.goodsNo))
      await json(route, filtered)
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
