# 比較見積機能 E2Eテスト計画

> 作成日: 2026-04-09
> 対象ページ: `/estimates/compare`
> 設計書: `claudedocs/design-estimate-comparison.md` (Rev.2)

---

## 1. テスト概要

### 目的
比較見積機能の画面表示、ユーザー操作、計算ロジック、権限制御が設計書どおりに動作することを E2E テストで検証する。

### スコープ
- 画面表示・初期状態
- 条件設定（店舗・得意先・配送先）
- 商品追加・検索
- 比較テーブル表示
- シミュレーション計算（粗利額・粗利率・合計粗利）
- 基準品操作
- 見積作成遷移（sessionStorage 連携）
- 印刷機能（得意先向け / 社内向け切替）
- Edge Cases（重複追加、上限、基準品除外、赤字警告など）
- 権限制御（admin / 非admin）

### スコープ外
- PDF 出力（Phase 2 — Could）
- バックエンド API 単体テスト（別途実施）

### 前提条件
- `mockAllApis(page)` で全 API をモック済み
- admin ユーザー（shopNo=0）でログイン（`loginAndGoto`）
- 非 admin テストは `MOCK_USER` を shopNo=1 に差し替え

---

## 2. テストケース一覧

### 2.1 画面表示・初期状態

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| INIT-01 | ページヘッダーが表示される | `/estimates/compare` に遷移 | 「比較見積」見出しが表示される | P1 |
| INIT-02 | 見積一覧への戻りリンクが表示される | ページ遷移後に確認 | 「← 見積一覧」リンクが存在し、`/estimates` へのリンクである | P1 |
| INIT-03 | 条件設定セクションが表示される | ページ遷移後に確認 | 得意先・配送先の SearchableSelect が表示される | P1 |
| INIT-04 | 商品追加ボタンが表示される | ページ遷移後に確認 | 「商品を追加」ボタンが活性状態で表示される | P1 |
| INIT-05 | 比較テーブルが空の状態で案内メッセージが表示される | ページ遷移後に確認 | 商品が未追加の旨のメッセージが表示され、テーブルは非表示 | P1 |
| INIT-06 | 商品カウントが 0/10 で表示される | ページ遷移後に確認 | 「比較商品 (0/10)」のようなカウント表示がある | P2 |

### 2.2 条件設定（店舗・得意先・配送先）

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| COND-01 | admin ユーザーで店舗セレクトが表示される | admin（shopNo=0）でページ遷移 | 店舗選択の SearchableSelect が表示される | P1 |
| COND-02 | 非 admin ユーザーで店舗セレクトが非表示 | shopNo=1 のユーザーモックでページ遷移 | 店舗選択が表示されない | P1 |
| COND-03 | 得意先を選択できる | 得意先 SearchableSelect を開き、候補を選択 | 選択した得意先名が表示される | P1 |
| COND-04 | 得意先選択後に配送先候補がフィルタされる | 得意先を選択 → 配送先セレクトを開く | 選択した得意先に紐づく配送先のみ表示される | P2 |
| COND-05 | 得意先をクリアできる | 得意先を選択後、クリアボタンを押下 | 得意先が未選択状態に戻り、配送先もクリアされる | P2 |
| COND-06 | 得意先変更で特値が自動再フェッチされる | 商品追加済みの状態で得意先を変更 | compare-goods API が新しい partnerNo で再呼出しされる（ネットワークモックで検証） | P1 |

### 2.3 商品追加・検索

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| ADD-01 | 商品追加ボタンで検索ダイアログが開く | 「商品を追加」ボタンをクリック | GoodsSearchDialog が表示される | P1 |
| ADD-02 | 検索結果から商品を選択して追加できる | ダイアログで検索 → 商品を選択 | ダイアログが閉じ、比較テーブルに商品列が追加される | P1 |
| ADD-03 | コード検索で商品を追加できる | コード入力欄に商品コードを入力 → 検索 | 該当商品が比較テーブルに追加される | P1 |
| ADD-04 | 最初に追加した商品が基準品になる | 商品を1つ追加 | ★マーク付きで「基準品」として表示される | P1 |
| ADD-05 | 2つ目以降の商品は代替品として追加される | 基準品追加後に2つ目を追加 | ★マークなしで追加され、基準品との差分表示がある | P1 |
| ADD-06 | 商品カウントが追加ごとに更新される | 3商品を追加 | カウント表示が「(3/10)」になる | P2 |

### 2.4 比較テーブル表示

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| TBL-01 | 商品基本情報が表示される | 商品追加後にテーブルを確認 | 商品コード、商品名、規格、メーカー、仕入先が各列に表示される | P1 |
| TBL-02 | 価格情報が表示される（admin） | admin ユーザーで商品追加 | 仕入価格、入数が表示される | P1 |
| TBL-03 | 価格変更予定が表示される | 価格変更予定のある商品を追加 | 「YYYY-MM-DD より ¥XXX→¥YYY」形式で変更予定が表示される | P2 |
| TBL-04 | 差分表示で改善時に緑色 ↑ 矢印が表示される | 基準品より粗利率が良い代替品を追加しシミュレーション | 差分値が緑色の ↑ 矢印付きで表示される | P1 |
| TBL-05 | 差分表示で悪化時に赤色 ↓ 矢印が表示される | 基準品より粗利率が悪い代替品でシミュレーション | 差分値が赤色の ↓ 矢印付きで表示される | P1 |
| TBL-06 | 基準品列には差分表示がない | 基準品の列を確認 | 差分矢印や差分値が表示されない | P2 |

### 2.5 シミュレーション計算

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| SIM-01 | 販売単価を入力できる | 販売単価入力欄に数値を入力 | 入力値が反映される | P1 |
| SIM-02 | 数量を入力できる | 数量（ケース）入力欄に数値を入力 | 入力値が反映される | P1 |
| SIM-03 | 粗利額が正しく計算される | 販売単価=1800、仕入価格=1200 を設定 | 粗利額 = 600 が表示される | P1 |
| SIM-04 | 粗利率が正しく計算される | 販売単価=1800、仕入価格=1200 を設定 | 粗利率 = 33.3% が表示される | P1 |
| SIM-05 | ケース粗利が正しく計算される | 販売単価=1800、仕入価格=1200、入数=3 | ケース粗利 = 1,800 (600 x 3) が表示される | P1 |
| SIM-06 | 合計粗利が正しく計算される | 販売単価=1800、仕入価格=1200、入数=3、数量=10 | 合計粗利 = 18,000 (1,800 x 10) が表示される | P1 |
| SIM-07 | 販売単価変更でリアルタイム再計算される | 販売単価を 1800 → 2000 に変更 | 粗利額・粗利率・合計粗利が即座に再計算される | P1 |
| SIM-08 | 数量変更で合計粗利が再計算される | 数量を 10 → 20 に変更 | 合計粗利が倍になる | P2 |

### 2.6 基準品操作

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| BASE-01 | 基準品に ★ マークが表示される | 商品追加後に確認 | 最初に追加した商品に ★ マークがある | P1 |
| BASE-02 | 基準品を変更できる | 代替品の「基準品にする」ボタンをクリック | クリックした商品が ★ 基準品になり、旧基準品から ★ が消える | P1 |
| BASE-03 | 基準品変更後に差分表示が再計算される | 基準品を変更 | 全代替品の差分矢印・色が新基準品を基準に再計算される | P1 |
| BASE-04 | 基準品には「基準品にする」ボタンが表示されない | 基準品の列を確認 | 「基準品にする」ボタンが非表示 | P2 |

### 2.7 見積作成遷移

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| NAV-01 | 見積作成ボタンで遷移する | 商品列の「見積作成→」ボタンをクリック | `/estimates/create` に遷移する | P1 |
| NAV-02 | sessionStorage にデータが格納される | 見積作成ボタンをクリック → sessionStorage を確認 | `estimate-prefill` キーに shopNo, partnerNo, destinationNo, details が格納されている | P1 |
| NAV-03 | 除外ボタンで商品を比較テーブルから削除できる | 「除外」ボタンをクリック | 該当商品の列がテーブルから消え、カウントが減少する | P1 |

### 2.8 印刷機能

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| PRINT-01 | 印刷ボタンが表示される | ページ下部を確認 | 「印刷」ボタンが表示される | P2 |
| PRINT-02 | 得意先向け表示で仕入情報が非表示になる | 「得意先向け表示」チェックボックスを ON | 仕入価格、粗利率、粗利額の行が非表示になる | P2 |
| PRINT-03 | 社内向け表示で全項目が表示される | 「得意先向け表示」チェックをOFF | 仕入価格、粗利率、粗利額を含む全項目が表示される | P2 |

### 2.9 Edge Cases

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| EDGE-01 | 同一商品の重複追加でトースト警告 | 既に追加済みの商品を再度追加 | トースト（sonner）で重複警告メッセージが表示され、追加されない | P1 |
| EDGE-02 | 10商品上限で追加ボタンが非活性になる | 10商品を追加 | 「商品を追加」ボタンが disabled になる | P1 |
| EDGE-03 | 基準品を除外すると先頭商品が自動昇格する | 基準品を除外 | 残った商品の先頭が新たな基準品（★）になる | P1 |
| EDGE-04 | 赤字（販売単価 <= 仕入価格）で赤字表示 | 販売単価を仕入価格以下に入力 | 粗利額が赤字（赤色）で表示され、警告が表示される | P1 |
| EDGE-05 | goodsNo=null（マスタ未登録商品）を追加できる | 検索結果からマスタ未登録商品を選択 | compare-goods API を経由せず、検索結果の情報がそのまま比較テーブルに表示される | P2 |
| EDGE-06 | 全商品を除外するとテーブルが空に戻る | 追加済みの全商品を除外 | 初期状態の案内メッセージが再表示される | P2 |
| EDGE-07 | 販売単価に 0 を入力した場合 | 販売単価に 0 を入力 | 粗利率の計算でゼロ除算エラーが発生せず、粗利額が負値で表示される | P3 |
| EDGE-08 | 数量に 0 を入力した場合 | 数量に 0 を入力 | 合計粗利が 0 と表示される | P3 |
| EDGE-09 | 販売単価・数量に負数を入力できない | マイナス値を入力 | 入力が拒否される、または 0 に補正される | P3 |

### 2.10 権限（admin / 非admin）

| ID | テスト名 | 手順 | 期待結果 | 優先度 |
|---|---|---|---|---|
| AUTH-01 | admin で仕入価格が表示される | admin（shopNo=0）で商品追加 | 仕入価格行が比較テーブルに表示される | P1 |
| AUTH-02 | admin で粗利率が表示される | admin（shopNo=0）で商品追加・シミュレーション | 粗利率行が表示される | P1 |
| AUTH-03 | 非 admin で仕入価格が非表示 | shopNo=1 のモックでログイン → 商品追加 | 仕入価格行が比較テーブルに存在しない | P1 |
| AUTH-04 | 非 admin で粗利率が非表示 | shopNo=1 のモックでログイン → シミュレーション | 粗利率行が表示されない | P1 |

---

## 3. テストケース集計

| カテゴリ | P1 | P2 | P3 | 合計 |
|---------|----|----|----|----|
| 画面表示・初期状態 | 5 | 1 | 0 | 6 |
| 条件設定 | 3 | 2 | 0 | 5 |
| 商品追加・検索 | 5 | 1 | 0 | 6 |
| 比較テーブル表示 | 3 | 3 | 0 | 6 |
| シミュレーション計算 | 7 | 1 | 0 | 8 |
| 基準品操作 | 3 | 1 | 0 | 4 |
| 見積作成遷移 | 3 | 0 | 0 | 3 |
| 印刷機能 | 0 | 3 | 0 | 3 |
| Edge Cases | 4 | 2 | 3 | 9 |
| 権限 | 4 | 0 | 0 | 4 |
| **合計** | **37** | **14** | **3** | **54** |

---

## 4. E2Eテスト実装方針

### 4.1 ファイル構成

```
frontend/e2e/
├── estimate-comparison.spec.ts       # メインテストファイル
├── helpers/
│   ├── mock-api.ts                   # 既存 + compare-goods モック追加
│   └── auth.ts                       # 既存（変更なし）
```

### 4.2 モック戦略

#### mock-api.ts への追加データ

```typescript
// 比較見積用モックデータ
export const MOCK_COMPARE_GOODS = [
  {
    goodsNo: 1,
    goodsCode: 'KAO-001',
    goodsName: '花王 除菌洗浄剤',
    specification: '5L',
    janCode: '4901301000001',
    makerName: '花王',
    supplierName: '花王プロフェッショナル',
    supplierNo: 1,
    purchasePrice: 1200,
    nowGoodsPrice: 1800,
    containNum: 3,
    changeContainNum: null,
    pricePlanInfo: '2026-05-01 より ¥1,200→¥1,300',
    planAfterPrice: 1300,
  },
  {
    goodsNo: 2,
    goodsCode: 'LION-001',
    goodsName: 'ライオン 除菌洗浄剤',
    specification: '4.5L',
    janCode: '4903301000001',
    makerName: 'ライオン',
    supplierName: 'ライオンハイジーン',
    supplierNo: 2,
    purchasePrice: 1050,
    nowGoodsPrice: 1700,
    containNum: 3,
    changeContainNum: null,
    pricePlanInfo: null,
    planAfterPrice: null,
  },
  {
    goodsNo: 3,
    goodsCode: 'SARAYA-01',
    goodsName: 'サラヤ 除菌洗浄剤',
    specification: '5L',
    janCode: '4987696000001',
    makerName: 'サラヤ',
    supplierName: 'サラヤ',
    supplierNo: 3,
    purchasePrice: 1100,
    nowGoodsPrice: 1650,
    containNum: 3,
    changeContainNum: null,
    pricePlanInfo: null,
    planAfterPrice: null,
  },
]
```

#### mock-api.ts へのルート追加

```typescript
// ---- Estimate Compare Goods ----
await page.route(
  (url) => url.pathname === '/api/v1/estimates/compare-goods',
  async (route) => {
    const url = new URL(route.request().url())
    const goodsNoList = url.searchParams.get('goodsNoList')?.split(',').map(Number) ?? []
    const filtered = MOCK_COMPARE_GOODS.filter(g => goodsNoList.includes(g.goodsNo))
    await json(route, filtered)
  },
)
```

#### 非 admin テスト用のモック差し替えパターン

```typescript
// テスト内で auth/me を上書き（LIFO で先にマッチ）
await page.route(
  (url) => url.pathname === '/api/v1/auth/me',
  async (route) => {
    await json(route, { ...MOCK_USER, shopNo: 1 })
  },
)
```

### 4.3 テストデータ設計

| 商品 | 役割 | 仕入価格 | 販売単価 (初期) | 入数 | 特記 |
|------|------|---------|----------------|------|------|
| KAO-001 | 基準品 | 1,200 | 1,800 | 3 | 価格変更予定あり |
| LION-001 | 代替品A（粗利改善） | 1,050 | 1,700 | 3 | 仕入が安い → 粗利率↑ |
| SARAYA-01 | 代替品B | 1,100 | 1,650 | 3 | 標準的 |

- 赤字テスト用: 販売単価を 1,000 に入力 → 仕入価格 1,200 を下回る
- 10商品上限テスト: goodsNo=1〜10 をモック用に用意（`MOCK_COMPARE_GOODS` を拡張）

### 4.4 テスト実装パターン

```typescript
import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis, MOCK_COMPARE_GOODS } from './helpers/mock-api'

test.describe('比較見積画面', () => {
  test.describe('初期表示', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
    })

    test('ページヘッダーが表示される', async ({ page }) => {
      await expect(
        page.getByRole('heading', { name: '比較見積' })
      ).toBeVisible()
    })

    test('商品未追加の案内メッセージが表示される', async ({ page }) => {
      // テーブルが非表示で案内メッセージが表示
      await expect(page.locator('table')).not.toBeVisible()
    })
  })

  test.describe('商品追加', () => {
    test.beforeEach(async ({ page }) => {
      await mockAllApis(page)
      await loginAndGoto(page, '/estimates/compare')
    })

    test('商品追加ダイアログが開き、商品を選択できる', async ({ page }) => {
      await page.getByRole('button', { name: /商品を追加/ }).click()
      // GoodsSearchDialog の表示確認
      await expect(page.getByRole('dialog')).toBeVisible()
    })
  })

  test.describe('権限制御', () => {
    test('非admin で仕入価格が非表示', async ({ page }) => {
      await mockAllApis(page)
      // 非admin ユーザーに差し替え
      await page.route(
        (url) => url.pathname === '/api/v1/auth/me',
        async (route) => {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              loginUserNo: 2, userName: '一般ユーザー',
              loginId: 'user1', companyNo: 1,
              companyType: 'SHOP', shopNo: 1,
            }),
          })
        },
      )
      await loginAndGoto(page, '/estimates/compare')
      // 商品追加後、仕入価格行が存在しないことを検証
    })
  })
})
```

### 4.5 実装優先順

1. **Phase 1（P1 のみ）**: 37 ケース — 機能の基本動作保証
2. **Phase 2（P2 追加）**: 14 ケース — 補助的な表示・UX 検証
3. **Phase 3（P3 追加）**: 3 ケース — 境界値・異常入力

### 4.6 注意事項

- **sessionStorage 検証**: `page.evaluate(() => sessionStorage.getItem('estimate-prefill'))` で JSON を取得して構造を検証する
- **リアルタイム計算**: input への入力後に `page.waitForTimeout(100)` 等で debounce を待つ必要がある可能性あり（実装依存）
- **10商品追加テスト**: モックデータを10件用意し、ループで追加操作を行う。パフォーマンスに注意
- **印刷テスト**: `window.print()` の直接検証は困難。代わりに「得意先向け表示」チェック時の DOM 表示/非表示を検証する
- **Selector 方針**: `getByText('text', { exact: true })` と `getByRole()` を優先。テーブル内の特定セルは `locator('td')` + `nth()` や `filter()` を組み合わせる
