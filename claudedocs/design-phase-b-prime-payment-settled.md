# Phase B' — 買掛金残の正確な管理（payment_settled 列追加）設計書

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes`（Phase A の後続、同一ブランチで継続）
関連設計書: `design-supplier-partner-ledger-balance.md`（Phase 0/A/B の母体）

---

## 1. 背景と問題（真因の特定）

### Phase A 完了後の残課題

Phase A (2026-04-22 コミット `567f34d`) で `t_accounts_payable_summary` に `opening_balance_tax_included/excluded` を追加、バッチで前月 closing を繰越、フィルタ緩和 (`> 0 → != 0`) で値引超過行も保持するようにした。

仕訳突合 (`MfJournalReconcileService`) の 2026-03-20 PURCHASE diff は `+¥2,973,089 → +¥10` まで収束。

ただし Phase B の**残高突合** (`MfBalanceReconcileTab`) で重大な乖離が発覚:

| 指標 | 値 | 意味 |
|---|---|---|
| MF 買掛金 closing @ 2026-03-20 | ¥41,085,601 | **残高** (期首 + 仕入 − 支払) |
| 自社累積残 (MF 突合対象) | ¥178,861,005 | **仕入累計** (opening + 仕入 change のみ) |
| 差分 | **−¥137,775,404** | ≒ 期間中の支払累計 − 期首買掛金残 |

### 根本原因

`t_accounts_payable_summary.tax_included_amount_change` は `t_purchase_detail` から集計した**仕入のみ**。支払取崩 (買掛金 debit) は DB に入っていない。

Phase A で追加した `opening = 前月 closing` のコピーも、前月 closing 自体が「仕入累計」なので、繰越しても「仕入累計」のまま。

**設計書 `design-supplier-partner-ledger-balance.md` §4 の closing 定義**:
```
closing = opening + effectiveChange   (effectiveChange は仕入のみ)
```
これが**買掛金残ではなく仕入累計を表していた**。MF 買掛金 account と直接突合できない。

### Phase B' の目的

**買掛金の T 勘定定義** (`closing = opening + 仕入 − 支払`) を自社 summary に実装する。payment_settled 列を追加し、当月取崩額 (前月の確定請求額 `verified_amount`) を保持する。これにより MF 買掛金 closing との直接突合が可能になる。

---

## 2. 設計方針（ultrathink で検討）

### 2.1 選択肢評価

| 案 | 概要 | 評価 |
|---|---|---|
| **A. payment_settled 列を追加 (既存 summary 拡張)** | `t_accounts_payable_summary` に `payment_amount_settled_tax_{included,excluded}` を追加し、バッチで前月 `verified_amount` をコピー | **採用** — 既存 PK とマッチ、税率別管理が自然、最小変更 |
| B. 別テーブル `t_accounts_payable_payment` 新設 | supplier 単位で支払を別管理、JOIN で残高算出 | 税率別管理ができない、JOIN コスト、既存 UI/Service への統合コストが高い |
| C. t_smile_payment を直接参照 (query-time) | View 経由で毎回 JOIN | パフォーマンス不安、voucher_date と transaction_month の対応が複雑 |
| D. 買掛帳 (取引元帳) フル実装 | journal 的な取引明細テーブル | 既存影響大、Phase A 設計で見送り済 |

→ **A 案採用**。既存 `verified_amount` を最大活用、税率別単位でそのまま対応可能。

### 2.2 payment_settled の算出ルール（レビュー R1 反映）

#### 既存 `verified_amount` の重要な性質

`PaymentMfImportService.applyVerification` は振込明細 Excel の **invoice (supplier 単位の合計値)** を、その supplier の**全税率行に同じ値として書き込む**実装 (L258-272):

> 振込明細の請求額は支払先単位で1件だが、DBは税率別に複数行ある場合がある。
> UI 表示用途のため、全税率行に同じ verified_amount を書き込む
> （税率別の請求額内訳は Excel 側に存在しないため、合計値を代表値として保持）。

既存 `sumVerifiedAmountForGroup` (L402-414) はこの事実を前提に **「全行一致なら代表値、不一致なら SUM」** で MF 出力金額を算出している。

**naive に tax_rate 別コピーすると税率数倍に過大計上** (R1 Blocker)。対処必須。

#### 基本ルール（memory/feature-payment-mf-import.md 準拠）

振込明細 Excel の運用:
- 5日送金・20日送金とも**前月 20日締め**の買掛を支払う
- `transferDate.minusMonths(1).withDayOfMonth(20)` = その送金で充当される締め月

→ **当月行 (transaction_month=M) の `payment_settled` 合計** = **前月 supplier 単位の支払額 (= `sumVerifiedAmountForGroup` 相当)**

#### 算出フロー（supplier 単位 → 税率比で按分）

```
1. 前月 M−1 の行を (shop, supplier) でグルーピング
2. 各 supplier について前月の支払合計を算出 (既存 sumVerifiedAmountForGroup ロジック準拠):
   - 全税率行の verified_amount が全て一致 → 代表値 (BULK パターン)
   - 不一致 → SUM (MANUAL パターン)
   - 全て null → supplier_paid = 0
3. 当月 M の行を (shop, supplier) でグルーピング
4. 各 supplier の当月 tax_included_amount_change 合計 (supplier_change[curr]) を算出
5. 当月各 tax_rate 行へ **change 比で按分**:
   
   if supplier_change[curr] > 0:
       row.payment_settled_tax_included = supplier_paid × (row.change / supplier_change[curr])
       row.payment_settled_tax_excluded = supplier_paid_excl × (row.change_excl / supplier_change_excl[curr])
   else:
       # 当月仕入ゼロ → payment-only 行で処理 (§2.2 後段参照)
```

**税抜側の算出**: supplier 内の change 比で按分するので `tax_rate/100` 逆算は不要。税抜 payment_settled も同様に supplier の change_excl 比で按分。これで R4 (丸め誤差) も解消 (既存税抜集計値の比で配分のみ)。

#### ゼロ除算・端数処理

- `supplier_change[curr] = 0` → payment-only 行ルートに切替 (§2.2 後段)
- 端数は `RoundingMode.DOWN`、supplier 内の **最後の行で差分調整** (Σ が supplier_paid に合うよう丸めを吸収)

#### 疑似コード

```java
// 前月 supplier 単位の支払合計を構築
Map<SupplierKey, BigDecimal> prevPaidPerSupplier = buildPrevPaidMap(prevList);
//   key: (shop, supplier)
//   value: sumVerifiedAmountForGroup 準拠で算出

// 当月 supplier 単位の change 合計を構築
Map<SupplierKey, BigDecimal> currChangePerSupplier = buildCurrChangeMap(currentList);

// 按分適用
for (SupplierKey key : currentList の supplier set) {
    BigDecimal paid = prevPaidPerSupplier.getOrDefault(key, ZERO);
    BigDecimal changeTotal = currChangePerSupplier.get(key);
    if (paid.signum() == 0) continue; // 前月支払なし
    if (changeTotal.signum() == 0) continue; // payment-only ルートへ
    List<TAccountsPayableSummary> supplierRows = groupBy(key, currentList);
    distributeByChangeRatio(supplierRows, paid); // 最終行で端数吸収
}
```

#### 手動確定行の扱い

手動確定行 (verified_manually=true) の **change 列は保護** (既存 Phase A ポリシー)、**payment_settled は上書き** (opening と同じポリシー)。

#### payment-only 行の生成（R1 + R2 反映）

**ケース**: 前月 supplier で verified_amount > 0 だが、当月 change 合計 = 0 (当月は仕入無し、支払のみ発生)。

この supplier には当月の行が (supplier, tax_rate) 単位で存在しない or 存在しても change=0。

**対処**: `is_payment_only=true` 列（新規）を持つ**単一行**を当月に生成:

```
前月 supplier で verified_amount > 0 かつ 当月 supplier の change 合計 = 0 の場合:
    tax_rate = 前月でその supplier が使った最大 tax_rate (tiebreak: 高い方)
    insert payment-only row:
        shop_no, supplier_no, supplier_code = 前月からコピー
        transaction_month = 当月
        tax_rate = 上記
        tax_included_amount_change = 0
        tax_excluded_amount_change = 0
        payment_settled_tax_included = prev supplier_paid
        payment_settled_tax_excluded = prev supplier_paid_excl (最終計算値を使用)
        opening_balance_tax_{included,excluded} = 前月 closing
        is_payment_only = true
        mf_export_enabled = true
        verified_manually = 前月 verified_manually (引継ぎ)
        verification_result = null
```

**stale-delete ガード**: `is_payment_only=true` の行は stale-delete 対象から除外。

**R1/R4 の解消**: supplier 単位で支払合計を持ち、単一行で保持するため税率重複なし。税抜は supplier の change_excl 比でなく、payment-only 行では税抜も「税込みを税率で逆算」ではなく **前月行で計算済みの税抜 closing 差分** を使う:
```
paymentSettledTaxExcluded (payment-only) = prevRow の sumVerifiedAmountExclFromChangeRatio と同等の方法で事前計算
```

簡素化のため payment-only 行の税抜/税込比は、前月 change 合計の税抜/税込比と同じで近似 (¥1〜数円の丸め差を許容、既存 verified_amount の精度と同程度)。

### 2.3 closing の再定義

```
effectiveChange = (verified_manually && verifiedAmount ≠ null) ? verifiedAmount : tax_included_amount_change
closing_tax_included = opening_balance_tax_included + effectiveChange - payment_settled_tax_included
closing_tax_excluded = opening_balance_tax_excluded + tax_excluded_amount_change - payment_settled_tax_excluded
```

Entity には持たない（Phase A と同じ方針）。DTO 層 (`AccountsPayableResponse.from(_, _, includeBalance=true)`) で算出。

### 2.4 期首残の扱い

Backfill の起点は `2025-06-20`。その前月 `2025-05-20` 以前の累積 ≒ 期首買掛金残は **自社 DB に存在しない**。

MF trial_balance_bs 2025-05-20 closing ≒ 期首残 (∑ 全 supplier 買掛)。Phase 0 で見た 2026-03-20 の opening_balance (API レスポンスの期首固定値) = 14,705,639 円。

**Phase B' のスコープ判断**: 期首残の自動注入は**本 Phase では行わない**。以下の理由:
1. MF 期首残は supplier×tax_rate に分解できない (account 単位のみ)
2. Phantom row (supplier=0 の特別行) を作ると既存フィルタ・検証・UI が全て影響を受ける
3. 期首残と仕入累積支払のネット差 (約 14.7M) は「既知残差」として UI に明示すれば運用上耐えられる
4. 厳密な期首注入は Phase B'' として後続対応可能

→ **残差 ≈ ¥14.7M は既知差**として UI に表示。残差の大半は期首残。

### 2.5 手動確定行 (`verified_manually=true`) との整合

**change 列は保護** (Phase A から継続): `tax_included_amount_change` / `verified_amount` はバッチ上書き禁止。

**payment_settled は常に上書き**: opening と同じポリシー。手動確定行でも前月 verified_amount を引けば payment_settled が算出できる。

→ closing = opening + effectiveChange - payment_settled は手動確定行でも正しく動作。

### 2.6 既存機能への影響

| 機能 | 影響 | 対処 |
|---|---|---|
| 既存 `MfJournalReconcileService` | なし (change 列無改修) | そのまま |
| 買掛金 CSV 出力 (MF 買掛支払 CSV) | なし (verified_amount ベースで継続) | そのまま |
| 仕入仕訳 CSV 出力 | なし (仕入データ源泉は変わらない) | そのまま |
| `SmilePaymentVerifier` | なし (既存 verify 動作継続) | そのまま |
| Phase A 値引繰越バッジ | 維持される (closing 負値で継続) | そのまま |
| `accountsPayableBackfillJob` | payment_settled も再計算要 | backfill Tasklet に同じロジック追加 |

---

## 3. DB 変更

### 3.1 V025 migration（R2 反映: is_payment_only 列追加）

```sql
-- V025__alter_accounts_payable_summary_add_payment_settled.sql
ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS payment_amount_settled_tax_included NUMERIC NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_amount_settled_tax_excluded NUMERIC NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_payment_only BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN t_accounts_payable_summary.payment_amount_settled_tax_included IS
    '当月完了した支払額 (税込、supplier 単位支払を税率別 change 比で按分)。5日/20日送金は前月20日締めに充てる運用。closing = opening + change - payment_settled の算出要素。';
COMMENT ON COLUMN t_accounts_payable_summary.payment_amount_settled_tax_excluded IS
    '当月完了した支払額 (税抜、同上 change_excl 比で按分)。';
COMMENT ON COLUMN t_accounts_payable_summary.is_payment_only IS
    'payment-only 行フラグ。当月 change=0 だが前月支払があった supplier のために生成された行。stale-delete 対象から除外する目印。';
```

既存 PK 非破壊、既存 index 再構築不要 (ADD COLUMN NOT NULL DEFAULT は PG 11+ で metadata-only、瞬時完了 / R12)。

### 3.2 Entity 拡張

```java
// TAccountsPayableSummary.java
@Builder.Default
@Column(name = "payment_amount_settled_tax_included", nullable = false)
@ColumnDefault("0")
private BigDecimal paymentAmountSettledTaxIncluded = BigDecimal.ZERO;

@Builder.Default
@Column(name = "payment_amount_settled_tax_excluded", nullable = false)
@ColumnDefault("0")
private BigDecimal paymentAmountSettledTaxExcluded = BigDecimal.ZERO;

@Builder.Default
@Column(name = "is_payment_only", nullable = false)
@ColumnDefault("false")
private Boolean isPaymentOnly = false;
```

---

## 4. バッチ改修

### 4.1 AccountsPayableAggregationTasklet

既存の「前月 closing → 当月 opening 繰越」ロジックに、以下を追加:

1. **payment_settled 計算**: 前月行の `verified_amount` を元に、当月行の payment_settled を set
2. **payment-only 行の生成**: 前月で支払があったが当月は仕入が無い (shop, supplier, tax_rate) について、当月に空行 + payment_settled を作る

#### 疑似コード

```java
// build prev month map with verified_amount
LocalDate prev = periodEndDate.minusMonths(1);
List<TAccountsPayableSummary> prevList = service.findByTransactionMonth(prev);
Map<Key, PrevRow> prevMap = new HashMap<>();
for (TAccountsPayableSummary p : prevList) {
    prevMap.put(key(p), new PrevRow(
        closing(p),           // opening + effectiveChange - payment_settled (既に Phase B' 後定義)
        p.getVerifiedAmount() // 当月の payment_settled source
    ));
}

// apply to current month rows
for (TAccountsPayableSummary row : toSave + preservedManual) {
    PrevRow prev = prevMap.get(key(row));
    // opening: 既存ロジック (Phase A)
    row.setOpeningBalance...(prev != null ? prev.closing().taxIncl() : 0);
    // payment_settled: 新規
    BigDecimal paymentIncl = (prev != null && prev.verifiedAmount() != null)
        ? prev.verifiedAmount() : BigDecimal.ZERO;
    BigDecimal paymentExcl = TaxCalculationHelper.toExcluded(paymentIncl, row.getTaxRate());
    row.setPaymentAmountSettledTaxIncluded(paymentIncl);
    row.setPaymentAmountSettledTaxExcluded(paymentExcl);
}

// payment-only 行生成
Set<Key> currentKeys = current 行の keys;
for (TAccountsPayableSummary p : prevList) {
    if (p.getVerifiedAmount() == null || p.getVerifiedAmount().signum() == 0) continue;
    if (currentKeys.contains(key(p))) continue;
    // payment-only 行を生成
    TAccountsPayableSummary paymentOnly = TAccountsPayableSummary.builder()
        .shopNo(p.getShopNo()).supplierNo(p.getSupplierNo())
        .supplierCode(p.getSupplierCode())
        .transactionMonth(periodEndDate)
        .taxRate(p.getTaxRate())
        .taxIncludedAmountChange(BigDecimal.ZERO)
        .taxExcludedAmountChange(BigDecimal.ZERO)
        .openingBalanceTaxIncluded(closing(p).taxIncl())
        .openingBalanceTaxExcluded(closing(p).taxExcl())
        .paymentAmountSettledTaxIncluded(p.getVerifiedAmount())
        .paymentAmountSettledTaxExcluded(toExcluded(...))
        // 検証関連は null/default
        .build();
    paymentOnlyList.add(paymentOnly);
}
service.saveAll(paymentOnlyList);
```

#### 既存 stale-delete ロジックへの影響

payment-only 行は新たに作られるので、stale-delete 判定で「集計対象外」と誤認されないよう注意。実装上は `saveAll(toSave + paymentOnlyList)` した後に stale 計算するため、savedKeys に payment-only 行の key も含めることで保護される。

### 4.2 AccountsPayableBackfillTasklet（R5 反映: 起点強制 + 途中再開禁止）

同じロジックを `processOneMonth` に適用。月単位 REQUIRES_NEW tx で fromMonth → toMonth を順次処理。

**R5 対処 (途中再開時の opening 不整合防止)**:

Phase A 時代の backfill は `fromMonth` を任意に指定できたが、Phase B' では「途中月から再開」すると、その月の前月 closing が **Phase A 旧式 (payment_settled 未反映) のまま** → 繰越 opening が過大になる。

**Controller バリデーション追加**:
- `/batch/execute/accountsPayableBackfill` で `fromMonth != 2025-06-20` の場合は **warning パラメータ `allowPartialResume=true` 必須**
- デフォルト (`allowPartialResume=false`) では fromMonth は 2025-06-20 固定強制
- UI は通常の再集計で `fromMonth=2025-06-20`、`toMonth=2026-03-20` (または最新月) を送る
- ad-hoc 再開は admin が意図的に allowPartialResume=true を付ける場合のみ許可

**tasklet 側のフォールバック**: `fromMonth != 2025-06-20` の場合 WARN ログを出し、既存 opening を信頼せず **前月行の payment_settled も最新ロジックで再計算しながら進む**。すなわち `processOneMonth(M)` が先に `processOneMonth(M-1)` を参照するとき、M-1 の closing を「その時 DB に残っている値から再計算 (on-the-fly)」する。

### 4.3 TaxCalculationHelper の扱い（R4 反映）

R4 で指摘の通り、税込→税抜の逆算は既存 `calculateTaxAmount` (税抜→税込 DOWN) と**可逆でない** (±1 円誤差)。

**Phase B' の設計判断**: 税抜 payment_settled は **前月 row の `tax_excluded_amount_change` 比で按分** することで取得する (§2.2)。

```
supplier_paid_excl = prev supplier の tax_excluded_amount_change 合計 に対する ratio を適用:
  = supplier_paid_incl × (Σ prev.tax_excluded_amount_change / Σ prev.tax_included_amount_change)
```

これにより:
- 税込→税抜の直接逆算は不要 → 新 helper `toExcluded` 追加不要
- 既存 `TaxCalculationHelper.calculateTaxAmount` (税抜→税込 DOWN) と丸め整合
- ±1 円の既知誤差は「Σ が supplier_paid 合計に合うよう最終行で端数吸収」で UI 見え方は問題なし

#### R3 (税抜/税込の非対称) 対処

手動確定行で `effectiveChange` は税込のみ `verifiedAmount` 優先、税抜は `tax_excluded_amount_change` (自動集計値) のまま。この非対称は Phase A から継承しているため Phase B' でも維持。

closing 表示は **税込が正** (MF 突合指標は税込)。税抜 closing は**参考値**として `MfBalanceReconcileTab` に注記:
> 税抜 closing は手動確定時に税込み closing と税額整合が微小ズレ可。突合は税込ベースで行う。

---

## 5. DTO 変更（R8/R9 反映: closing 算出を共通 util に集約）

### 5.1 共通 util `PayableBalanceCalculator` を新設

複数箇所で closing 算出するので共通化:

```java
// backend/src/main/java/jp/co/oda32/domain/service/finance/PayableBalanceCalculator.java
public final class PayableBalanceCalculator {
    private PayableBalanceCalculator() {}

    public static BigDecimal effectiveChangeTaxIncluded(TAccountsPayableSummary r) {
        boolean manual = Boolean.TRUE.equals(r.getVerifiedManually());
        return manual && r.getVerifiedAmount() != null
                ? r.getVerifiedAmount()
                : nz(r.getTaxIncludedAmountChange());
    }

    public static BigDecimal closingTaxIncluded(TAccountsPayableSummary r) {
        return nz(r.getOpeningBalanceTaxIncluded())
                .add(effectiveChangeTaxIncluded(r))
                .subtract(nz(r.getPaymentAmountSettledTaxIncluded()));
    }

    public static BigDecimal closingTaxExcluded(TAccountsPayableSummary r) {
        return nz(r.getOpeningBalanceTaxExcluded())
                .add(nz(r.getTaxExcludedAmountChange()))
                .subtract(nz(r.getPaymentAmountSettledTaxExcluded()));
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
```

### 5.2 呼び出し側の更新（R9 反映: 一括更新対象 4 ヶ所）

- `AccountsPayableResponse.from(_, _, includeBalance)` → `PayableBalanceCalculator.closingTaxIncluded/Excluded` を使用
- `AccountsPayableAggregationTasklet.buildPrevClosingMap` → 同上 (前月 closing 計算)
- `AccountsPayableBackfillTasklet.buildPrevClosingMap` → 同上
- `MfBalanceReconcileService.closingOf` → 同上

4 ヶ所全て同じ計算式に統一されることを **テスト計画 UT-07 / UT-30 / UT-33 で確認**。

### 5.3 `AccountsPayableResponse` 構造

```java
// include=balance 時のみ set
private BigDecimal openingBalanceTaxIncluded;       // 既存 (Phase A)
private BigDecimal openingBalanceTaxExcluded;       // 既存
private BigDecimal paymentSettledTaxIncluded;       // 新規 (Phase B')
private BigDecimal paymentSettledTaxExcluded;       // 新規
private BigDecimal closingBalanceTaxIncluded;       // PayableBalanceCalculator 使用
private BigDecimal closingBalanceTaxExcluded;       // PayableBalanceCalculator 使用
private Boolean isPaymentOnly;                      // 新規 (UI でバッジ表示用)
```

---

## 6. フロントエンド変更

### 6.1 `types/accounts-payable.ts`

```typescript
export interface AccountsPayableBalance {
  openingBalanceTaxIncluded: number
  openingBalanceTaxExcluded: number
  paymentSettledTaxIncluded: number      // 新規
  paymentSettledTaxExcluded: number      // 新規
  closingBalanceTaxIncluded: number      // 算出式変更後
  closingBalanceTaxExcluded: number
}
```

### 6.2 `accounts-payable.tsx`（R13 反映: 横幅緩和）

balance 列は既存の 2 段ヘッダに準じて 3 列構成 (balance トグル ON のみ表示):
```
| 前月繰越(opening) | 当月支払(payment_settled) | 累積残(closing) |
```

**横幅緩和策**:
- 当月支払列は「取崩済」バッジ + 金額のコンパクト表示 (`¥1,234,567 取崩済`)
- payment_settled=0 の行は金額省略 (`—`)
- balance トグル OFF (既定) では既存と同じ幅

**payment-only 行の表示**:
- `is_payment_only=true` の行には「支払のみ」Badge (outline, slate 色)
- 他の列 (仕入税抜・税込・振込明細額) は `—` 表示
- balance トグル ON 時のみ見える位置に挿入 (OFF 時は balance 列と一緒に隠す想定だが、UI 一貫性のため display フィルタを設ける)

**バッジ条件の修正 (R17 反映)**:
- 「値引繰越」バッジ: `closing < 0 AND NOT is_payment_only` (従来の値引超過)
- 「支払超過」バッジ (新): `closing < 0 AND is_payment_only` (支払完了後の過払い状態)

### 6.3 `MfBalanceReconcileTab.tsx`

残差に「期首残 ≈ ¥14.7M は既知差 (Phase B' スコープ外、期首注入は別 Phase)」の注記を追加。

---

## 7. 実装順序

1. V025 migration 作成
2. Entity に 2 列追加 (`@Builder.Default = ZERO`)
3. `TaxCalculationHelper` に `toExcluded` メソッド追加 (既存なければ)
4. DTO `AccountsPayableResponse` に balance 4 列 + closing 算出式変更
5. `MfBalanceReconcileService` の closingOf 算出式変更
6. `AccountsPayableAggregationTasklet` 改修 (前月 verified_amount → payment_settled, payment-only 行生成)
7. `AccountsPayableBackfillTasklet` 同じ改修
8. フロント型分離拡張
9. UI 列追加 (accounts-payable.tsx)
10. MfBalanceReconcileTab に期首残注記
11. コンパイル確認 (backend/frontend)
12. DB migration 適用 (Flyway 自動 on JVM restart)
13. 10 ヶ月再集計 (accountsPayableAggregation 2025-06 〜 2026-03)
14. backfill 再実行 (payment_settled を反映)
15. MfBalanceReconcileTab で 2026-03-20 の差が ≈ ¥14.7M に収束することを確認

---

## 8. 検証シナリオ

### 8.1 機能検証

| # | シナリオ | 期待結果 |
|---|---|---|
| V1 | V025 migration 適用後、既存行の payment_settled は DEFAULT 0 | NULL 無し、全行 0 |
| V2 | 10 ヶ月再集計後、1/20 行の payment_settled に 12/20 verified_amount がコピーされている | (shop, supplier, taxRate) 単位で一致 |
| V3 | 2/20 で当月仕入無しの supplier だが 1/20 に verified_amount > 0 だった場合、payment-only 行が 2/20 に生成される | change=0, payment_settled>0, opening=前月 closing |
| V4 | 手動確定行 (verified_manually=true) の change 列は保護、payment_settled は上書きされる | change 維持、payment_settled 更新 |
| V5 | 2026-03-20 の MfBalanceReconcileTab で diff が ≈ ¥14.7M (期首残) まで収束 | ¥178M → ¥15M 前後 |
| V6 | Phase A 機能 (竹の子の里の値引繰越) は継続動作 | closing 負値バッジ表示 |
| V7 | `MfJournalReconcileService` 3/20 PURCHASE diff は変わらず ¥10 | 仕入 change は無改修なので影響なし |

### 8.2 エッジケース

| # | ケース | 期待動作 |
|---|---|---|
| E1 | 1/20 verified_amount = null の行 → 2/20 で payment_settled=0 | WARN ログ、動作継続 |
| E2 | 起点 2025-06-20 は前月行なし → payment_settled=0 全行 | WARN ログ、既存 Phase A と同じ動作 |
| E3 | 税率 0% の行 (竹の子の里 etc) の payment_settled は taxExcluded=taxIncluded | 税抜=税込 |
| E4 | payment-only 行の supplier_code は前月からコピーされる | null でない |
| E5 | backfill 途中失敗 → 次回再実行は同じ月から再開 | month-level REQUIRES_NEW tx で cleanly resumable |

---

## 9. リスクと軽減策

| リスク | 影響 | 軽減策 |
|---|---|---|
| payment-only 行大量生成による DB 肥大化 | 中 | 1 supplier あたり月 1 行程度。supplier 数 ×12 ヶ月 = 数百行程度で問題なし |
| 期首残非注入による常時 ≈¥14.7M 差 | 低 (UI で既知差を明示) | Phase B'' で期首注入実装、もしくは運用で受容 |
| verified_amount の取込漏れで payment_settled=0 のまま | 中 | WARN ログ + UI の未検証 orange banner で可視化 (既存機能流用) |
| 税抜計算の丸めで closing 税抜合計が微小ずれ | 低 | RoundingMode.DOWN 統一で許容 (既存 TaxCalculationHelper と整合) |
| 既存 MfJournalReconcileService 影響 | 低 | 仕入 change 無改修のため影響なし |
| backfill 再実行中に UI でアクセスして不整合を見る | 低 | 月単位 REQUIRES_NEW tx で整合、UI は include=balance のみ影響 |

---

## 10. スコープ外（Phase B'' 候補）

- 期首残 (MF 2025-05-20 買掛金 closing) の phantom 行注入 → 完全一致を目指す
- **過去 7 ヶ月 (2025-06〜2025-12) の `verified_amount` 遡及充填** (`t_smile_payment` から集計、2025-06〜2025-12 は振込明細 Excel 機能実装前のため空)
- MfBalanceReconcileTab の仕入先別ドリルダウン (MF journals 累積 fallback)
- 売掛金残高突合 (Phase C の PK 5 列問題と合わせて)
- `t_smile_payment` と `verified_amount` の整合性監査 (現状 verified_amount 優先)
- 早払収益・振込手数料値引の細部差調整 (差が数万円規模のため後続で対応)

## 11. 実装・検証結果 (2026-04-22 完了)

### 実装確定

設計レビュー (Blocker 2 件 + Critical 3 件) を 1 ループで解消し、コードレビュー (Blocker 2 件 + Critical 3 件) も 1 ループで解消。実装は設計書通り完成。

**主な実装決定**:
- 共通 util `PayableBalanceCalculator` (closing 算出 4 箇所に集約)
- 共通 Service `PayableMonthlyAggregator` (Aggregation / Backfill 両方から利用)
- Backfill は `TransactionTemplate` で月単位 REQUIRES_NEW tx を明示制御 (self-invocation 制約回避)
- payment-only 行は `is_payment_only=true`, `verified_amount=null`, `verifiedManually=false` で生成 → 次月 paid 計算に影響させない

### 実測結果 (2026-03-20 時点)

| 項目 | 値 |
|---|---|
| 自社 累積残 (MF 対象) | ¥153,831,971 (45 行 内 payment-only 6 行) |
| MF 買掛金 closing | ¥41,085,601 |
| 差分 | ¥-112,746,370 |
| 期待 (期首残 ≈¥14.7M) | 期待差 ≈¥14.7M |

### 残差 ¥-112M の原因と判断

月別分析で **2025-06〜2025-12 (7 ヶ月) の `verified_amount = 0`** が判明。これは振込明細 Excel 取込機能 (memory: 2026-04-14 実装) より前の期間で、自社 DB に支払記録が存在しない。

- 2026-01-20: 初めて verified_amount 記録 (¥12,317,204)
- 2026-02-20: payment_settled 機能開始 (¥12,249,644 が 1/20 verified から反映)
- 2026-03-20: payment_settled ¥14,595,570 (2/20 verified から反映)

**Phase B' 実装は正常動作** — 2026-02-20 以降の残高突合が機能する。2025-06〜2025-12 の仕入累計 (≈¥100M) が取崩されていないため、期首残 ¥14.7M に加えて差に乗っている。

### 完了判断と次フェーズ

Phase B' の完了基準は「payment_settled 列と T 勘定化の動作確認」。これは満たす。
**過去データ遡及 (2025-06〜2025-12 verified_amount 充填) は別設計課題として Phase B'' に繰越**。

payment-only 行で実在検出した仕入先 (6 件): 三興化学、西日本衛材、大阪包装社、ﾄｯｸﾌﾞﾗﾝｼｭ、ハラプレックス、信越ファインテック。いずれも適切に opening→closing 繰越が機能している。

---

## 11. 参考資料

- `claudedocs/design-supplier-partner-ledger-balance.md` (Phase 0/A の母体)
- `memory/project_ap_ar_cumulative_balance.md` (Phase A 完了状況)
- `memory/feature-accounts-payable.md` (買掛金一覧機能)
- `memory/feature-payment-mf-import.md` (振込明細 Excel 取込)
- Commit `567f34d` (Phase A 実装)
