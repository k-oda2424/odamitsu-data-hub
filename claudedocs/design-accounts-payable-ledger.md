# 買掛帳画面 (Accounts Payable Ledger) 設計書

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes`
関連設計書: `design-supplier-partner-ledger-balance.md`, `design-phase-b-prime-payment-settled.md`

---

## 1. 背景と目的

### 既存機能の限界

`/finance/accounts-payable` (買掛金一覧) は **1 取引月の全仕入先一覧** で、1 仕入先の月次推移を追う動線が無い。残高突合で diff が出ても、**どの月にずれが生まれたか** を特定する手段が不足している。

具体的な運用課題:
- 竹の子の里が 2/20 に値引繰越 → 3/20 に繰り越された流れが見えない
- カミ商事の 2025-06 support データ (verified=0) がいつから欠落したか不明
- MF 側と自社が乖離する時点が特定できない

### 目的

**1 仕入先について月次推移を時系列で並べて「いつ・何が」ずれたかを一目で把握できる**画面を新設。

### 用語

- 自社 closing: `opening + effectiveChange - payment_settled` (Phase B' T 勘定)
- MF closing: `/journals` 累積で算出した仕入先別累積残 (sub_account ベース)
- effectiveChange: 手動確定時は `verified_amount` 優先、それ以外は `tax_included_amount_change`

---

## 2. スコープ

### 必須スコープ (第 1 版)

| # | 項目 | 優先度 |
|---|---|---|
| S1 | 新 API `/accounts-payable/ledger`: 自社 DB のみの月次推移取得 | 必須 |
| S2 | 新 API `/accounts-payable/ledger/mf`: MF /journals 累積で仕入先 closing 取得 (オプション) | 必須 |
| S3 | 新ページ `/finance/accounts-payable-ledger`: テーブル表示 + anomaly ハイライト | 必須 |
| S4 | 既存 `/finance/accounts-payable` 仕入先名リンク → 本画面遷移 | 必須 |
| S5 | サイドバー menu 追加 | 必須 |

### スコープ外 (次フェーズ候補)

- 税率別の詳細内訳 expand 表示 (第 1 版は合算表示、UI 余裕があれば後続)
- 仕入先名のあいまいマッチング (MF sub_account 名と自社 payment_supplier 名の表記揺れ対応)
- CSV/Excel 出力
- 複数仕入先の一括比較

---

## 3. データ構造 (既存 Phase B' モデル再利用)

### 集約単位

PK `(shop_no, supplier_no, transaction_month, tax_rate)` → **supplier 単位に税率別を合算** してレンダリング。

税率別の内訳を取ることも可能だが、第 1 版は集約値のみ表示する。

### anomaly 検出ルール（レビュー R5, R10 反映）

| コード | 条件 | Severity | 表示色 |
|---|---|---|---|
| UNVERIFIED | `Σchange > 0` AND `Σverified == 0` (税率別に全て null/0) | WARN | red |
| VERIFY_DIFF | `|Σchange - Σverified| > 100` AND `Σverified > 0` | WARN | orange |
| NEGATIVE_CLOSING | `closing < 0` AND `!hasPaymentOnly` | INFO | amber |
| PAYMENT_OVER | `closing < 0` AND `hasPaymentOnly` | INFO | amber |
| CONTINUITY_BREAK | 前月 closing ≠ 当月 opening (許容差 ±1 円、丸め) / 前月行が rows 内 or 期間外前月に存在する場合のみ判定 | CRITICAL | red |
| MONTH_GAP | 前月 row が rows 内にも期間外前月にも存在しないとき (summary 欠落、集計バッチ未実行疑い) | WARN | orange |
| MF_DELTA_MISMATCH | MF 比較 ON 時、`|selfPeriodDelta − mfPeriodDelta| > 10,000`（月次 credit−debit vs change−payment_settled の期間差比較、R8 反映） | WARN | amber |

※ **PAYMENT_ONLY はレビュー R10 反映で anomaly 列から除外**。row.hasPaymentOnly (boolean) で UI に「支払のみ」badge を表示する。

※ 閾値 100 円 (VERIFY_DIFF) / 10,000 円 (MF_DELTA_MISMATCH) は `application.yml` の `odamitsu.ledger.*` で将来外出し可能（第 1 版ハードコード、レビュー R11 の軽減策）。

### 前月との連続性チェック（レビュー R5, R6 反映）

自社 opening を「前月 closing と一致すべき」というチェック。Phase A/B' のバッチが正常動作していれば自動的に一致。不整合検出は backfill 未実行や bug の早期発見に役立つ。

**前月特定ルール**:
1. 当月 M のレコード走査時、M-1 月が `rows` に含まれていれば、そこの closing と比較
2. M-1 月が `rows` に無い場合 (fromMonth=M のとき、fromMonth 直前月): **Repository で期間外直前月を 1 回追加取得**して比較 (R6 反映)
3. 期間外直前月にも summary が無い場合 (真の起点): CONTINUITY_BREAK スキップ
4. M-1 月が存在するはずだが欠落している場合 (月抜け): **CONTINUITY_BREAK ではなく MONTH_GAP anomaly を出す** (R5 反映)

**実装**: `AccountsPayableLedgerService` は期間内 rows に加えて fromMonth 直前月 (= fromMonth.minusMonths(1)) の summary を事前取得し、起点月の比較に使う。

---

## 4. Backend API 設計

### 4.1 GET /api/v1/finance/accounts-payable/ledger

**Query**:
- `shopNo: Integer` (admin 以外は ignore されて自 shop 固定)
- `supplierNo: Integer` (必須)
- `fromMonth: yyyy-MM-dd` (必須、20日締め日)
- `toMonth: yyyy-MM-dd` (必須、20日締め日、from <= to)

**期間制限**: `toMonth - fromMonth` は最大 **24 ヶ月** (DoS 対策 + UI 負荷抑制)。デフォルト 12 ヶ月、根拠: MF /journals per_page=1000 × 24 ≒ 24000 件で 10 秒級（R15 反映）

**セキュリティ** (R7 反映):
- 非 admin が自 shop 以外の shopNo を指定 → `FinanceController#assertShopAccess` で **403** (既存パターン踏襲、silent override しない)
- 非 admin が自 shop の supplier_no 以外 (他 shop の supplier) を指定 → supplier 存在検証時に **404** (権限漏洩回避、存在を暴露しない)

**エラー** (R4 反映):
- supplier が存在しない (or 自 shop 外) → **404**
- supplier 存在・期間内 summary 0 件 → **200 + rows=[], summary 数値フィールド 0, finalClosing=0**
- fromMonth > toMonth → 400
- 期間 24 ヶ月超 → 400
- 未認証 → 401

**Response**:
```jsonc
{
  "supplier": {
    "shopNo": 1,
    "supplierNo": 32,
    "supplierCode": "010100",
    "supplierName": "ｶﾐ 商事(株) [5]"
  },
  "fromMonth": "2025-06-20",
  "toMonth": "2026-03-20",
  "rows": [
    {
      "transactionMonth": "2025-06-20",
      "openingBalanceTaxIncluded": 0,
      "changeTaxIncluded": 2301732,
      "verifiedAmount": 0,
      "paymentSettledTaxIncluded": 2462618,
      "closingBalanceTaxIncluded": -160886,
      "taxRateCount": 1,
      "rowKeys": [
        { "taxRate": 10, "verifiedManually": false, "verificationResult": null, "isPaymentOnly": false, "mfExportEnabled": true, "mfTransferDate": null }
      ],
      "hasPaymentOnly": false,
      "hasVerifiedManually": false,
      "anomalies": [
        { "code": "UNVERIFIED", "severity": "WARN", "message": "当月仕入あり・検証金額なし" },
        { "code": "NEGATIVE_CLOSING", "severity": "INFO", "message": "累積残が負 (値引繰越)" }
      ],
      "continuityOk": true
    }
    // ... 各月の行
  ],
  "summary": {
    "totalChangeTaxIncluded": 20000000,
    "totalVerified": 15000000,
    "totalPaymentSettled": 14000000,
    "finalClosing": 6000000,
    "unverifiedMonthCount": 2,
    "continuityBreakCount": 0,
    "negativeClosingMonthCount": 1,
    "paymentOnlyMonthCount": 0
  }
}
```

### 4.2 GET /api/v1/finance/accounts-payable/ledger/mf (レビュー R1, R2, R8, R13 反映)

**Query**: 同上 (shopNo, supplierNo, fromMonth, toMonth)

**処理**:
1. `payment_supplier` 取得 (supplier_no / supplier_code / supplier_name)
2. **supplier_code を key に `mf_account_master.search_key == supplier_code` から対応する `sub_account_name` を解決** (R1: 既存 `MfJournalReconcileService.buildMfSubAccountMap` と同じマッチング方式)。存在しない場合は payment_supplier_name にフォールバックし、unmatched 候補として UI に返す。
3. MF `/journals?start_date=fromMonth&end_date=toMonth` をページングで全件取得 (R2: `per_page=1000` 固定、items が per_page 未満になるまでループ)
4. 各 branch で:
   - `creditor.accountName == "買掛金" AND creditor.subAccountName ∈ matchedSubAccountNames` → credit 側として +value
   - `debitor.accountName == "買掛金" AND debitor.subAccountName ∈ matchedSubAccountNames` → debit 側として +value
   - (R13: debitor / creditor 両側検査)
5. 月毎に credit / debit をバケット分け + 累積 running total
6. Response: 月毎の `mfCreditInMonth` / `mfDebitInMonth` / `mfPeriodDelta = credit - debit`

**期間差比較方式** (R8 反映):
- 第 1 版は月次 **delta (= credit - debit) のみ返す** (closing 累積は出さない)
- 自社側も月次 delta (= effectiveChange - payment_settled) を比較
- diff = `|self.periodDelta - mf.periodDelta|` が閾値超なら MF_DELTA_MISMATCH
- closing 累積同士の比較は期首残差が常時点灯するためやめる (Phase B'' light と同じ判断)

**マッチング** (R1 反映):
- 主: `mf_account_master.search_key == supplier_code` で `sub_account_name` を解決
- 補: supplier_name での exact match も試行 (後方互換)
- どちらも 0 件なら `unmatchedCandidates` に supplier_name を入れて UI に警告
- 既存 `MfJournalReconcileService.java:buildMfSubAccountMap("買掛金", "買掛金")` 相当のヘルパーを Service 内に実装

**Response** (R8, R19 反映: 期間 echo 追加 + closing 列削除し delta のみ返す):
```jsonc
{
  "shopNo": 1,
  "supplierNo": 32,
  "supplierName": "ｶﾐ 商事(株) [5]",
  "fromMonth": "2025-06-20",
  "toMonth": "2026-03-20",
  "matchedSubAccountNames": ["カミ商事（株）"],
  "unmatchedCandidates": [],
  "rows": [
    {
      "transactionMonth": "2025-06-20",
      "mfCreditInMonth": 2301732,
      "mfDebitInMonth": 0,
      "mfPeriodDelta": 2301732    // = credit - debit (当月純増減、closing 累積ではない)
    }
    // ...
  ],
  "fetchedAt": "2026-04-22T10:00:00Z",
  "totalJournalCount": 54
}
```

**エラー**:
- MF scope 不足 (403) → `MfScopeInsufficientException` 経由で UI にメッセージ
- MF 認証切れ (401) → `MfReAuthRequiredException` → UI トースト

**タイムアウト/負荷**:
- 24 ヶ月 × 1000 件/月 ≒ 24000 件の journals 走査
- 数秒〜10 秒程度の所要を想定
- UI は Skeleton + progress

---

## 5. Service 設計

### 5.1 AccountsPayableLedgerService (新規)

**責務**: 自社 DB から月次推移を取得し、anomaly 検出を含めた DTO を生成。

**主要メソッド**:
```java
public LedgerResponse getLedger(Integer shopNo, Integer supplierNo,
                                 LocalDate fromMonth, LocalDate toMonth) {
    // 1. supplier 存在確認 (m_payment_supplier)
    // 2. 期間内の summary 行取得
    //    → 新 Repository method: findByShopNoAndSupplierNoAndTransactionMonthBetween
    // 3. 月毎に税率別を集約 (supplier 単位)
    // 4. anomaly 検出 + 前月継続チェック
    // 5. summary 統計算出
    // 6. Response を返す
}
```

**依存**:
- `TAccountsPayableSummaryRepository` (新 method 追加)
- `MPaymentSupplierService` (supplier 名取得)

**集約ロジック (supplier 単位で税率別合算)** — R3 反映:

closing は **既存 `PayableBalanceCalculator.closingTaxIncluded()` を税率別に呼び、supplier 単位で SUM** する (再実装せず Phase B' のロジックに委譲)。change / opening / payment_settled は単純和。

```java
// verified_amount は Phase B' の「全行同値なら代表値、不一致なら SUM」 ロジック準拠
// (既存 PaymentMfImportService.sumVerifiedAmountForGroup 相当)
BigDecimal aggregateVerified(List<TAccountsPayableSummary> group) {
    List<BigDecimal> perRow = group.stream()
        .map(r -> r.getVerifiedAmount() != null ? r.getVerifiedAmount() : BigDecimal.ZERO)
        .toList();
    BigDecimal first = perRow.get(0);
    boolean allSame = perRow.stream().allMatch(v -> v.compareTo(first) == 0);
    return allSame ? first : perRow.stream().reduce(ZERO, BigDecimal::add);
}

// closing は PayableBalanceCalculator を呼ぶ (R3 反映)
BigDecimal aggregateClosing(List<TAccountsPayableSummary> group) {
    return group.stream()
        .map(PayableBalanceCalculator::closingTaxIncluded)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

### 5.2 MfSupplierLedgerService (新規)

**責務**: MF /journals を期間累積で取得し、仕入先別の月末 closing を算出。

**主要メソッド**:
```java
public MfLedgerResponse getSupplierLedger(Integer shopNo, Integer supplierNo,
                                            LocalDate fromMonth, LocalDate toMonth) {
    // 1. supplier 取得 (MPaymentSupplierService)
    // 2. sub_account_name 候補を解決 (R1: mf_account_master.search_key == supplierCode)
    //    - 無ければ supplierName を unmatchedCandidates として返却
    // 3. MF /journals を per_page=1000 ループで全件取得 (R2: pagination safeguard 50 pages)
    // 4. 各 branch の debitor / creditor 両側検査 (R13):
    //    - accountName == "買掛金" AND subAccountName ∈ matchedSubAccountNames
    //    - creditor 側 → 月の credit bucket に +value
    //    - debitor  側 → 月の debit  bucket に +value
    // 5. transactionDate → 20日締め月への bucketing (date<=20: 当月20日、>20: 翌月20日)
    // 6. 月次 credit / debit / periodDelta を返す (closing 累積は返さない、R8)
}
```

**依存**:
- `MfOauthService`, `MfApiClient` (既存)
- `MPaymentSupplierService`
- `MfAccountMasterRepository` (R1 追加、sub_account_name 解決用)

**ページング** (R2 反映):
```java
int page = 1;
final int perPage = 1000;
while (true) {
    var res = mfApiClient.listJournals(client, token, startDate, endDate, page, perPage);
    all.addAll(res.items());
    if (res.items().size() < perPage) break;
    page++;
    if (page > 50) throw new IllegalStateException("pagination safeguard");
}
```

**両側検査** (R13 反映):
```java
for (branch in journal.branches):
    BigDecimal credit = ZERO, debit = ZERO;
    if (branch.creditor != null
        && "買掛金".equals(branch.creditor.accountName)
        && matchedSubAccountNames.contains(branch.creditor.subAccountName)) {
        credit = nz(branch.creditor.value);
    }
    if (branch.debitor != null
        && "買掛金".equals(branch.debitor.accountName)
        && matchedSubAccountNames.contains(branch.debitor.subAccountName)) {
        debit = nz(branch.debitor.value);
    }
    if (credit.signum() != 0 || debit.signum() != 0) {
        bucket[monthKey(journal.transactionDate)].credit += credit;
        bucket[monthKey(journal.transactionDate)].debit  += debit;
    }
```

**20日締め月への bucketing**:
- `date.getDayOfMonth() <= 20` → `YearMonth.from(date).atDay(20)`
- `date.getDayOfMonth() > 20` → `YearMonth.from(date).plusMonths(1).atDay(20)`
- 2025-07-15 → 2025-07-20、2025-07-25 → 2025-08-20

**マッチング** (R1 反映、既出):
- `mf_account_master.search_key == supplier_code` で `sub_account_name` を解決
- 複数返ればすべて matchedSubAccountNames に含めて合算
- 見つからなければ supplier_name をフォールバックで試し、それも 0 件なら unmatchedCandidates に返却

---

## 6. Repository 変更 (R17 反映: derived query 採用)

`TAccountsPayableSummaryRepository` に以下 method 追加:

```java
/**
 * 指定 supplier の期間内 summary を取得 (買掛帳 API 用)。tax_rate 昇順で返却、月単位の集約は Service 側で実施。
 */
List<TAccountsPayableSummary> findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
    Integer shopNo, Integer supplierNo, LocalDate fromMonth, LocalDate toMonth);

/**
 * 起点月直前月の summary を取得 (R6: CONTINUITY_BREAK 判定の期間外前月参照)。
 * 同月内に存在する全税率行を返す。
 */
List<TAccountsPayableSummary> findByShopNoAndSupplierNoAndTransactionMonth(
    Integer shopNo, Integer supplierNo, LocalDate transactionMonth);
```

---

## 7. DTO 設計

`AccountsPayableLedgerResponse` (新):
```java
@Data @Builder
public class AccountsPayableLedgerResponse {
    private SupplierInfo supplier;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private List<LedgerRow> rows;
    private LedgerSummary summary;

    @Data @Builder
    public static class SupplierInfo {
        private Integer shopNo;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
    }

    @Data @Builder
    public static class LedgerRow {
        private LocalDate transactionMonth;
        private BigDecimal openingBalanceTaxIncluded;
        private BigDecimal changeTaxIncluded;
        private BigDecimal verifiedAmount;
        private BigDecimal paymentSettledTaxIncluded;
        private BigDecimal closingBalanceTaxIncluded;
        private Integer taxRateCount;
        private List<TaxRateInfo> rowKeys;
        private boolean hasPaymentOnly;
        private boolean hasVerifiedManually;
        private List<Anomaly> anomalies;
        private boolean continuityOk;
    }

    @Data @Builder
    public static class TaxRateInfo {
        private BigDecimal taxRate;
        private Boolean verifiedManually;
        private Integer verificationResult;
        private Boolean isPaymentOnly;
        private Boolean mfExportEnabled;
        private LocalDate mfTransferDate;
    }

    @Data @Builder
    public static class Anomaly {
        private String code;      // UNVERIFIED, VERIFY_DIFF, NEGATIVE_CLOSING, etc.
        private String severity;  // CRITICAL, WARN, INFO
        private String message;
    }

    @Data @Builder
    public static class LedgerSummary {
        private BigDecimal totalChangeTaxIncluded;
        private BigDecimal totalVerified;
        private BigDecimal totalPaymentSettled;
        private BigDecimal finalClosing;
        private Integer unverifiedMonthCount;
        private Integer continuityBreakCount;
        private Integer negativeClosingMonthCount;
        private Integer paymentOnlyMonthCount;
    }
}
```

`MfSupplierLedgerResponse` (新、R8/R19 反映):
```java
@Data @Builder
public class MfSupplierLedgerResponse {
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierName;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private List<String> matchedSubAccountNames;
    private List<String> unmatchedCandidates;
    private List<MfLedgerRow> rows;
    private Instant fetchedAt;
    private Integer totalJournalCount;

    @Data @Builder
    public static class MfLedgerRow {
        private LocalDate transactionMonth;
        private BigDecimal mfCreditInMonth;
        private BigDecimal mfDebitInMonth;
        private BigDecimal mfPeriodDelta;  // = credit - debit (R8: closing 累積は出さない)
    }
}
```

---

## 8. Frontend 設計

### 8.1 新規ファイル

```
frontend/
  app/(authenticated)/finance/accounts-payable-ledger/
    page.tsx
  components/pages/finance/
    accounts-payable-ledger.tsx
  types/
    accounts-payable-ledger.ts
```

### 8.2 ページ構造

**URL**: `/finance/accounts-payable-ledger?shopNo=1&supplierNo=32&fromMonth=2025-06-20&toMonth=2026-03-20`

URL クエリで状態保持 (既存 accounts-payable と同じパターン)。

**UI 構成** (R14, R18 反映: MF は明示ボタン、URL クエリは supplierNo 必須):
```tsx
<PageHeader title="買掛帳" />

<Card> {/* 検索フォーム */}
  <SearchableSelect>仕入先</SearchableSelect>
  <Input type=month>開始月</Input>  {/* デフォルト 12 ヶ月前 */}
  <Input type=month>終了月</Input>
  <Button>検索</Button>
</Card>

{/* supplierNo 未選択時はここで終わり */}

<Card> {/* MF 比較トリガ (自社 ledger fetch 後に表示) */}
  <Button>MF と比較を取得</Button>  {/* 押下で /ledger/mf fetch、loading 中は disabled + Skeleton */}
</Card>

<Card> {/* 仕入先情報 */}
  {supplier.supplierCode} {supplier.supplierName}
</Card>

<Card> {/* サマリ */}
  期間累計: 仕入 ¥X / 検証 ¥Y / 支払反映 ¥Z
  最終残: ¥W
  警告: 未検証 N 件、値引繰越 M 件、継続不整合 K 件
</Card>

<DataTable> {/* 月次明細 */}
  月 | 前月繰越 | 仕入 | 検証額 | 支払反映 | 当月残 | ステータス
  [MF 比較 ON 時追加列] | MF 残 | 差
  row_bg: anomaly.severity に応じて
</DataTable>
```

### 8.3 ハイライト実装 (R9 反映: 複数バッジ並列)

- 行背景色: `anomalies` の **最高 severity 優先**
  - CRITICAL 含む → red 薄背景
  - WARN のみ → orange 薄背景
  - INFO のみ → amber 薄背景
- ステータス列: **複数 anomaly を並列バッジ表示** (severity 別色 + 短ラベル)
  - UNV (red): UNVERIFIED
  - VDF (orange): VERIFY_DIFF
  - NEG (amber): NEGATIVE_CLOSING
  - POV (amber): PAYMENT_OVER
  - BRK (red): CONTINUITY_BREAK
  - GAP (orange): MONTH_GAP
  - MFX (amber): MF_DELTA_MISMATCH (MF 比較結果あり時のみ)
- `hasPaymentOnly=true` は anomaly とは別に「支払のみ」badge (slate、R10 反映)
- tooltip で anomaly.message を全文表示 (Radix UI の Tooltip 利用)

### 8.4 型定義

```typescript
// types/accounts-payable-ledger.ts
export interface SupplierInfo { /* ... */ }
export interface LedgerRow { /* ... */ }
export type AnomalyCode = 'UNVERIFIED' | 'VERIFY_DIFF' | 'NEGATIVE_CLOSING'
                        | 'PAYMENT_OVER' | 'PAYMENT_ONLY' | 'CONTINUITY_BREAK' | 'MF_MISMATCH'
export type AnomalySeverity = 'CRITICAL' | 'WARN' | 'INFO'
export interface Anomaly {
  code: AnomalyCode
  severity: AnomalySeverity
  message: string
}
export interface LedgerSummary { /* ... */ }
export interface AccountsPayableLedgerResponse {
  supplier: SupplierInfo
  fromMonth: string
  toMonth: string
  rows: LedgerRow[]
  summary: LedgerSummary
}
export interface MfLedgerRow { /* ... */ }
export interface MfSupplierLedgerResponse { /* ... */ }
```

### 8.5 既存 `/finance/accounts-payable` の仕入先名リンク追加 (R12 反映)

現状 `accounts-payable.tsx` の supplierName 列は `/purchases` にリンクしている (既存 E2E が href をアサートする可能性あり)。

変更方針:
- **supplierCode 列を `/purchases` リンク** に移動 (仕入明細への既存導線を維持)
- **supplierName 列を `/finance/accounts-payable-ledger` リンク** に変更 (買掛帳への新規導線)
- 両方とも新タブで開く (`target="_blank"`)
- E2E 影響: `/purchases` アサートは supplierCode 列側に書き換え必要 (既存 accounts-payable.spec.ts を確認)

### 8.6 Sidebar menu

```
経理 >
  ...
  買掛金 (/finance/accounts-payable)
  買掛帳 (/finance/accounts-payable-ledger)   ← 新設 (アイコン: BookOpen)
  ...
```

---

## 9. anomaly 検出実装詳細

```java
List<Anomaly> detectAnomalies(LedgerRow current, LedgerRow previousMonth) {
    List<Anomaly> out = new ArrayList<>();

    // UNVERIFIED
    if (current.changeTaxIncluded.signum() > 0 && current.verifiedAmount.signum() == 0) {
        out.add(Anomaly.of("UNVERIFIED", "WARN", "当月仕入あり・検証金額なし"));
    }

    // VERIFY_DIFF (verified > 0 の場合のみ)
    if (current.verifiedAmount.signum() != 0) {
        BigDecimal diff = current.changeTaxIncluded.subtract(current.verifiedAmount).abs();
        if (diff.compareTo(BigDecimal.valueOf(100)) > 0) {
            out.add(Anomaly.of("VERIFY_DIFF", "WARN", "仕入と検証額に差: ¥" + diff));
        }
    }

    // NEGATIVE_CLOSING (値引繰越 or 支払超過)
    if (current.closingBalanceTaxIncluded.signum() < 0) {
        if (current.hasPaymentOnly) {
            out.add(Anomaly.of("PAYMENT_OVER", "INFO", "支払超過 (payment-only 行で残高負)"));
        } else {
            out.add(Anomaly.of("NEGATIVE_CLOSING", "INFO", "累積残が負 (値引繰越)"));
        }
    }

    // PAYMENT_ONLY (badge 用)
    if (current.hasPaymentOnly) {
        out.add(Anomaly.of("PAYMENT_ONLY", "INFO", "当月支払のみ (仕入無し)"));
    }

    // CONTINUITY_BREAK
    if (previousMonth != null) {
        BigDecimal diff = current.openingBalanceTaxIncluded
            .subtract(previousMonth.closingBalanceTaxIncluded).abs();
        if (diff.compareTo(BigDecimal.ONE) > 0) { // ±1 円許容
            out.add(Anomaly.of("CONTINUITY_BREAK", "CRITICAL",
                "前月 closing との継続不整合: 前月末 ¥" + previousMonth.closingBalanceTaxIncluded
                + " ≠ 当月 opening ¥" + current.openingBalanceTaxIncluded));
        }
    }

    return out;
}
```

MF 比較 ON 時は UI 側で:
```typescript
if (Math.abs(self.closing - mf.closing) > 10000) {
    anomalies.push({ code: 'MF_MISMATCH', severity: 'WARN', ... })
}
```

---

## 10. テスト観点

### Backend

- supplier が存在しない → 404 / 空 response
- fromMonth > toMonth → 400
- 期間が 24 ヶ月超 → 400
- 非 admin が他 shop 指定 → 自 shop に強制 (assertShopAccess)
- 期間内に summary が 0 件 → 空 rows, summary 0
- 期間の途中月に summary が欠落 (月抜け) → その月は rows に含まれない (表示 skip)
- 税率別が複数 → 合算・代表値ロジック動作
- is_payment_only=true 行 → hasPaymentOnly=true, taxRateCount=1
- CONTINUITY_BREAK 検出: 人為的に opening/closing を崩して確認
- MF Service: sub_account 名一致・不一致両パターン
- MF Service: MF 認証切れ → 401
- MF Service: scope 不足 → 403

### Frontend E2E

- 仕入先選択 → テーブル表示
- URL クエリパラメータ保持
- MF 比較 ON → 追加列表示 + loading + 結果表示
- MF 比較 OFF 切替 → 列消滅、リロードなし
- anomaly バッジ表示色 (UNVERIFIED=red, NEGATIVE_CLOSING=amber など)
- /accounts-payable からの遷移リンクが動作
- sidebar menu 追加が admin/非admin でそれぞれ可視

---

## 11. 実装順序

1. Repository に `findBySupplierAndMonthRange` 追加
2. DTO 2 本 新設 (`AccountsPayableLedgerResponse`, `MfSupplierLedgerResponse`)
3. Service 新設 (`AccountsPayableLedgerService`)
4. Service 新設 (`MfSupplierLedgerService`)
5. FinanceController に 2 endpoint 追加
6. Frontend types 新設
7. Frontend page 新設 (`/finance/accounts-payable-ledger`)
8. Sidebar menu 追加
9. 既存 `/finance/accounts-payable` の supplierName リンク差し替え
10. コンパイル確認
11. 動作確認 (自動バッチ再起動 → curl + UI 確認)

---

## 12. リスクと軽減

| リスク | 軽減策 |
|---|---|
| MF /journals 取得が遅い | fromMonth/toMonth 必須、期間 24 ヶ月上限、UI は任意取得 |
| sub_account 名の表記揺れ (カミ商事 vs ｶﾐ 商事) | exact match のみ第 1 版、不一致時 UI で候補提示 |
| DoS: 大量 supplier × 長期間 | API 期間制限 + per request 1 supplier |
| 既存 accounts-payable のリンク変更で E2E 壊れる | テキストリンクは変えずクリック先の URL 変更、テストはアサート緩い |
| 非 admin が他 shop の買掛帳見る | `assertShopAccess` 流用 |
| MF /journals の pagination 失敗 | 既存 `MfApiClient.listJournals` の per_page=1000 + ループ |

---

## 13. 既存機能への影響

| 機能 | 影響 | 対処 |
|---|---|---|
| `/finance/accounts-payable` 一覧 | supplierName リンクの遷移先変更 | 既存機能 (値引繰越表示等) は維持 |
| Phase B' payment_settled 算出 | 無改修 (API は DB から読むだけ) | — |
| MfBalanceReconcileService | 無改修 (独立 Service 追加) | — |
| MfJournalReconcileService | 無改修 (別 endpoint) | — |
| `SmilePaymentVerifier` | 無改修 | — |

---

## 14. 将来拡張候補

- **税率別 expand**: row 右端のアイコンで税率別内訳を展開
- **仕入先名のあいまいマッチング**: Levenshtein or 正規化比較
- **CSV/Excel エクスポート**: 買掛帳として保存
- **複数仕入先比較**: multi-select で複数 supplier の推移を横並び
- **グラフ表示**: 月次残の推移チャート
- **annotation**: 異常月にコメントを残せる機能
