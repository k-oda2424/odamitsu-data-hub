# Test Plan: 発注メニュー分割 (Send Order Menu Split)

## 概要
サイドバーの「発注入力」メニューを「発注一覧」と「発注入力」の2つに分割する変更に伴うE2Eテスト計画。
併せて、発注入力画面の商品選択フロー（SearchableSelect）のテストを追加する。

## 対象ファイル
- `frontend/e2e/send-order.spec.ts`
- `frontend/components/layout/Sidebar.tsx`
- `frontend/e2e/helpers/mock-api.ts` (変更不要の見込み)

## 前提条件
- `mockAllApis(page)` + `loginAndGoto(page, '/path')` パターンを使用
- モックユーザーは `shopNo=1`（admin以外、店舗セレクタ非表示）
- `MOCK_SALES_GOODS_MASTER_LIST` に `supplierNo=1` の商品が1件存在（マスタ販売商品A, goodsCode: MS001, purchasePrice: 120）

---

## 1. サイドバーメニューテスト

### 1-1. メニューリンクの存在確認

#### TC-SIDE-01: 「発注一覧」リンクが存在する
- **操作**: `/send-orders` に遷移
- **検証**: サイドバー内に `getByRole('link', { name: '発注一覧', exact: true })` が存在する
- **検証**: リンクの `href` が `/send-orders` である

#### TC-SIDE-02: 「発注入力」リンクが存在する
- **操作**: `/send-orders` に遷移
- **検証**: サイドバー内に `getByRole('link', { name: '発注入力', exact: true })` が存在する
- **検証**: リンクの `href` が `/send-orders/create` である

#### TC-SIDE-03: 「発注一覧」リンクから一覧画面に遷移できる
- **操作**: `/dashboard` に遷移 → サイドバーの「発注一覧」リンクをクリック
- **検証**: URL が `/send-orders` になる
- **検証**: ページヘッダー「発注一覧」が表示される

#### TC-SIDE-04: 「発注入力」リンクから入力画面に遷移できる
- **操作**: `/dashboard` に遷移 → サイドバーの「発注入力」リンクをクリック
- **検証**: URL が `/send-orders/create` になる
- **検証**: ページヘッダー「発注入力」が表示される

### 1-2. アクティブ状態テスト

`isMenuActive()` ロジックにより、`/send-orders` と `/send-orders/create` は親子関係として扱われる。
`allHrefs` に両方が含まれるため、`/send-orders/create` にいるとき `/send-orders` はアクティブにならない。

#### TC-SIDE-05: `/send-orders` で「発注一覧」がアクティブ
- **操作**: `/send-orders` に遷移
- **検証**: サイドバー `[data-sidebar="sidebar"]` 内の「発注一覧」リンク（`<a>`要素）に `data-active="true"` がある
- **検証**: サイドバー内の「発注入力」リンクに `data-active="true"` がない

#### TC-SIDE-06: `/send-orders/create` で「発注入力」がアクティブ
- **操作**: `/send-orders/create` に遷移
- **検証**: サイドバー内の「発注入力」リンクに `data-active="true"` がある
- **検証**: サイドバー内の「発注一覧」リンクに `data-active="true"` がない

---

## 2. 発注入力 - 商品選択フローテスト

### 前提
- 仕入先SearchableSelectで「仕入先A」（supplierNo=1）を選択済み
- `MOCK_SALES_GOODS_MASTER_LIST` がフィルタされた商品リストとして返される

### 2-1. 商品選択の基本フロー

#### TC-GOODS-01: 仕入先選択後に商品SearchableSelectが表示される
- **操作**: `/send-orders/create` に遷移 → 仕入先SearchableSelectで「仕入先A」を選択
- **検証**: 「仕入先を選択してください」メッセージが消える
- **検証**: 明細行の商品SearchableSelectが操作可能になる

#### TC-GOODS-02: 商品SearchableSelectにモックデータの商品が表示される
- **操作**: 仕入先A選択後 → 明細1行目の商品SearchableSelectをクリック（Popoverを開く）
- **検証**: Popover/Command内に「マスタ販売商品A」（MOCK_SALES_GOODS_MASTER_LISTの商品）が表示される
- **備考**: SearchableSelectはPopover + Command(cmdk)パターン。`input[role="combobox"]` でテキスト入力、`[cmdk-item]` で候補選択

#### TC-GOODS-03: 商品選択で価格・商品名が自動入力される
- **操作**: 商品SearchableSelectから「マスタ販売商品A」を選択
- **検証**: 明細行の仕入単価に `120`（purchasePrice）が入る
- **検証**: 明細行の商品名/商品コードに対応する値が入る

#### TC-GOODS-04: 複数行に異なる商品を追加できる
- **操作**: 1行目に商品を選択 → 「行追加」ボタンをクリック → 2行目に別の商品を選択
- **検証**: 2行とも正しい商品情報が表示される
- **備考**: `MOCK_SALES_GOODS_MASTER_LIST` に1件しかないため、追加のモックデータが必要になる可能性あり。実装時に確認すること

---

## 3. 既存テストの修正

### 3-1. 修正が必要なテスト

#### TC-EXISTING-01: 「発注入力ボタンで入力画面に遷移する」の更新
- **現在の実装** (line 46-49):
  ```ts
  test('発注入力ボタンで入力画面に遷移する', async ({ page }) => {
    await page.getByRole('button', { name: '発注入力' }).click()
    await page.waitForURL('**/send-orders/create')
  })
  ```
- **問題**: メニュー分割後、一覧画面に「発注入力」ボタンが残るかどうかによって対応が変わる
  - **ボタンが残る場合**: テストはそのまま動作する（変更不要）
  - **ボタンを削除する場合**: このテストを削除し、TC-SIDE-04（サイドバーリンクからの遷移）で代替する
- **判断**: 実装時に一覧画面のUIを確認して決定

### 3-2. 変更不要の既存テスト

以下のテストは変更不要（直接ページURLで遷移しているため影響なし）:

- 発注一覧画面: ページヘッダー表示、検索実行、ステータスバッジ、リセット
- 発注入力画面: ページヘッダー表示、発注情報フォーム、仕入先選択前メッセージ、確認画面ボタン、行追加ボタン、発注一覧ボタン遷移

---

## 4. Sidebar.tsx の変更内容（確認事項）

現在の `menuGroups` 定義:
```ts
{ title: '発注入力', icon: Truck, href: '/send-orders' },
```

変更後:
```ts
{ title: '発注一覧', icon: Truck, href: '/send-orders' },
{ title: '発注入力', icon: Truck, href: '/send-orders/create' },
```

`isMenuActive()` の動作確認:
- `/send-orders` → `allHrefs` に `/send-orders/create` が存在するため、`/send-orders` のみアクティブ（正しい）
- `/send-orders/create` → 完全一致で `/send-orders/create` がアクティブ、`/send-orders` は `hasMoreSpecificMatch` により非アクティブ（正しい）

---

## 5. テスト実行コマンド

```bash
cd frontend && npx playwright test e2e/send-order.spec.ts --headed
```

## 6. リスク・注意事項

1. **SearchableSelect操作の安定性**: Popover + cmdk はアニメーション待ちが必要な場合がある。`waitFor` や `toBeVisible` を適切に使うこと
2. **`exact: true` の使用**: 「発注一覧」と「発注入力」はプレフィックスが共通するため、`exact: true` を必ず指定すること
3. **サイドバーセレクタ**: `data-active` 属性は `<a>` 要素（asChild経由）にあるため、`locator('[data-sidebar="sidebar"]').getByRole('link', { name: '...', exact: true })` でアクセスすること
4. **モックデータの十分性**: TC-GOODS-04（複数行追加）は `MOCK_SALES_GOODS_MASTER_LIST` に商品が1件しかないため、テスト実装時にモックデータの追加が必要か判断すること
