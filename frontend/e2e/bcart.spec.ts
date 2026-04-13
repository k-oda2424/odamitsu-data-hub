import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const MOCK_BCART_CATEGORIES = [
  {
    id: 1,
    name: 'カテゴリA',
    parentCategoryId: null,
    flag: 1,
    bCartReflected: true,
    description: '',
    note: '',
    children: [
      { id: 11, name: 'サブカテゴリA-1', parentCategoryId: 1, flag: 1, bCartReflected: false, description: '', note: '', children: [] },
    ],
  },
  {
    id: 2,
    name: 'カテゴリB',
    parentCategoryId: null,
    flag: 0, // 非表示
    bCartReflected: true,
    description: '',
    note: '',
    children: [],
  },
]

const MOCK_BCART_PRODUCTS = [
  { goodsNo: 1, name: 'B-CART商品A', categoryId: 1, categoryName: 'カテゴリA', flag: 1, price: 1000 },
  { goodsNo: 2, name: 'B-CART商品B', categoryId: 11, categoryName: 'サブカテゴリA-1', flag: 1, price: 2000 },
]

async function mockBCartRoutes(page: Page) {
  await page.route(
    (url) => url.pathname === '/api/v1/bcart/categories',
    async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_BCART_CATEGORIES) })
    },
  )
  await page.route(
    (url) => url.pathname.startsWith('/api/v1/bcart/products'),
    async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_BCART_PRODUCTS) })
    },
  )
}

test.describe('B-CARTカテゴリマスタ', () => {
  test('BC-01: タイトル・ツリー表示', async ({ page }) => {
    await mockAllApis(page)
    await mockBCartRoutes(page)
    await loginAndGoto(page, '/bcart/categories')

    await expect(page.getByRole('heading', { name: 'B-CARTカテゴリマスタ' })).toBeVisible()
    await expect(page.getByText('カテゴリA', { exact: false }).first()).toBeVisible()
    await expect(page.getByText('カテゴリB', { exact: false }).first()).toBeVisible()
  })

  test('BC-02: 未反映バッジ表示', async ({ page }) => {
    await mockAllApis(page)
    await mockBCartRoutes(page)
    await loginAndGoto(page, '/bcart/categories')

    await expect(page.getByText('未反映').first()).toBeVisible()
  })

  test('BC-03: カテゴリクリック → 編集パネル表示', async ({ page }) => {
    await mockAllApis(page)
    await mockBCartRoutes(page)
    await loginAndGoto(page, '/bcart/categories')

    // カテゴリAをクリック
    await page.getByText('カテゴリA', { exact: false }).first().click()
    await expect(page.getByText(/ID:\s*1/)).toBeVisible()
  })
})

test.describe('B-CART商品マスタ', () => {
  test('BP-01: タイトル・検索フォーム表示', async ({ page }) => {
    await mockAllApis(page)
    await mockBCartRoutes(page)
    await loginAndGoto(page, '/bcart/products')

    await expect(page.getByRole('heading', { name: 'B-CART商品マスタ' })).toBeVisible()
    await expect(page.getByText('商品名', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('カテゴリ', { exact: true }).first()).toBeVisible()
  })

  test('BP-02: 検索実行 → 商品行表示', async ({ page }) => {
    await mockAllApis(page)
    await mockBCartRoutes(page)
    await loginAndGoto(page, '/bcart/products')

    await page.getByPlaceholder('商品名で検索').fill('A')
    await page.getByRole('button', { name: /検索/ }).click()

    await expect(page.getByText('B-CART商品A')).toBeVisible()
    await expect(page.getByText('B-CART商品B')).toBeVisible()
  })
})
