# 07. 財務・会計ドメイン 機能仕様書

**対象システム**: stock-app（小田光株式会社 社内システム）
**作成日**: 2026-02-23
**対象パッケージ**: `jp.co.oda32.app.finance`, `jp.co.oda32.batch.finance`, `jp.co.oda32.domain.model.finance`, `jp.co.oda32.domain.repository.finance`, `jp.co.oda32.domain.service.finance`, `jp.co.oda32.domain.specification.finance`

---

## 1. 概要

財務・会計ドメインは、小田光株式会社の会計処理を支援するモジュールである。主な機能は以下の通り。

- **買掛金管理**: 20日締め仕入データの集計、SMILE基幹システム支払情報との照合
- **売掛金管理**: 複数締め日（月末・20日・15日・都度現金払い）対応の売上集計
- **請求書管理**: SMILE請求実績データの閲覧・管理
- **マネーフォワード連携**: 買掛仕入仕訳CSV・売掛売上仕訳CSVの出力

### 外部システム連携

| システム | 連携方向 | 内容 |
|----------|----------|------|
| SMILE（基幹システム） | 取込 | 支払情報CSV（w_smile_payment経由）、請求実績データ（t_invoice） |
| マネーフォワード | 出力 | 仕訳CSV（19カラム形式） |

---

## 2. 画面一覧

| 画面名 | URL | コントローラ | テンプレート |
|--------|-----|-------------|-------------|
| 買掛金集計確認一覧 | GET `/finance/accountsPayable` | `AccountsPayableController` | `finance/accountsPayable/list` |
| 買掛金集計詳細 | GET `/finance/accountsPayable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}` | `AccountsPayableController` | `finance/accountsPayable/detail` |
| 請求書一覧 | GET `/finance/invoice/list` | `InvoiceListController` | `finance/invoice/list` |
| 請求書詳細 | GET `/finance/invoice/detail/{invoiceId}` | `InvoiceListController` | `finance/invoice/detail` |

---

## 3. 買掛金管理

### 3.1 買掛金集計確認一覧画面

**URL**: `GET /finance/accountsPayable`
**コントローラクラス**: `jp.co.oda32.controller.finance.AccountsPayableController`
**テンプレート**: `src/main/resources/templates/finance/accountsPayable/list.html`

#### 検索条件

| パラメータ名 | 型 | 説明 | デフォルト値 |
|-------------|-----|------|-------------|
| `transactionMonth` | String (yyyy-MM) | 取引月 | 当月20日（20日前なら前月20日） |

デフォルト取引月の算出ロジック:
```java
LocalDate date = LocalDate.now().withDayOfMonth(20);
if (LocalDate.now().getDayOfMonth() < 20) {
    date = date.minusMonths(1); // 20日前なら前月20日
}
```

#### 表示項目（検証済みデータ）

| 項目名 | 説明 | データソース |
|--------|------|-------------|
| ショップ | `shop_no` | `TAccountsPayableSummary.shopNo` |
| 仕入先 | 仕入先名 | `MSupplier`（`supplierMap`経由） |
| 税率 | 消費税率（%） | `TAccountsPayableSummary.taxRate` |
| 買掛金額（税込） | 集計後税込金額 | `TAccountsPayableSummary.taxIncludedAmountChange` |
| SMILE支払額 | SMILE基幹システムからの支払額 | `TAccountsPayableSummary.taxIncludedAmount` |
| 差額 | SMILE支払額 - 買掛金額 | `TAccountsPayableSummary.paymentDifference` |
| 検証結果 | 一致(1) / 不一致(0) | `TAccountsPayableSummary.verificationResult` |
| 操作 | 詳細リンク | - |

#### 表示セクション

画面は2セクションに分かれる。

1. **検証済み買掛金集計**（緑ヘッダー）: `verificationResult IS NOT NULL` のデータ
2. **未検証の買掛金集計**（黄色ヘッダー）: `verificationResult IS NULL` のデータ

不一致件数が1件以上の場合、警告メッセージを表示する。

---

### 3.2 買掛金集計詳細画面

**URL**: `GET /finance/accountsPayable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}`
**URL例**: `/finance/accountsPayable/1/100/2025-04/10`

#### 表示項目

| 項目名 | データソース |
|--------|-------------|
| 仕入先名 | `MSupplier.supplierName` |
| shop_no | `TAccountsPayableSummary.shopNo` |
| 取引月 | `TAccountsPayableSummary.transactionMonth` |
| 税率 | `TAccountsPayableSummary.taxRate` |
| 買掛金額（税込） | `TAccountsPayableSummary.taxIncludedAmountChange` |
| 買掛金額（税抜） | `TAccountsPayableSummary.taxExcludedAmountChange` |
| SMILE支払額（税込） | `TAccountsPayableSummary.taxIncludedAmount` |
| SMILE支払額（税抜） | `TAccountsPayableSummary.taxExcludedAmount` |
| 差額 | `TAccountsPayableSummary.paymentDifference` |
| 検証結果 | `TAccountsPayableSummary.verificationResult` |

#### 手動検証機能

詳細画面から手動で検証済み支払額を入力し更新できる。

**エンドポイント**: `POST /finance/accountsPayable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/verify`

手動検証時の処理:
1. 入力した `verifiedAmount`（検証済み支払額税込）を `taxIncludedAmount` にセット
2. 税抜金額計算: `taxExcludedAmount = verifiedAmount ÷ (1 + taxRate/100)`（小数点以下切り捨て）
3. 差額計算: `paymentDifference = verifiedAmount - taxIncludedAmountChange`
4. 一致判定: `|差額| <= 100円` なら一致（`verificationResult = 1`）、それ以外は不一致（`verificationResult = 0`）

---

### 3.3 20日締め処理

買掛金は20日締め（前月21日〜当月20日）で集計される。

**集計期間**:
- 開始日: `YearMonth.from(targetDate).minusMonths(1).atDay(21)`（前月21日）
- 終了日: `YearMonth.from(targetDate).atDay(20)`（当月20日）

**除外対象**:
- `supplier_no = 303`（小田光在庫表の手打ち商品）は棚卸時の手動入力データのため除外

---

### 3.4 買掛金集計ロジック

**クラス**: `jp.co.oda32.batch.finance.service.AccountsPayableSummaryCalculator`

#### 集計キー（SummaryKey）

```java
public class SummaryKey {
    private final Integer shopNo;              // ショップ番号
    private final Integer paymentSupplierNo;   // 支払先番号
    private final String paymentSupplierCode;  // 支払先コード
    private final BigDecimal taxRate;          // 税率
}
```

#### 集計手順

1. 指定期間内の仕入明細（`TPurchaseDetail`）を取得
2. `supplier_no != 303` でフィルタリング
3. `(shopNo, paymentSupplierNo, paymentSupplierCode, taxRate)` でグループ化
4. 各グループで**税抜金額（subtotal）のみを集計**（消費税は後で一括計算）
5. 消費税額を一括計算:
   ```
   calculatedTaxAmount = taxExcludedAmount × taxRate ÷ 100  （小数点以下HALF_DOWN切り捨て）
   taxIncludedAmountChange = taxExcludedAmount + calculatedTaxAmount
   ```
6. 金額がゼロのエントリは除外してエンティティを生成
7. `transactionMonth` には集計期間の終了日（当月20日）をセット

#### 注意事項

- 消費税は明細ごとではなく、**仕入先・税率単位で一括計算**する
- これにより端数の累積誤差を防ぐ
- デフォルト税率: `taxRate IS NULL` の場合は10%として扱う

---

### 3.5 SMILE支払検証

**クラス**: `jp.co.oda32.batch.finance.service.SmilePaymentVerifier`

#### 検証の概要

SMILE基幹システムの支払情報と、集計した買掛金額を**仕入先コード単位の合計で比較**する。

#### 5円許容差ルール

```
差額 = SMILE支払額合計 - 再計算した買掛集計税込合計
|差額| < 5円 → 「一致」として扱い、SMILE支払額に合わせる
差額 = 0円 → 「一致」
|差額| >= 5円 → 「不一致」
```

コード:
```java
public boolean isMatched() {
    return (difference != null && difference.compareTo(BigDecimal.ZERO) == 0)
            || adjustedToSmilePayment
            || (difference != null && difference.abs().compareTo(new BigDecimal(5)) < 0);
}
```

#### SMILE支払額への調整

差額が5円未満（かつゼロでない）場合、**税込金額が最大のレコード**を調整してSMILE支払額に合わせる。

調整ロジック:
```
adjustedAmount = smilePaymentAmount - (他のレコードの税込金額合計)
largestSummary.taxIncludedAmountChange = adjustedAmount
```

調整後マイナスになる場合は差額のみ記録し、金額は調整しない。

#### ショップ間マッピング

`shop_no=2` の仕入先データは、`MSupplierShopMapping` を使って `shop_no=1` の仕入先コードにマッピングし、SMILE支払情報と照合する。

#### 検証結果フラグ

| `verificationResult` | 意味 |
|---------------------|------|
| `1` | 一致（マネーフォワードエクスポート可） |
| `0` | 不一致（マネーフォワードエクスポート不可） |
| `null` | 未検証 |

`mf_export_enabled`:
- `true`: マネーフォワード連携対象
- `false`: 連携対象外（SMILE支払情報なし、または差額大）

---

### 3.6 税額再計算（TaxCalculationHelper）

**クラス**: `jp.co.oda32.batch.finance.helper.TaxCalculationHelper`

税率ごとの内訳から正確な税込金額を計算する。

```java
// 各税率ごとに計算してから合計
BigDecimal taxAmount = taxExcluded × taxRate ÷ 100  （小数点以下切り捨て DOWN）
BigDecimal taxIncluded = taxExcluded + taxAmount
totalTaxIncluded += taxIncluded
```

税額計算:
```java
public static BigDecimal calculateTaxAmount(BigDecimal baseAmount, BigDecimal taxRate) {
    return baseAmount × taxRate ÷ 100  （RoundingMode.DOWN）
}
```

---

## 4. 売掛金管理

### 4.1 売掛金集計

**クラス**: `jp.co.oda32.batch.finance.TAccountsReceivableSummaryTasklet`
**ジョブ名**: `accountsReceivableSummary`

#### 締め日タイプ

| コード | PaymentType | 説明 | 集計期間 |
|--------|-------------|------|----------|
| `0` または `null` | `MONTH_END` | 月末締め | 当月1日〜当月末日 |
| `20` | `DAY_20` | 20日締め | 前月21日〜当月20日 |
| `15` | `DAY_15` | 15日締め | 前月16日〜当月15日 |
| `-1` | `CASH_ON_DELIVERY` | 都度現金払い | 当月1日〜当月末日（月末扱い） |

#### TAccountsReceivableSummaryテーブルの `transactionMonth` の値

| 締め日タイプ | `transactionMonth` の値 |
|-------------|------------------------|
| 月末締め | 当月末日 |
| 20日締め | 当月20日 |
| 15日締め | 当月15日 |
| 都度現金払い | 当月末日（月末締め扱い） |

#### 集計手順

1. 得意先マスタ（`MPartner`）を全件プリロードして締め日タイプ別に分類
2. 各締め日グループごとに注文明細（`TOrderDetail`）を期間で取得
3. `(shopNo, partnerNo, taxRate, isOtakeGarbageBag, orderNo)` でグループ化してサマリー生成
4. TINVOICE（SMILEから取り込んだ請求書）と金額を照合
5. 照合OKのサマリーのみDBに保存

#### 税込金額計算

税区分（`TaxType`）によって計算方法が異なる。

| TaxType | 処理 |
|---------|------|
| `TAX_EXCLUDE`（税抜） | `totalAmountExcludingTax = totalAmount` |
| `TAXABLE_INCLUDE`（税込） | `totalAmountExcludingTax = totalAmount ÷ (1 + taxRate/100)`（小数点以下切り捨て） |
| `TAX_FREE`（非課税） | `totalAmountExcludingTax = totalAmount` |

税込金額の決定:
- `TAXABLE_INCLUDE`が全明細に適用されている場合: 元の税込金額をそのまま使用
- それ以外: `税抜合計 + 計算した消費税額`

消費税計算:
```
calculatedTaxAmount = totalAmountExcludingTax × (taxRate ÷ 100)  （RoundingMode.DOWN）
totalAmountIncludingTax = totalAmountExcludingTax + calculatedTaxAmount
```

1円誤差の吸収:
```
diff = 元の税込金額(切捨て) - 計算した税込金額
|diff| == 1円 → 元の税込金額（請求書ベース）を採用してログ警告
```

#### 請求書との照合

```
| 集計金額 == 請求書金額              | → OK。そのまま保存
| |集計金額 - 請求書金額| <= tolerance | → 警告ログ出力。請求書金額に合わせて保存
| |集計金額 - 請求書金額| > tolerance  | → NG。保存しない（エラーログ）
| 請求書が見つからない                 | → 金額0なら情報ログ、金額>0なら警告ログ
```

許容差額（tolerance）: デフォルト3円（設定で変更可）
設定キー: `batch.accounts-receivable.invoice-amount-tolerance`

#### 特殊処理

##### 「上様」（999999）扱い

得意先コードが**7桁以上**の場合、または都度現金払いの場合、**「999999」（上様）** として処理する。

- 集計後、請求書（`t_invoice`）の金額で上書き
- 比率で各サマリーの金額を調整: `adjustedAmount = summary.taxIncludedAmountChange × (invoiceAmount / totalAmount)`

##### 得意先コード「301491」（クリーンラボ）

- 実際の店舗番号が3であっても、**請求書検索は店舗番号1で行う**
- 売掛金CSVの貸方部門は「クリーンラボ」、貸方補助科目は「クリーンラボ売上高」

##### 得意先コード「000231」（四半期請求）

特殊な請求書検索ロジックを使用する。

- 基本: 当月15日締めの請求書を検索
- 前月が2月・5月・8月・11月（四半期末）の場合: 前月末締めの請求書も合算

```java
private boolean isSpecialMonth(LocalDate date) {
    int month = date.getMonthValue();
    return month == 2 || month == 5 || month == 8 || month == 11;
}
```

##### 大竹市ゴミ袋（isOtakeGarbageBag）

以下の商品コードを含む明細は `isOtakeGarbageBag = true` として分離集計する。

```java
private static final Set<String> OTAKE_GARBAGE_BAG_GOODS_CODES = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList(
        "00100001", "00100003", "00100005",
        "00100101", "00100103", "00100105"
    ))
);
```

ゴミ袋の売掛金CSVは通常の売掛金とは別勘定科目（「未収入金」）で出力される。

#### 締め日文字列フォーマット（請求書検索用）

```
月末締め / 都度現金払い → "YYYY/MM/末"
特定日締め（15日・20日等） → "YYYY/MM/DD"
```

例: `"2025/04/末"`, `"2025/04/20"`, `"2025/04/15"`

---

### 4.2 売掛金サービス（TAccountsReceivableSummaryService）

**クラス**: `jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService`

#### 主要メソッド

| メソッド名 | 概要 |
|-----------|------|
| `findByTransactionMonth(LocalDate)` | 指定取引月のデータを取得 |
| `findByCutoffYearMonth(YearMonth)` | 指定年月の15日・20日・月末データを取得 |
| `findByDateRange(LocalDate, LocalDate)` | 指定期間のデータを取得 |
| `getByPK(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag)` | 主キーで1件取得 |

`findByCutoffYearMonth` のロジック:
```java
LocalDate day15 = yearMonth.atDay(15);
LocalDate day20 = yearMonth.atDay(20);
LocalDate endOfMonth = yearMonth.atEndOfMonth();
return repository.findByTransactionMonthIn(Arrays.asList(day15, day20, endOfMonth));
```

---

## 5. 請求書管理

### 5.1 請求書一覧画面

**URL**: `GET /finance/invoice/list`（初期表示） / `POST /finance/invoice/search`（検索）
**コントローラクラス**: `jp.co.oda32.app.finance.InvoiceListController`
**テンプレート**: `src/main/resources/templates/finance/invoice/list.html`

#### 検索条件

| フィールド名 | 型 | 説明 | 検索方式 |
|-------------|-----|------|----------|
| `closingDate` | String (YYYY/MM) | 締め日 | 前方一致LIKE (`YYYY/MM%`) |
| `shopNo` | Integer | ショップ番号 | 完全一致 |
| `partnerCode` | String | 得意先コード | 前方一致LIKE |
| `partnerName` | String | 得意先名 | 部分一致LIKE |

**バリデーション**: 締め日または得意先情報（得意先コード・得意先名）のいずれかを必須入力とする。

**ソート順**: `closingDate` 降順

#### 表示項目

| 表示名 | データソース |
|--------|-------------|
| 請求ID | `TInvoice.invoiceId` |
| 得意先コード | `TInvoice.partnerCode` |
| 得意先名 | `TInvoice.partnerName` |
| 締め日 | `TInvoice.closingDate` |
| 前回請求残高 | `TInvoice.previousBalance` |
| 入金合計 | `TInvoice.totalPayment` |
| 繰越残高 | `TInvoice.carryOverBalance` |
| 純売上 | `TInvoice.netSales` |
| 消費税額 | `TInvoice.taxPrice` |
| 純売上（税込） | `TInvoice.netSalesIncludingTax` |
| 入金日 | `TInvoice.paymentDate`（インライン編集可） |
| 今回請求額 | `TInvoice.currentBillingAmount` |

#### 入金日の更新（Ajax）

一覧画面の入金日フィールドを変更すると、Ajax（`POST /finance/invoice/updatePaymentDate`）で即座に更新される。

```
入力パラメータ:
  invoiceId: Integer
  paymentDate: String（ISO DATE形式）

レスポンス:
  "success" または "error"（テキスト）
```

---

### 5.2 請求書詳細画面

**URL**: `GET /finance/invoice/detail/{invoiceId}`
**テンプレート**: `src/main/resources/templates/finance/invoice/detail.html`

#### 表示項目

| 項目名 | データソース |
|--------|-------------|
| 得意先名 | `TInvoice.partnerName` |
| 得意先コード | `TInvoice.partnerCode` |
| 締め日 | `TInvoice.closingDate` |
| ショップ番号 | `TInvoice.shopNo` |
| 前回請求残高 | `TInvoice.previousBalance` |
| 入金合計 | `TInvoice.totalPayment` |
| 繰越残高 | `TInvoice.carryOverBalance` |
| 純売上 | `TInvoice.netSales` |
| 消費税額 | `TInvoice.taxPrice` |
| 純売上（税込） | `TInvoice.netSalesIncludingTax` |
| 今回請求額 | `TInvoice.currentBillingAmount` |

---

### 5.3 請求書検索仕様（TInvoiceSpecification）

**クラス**: `jp.co.oda32.domain.specification.finance.TInvoiceSpecification`

| メソッド名 | 検索対象 | 方式 |
|-----------|---------|------|
| `closingDateContains(String)` | `closing_date` | 前方一致LIKE (`closingDate + "%"`) |
| `shopNoContains(Integer)` | `shop_no` | 完全一致 |
| `partnerCodeContains(String)` | `partner_code` | 前方一致LIKE (`partnerCode + "%"`) |
| `partnerNameContains(String)` | `partner_name` | 部分一致LIKE (`"%" + partnerName + "%"`) |
| `currentBillingAmountContains(Double, Double)` | `current_billing_amount` | 範囲検索（BETWEEN） |
| `previousBalanceContains(Double, Double)` | `previous_balance` | 範囲検索（BETWEEN） |

---

## 6. マネーフォワード連携

### 6.1 仕訳CSVフォーマット（MFJournalCsv）

**クラス**: `jp.co.oda32.batch.finance.MFJournalCsv`

マネーフォワード取込用の仕訳CSVは**19カラム**構成で、すべての値はダブルクォートで囲まれる。

#### CSVヘッダー（固定）

```
"取引No","取引日","借方勘定科目","借方補助科目","借方部門","借方取引先","借方税区分","借方インボイス","借方金額(円)","貸方勘定科目","貸方補助科目","貸方部門","貸方取引先","貸方税区分","貸方インボイス","貸方金額(円)","摘要","タグ","メモ"
```

#### カラム定義

| No | カラム名 | フィールド | 説明 |
|----|---------|-----------|------|
| 1 | 取引No | `transactionNo` | 連番（バッチ内で採番） |
| 2 | 取引日 | `transactionDate` | yyyy/MM/dd形式 |
| 3 | 借方勘定科目 | `debitAccount` | 借方の勘定科目 |
| 4 | 借方補助科目 | `debitSubAccount` | 借方の補助科目 |
| 5 | 借方部門 | `debitDepartment` | 借方の部門 |
| 6 | 借方取引先 | `debitPartner` | 借方の取引先 |
| 7 | 借方税区分 | `debitTaxCategory` | 借方の税区分 |
| 8 | 借方インボイス | `debitInvoice` | インボイス番号 |
| 9 | 借方金額(円) | `debitAmount` | 借方金額（整数） |
| 10 | 貸方勘定科目 | `creditAccount` | 貸方の勘定科目 |
| 11 | 貸方補助科目 | `creditSubAccount` | 貸方の補助科目 |
| 12 | 貸方部門 | `creditDepartment` | 貸方の部門 |
| 13 | 貸方取引先 | `creditPartner` | 貸方の取引先 |
| 14 | 貸方税区分 | `creditTaxCategory` | 貸方の税区分 |
| 15 | 貸方インボイス | `creditInvoice` | インボイス番号 |
| 16 | 貸方金額(円) | `creditAmount` | 貸方金額（整数） |
| 17 | 摘要 | `summary` | 摘要文 |
| 18 | タグ | `tag` | タグ（通常空） |
| 19 | メモ | `memo` | メモ（通常空） |

金額は小数点以下切り捨て、整数文字列として出力される。

---

### 6.2 買掛仕入仕訳CSV出力

**クラス**: `jp.co.oda32.batch.finance.AccountsPayableToPurchaseJournalTasklet`
**ジョブ名**: `purchaseJournalIntegration`

#### 出力内容

買掛金集計データをマネーフォワードの「仕入高 / 買掛金」仕訳として出力する。

#### 対象データの選択

通常モード（`forceExport=false`）:
- `mf_export_enabled = true`（SMILE検証一致）のデータのみ
- `taxIncludedAmountChange != 0` のデータのみ

強制エクスポートモード（`forceExport=true`）:
- 検証状態に関わらず `taxIncludedAmountChange != 0` の全データ
- ファイル名末尾に `_UNCHECKED` が付与される

#### 仕訳内容（1行）

| 項目 | 値 |
|------|-----|
| 借方勘定科目 | `仕入高` |
| 借方補助科目 | （空） |
| 借方部門 | 仕入先コード `030302` → `クリーンラボ`、それ以外 → `物販事業部` |
| 借方税区分 | 税率に応じた区分（後述） |
| 借方金額(円) | `taxIncludedAmountChange`（税込合計） |
| 貸方勘定科目 | `買掛金` |
| 貸方補助科目 | `MfAccountMaster.subAccountName`（検索キー: 仕入先コード） |
| 貸方税区分 | `対象外` |
| 貸方金額(円) | `taxIncludedAmountChange`（税込合計） |
| 摘要 | `{仕入先コード}: {補助科目名}` |

#### 税区分マッピング（借方）

| 税率 | 税区分文字列 |
|------|-------------|
| 10% | `課税仕入 10%` |
| 8% | `課仕 (軽)8%` |
| 0% | `非課税` |
| その他 | （空文字） |

#### ソート順

仕入先コード昇順 → 税率降順（高い順）

#### 集計キー

1件のCSVレコードは `(supplierNo, supplierCode, taxRate)` でさらに集計する（`shop_no` を跨いで合算）。

#### ファイル名

```
通常モード: accounts_payable_to_purchase_journal_{yyyyMMdd}.csv
強制モード: accounts_payable_to_purchase_journal_{yyyyMMdd}_UNCHECKED.csv
```

#### パラメータ

| パラメータ名 | 型 | 説明 |
|-------------|-----|------|
| `targetDate` | String (yyyyMMdd) | 対象日付（取引月の20日） |
| `forceExport` | String ("true"/"false") | 強制エクスポートモード（省略時: false） |

#### CSV出力後の更新

CSV出力成功後、対象サマリーの `taxIncludedAmountChange` → `taxIncludedAmount`、`taxExcludedAmountChange` → `taxExcludedAmount` にコピーする（実績として確定）。

---

### 6.3 売掛売上仕訳CSV出力

**クラス**: `jp.co.oda32.batch.finance.AccountsReceivableToSalesJournalTasklet`
**ジョブ名**: `salesJournalIntegration`

#### 出力内容

売掛金集計データをマネーフォワードの「売掛金 / 売上高」または「未収入金 / 仮払金」仕訳として出力する。

#### 検索キー構造

売掛金アカウントマスター検索キー: `{shopNo}_{partnerCode}`
ゴミ袋アカウントマスター検索キー: `g_{shopNo}_{partnerCode}`

#### 仕訳内容（通常売掛 `isOtakeGarbageBag=false`）

| 項目 | 値 |
|------|-----|
| 借方勘定科目 | `売掛金` |
| 借方補助科目 | `MfAccountMaster.subAccountName`（検索キー: `{shopNo}_{partnerCode}`） |
| 借方税区分 | `対象外` |
| 借方金額(円) | `taxIncludedAmountChange` |
| 貸方勘定科目 | `売上高` |
| 貸方補助科目 | `物販売上高`（得意先コード `301491` の場合は `クリーンラボ売上高`） |
| 貸方部門 | `物販事業部`（得意先コード `301491` の場合は `クリーンラボ`） |
| 貸方税区分 | 税率に応じた区分（後述） |
| 貸方金額(円) | `taxIncludedAmountChange` |
| 摘要 | `{searchKey}: {補助科目名}` |

#### 仕訳内容（大竹市ゴミ袋 `isOtakeGarbageBag=true`）

| 項目 | 値 |
|------|-----|
| 借方勘定科目 | `未収入金` |
| 借方補助科目 | `MfAccountMaster.subAccountName`（検索キー: `g_{shopNo}_{partnerCode}`） |
| 借方税区分 | `対象外` |
| 借方金額(円) | `taxIncludedAmountChange` |
| 貸方勘定科目 | `仮払金` |
| 貸方補助科目 | `ゴミ袋／大竹市` |
| 貸方税区分 | `対象外` |
| 貸方金額(円) | `taxIncludedAmountChange` |

#### 税区分マッピング（貸方・売上用）

| 税率 | 税区分文字列 |
|------|-------------|
| 10% | `課税売上 10%` |
| 8% | `課売 (軽)8%` |
| 0% | `非売` |
| その他 | （空文字） |

#### ソート順

取引日（`transactionMonth`）昇順 → `shopNo` 昇順 → `partnerCode` 昇順 → 税率降順

#### 取引番号

`initialTransactionNo` パラメータで指定（省略時: 1001）

#### ファイル名

```
fromDate == toDate の場合: accounts_receivable_to_sales_journal_{yyyyMMdd}.csv
fromDate != toDate の場合: accounts_receivable_to_sales_journal_{fromyyyyMMdd}_{toyyyyMMdd}.csv
```

#### パラメータ

| パラメータ名 | 型 | 説明 |
|-------------|-----|------|
| `fromDate` | String (yyyyMMdd) | 集計期間開始日 |
| `toDate` | String (yyyyMMdd) | 集計期間終了日 |
| `initialTransactionNo` | Long | 取引番号の初期値（省略時: 1001） |

#### アカウントマスタの重複チェック

マスタ作成時に重複キーがある場合は即座に例外をスローして処理を停止する。

#### CSV出力後の更新

CSV出力成功後、対象サマリーの `taxIncludedAmountChange` → `taxIncludedAmount`、`taxExcludedAmountChange` → `taxExcludedAmount` にコピーする（実績として確定）。

---

## 7. バッチ処理（全ジョブ）

### 7.1 ジョブ一覧

| ジョブ名 | Beanクラス | 説明 |
|---------|-----------|------|
| `accountsPayableAggregation` | `AccountsPayableAggregationConfig` | 買掛金集計（集計のみ） |
| `accountsPayableVerification` | `AccountsPayableVerificationConfig` | 買掛金検証（SMILE照合） |
| `purchaseJournalIntegration` | `PurchaseJournalIntegrationConfig` | 買掛仕入仕訳CSV出力 |
| `accountsReceivableSummary` | `AccountsReceivableSummaryConfig` | 売掛金集計 |
| `salesJournalIntegration` | `SalesJournalIntegrationConfig` | 売掛売上仕訳CSV出力 |

---

### 7.2 ジョブ: 買掛金集計（accountsPayableAggregation）

**設定クラス**: `jp.co.oda32.batch.finance.config.AccountsPayableAggregationConfig`

#### ステップ構成

```
accountsPayableSummaryInitStep（初期化）
    ↓
accountsPayableAggregationStep（集計）
```

#### Step 1: accountsPayableSummaryInitStep

**タスクレット**: `AccountsPayableSummaryInitTasklet`

対象月（当月20日）の買掛金サマリーテーブルをリセットする。

実行SQL:
```sql
UPDATE t_accounts_payable_summary
SET payment_difference = NULL, verification_result = NULL, mf_export_enabled = FALSE
WHERE transaction_month = {当月20日}
```

#### Step 2: accountsPayableAggregationStep

**タスクレット**: `AccountsPayableAggregationTasklet`

`AccountsPayableSummaryCalculator.calculatePayableSummaries()` で集計し、`TAccountsPayableSummary` に保存（UPSERT）する。

既存レコードがある場合:
- `taxIncludedAmountChange` を更新
- `taxExcludedAmountChange` を更新
- `verificationResult`、`paymentDifference` は `null` にリセット

#### 実行パラメータ

| パラメータ名 | 型 | 説明 |
|-------------|-----|------|
| `targetDate` | String (yyyyMMdd) | 対象日付（対象月の任意の日） |

**実行例**:
```bash
java -jar app.jar --spring.profiles.active=batch,dev \
  --spring.batch.job.name=accountsPayableAggregation \
  targetDate=20250420
```

---

### 7.3 ジョブ: 買掛金検証（accountsPayableVerification）

**設定クラス**: `jp.co.oda32.batch.finance.config.AccountsPayableVerificationConfig`

#### ステップ構成

```
smilePaymentWorkTableInitStep（ワークテーブル初期化）
    ↓
smilePaymentImportStep（SMILE支払情報取込）
    ↓
accountsPayableVerificationStep（買掛金検証）
    ↓
accountsPayableVerificationReportStep（検証レポート出力）
```

#### Step 1: smilePaymentWorkTableInitStep

**タスクレット**: `SmilePaymentWorkTableInitTasklet`

`w_smile_payment` ワークテーブルをTRUNCATEする。

#### Step 2: smilePaymentImportStep

**チャンクサイズ**: 100件
**スキップ設定**: `Exception.class`、スキップリミット 10,000件

SMILE支払情報CSVを読み込み、`WSmilePayment` → `TSmilePayment` へUPSERT。

#### Step 3: accountsPayableVerificationStep

**タスクレット**: `AccountsPayableVerificationTasklet`

1. 対象月（当月20日）の `TAccountsPayableSummary` を全件取得
2. **翌月**の `TSmilePayment` を取得（20日締め翌月払いのため）
3. `SmilePaymentVerifier.verifyWithSmilePayment()` で照合
4. 照合結果を `TAccountsPayableSummary` に反映（`verificationResult`, `paymentDifference`, `mfExportEnabled`）

SMILE支払情報取得月の計算:
```java
YearMonth.from(periodEndDate).plusMonths(1)
```
例: 対象月が4月20日 → SMILE支払情報は5月分

#### Step 4: accountsPayableVerificationReportStep

**タスクレット**: `AccountsPayableVerificationReportTasklet`

検証結果レポートCSVを出力する。

レポートファイル名: `accounts_payable_verification_report_{yyyyMMdd}.csv`

レポートCSV列:
```
仕入先コード, 仕入先名, ショップ番号, 税率, 買掛金額（税込）, SMILE支払額, 差額, 検証結果
```

レポート内の SMILE支払額算出:
```
smilePaymentAmount = taxIncludedAmountChange + paymentDifference
```

仕入先ごとの合計行と全体合計行も出力される。

**差額がnullのデータ処理**:
- `verificationResult = 0`（不一致）に設定
- `mf_export_enabled = false` に設定
- 差額はnullのまま保持

**5円未満差額の修正**:
レポートステップ実行時に差額がnullでなく、かつ `|paymentDifference| < 5円` のデータが不一致フラグになっている場合、自動的に一致に修正する。

**検証失敗時の停止制御**:
- `forceExecution = true` でない場合、未検証データまたは不一致データがあれば例外スローして処理停止
- `forceExecution = true` の場合、警告ログを出力して処理続行

#### 実行パラメータ

| パラメータ名 | 型 | 説明 |
|-------------|-----|------|
| `targetDate` | String (yyyyMMdd) | 対象日付 |
| `smilePaymentFilePath` | String | SMILE支払CSVファイルパス |
| `forceExecution` | Boolean | 強制実行フラグ（省略時: false） |

---

### 7.4 ジョブ: 買掛仕入仕訳CSV出力（purchaseJournalIntegration）

**設定クラス**: `jp.co.oda32.batch.finance.config.PurchaseJournalIntegrationConfig`

#### ステップ構成

```
accountsPayableToPurchaseJournalStep（仕訳CSV出力）
```

#### 実行パラメータ

| パラメータ名 | 型 | 説明 |
|-------------|-----|------|
| `targetDate` | String (yyyyMMdd) | 対象日付（当月20日） |
| `forceExport` | String ("true"/"false") | 強制エクスポート（省略時: false） |

**実行例**:
```bash
java -jar app.jar --spring.profiles.active=batch,dev \
  --spring.batch.job.name=purchaseJournalIntegration \
  targetDate=20250420 forceExport=false
```

---

### 7.5 ジョブ: 売掛金集計（accountsReceivableSummary）

**設定クラス**: `jp.co.oda32.batch.finance.config.AccountsReceivableSummaryConfig`

#### ステップ構成

```
tAccountsReceivableSummaryStep（売掛金集計・保存）
```

#### 実行パラメータ

| パラメータ名 | 型 | 説明 |
|-------------|-----|------|
| `targetDate` | String (yyyyMMdd) | 対象日付（月末日など対象月の任意の日） |

---

### 7.6 ジョブ: 売掛売上仕訳CSV出力（salesJournalIntegration）

**設定クラス**: `jp.co.oda32.batch.finance.config.SalesJournalIntegrationConfig`

#### ステップ構成

```
accountsReceivableToSalesJournalStep（売上仕訳CSV出力）
```

#### 実行パラメータ

| パラメータ名 | 型 | 説明 |
|-------------|-----|------|
| `fromDate` | String (yyyyMMdd) | 期間開始日 |
| `toDate` | String (yyyyMMdd) | 期間終了日 |
| `initialTransactionNo` | Long | 取引番号初期値（省略時: 1001） |

**実行例（20日締め1ヶ月分）**:
```bash
java -jar app.jar --spring.profiles.active=batch,dev \
  --spring.batch.job.name=salesJournalIntegration \
  fromDate=20250321 toDate=20250420 initialTransactionNo=1001
```

---

## 8. エンティティ定義

### 8.1 TInvoice（請求書テーブル）

**テーブル名**: `t_invoice`
**ユニーク制約**: `(partner_code, closing_date, shop_no)`

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|--------------|-----|------|
| `invoice_id` | `invoiceId` | Integer (PK, AUTO) | 請求ID |
| `partner_code` | `partnerCode` | String (NOT NULL) | 得意先コード |
| `partner_name` | `partnerName` | String (NOT NULL) | 顧客名 |
| `closing_date` | `closingDate` | String (NOT NULL) | 締め日（例: "2025/04/末", "2025/04/20"） |
| `previous_balance` | `previousBalance` | BigDecimal | 前回請求残高 |
| `total_payment` | `totalPayment` | BigDecimal | 入金合計 |
| `carry_over_balance` | `carryOverBalance` | BigDecimal | 繰越残高 |
| `net_sales` | `netSales` | BigDecimal | 純売上（税抜） |
| `tax_price` | `taxPrice` | BigDecimal | 消費税額 |
| `net_sales_including_tax` | `netSalesIncludingTax` | BigDecimal | 純売上（税込） |
| `current_billing_amount` | `currentBillingAmount` | BigDecimal | 今回請求額 |
| `shop_no` | `shopNo` | Integer | ショップ番号 |
| `payment_date` | `paymentDate` | LocalDate | 入金日 |

---

### 8.2 TAccountsPayableSummary（買掛金サマリーテーブル）

**テーブル名**: `t_accounts_payable_summary`
**複合主キー**: `(shop_no, supplier_no, transaction_month, tax_rate)`

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|--------------|-----|------|
| `shop_no` | `shopNo` | Integer (PK) | ショップ番号 |
| `supplier_no` | `supplierNo` | Integer (PK) | 支払先番号 |
| `supplier_code` | `supplierCode` | String | 支払先コード |
| `transaction_month` | `transactionMonth` | LocalDate (PK) | 取引月（当月20日） |
| `tax_rate` | `taxRate` | BigDecimal (PK) | 税率 |
| `tax_included_amount` | `taxIncludedAmount` | BigDecimal | 税込金額（CSV出力後に確定） |
| `tax_excluded_amount` | `taxExcludedAmount` | BigDecimal | 税抜金額（CSV出力後に確定） |
| `tax_included_amount_change` | `taxIncludedAmountChange` | BigDecimal | 税込金額（集計・調整値） |
| `tax_excluded_amount_change` | `taxExcludedAmountChange` | BigDecimal | 税抜金額（集計・調整値） |
| `verification_result` | `verificationResult` | Integer | 検証結果（1: 一致, 0: 不一致, null: 未検証） |
| `payment_difference` | `paymentDifference` | BigDecimal | SMILE支払額との差額 |
| `mf_export_enabled` | `mfExportEnabled` | Boolean | マネーフォワードエクスポート可否フラグ |

**`taxIncludedAmount` と `taxIncludedAmountChange` の違い**:
- `taxIncludedAmountChange`: バッチ集計・SMILE照合調整後の作業値
- `taxIncludedAmount`: マネーフォワードCSV出力後に `taxIncludedAmountChange` からコピーされる確定値

---

### 8.3 TAccountsReceivableSummary（売掛金サマリーテーブル）

**テーブル名**: `t_accounts_receivable_summary`
**複合主キー**: `(shop_no, partner_no, transaction_month, tax_rate, is_otake_garbage_bag)`

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|--------------|-----|------|
| `shop_no` | `shopNo` | Integer (PK) | ショップ番号 |
| `partner_no` | `partnerNo` | Integer (PK) | 得意先番号 |
| `partner_code` | `partnerCode` | String | 得意先コード（請求先コード） |
| `transaction_month` | `transactionMonth` | LocalDate (PK) | 取引月（締め日当日） |
| `tax_rate` | `taxRate` | BigDecimal (PK) | 税率 |
| `tax_included_amount` | `taxIncludedAmount` | BigDecimal | 税込金額（CSV出力後に確定） |
| `tax_excluded_amount` | `taxExcludedAmount` | BigDecimal | 税抜金額（CSV出力後に確定） |
| `tax_included_amount_change` | `taxIncludedAmountChange` | BigDecimal | 税込金額（集計・調整値） |
| `tax_excluded_amount_change` | `taxExcludedAmountChange` | BigDecimal | 税抜金額（集計・調整値） |
| `is_otake_garbage_bag` | `isOtakeGarbageBag` | boolean (PK) | 大竹市ゴミ袋フラグ |
| `cutoff_date` | `cutoffDate` | Integer | 締め日コード（0: 月末, 15: 15日, 20: 20日, -1: 都度現金） |
| `order_no` | `orderNo` | Integer | 注文番号（都度現金払い用） |

---

### 8.4 MfAccountMaster（マネーフォワード勘定科目マスタ）

**テーブル名**: `mf_account_master`

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|--------------|-----|------|
| `id` | `id` | Long (PK, AUTO) | ID |
| `report_name` | `reportName` | String (NOT NULL) | レポート名 |
| `category` | `category` | String (NOT NULL) | カテゴリ |
| `financial_statement_item` | `financialStatementItem` | String (NOT NULL) | 財務諸表項目（例: 「買掛金」「売掛金」「未収入金」） |
| `account_name` | `accountName` | String (NOT NULL) | 勘定科目名 |
| `sub_account_name` | `subAccountName` | String | 補助科目名 |
| `tax_classification` | `taxClassification` | String | 税区分 |
| `search_key` | `searchKey` | String | 検索キー（仕入先コードまたは `{shopNo}_{partnerCode}`） |
| `is_active` | `isActive` | Boolean (NOT NULL) | 有効フラグ（デフォルト: true） |
| `display_order` | `displayOrder` | Integer (NOT NULL) | 表示順（デフォルト: 0） |
| `created_at` | `createdAt` | LocalDateTime (NOT NULL) | 作成日時 |
| `updated_at` | `updatedAt` | LocalDateTime (NOT NULL) | 更新日時 |

`financial_statement_item` と `account_name` の組み合わせ:

| `financial_statement_item` | `account_name` | 用途 |
|--------------------------|----------------|------|
| `買掛金` | `買掛金` | 買掛仕入仕訳CSV |
| `売掛金` | `売掛金` | 売掛売上仕訳CSV（通常） |
| `未収入金` | `未収入金` | 売掛売上仕訳CSV（ゴミ袋） |

---

### 8.5 MMfSubAccount（マネーフォワード補助科目マスタ）

**テーブル名**: `m_mf_sub_account`
**ユニーク制約**: `(partner_code, sub_account_name)`

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|--------------|-----|------|
| `partner_no` | `partnerNo` | Long (PK) | 得意先番号 |
| `sub_account_name` | `subAccountName` | String (NOT NULL) | 補助科目名 |
| `partner_code` | `partnerCode` | String | 得意先コード |

---

## 9. PaymentType 定数

**クラス**: `jp.co.oda32.constant.PaymentType`

| 定数名 | `cutoffCode` | 説明 |
|--------|-------------|------|
| `MONTH_END` | `0` | 月末締め |
| `DAY_15` | `15` | 15日締め |
| `DAY_20` | `20` | 20日締め |
| `CASH_ON_DELIVERY` | `-1` | 都度現金払い |

`null` の場合は `MONTH_END` として扱う。

---

## 10. 特殊取引先コード一覧

| 取引先コード | 用途 | 特殊処理 |
|-------------|------|----------|
| `030302` | 仕入先 | 仕訳借方部門を「クリーンラボ」に設定 |
| `301491` | 得意先（クリーンラボ） | 店舗番号3でも請求書検索は店舗番号1で実施。売上仕訳貸方部門を「クリーンラボ」、補助科目を「クリーンラボ売上高」に設定 |
| `000231` | 得意先（四半期請求） | 当月15日締め + 前月が四半期末(2,5,8,11月)の場合は前月末締め請求書も合算 |
| `999999` | 上様（7桁以上の得意先コード） | 請求書金額で常に上書き。都度現金払い扱い |
| `303`（supplier_no） | 仕入先番号 | 小田光在庫表（棚卸手打ち）として買掛金集計から除外 |

---

## 11. 金額計算まとめ

### 消費税計算（共通ルール）

```
税額 = 税抜金額 × 税率 ÷ 100  （小数点以下切り捨て: RoundingMode.DOWN）
税込金額 = 税抜金額 + 税額
```

### 端数処理の基本方針

- 全ての金額: **小数点以下切り捨て（RoundingMode.DOWN）**
- CSV出力時: 整数として出力（小数点以下なし）
- サービス層の `save()` メソッド: 保存前に `setScale(0, RoundingMode.DOWN)` を適用

### 許容差ルール

| 対象 | 許容差 | 処理 |
|------|--------|------|
| 買掛金 vs SMILE支払 | 5円未満 | 一致扱い、SMILE支払額に合わせる |
| 売掛金 vs 請求書 | 3円以内（設定可） | 一致扱い、請求書金額に合わせる |
| 売掛金 1円誤差 | 1円 | 計算値を請求書金額に合わせる（警告ログ） |
| 手動検証（買掛金） | 100円以内 | 一致とみなす |

---

## 12. バッチ実行順序

月次処理の推奨実行順序:

```
1. accountsPayableAggregation（買掛金集計）
   └── パラメータ: targetDate=当月20日

2. accountsPayableVerification（買掛金検証 + SMILE照合）
   └── パラメータ: targetDate=当月20日, smilePaymentFilePath=CSVパス

3. purchaseJournalIntegration（買掛仕入仕訳CSV出力）
   └── パラメータ: targetDate=当月20日, forceExport=false

4. accountsReceivableSummary（売掛金集計）
   └── パラメータ: targetDate=当月末日（または20日など締め日）

5. salesJournalIntegration（売掛売上仕訳CSV出力）
   └── パラメータ: fromDate=前月21日, toDate=当月20日
```

---

## 13. ソースファイル一覧

| ファイルパス | 説明 |
|------------|------|
| `src/main/java/jp/co/oda32/app/finance/InvoiceListController.java` | 請求書一覧・詳細コントローラ |
| `src/main/java/jp/co/oda32/app/finance/InvoiceSearchForm.java` | 請求書検索フォーム |
| `src/main/java/jp/co/oda32/controller/finance/AccountsPayableController.java` | 買掛金確認コントローラ |
| `src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java` | 請求書エンティティ |
| `src/main/java/jp/co/oda32/domain/model/finance/TAccountsPayableSummary.java` | 買掛金サマリーエンティティ |
| `src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java` | 売掛金サマリーエンティティ |
| `src/main/java/jp/co/oda32/domain/model/finance/MfAccountMaster.java` | MF勘定科目マスタエンティティ |
| `src/main/java/jp/co/oda32/domain/model/finance/MMfSubAccount.java` | MF補助科目マスタエンティティ |
| `src/main/java/jp/co/oda32/domain/repository/finance/TInvoiceRepository.java` | 請求書リポジトリ |
| `src/main/java/jp/co/oda32/domain/repository/finance/TAccountsPayableSummaryRepository.java` | 買掛金サマリーリポジトリ |
| `src/main/java/jp/co/oda32/domain/repository/finance/TAccountsReceivableSummaryRepository.java` | 売掛金サマリーリポジトリ |
| `src/main/java/jp/co/oda32/domain/service/finance/TInvoiceService.java` | 請求書サービス |
| `src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java` | 買掛金サービス |
| `src/main/java/jp/co/oda32/domain/service/finance/TAccountsReceivableSummaryService.java` | 売掛金サービス |
| `src/main/java/jp/co/oda32/domain/specification/finance/TInvoiceSpecification.java` | 請求書検索条件 |
| `src/main/java/jp/co/oda32/batch/finance/MFJournalCsv.java` | MF仕訳CSVフォーマット定義 |
| `src/main/java/jp/co/oda32/batch/finance/AccountsPayableAggregationTasklet.java` | 買掛金集計タスクレット |
| `src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryInitTasklet.java` | 買掛金集計初期化タスクレット |
| `src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationTasklet.java` | 買掛金検証タスクレット |
| `src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationReportTasklet.java` | 買掛金検証レポートタスクレット |
| `src/main/java/jp/co/oda32/batch/finance/AccountsPayableToPurchaseJournalTasklet.java` | 買掛仕入仕訳CSV出力タスクレット |
| `src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java` | 売掛金集計タスクレット |
| `src/main/java/jp/co/oda32/batch/finance/AccountsReceivableToSalesJournalTasklet.java` | 売掛売上仕訳CSV出力タスクレット |
| `src/main/java/jp/co/oda32/batch/finance/service/AccountsPayableSummaryCalculator.java` | 買掛金集計計算サービス |
| `src/main/java/jp/co/oda32/batch/finance/service/SmilePaymentVerifier.java` | SMILE支払照合サービス |
| `src/main/java/jp/co/oda32/batch/finance/helper/TaxCalculationHelper.java` | 税額計算ヘルパー |
| `src/main/java/jp/co/oda32/batch/finance/model/SummaryKey.java` | 買掛金集計キーモデル |
| `src/main/java/jp/co/oda32/batch/finance/model/TaxAggregationResult.java` | 税額集計結果モデル |
| `src/main/java/jp/co/oda32/batch/finance/model/TaxBreakdown.java` | 税率別内訳モデル |
| `src/main/java/jp/co/oda32/batch/finance/model/VerificationResult.java` | 検証結果モデル |
| `src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableAggregationConfig.java` | 買掛金集計ジョブ設定 |
| `src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableVerificationConfig.java` | 買掛金検証ジョブ設定 |
| `src/main/java/jp/co/oda32/batch/finance/config/PurchaseJournalIntegrationConfig.java` | 買掛仕入仕訳ジョブ設定 |
| `src/main/java/jp/co/oda32/batch/finance/config/AccountsReceivableSummaryConfig.java` | 売掛金集計ジョブ設定 |
| `src/main/java/jp/co/oda32/batch/finance/config/SalesJournalIntegrationConfig.java` | 売掛売上仕訳ジョブ設定 |
| `src/main/resources/templates/finance/accountsPayable/list.html` | 買掛金一覧テンプレート |
| `src/main/resources/templates/finance/accountsPayable/detail.html` | 買掛金詳細テンプレート |
| `src/main/resources/templates/finance/invoice/list.html` | 請求書一覧テンプレート |
| `src/main/resources/templates/finance/invoice/detail.html` | 請求書詳細テンプレート |
