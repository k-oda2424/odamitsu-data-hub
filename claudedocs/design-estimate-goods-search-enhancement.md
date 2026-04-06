# 見積作成画面 商品検索の強化 設計書

| 項目 | 内容 |
|------|------|
| 対象機能 | 見積作成画面の明細商品選択 |
| 作成日 | 2026-04-06 |
| 最終更新 | 2026-04-06（最終実装反映） |

---

## 1. 課題と対応

| 課題 | 対応 |
|------|------|
| メーカー見積商品が検索できない | Ajax検索・ポップアップの両方で m_purchase_price_change_plan + t_quote_import_detail を検索対象に追加 |
| 商品コードがない商品 | JANコードを代用表示（`[JAN]4903301295334` 形式） |
| 商品名で検索ヒットしない | NFKC正規化によるあいまい検索を全検索パスに統一 |
| 探すボタンがない | 🔍ボタン → Dialog による商品検索ポップアップを実装 |
| goodsNo が必須だと未登録商品が使えない | goodsNo を nullable に変更 |

---

## 2. 商品入力の2つの導線

### 2.1 商品コード直接入力（Ajax）
- 商品コード（8文字以下）またはJANコード（9文字以上）を入力
- blur / Enter で `GET /estimates/goods-search?shopNo=X&code=YYY` を呼び出し
- 検索順序（商品コード）: 販売商品マスタ → ワーク → 仕入変更予定
- 検索順序（JANコード）: 商品マスタ → 仕入変更予定 → 見積取込明細

### 2.2 商品検索ポップアップ（🔍ボタン）
- Dialog で仕入先・メーカー・商品名の3条件で検索
- shopNo はヘッダから自動引き継ぎ
- 販売商品マスタ + 仕入変更予定/見積取込明細を並列検索・マージ表示

---

## 3. API一覧

### GET /estimates/goods-search（Ajax検索）
- パラメータ: `shopNo`(必須), `code`(必須), `partnerNo`(任意), `destinationNo`(任意)
- 8文字以下 → 商品コード検索、9文字以上 → JANコード検索
- 特値（得意先別価格）も自動反映

### GET /estimates/price-plan-goods（ポップアップ用）
- パラメータ: `shopNo`(任意), `goodsName`(任意)
- m_purchase_price_change_plan + t_quote_import_detail(PENDING) を商品名で NFKC あいまい検索

### GET /sales-goods/master（ポップアップ用・既存）
- パラメータ: `shopNo`(必須), `supplierNo`(任意), `goodsName`(任意)
- 販売商品マスタを検索

---

## 4. goodsNo nullable 設計

### 背景
- メーカー見積段階では商品が確定していない（JANコード変更、リニューアル、廃番→後継品）
- 自動登録すると商品マスタにゴミが溜まる
- 見積取込の突合フローで正式登録する流れが既にある

### 実装
- `EstimateDetailCreateRequest.goodsNo`: `@NotNull` 除去 → nullable
- `t_estimate_detail.goods_no`: DBカラムも nullable（元から）
- フロントのバリデーション: `goodsCode` と `goodsPrice > 0` が有効条件（goodsNo は不問）
- 見積明細は「商品コード/JAN + 商品名 + 単価」があれば成立

---

## 5. 実装ファイル一覧

### バックエンド
| ファイル | 内容 |
|---------|------|
| `api/estimate/EstimateController.java` | goods-search（4段階検索）、price-plan-goods エンドポイント |
| `dto/estimate/EstimateDetailCreateRequest.java` | goodsNo の @NotNull 除去 |
| `dto/estimate/EstimateGoodsSearchResponse.java` | janCode, source, purchasePriceChangePlanNo フィールド |
| `domain/service/purchase/MPurchasePriceChangePlanService.java` | findByGoodsName メソッド追加 |
| `domain/specification/purchase/MPurchasePriceChangePlanSpecification.java` | goodsNameContains 追加 |
| `domain/repository/purchase/TQuoteImportDetailRepository.java` | JANコード・商品名検索メソッド追加 |

### フロントエンド
| ファイル | 内容 |
|---------|------|
| `components/pages/estimate/form.tsx` | 商品コード入力 + 🔍検索ボタン、shopNo未設定時disable |
| `components/pages/estimate/GoodsSearchDialog.tsx` | 商品検索ポップアップ（900px幅） |
| `types/estimate.ts` | EstimateGoodsSearchResponse に janCode/source 追加、goodsNo nullable |
