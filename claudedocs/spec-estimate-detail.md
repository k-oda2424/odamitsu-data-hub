# 見積明細画面 機能仕様書

## 1. 概要

見積番号を指定して、見積のヘッダー情報と明細一覧を表示する画面。旧システムの `estimate_detail_list.html` に相当。印刷用レイアウトも対応する。

### 旧システム対応
| 旧画面 | 旧URL | 新URL |
|--------|--------|-------|
| 見積明細一覧 | `/estimateDetailList?estimateNo=xxx` | `/estimates/{estimateNo}` |

---

## 2. データモデル

### 2.1 使用テーブル
- `t_estimate` — 見積ヘッダー（既存）
- `t_estimate_detail` — 見積明細（既存）
- `m_partner` — 得意先マスタ（結合）
- `m_company` — 会社マスタ（結合）

### 2.2 TEstimateDetail 主要フィールド
| カラム | 型 | 説明 |
|--------|-----|------|
| estimate_no | Integer (PK) | 見積番号 |
| estimate_detail_no | Integer (PK) | 明細番号 |
| goods_code | String | 商品コード |
| goods_name | String | 商品名 |
| specification | String | 仕様 |
| goods_price | BigDecimal | 見積単価 |
| contain_num | BigDecimal | 入数 |
| change_contain_num | BigDecimal | 変更入数（null=変更なし） |
| estimate_case_num | BigDecimal | 見積ケース数 |
| estimate_num | BigDecimal | 見積数量 |
| purchase_price | BigDecimal | 仕入原価 |
| profit_rate | BigDecimal | 粗利率(%) |
| detail_note | String | 備考 |
| display_order | Integer | 表示順 |
| del_flg | String | 削除フラグ |

---

## 3. API設計

### 3.1 見積詳細取得（既存APIを拡張）

```
GET /api/v1/estimates/{estimateNo}
```

**現状**: `EstimateResponse` にヘッダー情報のみ返却。明細が含まれていない。

**拡張**: レスポンスに明細リストを追加。

```json
{
  "estimateNo": 1234,
  "shopNo": 1,
  "partnerCode": "022000",
  "partnerName": "いしい記念病院",
  "destinationNo": 0,
  "estimateDate": "2026-03-15",
  "priceChangeDate": "2026-04-01",
  "estimateStatus": "00",
  "note": "値上改定のご案内",
  "isIncludeTaxDisplay": false,
  "details": [
    {
      "estimateDetailNo": 1,
      "goodsCode": "00112531",
      "goodsName": "ファミリーフレッシュ 4.5L",
      "specification": "4.5L",
      "goodsPrice": 1500,
      "containNum": 4,
      "changeContainNum": null,
      "purchasePrice": 1423,
      "profitRate": 5.1,
      "detailNote": "",
      "displayOrder": 1
    }
  ]
}
```

---

## 4. 画面仕様

### 4.1 見積明細画面（表示）

**パス**: `/estimates/{estimateNo}`

#### ヘッダー部
- 見積番号、ステータス（Badge）
- 見積日、価格改定日
- 得意先コード・得意先名
- 備考（あれば表示）

#### 明細テーブル
| # | カラム | 説明 | 書式 |
|---|--------|------|------|
| 1 | コード | goods_code | |
| 2 | 商品名 | goods_name | |
| 3 | 単価 | goods_price | 通貨書式、右寄せ |
| 4 | 入数 | contain_num（change_contain_numがあればそちら） | 右寄せ |
| 5 | ケース価格 | goods_price × 入数 | 通貨書式、右寄せ |
| 6 | 備考 | detail_note | |

※ 表示順: display_order昇順
※ del_flg='1' は非表示

#### 粗利情報（管理者向け・印刷には含めない）
- 原価、粗利率、粗利額の列を追加表示（`no-print`クラス）

#### 税表示
- `isIncludeTaxDisplay=true` → 「(税込です)」
- `isIncludeTaxDisplay=false` → 「(消費税は含まれておりません)」

#### アクションボタン
- 「修正」— `/estimates/{estimateNo}/edit` に遷移（ステータス "00"/"20" の場合のみ表示）
- 「削除」— 確認ダイアログ → `DELETE /api/v1/estimates/{estimateNo}` → 一覧へ遷移（ステータス "00"/"20" の場合のみ表示）
- 「印刷」— `window.print()` で印刷ダイアログ
- 「一覧に戻る」— `/estimates` に遷移
- 「ステータス変更」— プルダウンで変更 → PUT API

※ 削除成功時は見積一覧のクエリキャッシュを無効化する

---

## 5. 実装ファイル

### 5.1 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `EstimateResponse.java` | `details` フィールド追加（`List<EstimateDetailResponse>`） |
| `EstimateDetailResponse.java` | **新規** — 明細DTO |
| `EstimateController.java` | `get()` メソッドで明細も返却するように変更 |

### 5.2 フロントエンド新規

| ファイル | 内容 |
|---------|------|
| `app/(authenticated)/estimates/[estimateNo]/page.tsx` | ルーティング |
| `components/pages/estimate/detail.tsx` | 見積明細ページコンポーネント |
| `types/estimate.ts` | `EstimateDetailResponse` 型追加 |

---

## 6. 印刷対応

- Tailwind CSSの `print:` バリアントを使用
- アクションボタン・サイドバー・ヘッダーを `print:hidden`
- テーブルに `print:text-xs` でコンパクト化
- ページタイトル: 「御見積書」

---

## 7. 旧システムとの差分

| 項目 | 旧 | 新 |
|------|---|---|
| 表示方式 | 別ページ遷移 | SPA内ページ遷移 |
| 印刷 | CSS no_print | Tailwind print: バリアント |
| ステータス変更 | 別フォーム送信 | インラインSelect + PUT API |
| 得意先情報 | Thymeleaf表示 | API取得 + React表示 |
| 粗利表示 | 管理者のみ表示 | 同様（admin判定） |
