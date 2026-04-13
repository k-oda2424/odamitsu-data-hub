import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis, MOCK_USER, MOCK_ESTIMATE_COMPARISON_DETAIL, MOCK_ESTIMATE_COMPARISON_SUBMITTED } from './helpers/mock-api'

// ==================== 一覧画面テスト ====================

test.describe('比較見積一覧画面', () => {
  test.describe('初期表示', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimate-comparisons')
    })

    test('ページヘッダーと新規作成ボタンが表示される', async ({ page }) => {
      await expect(page.getByRole('heading', { name: '比較見積一覧' })).toBeVisible()
      await expect(page.locator('text=新規作成')).toBeVisible()
    })

    test('検索フォームが表示される', async ({ page }) => {
      await expect(page.getByText('得意先', { exact: true })).toBeVisible()
      await expect(page.getByText('タイトル', { exact: true })).toBeVisible()
      await expect(page.getByText('作成日', { exact: true })).toBeVisible()
      await expect(page.getByText('ステータス', { exact: true })).toBeVisible()
    })

    test('検索前は案内メッセージが表示され、テーブルは非表示', async ({ page }) => {
      await expect(page.locator('text=検索条件を入力して')).toBeVisible()
      await expect(page.locator('table')).not.toBeVisible()
    })

    test('デフォルトで「作成」と「修正」ステータスがチェック済み', async ({ page }) => {
      const createCheckbox = page.locator('label').filter({ hasText: /^作成$/ }).locator('button[role="checkbox"]')
      const modifiedCheckbox = page.locator('label').filter({ hasText: /^修正$/ }).locator('button[role="checkbox"]')
      await expect(createCheckbox).toHaveAttribute('data-state', 'checked')
      await expect(modifiedCheckbox).toHaveAttribute('data-state', 'checked')
    })

    test('admin の場合、店舗セレクトが表示される', async ({ page }) => {
      await expect(page.getByText('店舗', { exact: true })).toBeVisible()
    })
  })

  test.describe('検索機能', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimate-comparisons')
    })

    test('検索ボタンでテーブルが表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })
    })

    test('テーブルヘッダーが正しく表示される', async ({ page }) => {
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.locator('th:has-text("比較見積番号")')).toBeVisible()
      await expect(page.locator('th:has-text("作成日")')).toBeVisible()
      await expect(page.locator('th:has-text("得意先")')).toBeVisible()
      await expect(page.locator('th:has-text("タイトル")')).toBeVisible()
      await expect(page.locator('th:has-text("グループ数")')).toBeVisible()
      await expect(page.locator('th:has-text("ステータス")')).toBeVisible()
    })

    test('検索結果にモックデータが表示される', async ({ page }) => {
      // Admin needs to select a shop first
      await page.locator('button:has-text("店舗を選択")').click()
      await page.locator('[role="option"]:has-text("小田光")').first().click()
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await expect(page.getByText('いしい記念病院').first()).toBeVisible()
      await expect(page.getByText('除菌洗浄剤 比較提案')).toBeVisible()
    })

    test('行クリックで詳細画面に遷移する', async ({ page }) => {
      await page.locator('button:has-text("店舗を選択")').click()
      await page.locator('[role="option"]:has-text("小田光")').first().click()
      await page.locator('button:has-text("検索")').click()
      await expect(page.locator('table')).toBeVisible({ timeout: 10000 })

      await page.locator('table tbody tr').first().click()
      await expect(page).toHaveURL(/\/estimate-comparisons\/\d+/)
    })

    test('「新規作成」ボタンで作成画面に遷移する', async ({ page }) => {
      await page.locator('text=新規作成').click()
      await expect(page).toHaveURL(/\/estimate-comparisons\/create/)
    })
  })

  test.describe('non-admin', () => {
    test('店舗セレクトが非表示', async ({ page }) => {
      await mockAllApis(page)
      // Override auth to non-admin
      await page.route(
        (url) => url.pathname === '/api/v1/auth/me',
        async (route) => {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ ...MOCK_USER, shopNo: 1 }),
          })
        },
      )
      await loginAndGoto(page, '/estimate-comparisons')
      await expect(page.getByRole('heading', { name: '比較見積一覧' })).toBeVisible()
      // 店舗 label should NOT be visible for non-admin
      const shopLabel = page.locator('.space-y-2').filter({ hasText: '店舗' })
      await expect(shopLabel).not.toBeVisible()
    })
  })
})

// ==================== 詳細画面テスト ====================

test.describe('比較見積詳細画面', () => {
  test.describe('表示', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimate-comparisons/1')
    })

    test('メタ情報が正しく表示される', async ({ page }) => {
      await expect(page.getByRole('heading', { name: '比較見積 #1' })).toBeVisible()
      await expect(page.getByText('いしい記念病院').first()).toBeVisible()
      await expect(page.getByText('除菌洗浄剤 比較提案').first()).toBeVisible()
    })

    test('グループセクションが表示される', async ({ page }) => {
      await expect(page.getByText('グループ1: 花王 除菌洗浄剤').first()).toBeVisible()
      await expect(page.getByText('グループ2: 花王 ハンドソープ').first()).toBeVisible()
    })

    test('基準品の情報が比較表に表示される', async ({ page }) => {
      const screenSection = page.locator('.print\\:hidden')
      await expect(screenSection.getByText('KAO-001').first()).toBeVisible()
    })

    test('代替提案が比較表に表示される', async ({ page }) => {
      const screenSection = page.locator('.print\\:hidden')
      await expect(screenSection.getByText('ライオン 除菌洗浄剤').first()).toBeVisible()
      await expect(screenSection.getByText('サラヤ 除菌洗浄剤').first()).toBeVisible()
    })

    test('admin は仕入単価・粗利情報が表示される', async ({ page }) => {
      const screenSection = page.locator('.print\\:hidden')
      await expect(screenSection.getByText('仕入単価').first()).toBeVisible()
      await expect(screenSection.getByText('粗利額').first()).toBeVisible()
      await expect(screenSection.getByText('粗利率').first()).toBeVisible()
      await expect(screenSection.getByText('ケース粗利').first()).toBeVisible()
    })

    test('元見積リンクが表示される', async ({ page }) => {
      await expect(page.locator('text=#570')).toBeVisible()
    })
  })

  test.describe('non-admin 仕入情報非表示', () => {
    test('仕入単価・粗利が非表示', async ({ page }) => {
      await mockAllApis(page)
      await page.route(
        (url) => url.pathname === '/api/v1/auth/me',
        async (route) => {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ ...MOCK_USER, shopNo: 1 }),
          })
        },
      )
      await loginAndGoto(page, '/estimate-comparisons/1')
      await expect(page.getByRole('heading', { name: '比較見積 #1' })).toBeVisible()
      // Screen section should NOT show purchase info
      const screenSection = page.locator('.print\\:hidden')
      await expect(screenSection.locator('text=仕入単価')).not.toBeVisible()
      await expect(screenSection.locator('text=粗利額')).not.toBeVisible()
    })
  })

  test.describe('ボタン制御', () => {
    test('ステータス 00 のとき編集・削除ボタンが表示される', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimate-comparisons/1')
      await expect(page.locator('button:has-text("修正")')).toBeVisible()
      await expect(page.locator('button:has-text("削除")')).toBeVisible()
    })

    test('ステータス 10 のとき編集・削除ボタンが非表示', async ({ page }) => {
      await mockAllApis(page)
      // Override detail to return submitted status
      await page.route(
        (url) => /^\/api\/v1\/estimate-comparisons\/\d+$/.test(url.pathname) && !url.pathname.includes('/status'),
        async (route) => {
          if (route.request().method() === 'GET') {
            await route.fulfill({
              status: 200,
              contentType: 'application/json',
              body: JSON.stringify(MOCK_ESTIMATE_COMPARISON_SUBMITTED),
            })
          } else {
            await route.fallback()
          }
        },
      )
      await loginAndGoto(page, '/estimate-comparisons/2')
      await expect(page.getByRole('heading', { name: '比較見積 #2' })).toBeVisible()
      await expect(page.locator('button:has-text("修正")')).not.toBeVisible()
      await expect(page.locator('button:has-text("削除")')).not.toBeVisible()
    })

    test('削除で一覧に戻る', async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimate-comparisons/1')

      page.on('dialog', (dialog) => dialog.accept())
      await page.locator('button:has-text("削除")').click()
      await expect(page).toHaveURL(/\/estimate-comparisons$/, { timeout: 10000 })
    })
  })
})

// ==================== 作成フォームテスト ====================

test.describe('比較見積作成フォーム', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/estimate-comparisons/create')
  })

  test('フォームヘッダーフィールドが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '比較見積 新規作成' })).toBeVisible()
    await expect(page.getByText('店舗 *')).toBeVisible()
    await expect(page.getByText('得意先', { exact: true })).toBeVisible()
    await expect(page.getByText('作成日 *')).toBeVisible()
    await expect(page.getByText('タイトル', { exact: true })).toBeVisible()
  })

  test('初期状態でグループが1件表示される', async ({ page }) => {
    await expect(page.locator('text=グループ 1')).toBeVisible()
  })

  test('「グループ追加」ボタンでグループが追加される', async ({ page }) => {
    await page.locator('button:has-text("グループ追加")').click()
    await expect(page.locator('text=グループ 2')).toBeVisible()
  })

  test('グループを削除できる', async ({ page }) => {
    // Delete the initial group
    await page.locator('[data-testid]').or(page.locator('button')).filter({ has: page.locator('.text-destructive') }).first().click()
    await expect(page.locator('text=グループがありません')).toBeVisible()
  })

  test('「代替提案を追加」ボタンで明細が追加される', async ({ page }) => {
    await page.locator('button:has-text("代替提案を追加")').click()
    await expect(page.locator('text=代替 1')).toBeVisible()
  })

  test('グループ0件で保存するとバリデーションエラー', async ({ page }) => {
    // Select a shop first to pass shopNo validation
    await page.locator('button:has-text("店舗を選択")').click()
    await page.locator('[role="option"]:has-text("小田光")').first().click()
    // Delete the initial group
    await page.locator('button').filter({ has: page.locator('.text-destructive') }).first().click()
    await page.locator('button:has-text("保存")').click()
    await expect(page.getByText('グループを1つ以上追加してください')).toBeVisible({ timeout: 5000 })
  })

  test('基準品名未入力で保存するとバリデーションエラー', async ({ page }) => {
    // Select a shop first to pass shopNo validation
    await page.locator('button:has-text("店舗を選択")').click()
    await page.locator('[role="option"]:has-text("小田光")').first().click()
    // Don't fill in base goods name — just click save
    await page.locator('button:has-text("保存")').click()
    await expect(page.getByText('基準品名を入力してください')).toBeVisible({ timeout: 5000 })
  })
})

// ==================== 編集フォームテスト ====================

test.describe('比較見積編集フォーム', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/estimate-comparisons/1/edit')
  })

  test('既存データがフォームに読み込まれる', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '比較見積 #1 編集' })).toBeVisible()
    // Check that group data is loaded
    await expect(page.locator('input[value="花王 除菌洗浄剤"]')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('input[value="花王 ハンドソープ"]')).toBeVisible()
  })

  test('代替提案が読み込まれる', async ({ page }) => {
    await expect(page.locator('input[value="ライオン 除菌洗浄剤"]')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('input[value="サラヤ 除菌洗浄剤"]')).toBeVisible()
  })

  test('グループを追加できる', async ({ page }) => {
    await page.locator('button:has-text("グループ追加")').click()
    await expect(page.locator('text=グループ 3')).toBeVisible()
  })
})

// ==================== 見積→比較見積生成テスト ====================

test.describe('見積から比較見積生成', () => {
  test('ステータス 00 の見積詳細に「比較見積を作成」ボタンが表示される', async ({ page }) => {
    await mockAllApis(page)
    // Override estimate detail to status 00
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+$/.test(url.pathname) && !url.pathname.includes('/status'),
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              estimateNo: 570,
              shopNo: 1,
              partnerNo: 108,
              partnerCode: '022000',
              partnerName: 'いしい記念病院',
              estimateDate: '2022-10-26',
              priceChangeDate: '2022-11-21',
              estimateStatus: '00',
              details: [],
            }),
          })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/estimates/570')
    await expect(page.locator('button:has-text("比較見積を作成")')).toBeVisible()
  })

  test('ステータス 10 の見積詳細にボタンが非表示', async ({ page }) => {
    await mockAllApis(page)
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+$/.test(url.pathname) && !url.pathname.includes('/status'),
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              estimateNo: 570,
              shopNo: 1,
              partnerNo: 108,
              partnerCode: '022000',
              partnerName: 'いしい記念病院',
              estimateDate: '2022-10-26',
              priceChangeDate: '2022-11-21',
              estimateStatus: '10',
              details: [],
            }),
          })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/estimates/570')
    await expect(page.locator('button:has-text("比較見積を作成")')).not.toBeVisible()
  })

  test('ボタンクリックでAPIコール→編集画面に遷移', async ({ page }) => {
    await mockAllApis(page)
    await page.route(
      (url) => /^\/api\/v1\/estimates\/\d+$/.test(url.pathname) && !url.pathname.includes('/status'),
      async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              estimateNo: 570,
              shopNo: 1,
              partnerNo: 108,
              partnerCode: '022000',
              partnerName: 'いしい記念病院',
              estimateDate: '2022-10-26',
              priceChangeDate: '2022-11-21',
              estimateStatus: '00',
              details: [],
            }),
          })
        } else {
          await route.fallback()
        }
      },
    )
    await loginAndGoto(page, '/estimates/570')
    await page.locator('button:has-text("比較見積を作成")').click()
    await expect(page).toHaveURL(/\/estimate-comparisons\/99\/edit/, { timeout: 10000 })
  })
})

// ==================== 印刷テスト ====================

test.describe('印刷レイアウト', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/estimate-comparisons/1')
  })

  test('印刷用コンテンツに「御見積書」ヘッダが存在する', async ({ page }) => {
    const printSection = page.locator('.print\\:block')
    await expect(printSection.locator('text=御見積書')).toBeAttached()
  })

  test('印刷用コンテンツに得意先名が含まれる', async ({ page }) => {
    const printSection = page.locator('.print\\:block')
    await expect(printSection.locator('text=いしい記念病院')).toBeAttached()
  })

  test('印刷用コンテンツに仕入情報が含まれない', async ({ page }) => {
    const printSection = page.locator('.print\\:block')
    // Print layout should NOT have purchase/profit rows
    await expect(printSection.locator('text=仕入単価')).not.toBeAttached()
    await expect(printSection.locator('text=粗利額')).not.toBeAttached()
    await expect(printSection.locator('text=粗利率')).not.toBeAttached()
  })

  test('印刷用コンテンツに商品名・規格・販売単価が表示される', async ({ page }) => {
    const printSection = page.locator('.print\\:block')
    await expect(printSection.getByText('花王 除菌洗浄剤').first()).toBeAttached()
    await expect(printSection.getByText('ライオン 除菌洗浄剤').first()).toBeAttached()
  })
})

// ==================== サイドバーテスト ====================

test.describe('サイドバー', () => {
  test('「比較見積」メニューが /estimate-comparisons に遷移する', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/dashboard')

    const sidebar = page.locator('[data-sidebar="sidebar"]')
    // Open the "見積" collapsible group
    await sidebar.getByText('見積', { exact: true }).click()
    await sidebar.getByRole('link', { name: '比較見積', exact: true }).click()
    await expect(page).toHaveURL(/\/estimate-comparisons$/, { timeout: 10000 })
  })
})
