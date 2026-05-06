# コードレビュー: 買掛金ファミリー (Cluster D)

レビュー日: 2026-05-04
ブランチ: refactor/code-review-fixes
レビュアー: Opus サブエージェント
対象設計レビュー: `claudedocs/design-review-accounts-payable-family.md` (Critical 4 / Major 9 / Minor 12)

## 前提

設計レビューと重複する指摘 (期首日の三重定義 / 旧 `AccountsPayableSummaryTasklet` 残存 / supplier_no=303 除外漏れ / `ConsistencyReview` ロールバック判定 等) は再掲しない。コード固有の不具合・実装品質・Spring Boot / Next.js 観点に絞る。

設計レビューは「役割整理 / 設計と実装の乖離」を扱い、本コードレビューは「実装上の Bug・性能・トランザクション・型安全性・UI 品質」を扱う。

---

## サマリー

| Severity | 件数 |
|---|---|
| Blocker | **1** |
| Critical | **5** |
| Major | **10** |
| Minor | **12** |

承認状態: **Needs Revision** (Blocker / Critical 修正後再レビュー必須)

最重要 (Blocker / Critical):
1. **B-1 重複 Bean 定義**: `accountsPayableSummaryInitStep` Bean が `AccountsPayableSummaryConfig` と `AccountsPayableAggregationConfig` の両方で定義されており、Spring Boot 起動時 `BeanDefinitionOverrideException` 必発。`spring.main.allow-bean-definition-overriding=true` で隠れている可能性あり (要 application.yml 確認)。
2. **C-impl-1 旧 ReportTasklet が `verified_manually` 行も `verification_result` / `mfExportEnabled` を踏み潰す** (設計レビュー M-5 とは独立に再発見、3 ループに保護なし)
3. **C-impl-2 `applyMfOverride` 内の `summaryRepository.save` が手動確定 (V026 列) を一切触らない**: 既存 `verifiedAmountTaxExcluded` / `autoAdjustedAmount` / `mfTransferDate` の挙動が未定義 (上書きされない or stale のまま)
4. **C-impl-3 `ConsistencyReviewService` の DB 操作と JPA dirty checking 依存**: `applyMfOverride` 後に `largest` 行の端数吸収を `summaryRepository.save(r)` ループ後に行うが、再 save なしで Hibernate の auto-flush 任せ (`@Transactional` 内なので動作するが意図不明瞭、テスト時の脆弱性源)
5. **C-impl-4 `MfPaymentAggregator.getMfDebitBySupplierForMonth` が `paymentSupplierService.findByShopNo()` を毎月 fetch**: backfill で 12 ヶ月分処理時に最大 12 回 N+1 (cache miss 時 36 ms × 12)
6. **C-impl-5 `SupplierBalancesService.accumulateMfJournals` の Map 二重 lookup バグ**: `map.computeIfAbsent(...).credit = map.get(...).credit.add(...)` パターンで dead-store (`computeIfAbsent` が返した Map value への参照を捨てて再 lookup)。動作はするがロジック明確性を著しく損なう

---

## Blocker

### B-1: `accountsPayableSummaryInitStep` Bean の重複定義
- **場所**:
  - `backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableSummaryConfig.java:83-87`
  - `backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableAggregationConfig.java:66-70`
- **現状**:
  ```java
  // AccountsPayableSummaryConfig.java:83
  @Bean
  public Step accountsPayableSummaryInitStep() {
      return new StepBuilder("accountsPayableSummaryInitStep", jobRepository)
              .tasklet(accountsPayableSummaryInitTasklet, transactionManager)
              .build();
  }

  // AccountsPayableAggregationConfig.java:66
  @Bean
  public Step accountsPayableSummaryInitStep() {     // ← 同名 Bean
      return new StepBuilder("accountsPayableSummaryInitStep", jobRepository)
              .tasklet(accountsPayableSummaryInitTasklet, transactionManager)
              .build();
  }
  ```
- **問題**: 同一 Bean 名 (メソッド名) `accountsPayableSummaryInitStep` を 2 つの `@Configuration` で公開。Spring Boot 2.1+ デフォルトでは `BeanDefinitionOverrideException` で起動失敗。設計レビュー C-2 で「旧 tasklet を deprecation」とされているが、旧 Config を残したままで Bean 名衝突が放置されている。
- **影響**: 起動失敗 or `allow-bean-definition-overriding=true` 設定で片方が静かに上書きされ、誤った Step (旧 tasklet 系) が `accountsPayableAggregationJob` に組み込まれるリスク。後者の場合 Phase B' 経路が壊れていることに気付かない。
- **修正案**:
  1. `AccountsPayableAggregationConfig` で Bean 名を `accountsPayableAggregationInitStep` に rename し、Step 名 (1st arg of `StepBuilder`) も別名にする
  2. もしくは `AccountsPayableSummaryConfig` を `@Deprecated` + `@ConditionalOnProperty` で無効化
  3. `application.yml` に `spring.main.allow-bean-definition-overriding` の指定があれば即座に削除し、起動時にエラーが出る状態に戻す

---

## Critical

### C-impl-1: `AccountsPayableVerificationReportTasklet` が `verified_manually` 行を保護していない (再発見)
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationReportTasklet.java:115-122, 130-141, 156-166`
- **現状** (3 箇所すべて):
  ```java
  // L115-122: 差額 null 行
  for (TAccountsPayableSummary summary : supplierSummaries) {
      summary.setVerificationResult(0);
      summary.setMfExportEnabled(false);
      tAccountsPayableSummaryService.save(summary);
      fixedCount++;
  }

  // L131-141: 差額 5 円未満
  if (... < FinanceConstants.PAYMENT_VERIFICATION_TOLERANCE) {
      summary.setVerificationResult(1);
      summary.setMfExportEnabled(true);
      tAccountsPayableSummaryService.save(summary);
      ...
  }

  // L157-166: 検証一致なら mfExportEnabled=true
  if (summary.getVerificationResult() == 1) {
      if (summary.getMfExportEnabled() == null || !summary.getMfExportEnabled()) {
          summary.setMfExportEnabled(true);
          tAccountsPayableSummaryService.save(summary);
          ...
      }
  }
  ```
- **問題**: `SmilePaymentVerifier` (line 155-159, 374-378) と `AccountsPayableSummaryInitTasklet` (line 53-57) は `verified_manually` 行を明示的にスキップしているが、本 ReportTasklet 3 箇所には同等の保護がない。`accountsPayableVerification` ジョブが走った後に `accountsPayableVerificationReport` も走る運用 (`AccountsPayableVerificationConfig.java:62-71` の job 定義) で、手動確定行が `verificationResult` / `mfExportEnabled` を上書きされる。
- **影響**: 経理 (k_oda) が UI から `verify` API 経由で MF 出力を `false` に手動セットしても、次回バッチで `true` に戻され MF へ二重出力される。設計レビュー M-5 と同じ問題だがコード上は独立に再発見しているため Critical で扱う (Major 越え)。
- **修正案**: 3 箇所の `for` ループ先頭に `if (Boolean.TRUE.equals(summary.getVerifiedManually())) continue;` を追加。

### C-impl-2: `ConsistencyReviewService.applyMfOverride` が V026 列 (verifiedAmountTaxExcluded / autoAdjustedAmount / mfTransferDate) の整合性を取らない
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:173-181, 200-217`
- **現状**:
  ```java
  // selfOnly: 自社取消
  for (TAccountsPayableSummary r : rows) {
      r.setVerifiedAmount(BigDecimal.ZERO);
      r.setVerifiedAmountTaxExcluded(BigDecimal.ZERO);
      r.setVerifiedManually(true);
      r.setVerificationResult(1);
      r.setMfExportEnabled(false);
      summaryRepository.save(r);
  }

  // amountMismatch
  for (TAccountsPayableSummary r : rows) {
      ...
      r.setVerifiedAmount(allocated);
      BigDecimal divisor = BigDecimal.valueOf(100).add(nz(r.getTaxRate()));
      r.setVerifiedAmountTaxExcluded(...);
      r.setVerifiedManually(true);
      r.setVerificationResult(1);
      r.setMfExportEnabled(true);
  }
  ```
- **問題**:
  1. `autoAdjustedAmount` (V026) が更新されない: 振込明細 Excel 取込で記録された自動調整額が古い値のまま残り、UI バッジ (`AccountsPayablePage:140-143` `showAdjBadge`) が誤った金額を表示し続ける。`MF_APPLY` で MF 金額に上書きする以上、自動調整額は意味を失うため `null` or `0` に戻すべき。
  2. `mfTransferDate` (V026) が更新されない: MF_APPLY 後に MF CSV 出力すると古い送金日 (前回 Excel 取込時の値) が使われる。`MF_APPLY` 後は `null` (= transactionMonth fallback) に戻すのが安全。
  3. `paymentDifference` が更新されない: `verifiedAmount` を変えたのに差額が古い値のまま (UI 列で混乱)。
- **影響**: 「整合性レポート画面で MF_APPLY」→「買掛金一覧で当月 detail を見ると、調整バッジ (auto_adjusted_amount) が古い金額表示」「mf_transfer_date が前回 Excel 由来のまま」という UX 不整合。会計監査時に説明不能な差異の元。
- **修正案**: amountMismatch ブロック (line 200-227) と selfOnly ブロック (line 174-182) で以下を追加:
  ```java
  r.setAutoAdjustedAmount(BigDecimal.ZERO);
  r.setMfTransferDate(null);
  r.setPaymentDifference(allocated.subtract(nz(r.getTaxIncludedAmountChange())));
  ```

### C-impl-3: `ConsistencyReviewService.applyMfOverride` の端数吸収後 save 漏れ (設計レビュー m-9 の発展)
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:200-227`
- **現状**:
  ```java
  for (TAccountsPayableSummary r : rows) { ... }      // line 200: 各行を allocate して save 済
  // 端数誤差は最大行で吸収
  BigDecimal diff = target.subtract(assigned);
  if (diff.signum() != 0 && largest != null) {
      largest.setVerifiedAmount(nz(largest.getVerifiedAmount()).add(diff));
      ...
      largest.setVerifiedAmountTaxExcluded(...);
  }
  for (TAccountsPayableSummary r : rows) summaryRepository.save(r);   // line 227: 全件再 save
  ```
- **問題**:
  - 設計レビュー m-9 は「端数吸収後の `largest` を再 save なしで Hibernate dirty check 任せ」と指摘したが、コード上は `for (... rows) summaryRepository.save(r)` を最後に再ループしているため一見問題なし。
  - **本当の問題**: 同 method 内で `summaryRepository.save(r)` を 2 回呼ぶ (allocation 時 + 最後に再 save)。これは Hibernate の `merge` を 2 回起動し、selectAfterUpdate / autoflush の挙動次第で UPDATE が 2 回発行される。性能ロス + ログ汚染 + (まれに) Optimistic locking 例外の温床。
  - 加えて `for (...rows) {...}` 内の各 `summaryRepository.save(r)` 呼び出しは `r` 自身が既に session 内 managed entity で merge 不要 (Spring Data JPA は managed の場合 `EntityManager.merge` ではなく直接 dirty check)。冗長。
- **影響**: 機能は動くが、再 save が 2 重で UPDATE が 2 回発行される。`@Transactional` 内なので結果は正しいが、log4j で audit trail を取ると 2 回 INSERT/UPDATE のように見える。
- **修正案**:
  1. allocation ループ内の `summaryRepository.save(r)` を削除 (entity は既に managed なので不要)
  2. 端数吸収後に `summaryRepository.saveAll(rows)` で 1 回だけ flush
  3. もしくは `repository.save` 呼び出しを完全削除し、tx commit 時の auto-flush に任せる (JpaRepository の挙動は merge/persist trigger 用なので明示 save が「正解」だが冗長度を下げる場合)

### C-impl-4: `MfPaymentAggregator.getMfDebitBySupplierForMonth` が毎呼び出しで master full-load
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:55-128`
- **現状**:
  ```java
  public Map<Integer, BigDecimal> getMfDebitBySupplierForMonth(
          Integer shopNo, LocalDate transactionMonth) {
      ...
      Map<String, Set<String>> subToCodes = buildMfSubToCodes();         // L85: master 全 fetch
      Collection<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(shopNo);  // L88: supplier 全 fetch
      ...
  }
  ```
- **問題**:
  - `AccountsPayableBackfillTasklet.processOneMonth` (line 128-130) で 12 ヶ月分回す場合、毎月 `mfAccountMasterRepository.findAll()` (~1000 行) と `paymentSupplierService.findByShopNo(1)` (~100 行) を再 fetch
  - 同様に `AccountsPayableAggregationTasklet.execute` (line 108-111) でも毎回 fetch
  - `MfHealthCheckService` `SupplierBalancesService` `AccountsPayableIntegrityService` で同等の `buildMfSubToCodes()` 重複実装あり (4 箇所)
- **影響**:
  - backfill 12 ヶ月で master 12 回 + supplier 12 回 fetch = 24 回の DB クエリ。各 50ms とすると 1.2s 超
  - 統合テストで N+1 検出器に引っかかる
  - 設計レビュー M-3 と同じ「逆引き map の重複構築」問題
- **修正案**:
  1. `MfAccountMasterReverseIndexService` を新設し `buildMfSubToCodes()` 結果を `@Cacheable("mfSubToCodes")` で共有
  2. もしくは `getMfDebitBySupplierForRange(shopNo, fromMonth, toMonth)` API に切替え、内部で全期間 1 回 fetch + 月単位 group by に変更
  3. `paymentSupplierService.findByShopNo` も同様に呼び出し側で 1 回取得して引数で渡す

### C-impl-5: `SupplierBalancesService.accumulateMfJournals` の `computeIfAbsent` パターン誤用
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:247-253`
- **現状**:
  ```java
  if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
          && cr.subAccountName() != null) {
      map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum()).credit =
              map.get(cr.subAccountName()).credit.add(nz(cr.value()));
  }
  ```
- **問題**:
  - `computeIfAbsent(...)` の戻り値 `MfAccum` を捨てて、すぐに `map.get(...)` で再 lookup。`computeIfAbsent` が無意味化
  - `map.get(...).credit.add(...)` の結果を `map.get(...).credit` ではなく `map.computeIfAbsent(...).credit` に代入しており、書き込み先と読み取り先が**同じインスタンスを指している**ため動作するが、コード可読性 0
  - 誤読 (i.e., 別 instance に書き込んでいる) で「集計が 0 になる」というバグレポートが将来発生する可能性大
- **影響**: パフォーマンス的には Hash lookup を 2 回するため微妙に遅い (~10%)。可読性は破滅的。
- **修正案**:
  ```java
  MfAccum accum = map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum());
  accum.credit = accum.credit.add(nz(cr.value()));
  ```

---

## Major

### M-impl-1: `TAccountsPayableSummaryService.findAll()` が dead code
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:39-41`
- **現状**:
  ```java
  public List<TAccountsPayableSummary> findAll() {
      return repository.findAll();
  }
  ```
- **問題**: 全 codebase で呼び出しなし (Grep 確認)。残しておくと外部から呼ばれて全件 fetch する温床。
- **修正案**: 削除。

### M-impl-2: `verify` API の `applyVerification` が opening_balance / payment_settled を再計算しない
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:160-187`
- **現状**: `verify` で `taxIncludedAmount` / `taxExcludedAmount` / `paymentDifference` / `verificationResult` を更新するが、`closing = opening + effectiveChange - payment_settled` の再計算を通らない。
- **問題**: `effectiveChange` 算出式 (`PayableBalanceCalculator.effectiveChangeTaxIncluded`) は手動確定時 `verifiedAmount` 優先のため、verify でセットした金額が次回 closing 計算時に使われる。**ただし current month の `closing_balance_tax_included` カラム自体は更新されない** (`closing` は DTO 層で都度算出のため OK)。
  - **真の問題**: 翌月 `AccountsPayableAggregationTasklet` 実行時の `applyOpenings` (line 137-153) で前月 closing を取得する際、`PayableBalanceCalculator.closingTaxIncluded(p)` を呼ぶ。手動 verify 後、`opening_balance_tax_included` カラム (DB) が古い値のままだが、計算は in-memory で都度行うため**1 回でも翌月集計を回せば伝搬する**。逆に翌月集計を回さないと次月以降の opening が古い。
  - 設計書 `design-phase-b-prime-payment-settled.md` には「verify 後に翌月の `accountsPayableAggregation` を再実行する運用」が前提のはずだが、UI 上の動線がない。
- **影響**: 経理が手動 verify を繰り返した後、翌月集計を実行し忘れると累積残が ±¥N ズレる。設計書 §2.2 の「常に上書き」原則と運用手順の乖離。
- **修正案**:
  1. `verify` API 内で「翌月以降の opening を再計算する必要がある」warning toast を返す (Response に `requiresRebackfill: true` 含める)
  2. もしくは `verify` 内で内部的に `AccountsPayableBackfillTasklet.processOneMonth` を呼んで翌月以降を即更新 (REQUIRES_NEW tx)

### M-impl-3: 整合性レポート Controller の例外ハンドリング型がレスポンス DTO と不整合
- **場所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:215-234`
- **現状**:
  ```java
  @GetMapping("/accounts-payable/integrity-report")
  public ResponseEntity<?> getIntegrityReport(...) {
      ...
      try {
          IntegrityReportResponse res = accountsPayableIntegrityService.generate(...);
          return ResponseEntity.ok(res);
      } catch (MfReAuthRequiredException e) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
      } catch (...) { ... }
  }
  ```
- **問題**:
  - 戻り型が `ResponseEntity<?>` (raw wildcard)。OpenAPI 自動生成 / TypeScript 型生成が不可能
  - エラー時 `Map.of("message", ...)` を返すため、フロント側 `IntegrityReportResponse` への暗黙キャストでクラッシュリスク
  - 同パターンが `getMfSupplierLedger` (L312)、`getSupplierBalances` (L246) でも 3 箇所重複
- **影響**: 型安全性低下。Controller Advice で統一すべき。
- **修正案**:
  1. `@RestControllerAdvice` で `MfReAuthRequiredException` / `MfScopeInsufficientException` / `IllegalStateException` をハンドル
  2. Controller の戻り型を `ResponseEntity<IntegrityReportResponse>` に絞る
  3. エラー response body 用 `ErrorResponse` DTO を作成

### M-impl-4: `AccountsPayableLedgerService.aggregateMonth` の `autoAdjustedAmount` 平均化ロジックが不正確
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:181-184`
- **現状**:
  ```java
  // 税率別複数行で autoAdjusted が重複計上される (applyVerification は全税率行に同額書き込み)。
  // 代表値として 1 行分に戻す: 行数で割る。単一税率なら変わらない。
  BigDecimal autoAdjustedAvg = breakdown.isEmpty()
          ? BigDecimal.ZERO
          : autoAdjusted.divide(BigDecimal.valueOf(breakdown.size()), 0, java.math.RoundingMode.DOWN);
  ```
- **問題**:
  - 設計レビュー m-10 で指摘済の通り、**振込明細経由で全税率行に同額書き込みされる前提**に依存
  - V026 + Phase B' 後の経路 (PaymentMfImportService の applyVerification) で「税率別に異なる auto_adjusted を入れる」変更が将来入ると、この平均化ロジックが破綻 (e.g., 8% 行 +¥10、10% 行 -¥5 → 平均 ¥2 と表示)
  - 設計書 `design-accounts-payable-ledger.md` には平均化に関する記述がなく、なぜ平均化するかの根拠が code comment にしかない
- **影響**: 将来 `applyVerification` が税率別に違う調整を行うように変更された場合、買掛帳画面の表示が誤る (即座に bug にはならないが、数値の意味が変わる)
- **修正案**:
  1. `PaymentMfImportService.applyVerification` の挙動を「全税率行に同額」固定とコメントで宣言する
  2. もしくは `LedgerRow.autoAdjustedAmount` を `Map<BigDecimal, BigDecimal>` (税率→額) に変更し UI で税率別表示
  3. 当面は `autoAdjusted.divide(...)` ではなく **first row 採用** (`group.get(0).getAutoAdjustedAmount()`) で「全行同値」前提を明示する

### M-impl-5: `AccountsPayableIntegrityService.processedSubNames` の key 衝突リスク
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:203, 266, 353`
- **現状**:
  ```java
  Set<String> processedSubNames = new HashSet<>();
  ...
  processedSubNames.add(sn + "|" + month);   // L266
  ...
  if (processedSubNames.contains(subName + "|" + month)) continue;  // L353
  ```
- **問題**:
  - `subName` (sub_account_name) が `|` を含む場合 (例: 「東京|株式会社」) key が衝突
  - MF master の sub_account_name は事実上「企業名」だが、theoretical には任意文字列許容
  - 設計レビュー M-4 で同等指摘済 (但し実装の record 化案は本コードレビューの修正案として独立)
- **影響**: low (現実的な supplier 名で `|` を含むケースは稀) だが、将来 master 取込時に exception で fail-fast すべき
- **修正案**:
  ```java
  record SubNameMonthKey(String subName, LocalDate month) {}
  Set<SubNameMonthKey> processed = new HashSet<>();
  ```

### M-impl-6: `AccountsPayableBackfillTasklet.toSave.contains(po)` が O(N²)
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:136-139`
- **現状**:
  ```java
  List<TAccountsPayableSummary> toSave = new ArrayList<>(current);
  for (TAccountsPayableSummary po : generated) {
      if (!toSave.contains(po)) toSave.add(po);
  }
  ```
- **問題**:
  - `List.contains(Object)` は O(N) で `equals` 比較。`@Data` の Lombok 生成 `equals` は全フィールド比較なので 1 比較自体も重い
  - 当月行が 100 件 / generated が 50 件で 100*50 = 5000 回比較
  - 加えて Entity の `equals` が IdClass フィールド (shopNo / supplierNo / transactionMonth / taxRate) だけでなく全フィールド (verifiedAmount 等) を含むため、in-memory 編集後に「同じ PK だが equals false」で重複追加される可能性大
- **影響**: backfill 12 ヶ月で 100ms オーダーの遅延。さらに「同 PK が 2 件 saveAll」→ Hibernate `EntityExistsException` のリスク
- **修正案**:
  ```java
  Set<String> existingKeys = current.stream()
      .map(PayableMonthlyAggregator::rowKey).collect(toSet());
  for (TAccountsPayableSummary po : generated) {
      if (existingKeys.add(PayableMonthlyAggregator.rowKey(po))) toSave.add(po);
  }
  ```

### M-impl-7: `AccountsPayableAggregationTasklet` の payment-only 上書き判定が説明と挙動で齟齬
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableAggregationTasklet.java:115-125`
- **現状**:
  ```java
  for (TAccountsPayableSummary po : paymentOnlyRows) {
      String k = PayableMonthlyAggregator.rowKey(po);
      if (!savedRowKeys.contains(k)) {
          allCurrRows.add(po);
          savedRowKeys.add(k);
      }
      // currMap の既存 row を payment-only 上書きした場合は既に allCurrRows に含まれる
  }
  ```
- **問題**:
  - コメントは「currMap の既存 row を payment-only 上書きした場合は既に allCurrRows に含まれる」だが、実際には `generatePaymentOnlyRows` (line 332-345) で `currMap.get(k)` から既存 row を取り出して in-place 編集している。同 row が `allCurrRows` 内にも存在 → `allCurrRows` 経由で `saveAll` される
  - **しかし**: payment-only 行の `verifiedAmount=null` / `verifiedManually=false` セット (line 352-353) が、`allCurrRows` 内の preservedManual (verified_manually=true) 行に当たった場合、**手動確定が剥がされる**
  - 具体的シナリオ: 経理が当月手動 verify 済 supplier (manual=true, change=0) があると、generatePaymentOnlyRows で「当月 change 合計 = 0」と判定されて payment-only として上書きされ、verified_manually=false / verifiedAmount=null になる
- **影響**: 「change=0 かつ 手動確定済 かつ 前月 paid>0」の supplier (= 経理が当月分を 0 円と確定) で手動確定が消える。低頻度シナリオだが発生したら検出困難
- **修正案**:
  - `generatePaymentOnlyRows` 内 (line 332) で `currMap.get(k)` した row が `verifiedManually=true` ならスキップ:
  ```java
  TAccountsPayableSummary row = currMap.get(k);
  if (row != null && Boolean.TRUE.equals(row.getVerifiedManually())) continue;
  ```

### M-impl-8: `accumulateMfJournals` (SupplierBalancesService) と `accumulateMfJournals` (AccountsPayableIntegrityService) のロジック重複
- **場所**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:233-258`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:142-176`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:97-110`
- **問題**: MF journal の `branches[].creditor/debitor` の「買掛金 sub_account 抽出 + sub_account_name 単位集計」ロジックが 3 service に重複。それぞれ集計粒度 (期間累計 / 月単位 / 当月のみ) と filter (期首仕訳除外有無) が微妙に違うため、リファクタリング時の乖離源。
- **影響**: 「期首仕訳の扱い」を変えたい時、3 箇所修正が必要。設計レビュー M-1 と関連。
- **修正案**: `MfPayableJournalAggregator` (新設) に `aggregate(journals, fromMonth, toMonth, includeOpening)` を集約し、3 service から呼ぶ。

### M-impl-9: `Frontend integrity-report.tsx` の `window.prompt` 使用
- **場所**: `frontend/components/pages/finance/integrity-report.tsx:111`
- **現状**:
  ```tsx
  const note = window.prompt(`${actionLabel}\n備考 (任意):`, '')
  if (note === null) return
  reviewMutation.mutate({ ...args, note })
  ```
- **問題**:
  - `window.prompt` は HTML5 標準だが UX 最悪 (モバイル対応不可、スタイリング不可、validation 不可)
  - 設計書 `design-consistency-review.md` では「note (max 500)」と記述されているが prompt は length 制限不可
  - shadcn/ui の Dialog + Form パターンが既存 (BulkVerifyDialog 等) なのに統一されていない
- **影響**: UX 一貫性の欠如、モバイル UI 破綻
- **修正案**: `ConsistencyReviewDialog.tsx` を新設し、shadcn/ui Dialog + Textarea + 文字数 counter で統一。

### M-impl-10: `Frontend supplier-balances.tsx` の MF 期首日が hardcode
- **場所**: `frontend/components/pages/finance/supplier-balances.tsx:151`
- **現状**:
  ```tsx
  期首 (2025-05-20) 〜 基準月 の全 supplier 累積残を自社 / MF で突合。
  ```
- **問題**:
  - 設計書 / バックエンド (SupplierBalancesService.MF_PERIOD_START) と独立に hardcode
  - レスポンス内に `mfStartDate` フィールドがあるのに使われていない (L101 の `gotoLedger` 内では `report.mfStartDate` を使っているが、ヘッダ表示文では hardcode)
  - 設計レビュー C-1 で期首日定義の不整合を指摘済だが、**フロントにも 4 つ目の hardcode** が存在する (B-1 の三重定義に加えて 4 重化)
- **影響**: バックエンド側で期首日を変更しても UI 表示が古い日付のまま。
- **修正案**: ヘッダ表示文を `期首 ({report?.mfStartDate ?? '...'}) 〜 基準月 ...` に変更。

---

## Minor

### m-impl-1: `AccountsPayableSummaryInitTasklet` の SQL リテラル中の shop_no=1 が hardcode
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryInitTasklet.java:53-57`
- **現状**:
  ```java
  String sql = "UPDATE t_accounts_payable_summary " +
          "SET payment_difference = NULL, verification_result = NULL, mf_export_enabled = FALSE " +
          "WHERE transaction_month = ? " +
          "  AND shop_no = 1 " +     // ← hardcode
          "  AND (verified_manually IS NULL OR verified_manually = FALSE)";
  ```
- **問題**: `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` を使うべき。設計レビュー C-3 と関連。
- **修正案**:
  ```java
  String sql = "... AND shop_no = ? AND (verified_manually IS NULL OR verified_manually = FALSE)";
  jdbcTemplate.update(sql, java.sql.Date.valueOf(periodEndDate), FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO);
  ```

### m-impl-2: `AccountsPayableLedgerResponse.LedgerRow` の `Closing < 0` 判定で `BigDecimal.signum()` を使うべき
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:246`
- **現状**:
  ```java
  if (closing.signum() < 0) { ... }
  ```
- 実は OK。`compareTo` ではなく `signum` を使っている良い例。レビュー観点としては code-base 全体で `compareTo(BigDecimal.ZERO) < 0` が散在している (例: SmilePaymentVerifier line 478) ので、`signum()` 統一を全体ガイドラインに追加推奨。

### m-impl-3: `AccountsPayableLedgerService.detectAnomalies` の `verified.signum() != 0` 判定で 0 円 verify が無視される
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:234`
- **現状**:
  ```java
  if (verified.signum() != 0) {
      BigDecimal diff = change.subtract(verified).abs();
      if (diff.compareTo(VERIFY_DIFF_THRESHOLD) > 0) { ... VERIFY_DIFF }
  }
  ```
- **問題**: 経理が「当月分は 0 円で確定 (例: 全部相殺)」と verifiedAmount=0 を入力した場合、anomaly 検出が走らない。change=10000 / verified=0 のケースで `change != verified` だが anomaly 出ない。
- **影響**: low (実運用ではほぼ発生しないが、エッジケース)
- **修正案**: `if (verified != null)` (Optional 化) や `verifiedManually` フラグでガード:
  ```java
  if (Boolean.TRUE.equals(hasVerifiedManually) || verified.signum() != 0) { ... }
  ```

### m-impl-4: `MfPaymentAggregator.getMfDebitBySupplierForMonth` で `break` 後の重複検出ログなし
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:117-123`
- **現状**:
  ```java
  for (String code : codes) {
      Integer no = codeToSupplierNo.get(code);
      if (no != null) {
          result.merge(no, e.getValue(), BigDecimal::add);
          break; // 複数 supplier_code が同一 sub_account にマッピングされる稀ケースは先頭採用
      }
  }
  ```
- **問題**: `codes.size() > 1` で複数 supplier_no にヒットする場合、先頭採用で残り無視。warn ログがないため検出困難。
- **影響**: 設計書 §3 で「mf_account_master に複数 supplier_code 登録ケースは想定外」と書いてあれば OK だが、検出ログだけでも残すべき
- **修正案**:
  ```java
  if (codes.size() > 1) {
      log.warn("[mf-payment] sub_account {} に複数 supplier_code: {} (先頭採用)", e.getKey(), codes);
  }
  ```

### m-impl-5: `AccountsPayableResponse.from(...)` の overload 3 つに対し `@Deprecated` 整理がない
- **場所**: `backend/src/main/java/jp/co/oda32/dto/finance/AccountsPayableResponse.java:78-91`
- **現状**: `from(ap, ps)`, `from(ap)`, `from(ap, ps, includeBalance)` の 3 つ。最初 2 つは 3rd version への delegate。
- **問題**: 整合性は取れているが、呼び出し側 (FinanceController L122, 145, 158, 172) で 2 つ目を使っており、長期的には `from(ap, ps, false)` に統一して overload を削減推奨。
- **修正案**: `@Deprecated` を 2-arg / 1-arg overload に付与し、新規呼び出し禁止のシグナル化。

### m-impl-6: `Frontend accounts-payable.tsx` の `purchaseDateRange` ロジックが Date オブジェクト経由で TZ 依存リスク
- **場所**: `frontend/components/pages/finance/accounts-payable.tsx:74-81`
- **現状**:
  ```tsx
  const to = new Date(y, m - 1, d)       // 当月20日
  const from = new Date(y, m - 2, d + 1) // 前月21日
  const fmt = (x: Date) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  ```
- **問題**: コメントに「local TZ で生成し UTC 変換は挟まないので TZ 非依存」とあるが、`new Date(y, m-1, d)` 自体は local TZ で生成されるため、サーバーが UTC で動く環境 (Vercel) で `getMonth()` / `getDate()` が UTC ではなく local 解釈で返るのが期待動作。実際は問題ないが、`m-2` の月跨ぎ (例: m=1 → m-2 = -1) で前年 12 月にロールオーバーするのは Date コンストラクタの仕様で OK。
- **影響**: low (現状は正しく動作するが、`m=1, d=20` 起算で `from = new Date(y, -1, 21)` の挙動を頼っており、未来の Date 仕様変更で破綻リスク)
- **修正案**: `date-fns` の `subMonths` + `startOfDay` を使う:
  ```tsx
  import { subMonths, format } from 'date-fns'
  const to = new Date(`${transactionMonth}T00:00:00`)
  const from = subMonths(to, 1)
  // から from を 21 日に...
  ```

### m-impl-7: `Frontend integrity-report.tsx` の `runMutation.mutate(false)` を `onSuccess` で 2 度呼ぶ
- **場所**: `frontend/components/pages/finance/integrity-report.tsx:88, 105`
- **現状**: review 保存 / delete の `onSuccess` 内で `runMutation.mutate(false)` を呼んでいる。これは「review 操作後に整合性レポートを再 fetch」用だが、TanStack Query の `invalidateQueries` で済む。
- **問題**:
  - mutation chain は anti-pattern
  - `runMutation.isPending` が一時的に `false` (chain 中の race condition)、UI で「整合性チェック中」のスピナーが消える瞬間がある
- **修正案**: `useMutation` の `onSuccess` で `queryClient.invalidateQueries({ queryKey: ['integrity-report'] })` し、整合性レポート取得を `useQuery` に変える。

### m-impl-8: `Frontend supplier-balances.tsx` の row key が脆い
- **場所**: `frontend/components/pages/finance/supplier-balances.tsx:208`
- **現状**:
  ```tsx
  <tr key={`${r.supplierNo ?? 'null'}-${r.mfSubAccountNames[0] ?? ''}`}
  ```
- **問題**:
  - `mfSubAccountNames[0]` 依存。同じ supplier_no で 2 行返る (例: SELF_MISSING + 通常) ケースで衝突
  - `mfSubAccountNames` が空配列の場合 `[0]` は `undefined`、key 重複で React warning
- **修正案**:
  ```tsx
  <tr key={`${r.supplierNo ?? 'null'}-${r.status}-${r.mfSubAccountNames.join('|')}`}>
  ```

### m-impl-9: `Frontend accounts-payable.tsx` の BatchButton で `data-testid` がない
- **場所**: `frontend/components/pages/finance/accounts-payable.tsx:96-124`
- **問題**: E2E テストで再集計ボタンを特定する際、`getByText('再集計')` で当てる必要があり、文言変更で fragility 高い。
- **修正案**: `data-testid={\`batch-button-${job}\`}` を追加。

### m-impl-10: `frontend types/accounts-payable.ts` の `BalanceFilter` enum と `BALANCE_FILTER_LABELS` が同一ファイルでなく分散
- **場所**: import statement L37-43
- **問題**: `BalanceFilter` `VerificationFilter` が type alias の場合、union 型を切り取って label map と分離している。命名上は合うが、追加時にどちらかを忘れるリスク。
- **修正案**: `as const` const assertion + `keyof typeof` で型導出に統一:
  ```ts
  export const BALANCE_FILTER_LABELS = { all: '全て', positive: '残あり', negative: '値引繰越' } as const
  export type BalanceFilter = keyof typeof BALANCE_FILTER_LABELS
  ```

### m-impl-11: `ConsistencyReviewService.upsert` の `existingOpt.ifPresent(...)` 内で `req.shopNo` を closure キャプチャ
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:62-68`
- **問題**: lambda 内で `req` を closure キャプチャ。読みやすさ低下。
- **修正案**: 通常の `if (existingOpt.isPresent())` ブロックに変更すれば `req` を直接参照しているのが明確。

### m-impl-12: V025-V029 migration ファイル名規約の連番が機能名で分離されていない
- **場所**: `backend/src/main/resources/db/migration/`
- **設計レビュー m-12 と同等**だが、コード視点で追記:
  - `V025__opening_balance_columns.sql` `V026__verified_amount_tax_excluded.sql` `V027__t_consistency_review.sql` `V028__t_payment_mf_aux_row.sql` `V029__delivery_code_mapping_fix.sql` (推測)
  - 並び順 OK だが、別ブランチ (B-CART pending changes 等) で V030+ が衝突する温床
- **修正案**: Flyway 5+ の Repeatable migration (`R__`) を活用 or branch ごとに `V025_01_` `V025_02_` といったサブ番号運用へ移行 (チーム規約として `claudedocs/migration-naming.md` に明文化)

---

## Spring Boot 観点チェックリスト

| 項目 | 結果 | 備考 |
|---|---|---|
| Layer 違反 (Controller→Repository 直接) | OK | Controller→Service→Repository |
| @Transactional 配置 | 概ね OK | ConsistencyReviewService class-level write (line 42) + readOnly (L104 で findForPeriod) は適切 |
| AccountsPayableBackfillTasklet の REQUIRES_NEW tx | OK | `TransactionTemplate` で self-invocation 制約回避 (line 73-75) |
| N+1 | 部分的 NG | C-impl-4 master/supplier 毎月 fetch、設計レビュー M-2 の `summary()` 全件 fetch |
| バリデーション | 部分的 NG | `AccountsPayableVerifyRequest` `@PositiveOrZero` 不足 (設計レビュー m-3)、`ConsistencyReviewRequest` は `@NotBlank` + `@Size(max=500)` で OK |
| Migration 安全性 | OK | V025-V027 metadata-only 中心 |
| DTO Entity 漏洩 | OK | 全 endpoint で `Response.from()` 経由 |
| @PreAuthorize | 部分的 OK | `hasRole('ADMIN')` の使い分け (mf-export 系は逆に admin 限定外、要設計確認) |
| BigDecimal compareTo vs equals | OK | `compareTo(BigDecimal.ZERO) == 0` / `signum()` 適切に使い分け、`equals` 誤用なし |
| Bean 重複 | NG | B-1 |
| TX境界の bulk save | 部分的 NG | C-impl-3 二重 save |

## Next.js / React 観点チェックリスト

| 項目 | 結果 | 備考 |
|---|---|---|
| use(params) (Next.js 16 async params) | N/A | dynamic route なし |
| URL 永続化 | OK | accounts-payable.tsx の `?tab=` (L188-200)、ledger の committed pattern (L64-71) |
| TanStack Query invalidate | 部分的 NG | m-impl-7 mutation chain anti-pattern |
| key の安定性 | NG | m-impl-8 supplier-balances row key |
| useMemo 依存配列 | OK | accounts-payable.tsx L216 の queryString は適切 |
| useEffect cleanup | OK | polling cleanup (L319-323) 適切 |
| sonner toast | OK | 統一されている |
| shadcn/ui コンポーネント | OK | 統一 |
| window.prompt 使用 | NG | M-impl-9 |
| client component "use client" | OK | 全 page で適切 |

## Accounts Payable 固有観点

| 項目 | 結果 | 備考 |
|---|---|---|
| BigDecimal scale | OK | `setScale(0, RoundingMode.DOWN)` 一貫適用 |
| supplier_no=303 除外の一貫性 | NG | 設計レビュー C-3 (集計のみ適用) |
| バッチ再実行時の冪等性 | 概ね OK | InitTasklet で reset → AggregationTasklet で stale-delete 流れ。但し M-impl-7 の手動確定保護に穴 |
| 旧 SummaryTasklet 残存 | NG | 設計レビュー C-2 + 本レビュー B-1 |
| ConsistencyReview ロールバック | NG | 設計レビュー C-4、本レビュー C-impl-2 (V026 列の整合性) |
| Cache invalidate タイミング | OK | `MfJournalCacheService.invalidateAll(shopNo)` 提供、UI から呼べる |
| bulkVerify トランザクション境界 | OK | `PaymentMfImportService.applyVerification` で `@Transactional` 確認済 (本レビュー対象外) |
| Tasklet 内 State 持ち回し | OK | chunk 不使用 (Tasklet) なので部分失敗時のロールバック単位が tasklet 全体で明確 |

---

## 推奨対応優先度

| Priority | ID | 内容 | 工数目安 |
|---|---|---|---|
| 1 | **B-1** | accountsPayableSummaryInitStep Bean 衝突解消 | 30 分 |
| 2 | **C-impl-1** | ReportTasklet の verified_manually スキップ追加 | 1 時間 |
| 3 | **C-impl-2** | applyMfOverride で V026 列リセット | 2 時間 (テスト含) |
| 4 | C-impl-3 | save 二重呼び出し整理 | 1 時間 |
| 5 | C-impl-5 | accumulateMfJournals computeIfAbsent 修正 | 30 分 |
| 6 | M-impl-7 | payment-only 上書きで手動確定保護 | 1 時間 |
| 7 | M-impl-9 | window.prompt → Dialog 化 | 3 時間 |
| 8 | C-impl-4 | master/supplier 共通キャッシュ化 | 4 時間 |
| 9 | M-impl-3 | Controller exception を Advice 化 | 2 時間 |
| 10 | M-impl-6 | toSave.contains O(N²) → Set 化 | 30 分 |

## 終わりに

Cluster D は Phase A → B → B' → 案 A → 整合性 → consistency-review → 累積残 → ヘルスチェック と短期間に層を重ねた結果、**コード上の重複定義 (B-1) / 旧 ReportTasklet の保護漏れ (C-impl-1) / V026 列の整合性 (C-impl-2)** が露呈している。設計レビューで指摘された「期首日の三重定義」「supplier_no=303 除外」「旧 tasklet 残存」と組み合わせて、Critical 9 件 (設計 4 + コード 5) を 1 sprint で解消することを推奨。

Blocker 1 件 (B-1) は **本番起動失敗 or 静かな旧経路上書き** のため最優先。本番環境の `application.yml` に `spring.main.allow-bean-definition-overriding=true` がある場合、現在は問題なく動作しているように見えるが、accountsPayableAggregationJob の Init step が旧 Config 経由で組まれているか新 Config 経由か未確認のため、テスト環境で起動ログ確認 + Bean 走査を運用フェーズに入る前に必ず実施。

V026 列 (autoAdjustedAmount, mfTransferDate, verifiedAmountTaxExcluded) と consistency-review の整合性は、会計監査の「説明責任」という観点で今後の業務拡大時に重要となるため、C-impl-2 は数値以上の意味を持つ。

実運用フェーズ (2026-04-23〜) の業務フィードバックと並行して、本レビュー Critical を 2 sprint 以内に解消し、設計書 6 本 + コードを「現在地」に再同期することで、Cluster D は安定運用フェーズに移行できる見込み。
