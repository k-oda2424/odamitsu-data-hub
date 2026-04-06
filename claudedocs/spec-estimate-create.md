# 見積作成・修正画面 仕様書

| 項目 | 内容 |
|------|------|
| 画面ID | EST-CREATE |
| 画面名 | 見積作成 / 見積修正 |
| URL | `/estimates/create`（新規）/ `/estimates/{estimateNo}/edit`（修正）|
| 最終更新 | 2026-04-06 |

---

## 1. 画面概要

メーカーからの見積データや販売商品マスタの情報をもとに、得意先向けの見積書を作成・修正する画面。

---

## 2. ヘッダ部

| 項目 | コンポーネント | 必須 | 備考 |
|------|-------------|------|------|
| 店舗 | Select（admin時のみ表示） | Yes | `GET /masters/shops` |
| 得意先 | SearchableSelect | Yes | `GET /masters/partners?shopNo=X` |
| 納品先 | SearchableSelect (clearable) | No | `GET /masters/destinations?partnerNo=X`。得意先選択後に有効化 |
| 見積日 | date input | Yes | デフォルト: 今日 |
| 価格改定日 | date input | Yes | |
| 備考 | text input | No | |

### 店舗の役割
- admin（shopNo=0）: 店舗選択を表示
- 一般ユーザー: ログインユーザーの shopNo を自動設定
- **店舗が未選択の場合、明細の商品コード入力・検索ボタンは disabled**

---

## 3. 明細部

### 3.1 明細テーブルカラム

| カラム | 入力/表示 | 説明 |
|--------|---------|------|
| 商品コード/JAN | Input + 🔍検索ボタン | blur/Enter で Ajax 検索。🔍で商品検索ポップアップ |
| 商品名 | 表示 | 検索結果から自動設定 |
| 仕様 | 表示 | 検索結果から自動設定 |
| 原価 | 表示（admin のみ） | 仕入価格 |
| 原価改訂予定 | 表示（admin のみ） | 「2026-05-01より1000→1200」形式 |
| 見積単価 | Input (number) | ユーザー入力。変更時に粗利を再計算 |
| 入数 | 表示 | ケース入数 |
| 粗利 | 表示（admin のみ） | `goodsPrice - purchasePrice` |
| 粗利率 | 表示（admin のみ） | `(1 - purchasePrice / goodsPrice) * 100` |
| ケース粗利 | 表示（admin のみ） | `profit * containNum` |
| 備考 | Input | |
| 表示順 | Input (number) | デフォルト: 行番号 |
| 削除 | Button (Trash2) | 行削除 |

### 3.2 商品コード入力（Ajax検索）

`GET /api/v1/estimates/goods-search?shopNo=X&code=YYY`

**入力値の文字数で分岐:**
- **8文字以下（商品コード）**:
  1. 販売商品マスタ（m_sales_goods）の商品コードで完全一致
  2. 販売商品ワーク（w_sales_goods）の商品コードで完全一致
  3. 仕入価格変更予定（m_purchase_price_change_plan）の商品コード
- **9文字以上（JANコード）**:
  1. 商品マスタ（m_goods）のJANコードで完全一致
  2. 仕入価格変更予定（m_purchase_price_change_plan）のJANコード
  3. 見積取込明細（t_quote_import_detail、status=PENDING）のJANコード

### 3.3 goodsNo の扱い

- 見積明細の `goodsNo` は **nullable**
- 販売商品マスタ/ワークからの商品: goodsNo あり
- 仕入価格変更予定・見積取込明細からの商品: goodsNo なし（商品マスタ未登録）
- goodsNo がない商品も見積明細に登録可能（商品コード/JAN + 商品名 + 単価で成立）
- 商品マスタへの正式登録は見積取込の突合フローで行う

### 3.4 行追加・削除
- 「行追加」ボタンで空行を末尾に追加
- 各行の🗑ボタンで削除（最後の1行は削除不可→空行に戻る）

---

## 4. 商品検索ポップアップ

明細行の🔍ボタンで開く。

### 4.1 画面仕様

| 項目 | 内容 |
|------|------|
| コンポーネント | Dialog（shadcn/ui） |
| サイズ | 900px幅、画面高さ85% |
| shopNo | ヘッダ部から引き継ぎ（検索条件に自動設定） |

### 4.2 検索条件

| 項目 | コンポーネント | 必須 | 備考 |
|------|-------------|------|------|
| 仕入先 | SearchableSelect (clearable) | No | `GET /masters/suppliers?shopNo=X` |
| メーカー | SearchableSelect (clearable) | No | `GET /masters/makers`。フロント側フィルタ |
| 商品名 | text input | No | NFKC正規化あいまい検索。Enter で検索実行 |

### 4.3 検索対象

2つのAPIを並列で呼び出し、結果をマージして表示する。

| データソース | API | 備考 |
|------------|-----|------|
| 販売商品マスタ | `GET /sales-goods/master?shopNo=X&supplierNo=Y&goodsName=Z` | 通常の商品 |
| 仕入価格変更予定 + 見積取込明細 | `GET /estimates/price-plan-goods?shopNo=X&goodsName=Z` | メーカー見積商品 |

### 4.4 結果テーブル

| カラム | 説明 |
|--------|------|
| 仕入先 | 販売商品: 仕入先名。変更予定/取込: 「(仕入変更予定)」 |
| 商品コード | 商品コード。なければ `[JAN]4903301295334` 形式 |
| 商品名 | |
| 仕入単価 | |
| 標準売価 | 販売商品のみ表示。変更予定/取込は `-` |

- 仕入変更予定/見積取込データは **青色背景** で区別
- 販売商品と重複する商品コードは変更予定側を非表示
- 行クリックで商品を選択 → ダイアログ閉じ → 明細行に反映
- ダイアログ再オープン時は検索条件をリセット

### 4.5 あいまい検索仕様

全検索で NFKC 正規化を使用:
- `nanox` / `NANOX` / `ＮＡＮＯＸ` / `ｎａｎｏｘ` → すべて同じ結果
- 大文字/小文字、全角/半角を統一して検索

---

## 5. 保存処理

### 5.1 バリデーション（フロント）

| 条件 | エラーメッセージ |
|------|-------------|
| shopNo 未選択 | 「店舗を選択してください」 |
| partnerNo 未選択 | 「得意先を選択してください」 |
| estimateDate 未入力 | 「見積日を入力してください」 |
| priceChangeDate 未入力 | 「価格改定日を入力してください」 |
| 有効明細0件 | 「有効な明細を1件以上入力してください」 |

有効明細 = `goodsCode` が入力済み かつ `goodsPrice > 0`

### 5.2 API

| モード | メソッド | URL |
|--------|--------|-----|
| 新規作成 | POST | `/api/v1/estimates` |
| 修正 | PUT | `/api/v1/estimates/{estimateNo}` |

### 5.3 保存後の処理

- 新規: estimateStatus = "00"（作成）
- 修正: estimateStatus = "20"（修正）
- 明細: delete-insert パターン（全明細を物理削除後に再登録）
- **親子得意先同期**: 保存後に自動実行（詳細は DD_05 参照）
- 成功時: 見積詳細画面（`/estimates/{estimateNo}`）へ遷移

---

## 6. 修正モード

- URL: `/estimates/{estimateNo}/edit`
- `GET /api/v1/estimates/{estimateNo}` で既存データをフォームに展開
- ステータスが "00"（作成）または "20"（修正）の場合のみ編集可能

---

## 7. 見積詳細画面からの導線

| ボタン | 条件 | 遷移先 |
|--------|------|--------|
| 修正 | ステータス "00" or "20" | `/estimates/{estimateNo}/edit` |
| 削除 | ステータス "00" or "20" | 確認ダイアログ → DELETE API → 一覧へ |
| 印刷 | 常時 | ブラウザ印刷ダイアログ |
| 一覧に戻る | 常時 | `/estimates` |
