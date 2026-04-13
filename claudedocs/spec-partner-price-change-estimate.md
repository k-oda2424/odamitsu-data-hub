# 得意先価格変更予定作成・見積自動生成 仕様書

| 項目 | 内容 |
|------|------|
| 機能ID | BATCH-ESTIMATE |
| ジョブ名 | partnerPriceChangePlanCreate |
| バッチ画面表示 | カテゴリ「見積管理」/ 説明「得意先価格変更予定作成・見積自動生成」 |
| 最終更新 | 2026-04-07 |

---

## 1. 概要

仕入価格変更予定（m_purchase_price_change_plan）に登録された価格変更データをもとに、
得意先商品（m_partner_goods）に登録されている商品の見積を **同じ掛け率** で自動生成するバッチ処理。

---

## 2. 処理フロー（5ステップ）

```
Step 1: PartnerPriceChangePlanCreateTasklet
  仕入価格変更予定 → 得意先商品価格変更予定の作成
  ※ 同じ掛け率で自動計算

Step 2: ParentPartnerPriceChangePlanCreateTasklet
  子得意先の変更予定 → 親得意先の変更予定の自動生成

Step 3: PriceChangeToEstimateCreateTasklet
  価格変更予定 → 見積（t_estimate + t_estimate_detail）の自動生成

Step 4: ParentEstimateCreatedTasklet
  親見積作成済みの子見積ステータスを「他同グループ提出済(40)」に更新

Step 5: PartnerPriceChangeReflectTasklet
  提出済み見積の価格を得意先商品マスタへ反映
```

---

## 3. 掛け率計算ロジック（Step 1）

### 計算式

```
掛け率 = 現在の得意先売価 ÷ 現在の仕入価格
新売価 = 新仕入価格 × 掛け率（四捨五入、小数点以下0桁）
```

### 具体例

| 項目 | 変更前 | 変更後 |
|------|--------|--------|
| 仕入価格 | 1,000円 | 1,100円 |
| 得意先売価 | 1,200円 | **1,320円**（自動計算） |
| 掛け率 | 1.2 | 1.2（維持） |

### 赤字チェック

新売価で利益率が0以下になる場合:
- `deficitFlg = true` を設定
- 明細備考に「【赤字です】価格修正してください。現単価{beforePrice}円」を追加
- 見積は作成されるが、手動での価格修正が必要

---

## 4. 実行方法

### バッチ管理画面から実行

1. メニュー「バッチ管理」を開く
2. カテゴリ「見積管理」の「得意先価格変更予定作成・見積自動生成」を見つける
3. 「実行」ボタンをクリック
4. ステータスがポーリングで更新される

### API

```
POST /api/v1/batch/execute/partnerPriceChangePlanCreate
GET  /api/v1/batch/status/partnerPriceChangePlanCreate
```

---

## 5. 前提条件

- `m_purchase_price_change_plan` に `partnerPriceChangePlanCreated = false` のデータが存在すること
- 対象商品が `m_partner_goods` に登録されていること（`goodsPrice != 0`）
- 仕入先見積取込や手動登録で仕入価格変更予定が作成済みであること

---

## 6. 実装ファイル

| ファイル | 内容 |
|---------|------|
| `batch/estimate/config/PartnerPriceChangePlanCreateConfig.java` | **新規** — Spring Batch ジョブ定義（5ステップ） |
| `batch/estimate/PartnerPriceChangePlanCreateTasklet.java` | 既存 — Step 1 ロジック |
| `batch/estimate/ParentPartnerPriceChangePlanCreateTasklet.java` | 既存 — Step 2 ロジック |
| `batch/estimate/PriceChangeToEstimateCreateTasklet.java` | 既存 — Step 3 ロジック |
| `batch/estimate/ParentEstimateCreatedTasklet.java` | 既存 — Step 4 ロジック |
| `batch/estimate/PartnerPriceChangeReflectTasklet.java` | 既存 — Step 5 ロジック |
| `api/batch/BatchController.java` | 変更 — JOB_DEFINITIONS にジョブ追加 |
