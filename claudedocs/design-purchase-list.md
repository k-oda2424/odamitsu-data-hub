# 仕入一覧（Purchase List）新規 + 仕入価格一覧へのリネーム 設計書

作成日: 2026-04-14
ステータス: Draft
関連: `/finance/accounts-payable`（買掛金一覧）からの drill-down

## 1. 背景・目的

現状、`/purchase-prices` が画面タイトル「仕入一覧」だが、表示しているのは **仕入価格マスタ (`m_purchase_price`)** であり、商品ごとの単価情報。買掛金一覧から「なぜこの金額？」とドリルダウンしても、価格マスタが表示されるだけで実際の仕入実績の合算は確認できない。

**目的**:
1. `/purchase-prices` を「**仕入価格一覧**」に正しくリネーム
2. 新規に「**仕入一覧**」(`/purchases`) を作成し、`t_purchase` / `t_purchase_detail` の実績を期間・仕入先で絞り込み + 集計表示
3. 買掛金一覧の drill-down リンク先を新「仕入一覧」に切替

## 2. 用語整理

| 用語 | 実体 | 既存画面 |
|---|---|---|
| 仕入価格（マスタ） | `m_purchase_price` (商品×仕入先×得意先の単価) | `/purchase-prices` |
| 仕入実績（取引） | `t_purchase` (ヘッダ) + `t_purchase_detail` (明細) | **未実装** |
| 買掛金集計 | `t_accounts_payable_summary` (期間×支払先×税率) | `/finance/accounts-payable` |

`t_accounts_payable_summary` は `t_purchase_detail` を期間で集計したもの（`AccountsPayableSummaryCalculator`）。検証は同じソース＝`t_purchase_detail` を別軸（明細単位）で見られると差異の原因が分かる。

## 3. 画面構成

### 3.1 既存リネーム（`/purchase-prices`）
- ページタイトル: 「仕入一覧」→ **「仕入価格一覧」**
- サイドバー: 「仕入一覧」→ **「仕入価格一覧」**
- 機能・URL は変更なし（既存ブックマーク互換）

### 3.2 新規「仕入一覧」`/purchases`
既存の `/purchases` ページが basic 実装されていたが、これを置き換える。

#### 検索条件
| 項目 | キー | UI |
|---|---|---|
| 店舗 | `shopNo` | Select（admin のみ。デフォルト=自店舗、買掛金 drill-down で `1` 固定） |
| 仕入先（支払先絞り） | `paymentSupplierNo` | SearchableSelect（`m_payment_supplier`） |
| 仕入先（子・個別） | `supplierNo` | SearchableSelect（`m_supplier`、shopNo に従う） |
| 仕入日 from | `fromDate` | DatePicker |
| 仕入日 to | `toDate` | DatePicker |
| 商品コード | `goodsCode` | Input（明細単位フィルタ） |
| 仕入No | `purchaseNo` | Input |

`paymentSupplierNo` 指定時は子仕入先一覧に展開。`supplierNo` 単独指定でも検索可。

#### 表示モード（タブ切替）
1. **ヘッダ表示** (`t_purchase` 単位、デフォルト)
   - 列: 仕入No / 仕入コード / 仕入日 / 仕入先 / 税率 / 税抜金額 / 税込金額 / 備考
   - 行クリックで明細展開（次タブと連動）
2. **明細表示** (`t_purchase_detail` 単位)
   - 列: 仕入No / 仕入日 / 仕入先 / 商品コード / 商品名 / 数量 / 単価 / 税抜小計 / 税率 / 税込小計

#### 集計（フッタ + サイドサマリー）
- **税率別集計**: 税率（10% / 軽8% / 非課税）ごとの 件数・税抜計・消費税・税込計
- **総合計**: 全税率合算の 件数・税抜・税込
- **支払先別小計**（`paymentSupplierNo` 絞り込み時、子仕入先ごと）: optional

サマリー表示位置: 検索結果テーブルの上部に固定カード、または fold-out パネル。

#### 買掛金との照合表示（オプション）
画面上部に「対応する買掛金: 1,771,506円 / 仕入合計: 1,771,506円 / 差: 0円」表示。drill-down 元の `transactionMonth` が URL にあれば該当 `t_accounts_payable_summary` を引いて表示。

## 4. データ取得

### 4.1 backend API

#### `GET /api/v1/purchases`（拡張）
既存エンドポイントを拡張。

| param | 型 | 説明 |
|---|---|---|
| `shopNo` | Integer | （既存） |
| `paymentSupplierNo` | Integer | 子仕入先に展開 |
| `supplierNo` | Integer | 単一指定 |
| `fromDate` | LocalDate | `purchase_date >= ` |
| `toDate` | LocalDate | `purchase_date <= ` |
| `goodsCode` | String | （明細単位の絞り、ヘッダ含めるか要検討） |
| `purchaseNo` | Integer | 単一 |

レスポンス: `PurchaseListResponse`
```json
{
  "rows": [PurchaseHeader],
  "summary": {
    "totalRows": 12,
    "totalAmountExcTax": 1234567,
    "totalAmountIncTax": 1358000,
    "byTaxRate": [
      { "taxRate": 10, "rows": 10, "amountExcTax": 1100000, "taxAmount": 110000, "amountIncTax": 1210000 },
      { "taxRate": 8,  "rows": 2,  "amountExcTax": 134567,  "taxAmount": 10765,  "amountIncTax": 145332 }
    ]
  }
}
```

`PurchaseHeader`:
```json
{
  "purchaseNo": 12345,
  "purchaseCode": "P-2026-001",
  "purchaseDate": "2026-02-15",
  "shopNo": 1,
  "supplierNo": 21,
  "supplierCode": "0021",
  "supplierName": "イトマン株式会社",
  "purchaseAmount": 100000,
  "includeTaxAmount": 110000,
  "taxAmount": 10000,
  "taxRate": 10,
  "note": null
}
```

#### `GET /api/v1/purchases/{purchaseNo}/details`
- 単一仕入の明細リスト（`t_purchase_detail`）を返す
- 行クリック→明細展開で利用

### 4.2 backend 内部
- `TPurchaseSpecification#supplierNoListContains(List<Integer>)` 追加（既存設計案を再利用）
- `TPurchaseService#find(shopNo, supplierNoList, fromDate, toDate, delFlg)` オーバーロード追加
- `MSupplierService#findByPaymentSupplierNo` 流用で paymentSupplierNo→子展開
- 集計はアプリ層 `Stream` で Group by taxRate（明細件数<10000程度想定）

## 5. URL 例（buy-pay drill-down）

```
/purchases
  ?shopNo=1
  &paymentSupplierNo=21
  &fromDate=2026-01-21
  &toDate=2026-02-20
  &supplierName=イトマン株式会社
  &transactionMonth=2026-02-20   # オプション、買掛金との照合表示用
```

買掛金一覧側のリンク生成ロジック:
- `fromDate = transactionMonth - 1ヶ月 + 1日`（例: 2026-02-20 → 2026-01-21）
- `toDate = transactionMonth`（2026-02-20）
- `paymentSupplierNo = row.supplierNo`（買掛金行の supplierNo は実質 payment_supplier_no）
- `shopNo = 1` 固定（支払先集約）

## 6. リネーム影響

### 6.1 サイドバー
`frontend/components/layout/Sidebar.tsx` の「仕入一覧 / `/purchase-prices`」ラベルを変更。新しい「仕入一覧 / `/purchases`」を追加（仕入価格一覧の上）。

### 6.2 既存テスト
`purchase-prices-drilldown.spec.ts` の検証文言を「仕入価格一覧」に変更。
新規 `purchases-drilldown.spec.ts` を作成。

### 6.3 既存メモリ・ドキュメント
`feature-payment-mf-import.md` 等の言及箇所を必要に応じて更新（リンク先）。

## 7. 実装順

1. **Backend**
   - `TPurchaseSpecification#supplierNoListContains` 追加
   - `TPurchaseService#find(...)` オーバーロード追加
   - `PurchaseController#listPurchases` 拡張（paymentSupplierNo / supplierNo / fromDate / toDate / goodsCode）
   - `PurchaseListResponse` 新規（rows + summary）
   - `PurchaseController#getDetails(purchaseNo)` 新規
   - `compileJava` + ユニットテスト（集計ロジック）
2. **Frontend**
   - サイドバー: ラベル変更 + 新メニュー追加
   - `/purchase-prices` のページタイトルを「仕入価格一覧」に
   - 新規 `/purchases` ページ作成
     - 検索フォーム（shop / paymentSupplier / supplier / dateFrom / dateTo / goodsCode / purchaseNo）
     - サマリーカード（税率別 + 総合計）
     - DataTable（ヘッダ表示）
     - 行クリック→明細ダイアログ（または下部展開）
     - URL params 対応（drill-down 互換）
   - 買掛金一覧のリンク先を `/purchase-prices` → `/purchases` に変更（fromDate/toDate 付与）
3. **テスト**
   - E2E: `/purchases` のドリルダウン
   - 既存 `purchase-prices-drilldown.spec.ts` の更新（新リンク先・タイトル）
4. **ドキュメント**
   - メモリ更新

## 8. 残論点

- **明細単位の表示モード**: 件数が多い場合のページネーション要否（ヘッダ単位なら同期間で数十〜数百件、明細単位だと数百〜数千件）
- **集計基準**: `t_purchase.purchase_amount`（ヘッダ集計）vs `t_purchase_detail.subtotal` SUM（明細集計）。ヘッダ値が信頼できるならヘッダで OK、明細から再計算する場合は性能配慮
- **`supplierNo=303`の除外**: `AccountsPayableSummaryCalculator` で 303 を除外しているが、仕入一覧でも除外するか？（買掛との一致を取るなら除外、純粋な仕入実績なら含める）→ 「買掛との一致」モードと「全仕入」モードのトグル？
- **削除データ**: `del_flg='1'` は表示する？（買掛集計は除外）
- **`taxRate` がヘッダにあるが税率混在の仕入の扱い**: t_purchase は単一税率前提か、複数税率明細を含むか確認必要

## 9. スコープ外（次フェーズ候補）

- 仕入実績 CSV エクスポート
- 仕入登録/編集（現状 SMILE バッチ取込のみ）
- 商品別の仕入トレンド分析
