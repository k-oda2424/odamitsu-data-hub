# 設計レビュー: 買掛金ファミリー (Cluster D)

レビュー日: 2026-05-04
対象設計書: 6 本
- `claudedocs/design-accounts-payable.md`
- `claudedocs/design-accounts-payable-ledger.md`
- `claudedocs/design-integrity-report.md`
- `claudedocs/design-supplier-balances-health.md`
- `claudedocs/design-consistency-review.md`
- `claudedocs/design-phase-b-prime-payment-settled.md`

レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

---

## サマリー

- 総指摘件数: **Critical 4 / Major 9 / Minor 12** (計 25 件)
- 承認状態: **Needs Revision** (Critical 修正後に再レビュー推奨)

最重要 (要対応):
1. **C-1 期首日のズレ**: 設計書は「MF 期首 = 2025-06-21」と記載するが実装は `MF_PERIOD_START = 2025-05-20` 固定。`MfPaymentAggregator.MF_FIRST_BUCKET = 2025-07-20` とも不整合。
2. **C-2 旧 `AccountsPayableSummaryTasklet` がまだ Bean 登録されており Phase A/B' を経由しない並行集計バッチとして残存**。本テーブルへ書き込み可能でリグレッション源。
3. **C-3 supplier_no=303 の除外が `AccountsPayableSummaryCalculator` のみで適用** されており、整合性レポート / 累積残一覧 / 買掛帳 ではフィルタしないため "MfOnly" や "SELF_MISSING" の偽陽性ノイズの源。
4. **C-4 `ConsistencyReviewService.upsert` が IGNORE → MF_APPLY 切替時にロールバック条件分岐が誤る** — 旧 review が IGNORE (副作用なし) でも `previousVerifiedAmounts != null` ならロールバックを呼ぶべきだが、現在は `actionType == MF_APPLY` のみで判定。逆方向 (MF_APPLY → IGNORE) は OK だが、IGNORE で previous が消えるとロールバック手段を失う。

---

## Critical 指摘

### C-1 期首日と MF fiscal year 境界の三重定義不整合 (CRITICAL)

**該当**:
- `claudedocs/design-supplier-balances-health.md:124` — 「fromMonth は **MF 会計期首 (2025-05-20)** 固定」
- `claudedocs/design-phase-b-prime-payment-settled.md:179-186` — 「Backfill の起点は `2025-06-20`、その前月 `2025-05-20` 以前の累積 ≒ 期首買掛金残」
- `MEMORY.md` — 「MF journal #1 (2025-06-21, 41 supplier ¥14,705,639)」「期首 (2025-07-20 bucket 未満) は verified_amount で fallback」
- 実装:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:54` `MF_PERIOD_START = 2025-05-20`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:56` `OPENING_DATE = 2025-06-20`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:38-43` `MF_PERIOD_START = 2025-05-20`、`MF_FIRST_BUCKET = 2025-07-20`
  - `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:63` `EXPECTED_FROM_MONTH = 2025-06-20`

**問題**:
4 つの異なる「期首日」が混在する。
1. `2025-05-20` (MF_PERIOD_START、MF /journals fetch 開始日) — 設計書 D で「MF 会計期首」と明記
2. `2025-06-20` (OPENING_DATE / EXPECTED_FROM_MONTH、自社 backfill 起点) — 設計書 B' で起点
3. `2025-07-20` (MF_FIRST_BUCKET、MF debit 上書きを開始する閾値) — `MfPaymentAggregator` のみ
4. `2025-06-21` (実 MF fiscal year 開始日、MEMORY.md より) — どこにも定数化されていない

3 と 4 の関係は「MF API は会計年度跨ぎの取引が含まれず、2025-06-21 以降の取引が `toClosingMonthDay20` で 2025-07-20 bucket に入る」と推察できるが、設計書 B' / D / Phase B''(light) いずれにも明記なし。

**影響**:
- `SupplierBalancesService.accumulateMfJournals` は MF_PERIOD_START (2025-05-20) 以降の monthKey を採用するが、実際 MF fiscal year は 2025-06-21 開始のため bucket 2025-05-20 / 2025-06-20 は常に空 — レスポンス上 `mfStartDate=2025-05-20` と返るが意味のないラベル。
- `MfPaymentAggregator.getMfDebitBySupplierForMonth` は transactionMonth が 2025-07-20 未満なら空 Map を返し fallback、それ以上なら MF debit で上書きする。バッチで 2025-06-20 / 2025-07-20 月を再集計したとき、両月の payment_settled が verified_amount fallback になるか MF debit になるかが暗黙ルール。コメントは「期首前 (2025-07-20 bucket 未満) は verified_amount で fallback」だが MEMORY.md 以外に文書化がない。
- 設計書 D §3.4 に「期首月 opening が 0 でない supplier (既存繰越あり) は ... 期首月の単月比較では差が出る」と書くが、そもそも「期首月」の定義が 2025-05-20 / 06-20 / 06-21 / 07-20 のどれか不明確。

**修正案**:
1. 共通定数クラス `FinanceConstants` (or 新 `MfPeriodConstants`) に以下を集約:
   - `MF_FISCAL_YEAR_START = LocalDate.of(2025, 6, 21)` — MF fiscal year 開始 (取引 1 件目)
   - `SELF_BACKFILL_START = LocalDate.of(2025, 6, 20)` — 自社 backfill 起点 (前月 20 日締め日)
   - `FIRST_PAYABLE_BUCKET = LocalDate.of(2025, 7, 20)` — MF debit 上書きの初回 bucket
   - `MF_JOURNALS_FETCH_FROM = LocalDate.of(2025, 5, 20)` — fetch 開始日 (fiscal year 跨ぎ fallback 含)
2. 設計書 6 本で同名の用語を使い、「期首」「期首月」「fiscal year 開始」を明確に区別する。
3. SupplierBalancesService / MfPaymentAggregator / MfSupplierLedgerService 各 service の `MF_PERIOD_START` private const を削除し共通定数に集約。

### C-2 旧 `AccountsPayableSummaryTasklet` が残存 (CRITICAL)

**該当**:
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryTasklet.java:33` — `@Component @StepScope` で Bean 登録されたまま
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableAggregationTasklet.java:43` — Phase B' 対応の新規 tasklet
- `backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableSummaryConfig.java` (確認推奨) — job 定義
- 設計書 `claudedocs/design-accounts-payable.md:34` — 「`AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` は既存のまま」
- 設計書 `claudedocs/design-accounts-payable.md:451` — 「再集計ジョブ `accountsPayableAggregation`(軽量版、Init+集計の2ステップ)に変更」

**問題**:
旧 tasklet は `opening_balance` / `payment_settled` / `is_payment_only` / `PayableMonthlyAggregator` を一切経由せず `t_accounts_payable_summary` を save する。設計書 D / B' / 整合性で前提とする「すべての書き込みは Phase B' 計算経路を通る」が崩れる。

`AccountsPayableSummaryConfig` 配下で `accountsPayableSummaryJob` Bean が今も残っている場合 (設計書 §6.5 では "既存" とのみ記載)、誤って起動すると:
- 既存行の opening_balance が触られないため Phase A 不変条件は守られるが、change のみ更新で payment_settled 不整合
- 新規 supplier 行の場合 opening_balance / payment_settled が NULL→DEFAULT 0 になり「期首前繰越のないクリーンな集計」と区別不能

**影響**:
- `/code-review` の対象差分には旧 tasklet がそのまま残っているが、設計書には「旧版 deprecation」の記述なし。
- 既存運用で `accountsPayableSummary` ジョブを誤起動すると Phase B' との整合が壊れ、整合性レポート / 累積残一覧で診断不能な diff が発生する。

**修正案**:
- 旧 `AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` の役割を整理:
  - Phase B' 後は `AccountsPayableAggregationTasklet` + `AccountsPayableBackfillTasklet` + `AccountsPayableVerificationReportTasklet` が正規ルート
  - 旧 tasklet 2 本を `@Deprecated` + Job 削除 or `@ConditionalOnProperty` で禁止フラグ
- `AccountsPayableSummaryConfig` (現存ならば) のジョブ Bean を `accountsPayableAggregationJob` 1 本に統合する設計を設計書 D に追記する。

### C-3 `supplier_no=303` 除外が集計レイヤのみで適用 (CRITICAL)

**該当**:
- `backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsPayableSummaryCalculator.java:89` `EXCLUDED_SUPPLIER_NO = 303` で集計時除外
- `MEMORY.md` 仕入一覧: 「supplierNo=303 除外 (買掛集計と一致)」
- 整合性レポート `AccountsPayableIntegrityService.java:200-333` — supplier_no=303 のフィルタなし
- 累積残一覧 `SupplierBalancesService.java:117-204` — 同上
- 買掛帳 `AccountsPayableLedgerService.java` — 同上

**問題**:
集計バッチで supplier_no=303 を除外しても、過去に集計済みで残っている行 (もしくは shop_no=2 経由で混入する手打ち行) は累積残突合・整合性レポートに登場する。MF 側にも対応 sub_account がない可能性大で常時 `SELF_MISSING` 偽陽性。

**影響**:
- 累積残一覧 (軸 D) のサマリ `selfMissingCount` が偽カウント
- 整合性レポート画面が「無視可能」雑音で埋まる → 案 X+Y (IGNORE 操作) で消す運用になり、本来検出したい diff の発見性が下がる
- 設計書には「在庫表手打ち supplier の扱い」記述ゼロ

**修正案**:
1. 共通 util `PayableExclusionFilter.isExcludedSupplier(supplierNo)` を新設して全 service で適用
2. 設計書 D / 整合性 / 買掛帳 §2 / §3 の「対象データ」に「supplier_no=303 (在庫表手打ち) は除外」を明記
3. もしくは Repository 層に `findByShopNoAndTransactionMonthBetweenExcludingSupplierIn` のような専用 method を追加し service 横断で一律フィルタ

### C-4 `ConsistencyReviewService.upsert` のロールバック判定が不完全 (CRITICAL)

**該当**:
- `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:62-68`

```java
existingOpt.ifPresent(old -> {
    if (ACTION_MF_APPLY.equals(old.getActionType())) {
        rollbackVerifiedAmounts(req.getShopNo(), req.getEntryKey(),
                req.getTransactionMonth(), old.getPreviousVerifiedAmounts());
    }
});
```

- 設計書 `claudedocs/design-consistency-review.md:75` — 「IGNORE 既存 review が MF_APPLY だった場合は **verified_amount を previous から復元**」
- 設計書 `claudedocs/design-consistency-review.md:243-260` (§8 フロー) — 「old.actionType == MF_APPLY」前提

**問題**:
1. 旧 review が `MF_APPLY` で previous_verified_amounts を持つ → 新 review が `MF_APPLY` の場合、ロールバックしてから applyMfOverride。これは OK。
2. 旧 review が `MF_APPLY` → 新 review が `IGNORE` に切替。ロールバック → review 上書き保存。これも OK。
3. **問題ケース**: 旧 review が `IGNORE` (previous = null) で、なんらかの理由で previous_verified_amounts が NULL でない (manual SQL 編集等) 場合、現在実装はロールバックを **スキップ** してしまう。

加えて設計書 §8 と実装に乖離あり:
- 設計書: `previous = captureCurrentVerifiedAmounts(); applyMfOverride(req); save review(req, previous)` — `previous` は MF_APPLY 時に毎回キャプチャ
- 実装: `applyMfOverride` 内で `previous` を return し、IGNORE 時は `previous = null` のまま review に保存

これは本質的に問題ないが、**`Map<String, BigDecimal>` を JSONB 列に書く際 IGNORE 時に明示的に NULL 化されていないと、PUT 連打で既存の MF_APPLY 由来の previous が JSONB に残り続けるリスク**がある (Hibernate の `@DynamicUpdate` 無し / merge 時の動作)。

**影響**:
- DELETE 時のロールバック対象判定 (`r.getActionType() == MF_APPLY`) は同じ条件で判断するため、IGNORE 上書きの後 DELETE → ロールバックしないが verified_amount は変わったまま、になる可能性。

**修正案**:
1. ロールバック判定を `old.getPreviousVerifiedAmounts() != null && !old.getPreviousVerifiedAmounts().isEmpty()` にする (action 種別ではなく snapshot 有無で判定)
2. `IGNORE` 上書き時 / `DELETE` 時に明示的に `setPreviousVerifiedAmounts(null)` してデータ整合
3. 設計書 §8 のフローも「`old.previousVerifiedAmounts != null` ならロールバック」に書き換え

---

## Major 指摘

### M-1 `MfPaymentAggregator.overrideWithMfDebit` が手動確定行を上書きしてしまう (Major)

**該当**: `backend/src/main/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregator.java:165-228`

設計書 B' §2.5 「**payment_settled は常に上書き**」とあるが、**案 A (MF debit 由来切替)** の `overrideWithMfDebit` は手動確定行 (`verified_manually=true`) も区別せずに MF debit を税率別に按分して payment_settled に書き込む。change=0 の supplier では「代表行に全額」+ 税率逆算という設計書 B' §2.2 §4.3 (R4) の方針 (「税抜逆算は使わない」) を破る経路がある (line 190-198)。

**影響**:
- 手動確定済みの supplier 月で、MF debit が verified_amount より優先される。MEMORY.md には「期首前は verified_amount で fallback」とあるが「期首後でも手動確定行を保護する」記述はなく、設計書にも案 A の挙動が文書化されていない。
- change=0 行に税率逆算を行う fallback コードは payment_settled が分からない supplier (MF にしかない) で発火するが、`r.getTaxRate()` が null や 0 の supplier (在庫表 / phantom 行) では NPE / DivideByZero (`divisor` は 100 以上だが) のリスク。

**修正案**:
1. 設計書 B' に "案 A (2026-04-23 追加)" として overrideWithMfDebit のセクション追加
2. 手動確定行は MF debit 上書き対象外 (verified_amount で固定) とする (R5 と一貫)
3. change=0 fallback を payment-only 行生成ロジックに統一する (overrideWithMfDebit から removeする)

### M-2 `summary` API がページングされていない supplier 集計をフルロードする (Major)

**該当**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:55-74`

```java
public AccountsPayableSummaryResponse summary(Integer shopNo, LocalDate transactionMonth) {
    Specification<TAccountsPayableSummary> spec = buildSpec(shopNo, null, transactionMonth, "all");
    List<TAccountsPayableSummary> list = repository.findAll(spec);
    long total = list.size();
    ...
```

**問題**:
- `findAll(spec)` で対象月の全行をロードしてカウント。COUNT クエリではない
- shop_no が null (admin) の場合は全 shop の全 supplier 取得 (現状 shop=1 のみだが将来的にスケールしない)
- 設計書 `claudedocs/design-accounts-payable.md` §5.2 では SQL レベルで集計する想定 (Response 構造から想像)

**影響**: 行数 200-300 程度のうちは問題ないが、「最新月時点で全 supplier × 税率」で 1000 行超えたとき heavy。

**修正案**:
- Repository に `@Query("SELECT COUNT(s), SUM(CASE WHEN s.verificationResult IS NULL THEN 1 ELSE 0 END), ... FROM TAccountsPayableSummary s WHERE ... ")` 集計クエリを 1 本追加

### M-3 設計書 D の「supplier_balances 初回 12.98s → cache hit 75ms」と実装に乖離 (Major)

**該当**:
- `MEMORY.md` 「supplier 累積残 + MF ヘルスチェック (軸 D+E): 初回 12.98s、cache hit 75ms」
- `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:140-150` (1) ループで `accumulateSelf` を每 supplier 呼ぶ
- `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:106-114` (`paymentSupplierService.findByShopNo` を fetch 後に各 supplier ごとに `resolveMatchedSubs` で `mfSubToCodes` を full-scan)

**問題**:
- `resolveMatchedSubs` は `mfSubToCodes` (`Map<String, Set<String>>`) を 2 回 (line 279-287) 走査。supplier 数 N に対して O(N × M)、M は MF master 件数 (~1000)
- 設計書 D §3.6 「supplier 集計は `subAccountName → MonthBucket` の Map 事前構築で O(1) lookup」と書いているが、**実装では sub_account_name → supplier_code の逆引き map がないため supplier_code → matched sub_account_names の lookup は O(M)**

**影響**: 75ms はキャッシュ hit 時の MF 取得回避効果。実コード上は逆引き不足で支払い先 100 件 × MF master 1000 件 = 10 万回比較。今は気にならないが MEMORY.md に記された性能改善の出処が不明。

**修正案**:
- 設計書 D §3.6 にロジック詳細を追記
- `Map<String, Set<String>> codeToSubNames = invertMfSubToCodes()` を service 起動時 1 回構築

### M-4 整合性レポートの「処理済 sub_account 重複検出」が transient state (Major)

**該当**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:203, 266, 353`

```java
processedSubNames.add(sn + "|" + month);
...
if (processedSubNames.contains(subName + "|" + month)) continue;
```

**問題**:
- `processedSubNames` は self 側ループで「supplier × matchedSubName × month」を全埋め
- 1 supplier の supplier_code が複数の sub_account に対応する場合 (carded 表記揺れ) 全て processed として登録
- MF 側の "self にない sub_account" 列挙 (line 336-372) で漏れなく "MF only" として出してしまう

具体例: 同一 supplier に MF master が「カミ商事」「ｶﾐ 商事」両方登録されている場合、self 側ループで両方に処理マーク → MF 側は 0 件出力。これは正しい動作だが、`subName + "|" + month` キーの単純連結なので `|` を含む sub_account 名 (理論上ありうる) でキー衝突リスク。

**修正案**: `Map<String, Set<LocalDate>> processed` に変更 + key を `Pair<String, LocalDate>` レコードに切替

### M-5 旧 `AccountsPayableVerificationReportTasklet` が `verified_manually` 行も flag 上書き (Major)

**該当**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationReportTasklet.java:115-122, 130-141, 156-166`

**問題**:
- 「差額が null のデータ」「差額が 5 円未満」「verificationResult=1」分岐すべてで `summary.setVerificationResult` / `summary.setMfExportEnabled` を呼ぶ
- `verifiedManually=true` の行を **スキップしていない**
- 設計書 `claudedocs/design-accounts-payable.md:317-321` 「`SmilePaymentVerifier` の改修」では「`verified_manually = true` の行は**スキップ**」だが、Report tasklet には同じ保護がない

**影響**:
- 手動確定行が verificationReport ジョブ実行で mfExportEnabled=true / false に強制更新される (運用で `accountsPayableVerification` ジョブが走った後に `accountsPayableVerificationReport` も走る場合)
- 「差額 5 円未満で MF出力ON」を強制する処理は手動確定行の経理判断を覆す

**修正案**: ReportTasklet 内 3 箇所のループに `if (Boolean.TRUE.equals(summary.getVerifiedManually())) continue;` を追加

### M-6 `MfHealthCheckService` の anomaly 集計が 0 固定残存 (Major)

**該当**:
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java:91-107`
- 設計書 `claudedocs/design-supplier-balances-health.md:188-192` — 「共通 util `PayableAnomalyCounter.count(shopNo)` を新設」
- 同 `MEMORY.md` Next Session Pickup — 「shop 単位の anomaly 集計 util 化 (`PayableAnomalyCounter`) — ヘルスチェック画面の `verifyDiffCount/continuityBreakCount/monthGapCount` を 0 固定から実装へ」

**問題**:
- 設計書 D §4.2 に「PayableAnomalyCounter で集計」と書いてあるが、現状 `verifyDiffCount` / `continuityBreakCount` / `monthGapCount` は **0 固定**
- `claudedocs/design-supplier-balances-health.md:196-200` のヘルス判定ロジック (🔴/🟡/🟢) は anomaly 合計 > 0/10 でしきい値判断するため、**機能していない**
- TODO コメントが残っているのは率直だが、設計書ではこれを「実装する」と明記

**修正案**:
- 設計書側を「v1: anomaly 詳細は 0 固定。v2 で `PayableAnomalyCounter` 実装」と明示する or 実装する
- Sprint 単位での "未着手" 状態をユーザーに見せる UI バッジ追加 (UI 側で `verifyDiffCount === 0 && continuityBreakCount === 0 ? '集計未対応' : ...`)

### M-7 `AccountsPayableIntegrityService.buildSupplierCumulativeDiffMap` が SupplierBalancesService の重い処理を毎回呼ぶ (Major)

**該当**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:543-560`

`/integrity-report` API 呼び出しのたびに `supplierBalancesService.generate(shopNo, toMonth, refresh)` を呼ぶ。設計書 §11 に "12 ヶ月処理の所要時間 8-15s" と記述されているが、その 8-15s には軸 D の generate コストも含まれるか不明。

**影響**:
- `/integrity-report` のレスポンス時間が `/supplier-balances` と同じレベルになる
- キャッシュ hit 時 75ms だが、refresh=true で 13 秒級になる
- 設計書 整合性 §11 のリスク表に記載なし

**修正案**:
- 設計書に「軸 D 連動で supplier_cumulative_diff を併記する」を §3 / §11 に明記
- パフォーマンス見積もりを「キャッシュ hit 時 ~200ms / cold 13s」に更新

### M-8 設計書 B' §6.2 の説明と実装の `closing` 列構成が違う (Major)

**該当**:
- 設計書 `claudedocs/design-phase-b-prime-payment-settled.md:432-438` — UI 列構成 `| 前月繰越(opening) | 当月支払(payment_settled) | 累積残(closing) |`
- 実際の `frontend/components/pages/finance/accounts-payable.tsx` (確認推奨) と `AccountsPayableLedgerResponse` 構造

設計書では balance トグル ON で 3 列追加だが、`AccountsPayableResponse.from` (line 121-130) の構造を見ると 6 列 (opening_incl/excl, payment_settled_incl/excl, closing_incl/excl) すべて返す。**設計書の記述粒度と実装の不整合**。

**影響**: フロント実装担当者が見るとき「結局何列出すの?」が不明確。

**修正案**: 設計書 B' §6.2 を実装に合わせて「税込/税抜の 2 段表記で 3 列 (opening/payment_settled/closing) × 2 = 6 セル」に修正。

### M-9 案 A の MF debit 上書き経路が `AccountsPayableBackfillTasklet` と `AccountsPayableAggregationTasklet` で重複定義 (Major)

**該当**:
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableAggregationTasklet.java:107-111`
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:127-130`

両方とも `monthlyAggregator.overrideWithMfDebit(rows, mfPaymentAggregator.getMfDebitBySupplierForMonth(shop, month), month)` を呼ぶ。同じ 5 行のシーケンス。

**影響**: 修正時に片方忘れリスク (例: shop_no をパラメータ化したいとき)。

**修正案**:
- `PayableMonthlyAggregator` に `applyAllPipelines(rows, prev, shopNo, month)` を追加し opening / payment_settled / mf_debit / payment_only を 1 メソッドに集約。
- `monthlyAggregator.applyOpenings` / `applyPaymentSettled` / `overrideWithMfDebit` / `generatePaymentOnlyRows` の呼び出し順は実装的に意味があるためコメントで明示。

---

## Minor 指摘

### m-1 `TAccountsPayableSummaryService.findAll()` が DB 全件 (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:39-41` — どこから呼ばれているか不明だが Service 公開 method として残存。使用箇所がなければ削除推奨。

### m-2 `findPaged` の `verificationFilter` が `String` で型安全性なし (Minor)

`TAccountsPayableSummaryService.java:44-52, 89-103` — `"unverified"|"unmatched"|"matched"|"all"` の文字列マッチ。設計書 §5.1 の `verificationResult` パラメータ (`null|0|1|all`) と命名不一致。 enum 化 + `@Pattern` バリデーション推奨。

### m-3 `AccountsPayableVerifyRequest` に `verifiedAmount` の正値バリデーションなし (Minor)

`backend/src/main/java/jp/co/oda32/dto/finance/AccountsPayableVerifyRequest.java:11-12` — `@NotNull` のみ。負値や 0 が来ると差額計算で予期せぬ挙動 (例: `applyVerification` 内で taxExcluded に負値が入る)。設計書 `design-accounts-payable.md` §5.3 にも値域記述なし。`@PositiveOrZero` 推奨。

### m-4 設計書「振込明細MF一括検証 (`PaymentMfImportService#applyVerification`)」の参照名と実コードに齟齬 (Minor)

設計書 `claudedocs/design-accounts-payable.md:289-291` 「振込明細MF一括検証 (`PaymentMfImportService#applyVerification`)」 vs 実装 `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:184` `applyVerification(String uploadId, Integer userNo)` — 引数情報がなく、設計書から API 把握しにくい。

### m-5 設計書 D §3.4「期首月 opening が 0 でない supplier」の記述が支離滅裂 (Minor)

`claudedocs/design-supplier-balances-health.md:115-117` — 「期首月 opening が 0 でない supplier (既存繰越あり) は、その opening が self 側のみに現れるため期首月の単月比較では差が出るが、本 endpoint は asOfMonth 時点の累積差のみを返す」が、**実際は m_supplier_opening_balance による期首注入で MF も含めて両者対称になるよう修正済**。設計書がこの修正を反映していない。

### m-6 `PayableMonthlyAggregator.SupplierAgg` 内 `closingExclTotal` が未使用 (Minor)

`backend/src/main/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregator.java:401-412` — record fields のうち `closingExclTotal` は payment-only 行生成時の `setOpeningBalanceTaxExcluded(agg.closingExclTotal())` (line 347) でのみ使用。これは正しいが、`SupplierAgg` の全体構造のうち `payment-only` ルート以外で参照される様子なし。設計書 B' §2.2 疑似コードに合わせれば OK。

### m-7 `MfJournalCacheService` に永続化 / TTL なし (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalCacheService.java:21-30` — JVM 再起動でキャッシュ全破棄。設計書 `design-supplier-balances-health.md:351` 「キャッシュの永続化 (DB 化) は不要」と明示されているため設計書通りだが、運用でバックエンド再起動が頻繁なら 8-15s レスポンスが復帰時に頻発する。invalidate API はあるが「再起動で勝手にクリア」の挙動を運用ドキュメントに残すこと推奨。

### m-8 設計書 整合性 §10 の 「決定」表に欠落あり (Minor)

`claudedocs/design-integrity-report.md:404-413` — 6 行の決定表に「supplier_no=303 除外の有無」「shop_no=1 固定 vs マルチ」「期首期間以前の扱い」「mf_account_master 重複時の挙動」が含まれていない。

### m-9 `ConsistencyReviewService.applyMfOverride` 端数吸収後に再 save (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:200-227` — for ループ内で `summaryRepository.save(r)` を呼んだ後、端数吸収で `largest` を更新するが **再 save なし**。Entity が同じ tx 内で managed 状態なので merge 時に flush で反映されるはず (Hibernate dirty check) だが、**`largest` の `verifiedAmountTaxExcluded` 更新を `save` 経由で永続化していないため、cascade / @Transactional の動作に依存**。明示 save 推奨。

### m-10 `AccountsPayableLedgerService.aggregateMonth` の autoAdjustedAmount 平均化ロジック (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:181-184` — 「税率別複数行で autoAdjusted が重複計上される」ため行数で割って代表値化。これは振込明細の "全税率行に同額書き込み" の前提に依存する。手動 verify では税率別に異なる autoAdjusted が入る可能性あるが、その場合の挙動は未定義 (平均で歪む)。

### m-11 buildSupplierCumulativeDiffMap の例外処理が雑 (Minor)

`AccountsPayableIntegrityService.java:556` — `catch (Exception e)` で全飲み込み + WARN ログ + 空 Map 返却。MF 認証切れも握りつぶされ、UI には「期末累積残判定スキップ (計算失敗)」程度のログのみ。MfReAuthRequiredException / MfScopeInsufficientException は再 throw して上位の handleException に委譲推奨。

### m-12 V026 / V027 / V028 migration が同一ブランチで増殖 (Minor)

`backend/src/main/resources/db/migration/V025-V029` — 5 個のミグレーションが同じブランチで連続。各ファイル内コメントは充実しているが、ブランチマージ時の version 競合リスク。Liquibase でなく Flyway の場合、別ブランチから V028 が来るとマージで衝突。`feature/payment-settled` `feature/consistency-review` 等、機能粒度のブランチに切る運用推奨 (設計プロセス側の問題)。

---

## 設計書間の整合性 (6 本横断レビュー)

### 用語統一の問題

| 用語 | 設計書 D | 設計書 B' | 設計書 整合性 | 設計書 買掛帳 | コード |
|---|---|---|---|---|---|
| 期首日 | 2025-05-20 | 2025-06-20 | (記述なし) | (記述なし) | 2025-05-20 / 06-20 / 06-21 / 07-20 |
| 「supplier」 | payment_supplier | payment_supplier | payment_supplier | payment_supplier | OK |
| 累積残 | "selfBalance" / "mfBalance" | "closing" | (該当なし) | "closingBalanceTaxIncluded" | 不統一 |
| 「期首前」 | (asOfMonth が MF_PERIOD_START 以前は 400) | (Backfill 対象外) | (記述なし) | (continuity チェックの起点) | 4 種類混在 |
| MATCH 閾値 | ¥100 | (記述なし) | ¥100 (MATCH_TOLERANCE) | ¥100 (VERIFY_DIFF_THRESHOLD) | 3 箇所定数別定義 |

### 重複定義

- `MATCH_TOLERANCE = 100` が以下 4 箇所で重複:
  - `TAccountsPayableSummaryService.MATCH_THRESHOLD = 100`
  - `AccountsPayableIntegrityService.MATCH_TOLERANCE = 100`
  - `AccountsPayableLedgerService.VERIFY_DIFF_THRESHOLD = 100`
  - `SupplierBalancesService.MATCH_TOLERANCE = 100`
  - `ConsistencyReviewService.STALE_TOLERANCE = 100` (実は同じ意図)

設計書 D §3.6 / 整合性 §3.2 / B' / consistency-review §C3 すべてが「MATCH_TOLERANCE と揃える」と書いているが、実装は **共通定数化されていない**。`FinanceConstants` への集約推奨。

### スコープの食い違い

- 設計書 `design-accounts-payable-ledger.md:564` 「MF 比較は明示ボタン」 vs 設計書 `design-integrity-report.md:39` 「buying ledger に MF 比較バッジ追加」 — どちらが正?
- 設計書 D §3.4 「fromMonth は **MF 会計期首 (2025-05-20)** 固定」 vs 設計書 B' §1.4 「期首残 ≈ ¥14.7M は既知差として UI に明示」 — D は期首注入後の世界を前提、B' は期首注入前の世界を前提 — 両者の前後関係が時系列で書いていない
- 設計書 整合性 §2.3 「サーバー側キャッシュなし (ユーザー却下済)」 vs `MfJournalCacheService` 実装 — 整合性レポート自体はキャッシュしないが MF /journals はキャッシュ。ユーザー指摘の意図と乖離してないか確認推奨

### バージョン管理の不整合

- 設計書 D の作成日 2026-04-23、整合性 2026-04-22、B' 2026-04-22、累積残 2026-04-23 — 1 日ずれの依存関係 (D は整合性+B' に依存)
- 設計書 D §1 の関連設計書リストに `design-supplier-partner-ledger-balance.md` (Phase A の母体) が出てくるが本レビュー対象 6 本に含まれない — 暗黙の前提が大きい

---

## 設計書 vs 実装の乖離

| # | 設計書 | 実装 | 乖離内容 |
|---|---|---|---|
| 1 | `design-supplier-balances-health.md:188-192` PayableAnomalyCounter 新設 | `MfHealthCheckService.java:97-106` | 未実装、0 固定 |
| 2 | `design-supplier-balances-health.md:124` MF 期首 2025-05-20 固定 | `MfPaymentAggregator.java:43` MF_FIRST_BUCKET 2025-07-20 別経路 | 三重定義 (C-1) |
| 3 | `design-accounts-payable.md:34` 旧 tasklet そのまま | `AccountsPayableSummaryTasklet.java` 残存、新 tasklet と並行 | 役割整理なし (C-2) |
| 4 | `design-consistency-review.md:75` IGNORE 既存 review が MF_APPLY だった場合は復元 | `ConsistencyReviewService.java:64-68` | actionType でしか判定せず previous でない (C-4) |
| 5 | `design-accounts-payable.md:317-321` SmilePaymentVerifier の手動確定スキップ | `AccountsPayableVerificationReportTasklet.java:115-141` | スキップ漏れ (M-5) |
| 6 | `design-phase-b-prime-payment-settled.md:432-438` UI 3 列 | `AccountsPayableResponse.java` で 6 セル相当 | 列数の乖離 (M-8) |
| 7 | `design-supplier-balances-health.md:115-117` 期首月 opening 自社のみ | `SupplierBalancesService.java:128-129` openingMap 経由 MF も同期 | 設計書が古い (m-5) |
| 8 | `design-integrity-report.md:411` 30 秒以内想定 | `AccountsPayableIntegrityService.java:543` SupplierBalancesService.generate 重重複呼び出し | 性能リスク見積もり不足 (M-7) |
| 9 | `design-accounts-payable.md:312-313` SmilePaymentVerifier 改修「verified_manually 行スキップ」 | `SmilePaymentVerifier.java:155-159, 374-378` で実装済 | これは OK |
| 10 | `design-supplier-partner-ledger-balance.md` (本レビュー外) | `PayableBalanceCalculator` 新設 | 設計書間の依存連鎖が長く、6 本だけでは閉じない |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 |
|---|---|
| Layer 違反 (Controller→Repository 直接) | OK — Controller→Service→Repository の構造維持 |
| @Transactional 配置 | OK 概ね — `ConsistencyReviewService.upsert` が class-level @Transactional + readOnly でない (write を含むため正しい) |
| N+1 | 部分的に未解消 — `SupplierBalancesService` の supplier 逆引きで O(N×M)、`TAccountsPayableSummaryService.summary` で行全件 fetch (M-2/3) |
| DI | OK — constructor injection / `@RequiredArgsConstructor` |
| DTO 変換 | OK — `AccountsPayableResponse.from` factory method パターン |
| バリデーション | 部分的 — `AccountsPayableVerifyRequest` は @NotNull のみ (m-3)、`ConsistencyReviewRequest` には設計書 §3.1 の note @Size(max=500) があるか確認推奨 |
| Migration 安全性 | OK — V025 / V026 / V027 はすべて metadata-only or 安全 |

### Accounts Payable 固有観点

| 項目 | 結果 |
|---|---|
| 累積残計算 (期首残 + 月次入出金 = 当月残) | OK — `PayableBalanceCalculator` で集約、4 箇所で一貫 |
| MfPaymentAggregator の supplier 集計 | 設計書外の挙動あり — 手動確定行も上書き、change=0 fallback (M-1) |
| 期首前 fallback | コードと設計書で日付の定義不一致 (C-1) |
| `verified_manually` 保護 | 集計バッチ・SmilePaymentVerifier では OK、ReportTasklet で漏れ (M-5) |
| 100 円閾値 | 4 箇所で個別定義、共通化されていない (整合性 §) |
| V026/V027 migration 安全性 | OK |
| 設計書 6 本の整合性 | 用語ブレ・期首日定義の三重化・スコープ食い違いあり (上記参照) |
| shop_no=1 固定 vs マルチ | 設計書で「現在 shop=1」明記、実装で `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO=1` 集約。OK だが将来拡張時の TODO コメントなし |
| 整合性 4 カテゴリ網羅 | OK — mfOnly / selfOnly / amountMismatch / unmatchedSuppliers (実装は 4 カテゴリで設計書 §3.2 と一致) |
| キャッシュ invalidate | OK — `MfJournalCacheService.invalidateAll(shopNo)` あり、UI から呼べる |

---

## 推奨アクション (優先度順)

1. **[Critical]** C-1 の期首日定義を `FinanceConstants` に集約し、設計書 6 本で用語統一
2. **[Critical]** C-2 旧 `AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` を `@Deprecated` 化、新ジョブを正規ルートに
3. **[Critical]** C-3 supplier_no=303 除外を全 service で適用、設計書に明記
4. **[Critical]** C-4 `ConsistencyReviewService` のロールバック判定を `previous != null` ベースに修正
5. **[Major]** M-1 `overrideWithMfDebit` の手動確定行スキップ、case 分岐の設計書追記
6. **[Major]** M-2 `summary()` API を JPQL 集計に置換
7. **[Major]** M-5 `AccountsPayableVerificationReportTasklet` の `verified_manually` スキップ追加
8. **[Major]** M-6 `PayableAnomalyCounter` 実装 or 設計書側で v2 に明示繰越
9. **[Major]** M-8 設計書 B' §6.2 の UI 列構成を実装に合わせて修正
10. **[Minor]** 100 円閾値を `FinanceConstants.MATCH_TOLERANCE` で集約、5 箇所参照を一本化
11. **[Minor]** 設計書 D §3.4 / B' §2.4 の期首残扱いを「現在は期首注入済み」記述に更新

---

## 終わりに

買掛金ファミリー (Cluster D) は本プロジェクトで最も複雑かつ業務クリティカルな機能群。設計書は 6 本構成で十分網羅的だが、**Phase A → B → B' → 案 A → 整合性 → consistency-review → 累積残 → ヘルスチェック** と短期間で連続増分されたため、**期首日・閾値・除外 supplier・旧 tasklet 残存** の 4 軸で技術的負債が顕在化している。

機能要件は 80% 以上満たしているが、**運用安定化フェーズ**として上記 Critical 4 件 + Major 9 件を 1〜2 スプリント程度で消化し、設計書を「実装の現在地」に再同期することを推奨する。
