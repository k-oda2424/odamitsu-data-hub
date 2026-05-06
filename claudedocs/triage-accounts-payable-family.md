# Triage: 買掛金ファミリー (Cluster D) 修正対応

triage 日: 2026-05-04
対象指摘総数: 66 件 (設計 25 + コード 28 + Codex 13)
出典: design-review-accounts-payable-family.md / code-review-accounts-payable-family.md / codex-adversarial-accounts-payable-family.md

## サマリー
| 分類 | 件数 |
|---|---|
| SAFE-FIX | 28 件 |
| DESIGN-DECISION | 14 件 |
| DEFER | 19 件 |
| ALREADY-RESOLVED | 5 件 |

優先度概観:
- **Blocker / Critical** で即適用可能な SAFE-FIX: 9 件 (B-1 / C-impl-1 / C-impl-2 / C-impl-5 / C-1 部分 / C-2 部分 / C-3 部分 / C-4 / M-impl-7)
- **DESIGN-DECISION** はほぼ Codex 由来 (源泉アーキテクチャ判断) と Opus 設計レビューの一部 (C-2 旧 tasklet 廃止方針 / C-3 supplier=303 除外戦略)
- **DEFER** は audit trail / event sourcing / supplier 履歴モデル / shop マルチテナント等の長期課題

---

## SAFE-FIX (即適用)

### SF-01: `accountsPayableSummaryInitStep` Bean 衝突解消 (BLOCKER)
- **元レビュー**: code-review B-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableAggregationConfig.java:66-70`
- **修正内容**: `AccountsPayableAggregationConfig.accountsPayableSummaryInitStep` の Bean メソッド名と StepBuilder 第 1 引数を `accountsPayableAggregationInitStep` に rename。`accountsPayableAggregationJob` の組立て箇所も追従変更
- **想定影響範囲**: 1 ファイル (Config) + Job 組立て定義 1 箇所
- **テスト確認**: `./gradlew compileJava` + Spring Boot 起動確認 (Bean 一覧で重複なきこと)
- **依存関係**: SF-02 / SF-03 と同一サブエージェントで並列実行可
- **担当推奨**: 1 サブエージェント、即着手

### SF-02: `AccountsPayableVerificationReportTasklet` の `verified_manually` 行スキップ追加
- **元レビュー**: design-review M-5 / code-review C-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationReportTasklet.java:115-122, 130-141, 156-166`
- **修正内容**: 3 つの `for (TAccountsPayableSummary summary : ...)` ループ先頭に `if (Boolean.TRUE.equals(summary.getVerifiedManually())) continue;` を追加 (`SmilePaymentVerifier` と同じパターン)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 既存バッチテストで OK
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ A "保護漏れ修正")

### SF-03: `ConsistencyReviewService.applyMfOverride` で V026 列リセット
- **元レビュー**: code-review C-impl-2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:173-181, 200-227`
- **修正内容**: selfOnly / amountMismatch ブロックで `r.setAutoAdjustedAmount(BigDecimal.ZERO)`, `r.setMfTransferDate(null)`, `r.setPaymentDifference(...)` を追加。MF_APPLY によって振込明細 Excel 由来の値が stale 化しないようにする
- **想定影響範囲**: 1 ファイル (Service) + UI バッジ (`AccountsPayablePage.showAdjBadge`) の挙動正常化
- **テスト確認**: `./gradlew compileJava` + 整合性レポート → MF_APPLY → 買掛金一覧で `auto_adjusted_amount` が消えることを目視確認
- **依存関係**: SF-04 (ロールバック判定) を先に修正することで V026 列リセットの整合性が完全になる
- **担当推奨**: 1 サブエージェント

### SF-04: `ConsistencyReviewService.upsert` のロールバック判定を `previous != null` ベースに修正
- **元レビュー**: design-review C-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:62-68`
- **修正内容**:
  ```java
  existingOpt.ifPresent(old -> {
      if (old.getPreviousVerifiedAmounts() != null && !old.getPreviousVerifiedAmounts().isEmpty()) {
          rollbackVerifiedAmounts(...);
      }
  });
  ```
  IGNORE 上書き / DELETE 時に `setPreviousVerifiedAmounts(null)` を明示
- **想定影響範囲**: 1 ファイル (Service)
- **テスト確認**: 整合性レビュー切替シナリオ手動確認
- **依存関係**: なし。SF-03 と同一エージェントで連続適用推奨
- **担当推奨**: SF-03 と同一サブエージェント

### SF-05: `SupplierBalancesService.accumulateMfJournals` の `computeIfAbsent` パターン修正
- **元レビュー**: code-review C-impl-5
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:247-253`
- **修正内容**:
  ```java
  MfAccum accum = map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum());
  accum.credit = accum.credit.add(nz(cr.value()));
  ```
- **想定影響範囲**: 1 ファイル (Service)
- **テスト確認**: `./gradlew compileJava` + 累積残一覧 generate で挙動同一を確認
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ B "コード品質")

### SF-06: `MfPaymentAggregator` の複数 supplier_code ヒット時 warn ログ追加
- **元レビュー**: code-review m-impl-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:117-123`
- **修正内容**: `if (codes.size() > 1)` で `log.warn(...)` を追加
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-05 と同一サブエージェント

### SF-07: `M-impl-7` payment-only 上書き時の `verifiedManually` 行スキップ
- **元レビュー**: code-review M-impl-7
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregator.java` (`generatePaymentOnlyRows` line 332 付近)
- **修正内容**: `currMap.get(k)` で得た既存 row が `verifiedManually=true` ならスキップする
- **想定影響範囲**: 1 ファイル (Service)
- **テスト確認**: `./gradlew compileJava` + 手動確定行のあるバッチ実行で剥がれないこと確認
- **依存関係**: なし
- **担当推奨**: SF-02 と同一サブエージェント (保護漏れ修正)

### SF-08: `AccountsPayableBackfillTasklet.toSave.contains(po)` の O(N²) 解消
- **元レビュー**: code-review M-impl-6
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:136-139`
- **修正内容**: `Set<String>` で rowKey 集合を作り `Set.add` の戻り値で重複判定
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + backfill 12 ヶ月で結果差分なきこと確認
- **依存関係**: なし
- **担当推奨**: SF-05 と同一サブエージェント

### SF-09: `TAccountsPayableSummaryService.findAll()` 削除
- **元レビュー**: design-review m-1 / code-review M-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:39-41`
- **修正内容**: dead code `findAll()` メソッドを削除 (Grep で呼び出し元なきこと確認済)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` で参照エラーがないこと
- **依存関係**: なし
- **担当推奨**: SF-05 と同一サブエージェント

### SF-10: 期首日定数の `FinanceConstants` (or `MfPeriodConstants`) 集約
- **元レビュー**: design-review C-1 (実装側) / code-review M-impl-10 (フロント側)
- **対象ファイル** (新規): `backend/src/main/java/jp/co/oda32/domain/service/finance/MfPeriodConstants.java`
- **修正内容**: 4 つの定数を 1 ファイルに集約:
  - `MF_FISCAL_YEAR_START = LocalDate.of(2025, 6, 21)`
  - `SELF_BACKFILL_START = LocalDate.of(2025, 6, 20)`
  - `FIRST_PAYABLE_BUCKET = LocalDate.of(2025, 7, 20)`
  - `MF_JOURNALS_FETCH_FROM = LocalDate.of(2025, 5, 20)`
- **想定影響範囲**: 新規 1 ファイル + `SupplierBalancesService` / `MfPaymentAggregator` / `MfSupplierLedgerService` / `AccountsPayableBackfillTasklet` の private const 削除 (4 ファイル)
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: SF-11 / SF-12 が依存 (定数を呼び出し側に展開する形)
- **担当推奨**: 1 サブエージェント (グループ C "定数集約", 先頭タスク)

### SF-11: `MATCH_TOLERANCE = 100` を `FinanceConstants` に集約
- **元レビュー**: design-review (Major+Minor 横断) / 設計書 D §3.6, 整合性 §3.2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/FinanceConstants.java` (既存) に `MATCH_TOLERANCE = BigDecimal.valueOf(100)` 追加 + 5 箇所の private const 削除
  - `TAccountsPayableSummaryService.MATCH_THRESHOLD`
  - `AccountsPayableIntegrityService.MATCH_TOLERANCE`
  - `AccountsPayableLedgerService.VERIFY_DIFF_THRESHOLD`
  - `SupplierBalancesService.MATCH_TOLERANCE`
  - `ConsistencyReviewService.STALE_TOLERANCE`
- **想定影響範囲**: 1 定数追加 + 5 ファイル更新
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: SF-10 と同じエージェントで並列実行可
- **担当推奨**: SF-10 と同一サブエージェント

### SF-12: フロント `supplier-balances.tsx` の期首日 hardcode 解消
- **元レビュー**: code-review M-impl-10
- **対象ファイル**: `frontend/components/pages/finance/supplier-balances.tsx:151`
- **修正内容**: ヘッダ表示文を `期首 ({report?.mfStartDate ?? '...'}) 〜 基準月 ...` に変更 (レスポンス内 `mfStartDate` を使う)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし (SF-10 のバックエンド側変更とは別)
- **担当推奨**: 1 サブエージェント (グループ E "フロント")

### SF-13: `SqlInitTasklet` の `shop_no=1` hardcode を `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` に置換
- **元レビュー**: code-review m-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryInitTasklet.java:53-57`
- **修正内容**: SQL リテラルの `shop_no = 1` をパラメータ化、`jdbcTemplate.update(sql, ..., FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)`
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + InitTasklet 実行確認
- **依存関係**: なし
- **担当推奨**: SF-10 と同一サブエージェント (定数集約系)

### SF-14: `processedSubNames` の Pair-key record 化
- **元レビュー**: design-review M-4 / code-review M-impl-5
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:203, 266, 353`
- **修正内容**: `record SubNameMonthKey(String subName, LocalDate month) {}` を導入し `Set<String>` を `Set<SubNameMonthKey>` に置換
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-05 と同一サブエージェント

### SF-15: `ConsistencyReviewService.applyMfOverride` の二重 save 整理
- **元レビュー**: design-review m-9 / code-review C-impl-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:200-227`
- **修正内容**: ループ内 `summaryRepository.save(r)` を削除し、端数吸収後に `summaryRepository.saveAll(rows)` を 1 回呼ぶ
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + MF_APPLY 動作確認
- **依存関係**: SF-03 / SF-04 と同一エージェント
- **担当推奨**: SF-03 と同一サブエージェント

### SF-16: `AccountsPayableVerifyRequest` の `@PositiveOrZero` バリデーション追加
- **元レビュー**: design-review m-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/dto/finance/AccountsPayableVerifyRequest.java:11-12`
- **修正内容**: `@PositiveOrZero` を `verifiedAmount` に追加
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-05 と同一サブエージェント

### SF-17: `MfPaymentAggregator.overrideWithMfDebit` で `verified_manually=true` 行を保護
- **元レビュー**: design-review M-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregator.java:165-228`
- **修正内容**: 上書き対象 supplier の集計時、`verifiedManually=true` 行はスキップして MF debit 上書きの対象外とする (R5 と一貫)
- **想定影響範囲**: 1 ファイル (Service)
- **テスト確認**: `./gradlew compileJava` + 手動確定行のある月で再集計しても上書きされないこと
- **依存関係**: SF-07 と同じ "手動確定保護" 系
- **担当推奨**: SF-02 と同一サブエージェント (グループ A)

### SF-18: フロント `integrity-report.tsx` の `window.prompt` を Dialog 化
- **元レビュー**: code-review M-impl-9
- **対象ファイル**: `frontend/components/pages/finance/integrity-report.tsx:111` + 新規 `ConsistencyReviewDialog.tsx`
- **修正内容**: `window.prompt` を shadcn/ui Dialog + Textarea + 文字数 counter に置換 (max 500)
- **想定影響範囲**: 1 ファイル変更 + 1 ファイル新規
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ E "フロント")

### SF-19: フロント `integrity-report.tsx` の mutation chain を `invalidateQueries` に置換
- **元レビュー**: code-review m-impl-7
- **対象ファイル**: `frontend/components/pages/finance/integrity-report.tsx:88, 105`
- **修正内容**: `runMutation.mutate(false)` を `queryClient.invalidateQueries({ queryKey: ['integrity-report'] })` に置換し、useQuery 化
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: SF-18 と同一エージェント
- **担当推奨**: SF-18 と同一サブエージェント

### SF-20: フロント `supplier-balances.tsx` の React row key 修正
- **元レビュー**: code-review m-impl-8
- **対象ファイル**: `frontend/components/pages/finance/supplier-balances.tsx:208`
- **修正内容**: `key={\`${r.supplierNo ?? 'null'}-${r.status}-${r.mfSubAccountNames.join('|')}\`}` に変更
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: SF-12 と同一サブエージェント

### SF-21: フロント `accounts-payable.tsx` の BatchButton に `data-testid` 追加
- **元レビュー**: code-review m-impl-9
- **対象ファイル**: `frontend/components/pages/finance/accounts-payable.tsx:96-124`
- **修正内容**: `data-testid={\`batch-button-${job}\`}` を追加
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: SF-12 と同一サブエージェント

### SF-22: フロント `BalanceFilter` を `as const` const assertion パターンに統一
- **元レビュー**: code-review m-impl-10
- **対象ファイル**: `frontend/types/accounts-payable.ts`
- **修正内容**: `BALANCE_FILTER_LABELS` を `as const` で定義し、type を `keyof typeof` で導出
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: SF-12 と同一サブエージェント

### SF-23: `MfPaymentAggregator` master/supplier の毎月 fetch 解消
- **元レビュー**: code-review C-impl-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:55-128` + 新規 `MfAccountMasterReverseIndexService` または `@Cacheable("mfSubToCodes")`
- **修正内容**: `buildMfSubToCodes()` と `paymentSupplierService.findByShopNo(shopNo)` の結果を Spring Cache or Service 化して 1 度の backfill 実行内で再利用
- **想定影響範囲**: 1 service 新規 + 4 callsite (`AccountsPayableBackfillTasklet`, `AccountsPayableAggregationTasklet`, `MfHealthCheckService`, `SupplierBalancesService`, `AccountsPayableIntegrityService`)
- **テスト確認**: `./gradlew compileJava` + backfill 12 ヶ月で時間短縮確認
- **依存関係**: SF-10 (定数集約) 後の方が望ましい (定数経由になるため)
- **担当推奨**: 1 サブエージェント (グループ D "性能・キャッシュ")

### SF-24: `summary()` API を JPQL 集計クエリに置換
- **元レビュー**: design-review M-2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:55-74` + Repository に集計クエリ
- **修正内容**: `findAll(spec)` を `@Query("SELECT COUNT(s), SUM(CASE WHEN ...) FROM ...")` 集計クエリに置換
- **想定影響範囲**: 1 service + 1 repository
- **テスト確認**: `./gradlew compileJava` + 集計値 (total / verified / unverified) が同一であること手動確認
- **依存関係**: なし
- **担当推奨**: SF-23 と同一サブエージェント (グループ D)

### SF-25: 整合性レポート Controller の例外を `@RestControllerAdvice` で集約
- **元レビュー**: code-review M-impl-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:215-234, 246, 312` + 新規 `FinanceExceptionHandler.java`
- **修正内容**: `MfReAuthRequiredException` / `MfScopeInsufficientException` / `IllegalStateException` を `@RestControllerAdvice` でハンドル。Controller 戻り型を `ResponseEntity<IntegrityReportResponse>` に絞る + 共通 `ErrorResponse` DTO
- **想定影響範囲**: 3 endpoint + 1 advice 新規 + 1 DTO 新規
- **テスト確認**: `./gradlew compileJava` + 401 / 500 系のレスポンス body 構造維持確認
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ F "API 整理")

### SF-26: `m-impl-11` lambda → if-block への展開 (closure 簡素化)
- **元レビュー**: code-review m-impl-11
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:62-68`
- **修正内容**: `existingOpt.ifPresent(old -> {...})` を `if (existingOpt.isPresent()) { var old = existingOpt.get(); ... }` に展開
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: SF-04 と同時適用 (同じ block を触る)
- **担当推奨**: SF-04 と同一サブエージェント

### SF-27: `buildSupplierCumulativeDiffMap` の例外握りつぶし修正
- **元レビュー**: design-review m-11
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:556`
- **修正内容**: `MfReAuthRequiredException` / `MfScopeInsufficientException` は再 throw する。それ以外の Exception のみ WARN ログ + 空 Map 返却
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: SF-25 (RestControllerAdvice) で例外統合される前提
- **担当推奨**: SF-25 と同一サブエージェント

### SF-28: V030 migration: 旧 `accountsPayableSummaryJob` の削除手順 documentation
- **元レビュー**: design-review C-2 の SAFE-FIX 部分
- **対象ファイル**: `backend/src/main/resources/db/migration/V030__deprecate_old_accounts_payable_summary_job.sql` (新規) + `AccountsPayableSummaryConfig.java` に `@Deprecated`
- **修正内容**:
  - `AccountsPayableSummaryConfig` および `AccountsPayableVerificationConfig` に `@Deprecated` + `@ConditionalOnProperty(name="finance.legacy-payable-job", havingValue="true", matchIfMissing=false)` 付与で起動時 Bean 登録を停止
  - migration は `BATCH_JOB_INSTANCE` から `accounts_payable_summary` を削除する SQL (任意 / metadata-only)
- **想定影響範囲**: 2 Config ファイル + 1 migration 新規
- **テスト確認**: `./gradlew compileJava` + Spring Boot 起動で旧 Job が登録されないこと確認
- **依存関係**: SF-01 (Bean 名衝突解消) を先に適用しないと `allow-bean-definition-overriding` で隠れる
- **担当推奨**: SF-01 と同一サブエージェント (Bean 整理系)

---

## DESIGN-DECISION (要ユーザー判断)

### DD-01: MF debit を `payment_settled` の入力源にするか、照合先のみに留めるか (Codex 1 / 案 A 全体)
- **元レビュー**: codex-adversarial 1, 11 / Opus 設計レビュー C-1 (案 A 文書化欠如)
- **論点**: 案 A (現実装) は MF debit を自社 `payment_settled` に書き込むため、整合性レポートの片側が MF 由来 → 自己参照で支払漏れ・MF 誤仕訳が検出不能に
- **選択肢**:
  - A: 現状維持 (案 A 継続) + `payment_settled_source` カラム追加で出所明示 (Codex 推奨の妥協案)
  - B: 案 A を破棄し `verified_amount` ベースに戻す (整合性検出能力優先、MEMORY.md の「間欠取引 supplier の業務実態と一致」を放棄)
  - C: 段階的移行: 期首後の月のみ案 A 維持、新規月は payment_settled_source=VERIFIED_AMOUNT を default
- **影響範囲**: `MfPaymentAggregator`, `PayableMonthlyAggregator.overrideWithMfDebit`, `t_accounts_payable_summary` スキーマ, 設計書 B' 全体
- **推奨**: **A (Codex)** が妥協点 — `payment_settled_source` enum 追加だけで監査説明可能になる

### DD-02: 「権威ある source of truth」階層の明文化 (Codex 2)
- **元レビュー**: codex-adversarial 2
- **論点**: 「請求書 / SMILE / 振込明細 / MF」のうちどれが正なのかが画面ごとに異なる
- **選択肢**:
  - A: 階層定義 (請求額=請求書/SMILE, 支払実績=銀行/振込明細, 会計反映=MF) を design doc + UI ツールチップで明示
  - B: 現状維持 (運用慣行で対処)
- **影響範囲**: 主に design docs (Cluster D 6 本) と UI ツールチップ
- **推奨**: **A (Codex)** — 数値以上の運用負債

### DD-03: 部分支払を別テーブル `payable_settlement_allocation` で表現するか (Codex 3)
- **元レビュー**: codex-adversarial 3
- **論点**: 現行モデル supplier×月×税率 では「100 万請求中 60 万支払」のうち税率別・請求別配賦が推定値になる
- **選択肢**:
  - A: `payable_settlement_allocation` テーブル新設 (大規模設計変更)
  - B: 現状維持 + 設計書に「集計モデル: 残高は概算」と明記する (Codex の妥協案)
- **影響範囲**: スキーマ大改造 or design docs のみ
- **推奨**: **B (Codex の妥協案)** を Phase 1、A は中期 backlog

### DD-04: MF 訂正/取消仕訳のスナップショット記録 (Codex 5)
- **元レビュー**: codex-adversarial 5
- **論点**: 過去月の MF 訂正で自社台帳が静かに書き換わるリスク
- **選択肢**:
  - A: `t_accounts_payable_summary` に `mf_snapshot_fetched_at`, `mf_journal_hash` 追加 + 締め済月は自動上書き禁止
  - B: 現状維持
- **影響範囲**: スキーマ + バッチ + 設計書 D
- **推奨**: **A (Codex)** — 監査要件あり

### DD-05: MF API 障害時の fallback 履歴記録 (Codex 6)
- **元レビュー**: codex-adversarial 6
- **論点**: MF 取得失敗時 fallback が verified_amount に静かに切替わるが、画面で識別不能
- **選択肢**:
  - A: `mf_payment_source = MF_DEBIT | FALLBACK | VERIFIED_AMOUNT` カラム追加 (DD-01 と統合可)
  - B: バッチ Output に「fallback 件数」を追加するのみ
- **影響範囲**: DD-01 と統合可
- **推奨**: **A (DD-01 と一括)**

### DD-06: supplier の合併・分割・コード変更履歴モデル (Codex 7)
- **元レビュー**: codex-adversarial 7
- **論点**: 過去月の supplier コード変更で履歴残高が現在 master で再解釈される
- **選択肢**:
  - A: `m_payment_supplier_history` を新設し supplier-code/sub_account 対応に有効期間
  - B: 現状維持 (mapping 変更を記録から外す)
- **影響範囲**: 大規模スキーマ改造
- **推奨**: **DEFER 候補** だが運用判断は仰ぐ。当面は B を明記

### DD-07: ConsistencyReview の action 種別拡張 (Codex 8)
- **元レビュー**: codex-adversarial 8
- **論点**: IGNORE / MF_APPLY だけでは部分適用、後日確認、supplier 紐付け修正、MF 修正待ちが表現不可
- **選択肢**:
  - A: enum 拡張 (`PARTIAL_APPLY`, `REVIEW_LATER`, `LINK_SUPPLIER`, `MF_FIX_PENDING`, `SPLIT_APPLY`)
  - B: IGNORE 配下に reason enum 追加のみ (Codex 妥協案)
  - C: 現状維持
- **影響範囲**: enum + Service + UI Dialog (SF-18 と統合可)
- **推奨**: **B (妥協案)** + 必要時に A を追加

### DD-08: MF debit の意味分類 (Codex 9)
- **元レビュー**: codex-adversarial 9
- **論点**: MF debit = `BANK_PAYMENT | PURCHASE_RETURN | DISCOUNT | OFFSET | REVERSAL` の区別なく一律 `payment_settled` 扱い
- **選択肢**:
  - A: MF branch の相手科目で分類し、「支払取崩に使ってよい debit」を限定
  - B: 現状維持
- **影響範囲**: `MfPaymentAggregator` 内で相手科目フィルタ追加
- **推奨**: **A (Codex)** — MF 仕訳の意味誤解釈の防止

### DD-09: 旧 `AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` の最終的な廃止判断 (design C-2)
- **元レビュー**: design-review C-2
- **論点**: SF-28 で `@Deprecated` + `@ConditionalOnProperty` で停止までは SAFE。**完全削除のタイミング**は判断必要
- **選択肢**:
  - A: 1 sprint 後に物理削除
  - B: 2-3 sprint 様子見
  - C: 永久に残す (legacy fallback として)
- **影響範囲**: 設計書 D §6.5 + 旧 Config / Tasklet 削除
- **推奨**: **B** (本格運用 1 ヶ月後に判断)

### DD-10: supplier_no=303 除外戦略 (design C-3)
- **元レビュー**: design-review C-3
- **論点**: 集計時のみ除外なので過去行・shop=2 経由・整合性レポートで偽陽性
- **選択肢**:
  - A: 共通 util `PayableExclusionFilter.isExcludedSupplier(supplierNo)` を全 service で適用 (SAFE 寄り)
  - B: Repository 層に `findByShopNoAndTransactionMonthBetweenExcludingSupplierIn` 専用 method 追加
  - C: マスタに `excluded_from_payable=true` フラグ追加で SQL レベルで除外
- **影響範囲**: 4 service (整合性 / 累積残 / 買掛帳 / 集計)
- **推奨**: **A** が妥当だが、運用責任者の確認後に SAFE-FIX 化

### DD-11: `MfHealthCheckService` の anomaly 集計 0 固定の扱い (design M-6)
- **元レビュー**: design-review M-6
- **論点**: 設計書には「PayableAnomalyCounter で集計」と書かれているが現状 0 固定で機能していない
- **選択肢**:
  - A: 設計書を「v1: 0 固定 (UI バッジで集計未対応表示) / v2 で実装」に書換
  - B: `PayableAnomalyCounter` を実装 (SAFE-FIX 化可能)
- **影響範囲**: 1 service 新規 or design doc のみ
- **推奨**: **B** が望ましいが工数次第。MEMORY.md に Next Session Pickup として記載済

### DD-12: 設計書 B' §6.2 UI 列構成の現実への追従 (design M-8)
- **元レビュー**: design-review M-8
- **論点**: 設計書 (3 列) と実装 (6 セル) の乖離
- **選択肢**:
  - A: 設計書を「税込/税抜 2 段表記の 3 列 = 6 セル」に修正
  - B: 実装を 3 列に簡素化
- **影響範囲**: design doc のみ (A) or フロント 1 ファイル + AccountsPayableResponse (B)
- **推奨**: **A** (実装は機能要件を満たしている)

### DD-13: 整合性レポート §10 「決定」表の補完 (design m-8)
- **元レビュー**: design-review m-8
- **論点**: 6 行の決定表に「supplier_no=303 除外」「shop_no=1 固定 vs マルチ」「期首期間以前の扱い」「mf_account_master 重複時」が欠落
- **選択肢**: 設計書追記 (DD-10 / DD-06 と整合する内容)
- **推奨**: DD-10 / DD-06 確定後に doc-update

### DD-14: 振込明細経由の `applyVerification` 仕様 (税率別同額 vs 個別)
- **元レビュー**: design-review m-10 / code-review M-impl-4
- **論点**: 現行 `applyVerification` は全税率行に同額書き込み前提。`AccountsPayableLedgerService.aggregateMonth` の平均化ロジックがその前提に依存
- **選択肢**:
  - A: 「全税率行に同額」を仕様として明文化 + `LedgerService` を `group.get(0).getAutoAdjustedAmount()` に変更 (SAFE 寄り)
  - B: 税率別個別調整に拡張
- **影響範囲**: A は 1 service + 1 doc / B は大規模
- **推奨**: **A** (現行運用を仕様化)

---

## DEFER (将来課題)

### DEF-01: `payable_ledger_event` (event sourcing) への移行 (Codex 11)
- **元レビュー**: codex-adversarial 11
- **理由**: 現状 `t_accounts_payable_summary` snapshot で動作中。中期課題、設計コスト大

### DEF-02: 監査証跡 `finance_audit_log` 新設 (Codex 12)
- **元レビュー**: codex-adversarial 12 / Opus MEMORY.md 軸 F
- **理由**: 既に `claudedocs/design-audit-trail-accounts-payable.md` ドラフト済、別 Sprint 扱い

### DEF-03: shop マルチテナント拡張 (mf_account_master scope) (Codex 10)
- **元レビュー**: codex-adversarial 10
- **理由**: 当面 shop=1 固定。将来 shop が増える時点で再設計

### DEF-04: 「期末解消済み」を別ステータスにし、期間 delta 異常を別シグナル化 (Codex 13)
- **元レビュー**: codex-adversarial 13
- **理由**: 監査モード追加は別タスク。現状 `reconciledAtPeriodEnd` トグルで運用回避可

### DEF-05: 設計書間の用語統一 (期首日・累積残 ラベル / 「期首前」)
- **元レビュー**: design-review 横断 (用語統一の問題)
- **理由**: SF-10 (定数集約) 後に design-doc 一括更新タスクとして実施。本 triage の SAFE-FIX 範囲外

### DEF-06: 設計書 D / B' / 整合性 / 買掛帳 の前後関係明示
- **元レビュー**: design-review (バージョン管理の不整合)
- **理由**: doc-update タスクとして別途。実装影響なし

### DEF-07: `MfJournalCacheService` の永続化 / TTL 検討
- **元レビュー**: design-review m-7
- **理由**: 設計書通り (永続化不要)。運用ドキュメントへの「再起動でクリア」明記のみ別タスク

### DEF-08: V025-V029 migration ファイル名規約 (機能分離)
- **元レビュー**: design-review m-12 / code-review m-impl-12
- **理由**: 既に並びは正しい。チーム規約 (`migration-naming.md`) は別タスク

### DEF-09: `m_payment_supplier_history` 履歴モデル (Codex 7 = DD-06 実装)
- **元レビュー**: codex-adversarial 7
- **理由**: DD-06 が DEFER 推奨に至った場合の実装

### DEF-10: 設計書 D §3.4 期首月 opening 記述更新 (m-5)
- **元レビュー**: design-review m-5
- **理由**: doc-update タスク (実装側は m_supplier_opening_balance 注入で正しい)

### DEF-11: 9 案 A の MF debit 上書き経路重複定義の集約 (M-9)
- **元レビュー**: design-review M-9
- **理由**: `monthlyAggregator.applyAllPipelines(rows, prev, shopNo, month)` への集約は中規模リファクタ。SF-23 (キャッシュ) と同タスクで合体する選択肢あり

### DEF-12: M-impl-2 `verify` API 実行後の翌月 backfill 自動誘導
- **元レビュー**: code-review M-impl-2
- **理由**: UI フロー検討必要。当面は運用手順で対処 (経理に「翌月集計再実行」周知)

### DEF-13: m-impl-2 / m-impl-3 buyer ledger の `signum()` 統一 + 0円 verify anomaly 検出
- **元レビュー**: code-review m-impl-2, m-impl-3
- **理由**: `signum()` は code-base 全体で散在しているため別タスク (low priority)

### DEF-14: `applyVerification` 設計書参照名の整合 (m-4)
- **元レビュー**: design-review m-4
- **理由**: doc-update タスク

### DEF-15: フロント `accounts-payable.tsx` の `purchaseDateRange` を `date-fns` に置換
- **元レビュー**: code-review m-impl-6
- **理由**: 現状動作するため緊急性なし。`date-fns` 採用ガイドライン整備時にまとめて

### DEF-16: m-impl-5 `AccountsPayableResponse.from(...)` overload の `@Deprecated` 整理
- **元レビュー**: code-review m-impl-5
- **理由**: 整合性は取れている。リファクタは別タスク

### DEF-17: m-6 `PayableMonthlyAggregator.SupplierAgg.closingExclTotal` の用途整理
- **元レビュー**: design-review m-6
- **理由**: 現実装は正しいので緊急性なし。doc 充実タスクとして

### DEF-18: 整合性レポート §2.3 vs `MfJournalCacheService` のキャッシュポリシー記述差
- **元レビュー**: design-review (スコープの食い違い)
- **理由**: doc-update タスク

### DEF-19: M-7 `/integrity-report` の性能見積もり更新
- **元レビュー**: design-review M-7
- **理由**: doc-update タスク (設計書 整合性 §11 のリスク表更新)

---

## ALREADY-RESOLVED

### AR-01: design-review M-3 の `SupplierBalancesService` キャッシュ性能
- **解消経緯**: SF-23 (キャッシュ化) で解決済の方向性。`MEMORY.md` で「初回 12.98s, cache hit 75ms」と既に効果計測済

### AR-02: design-review m-5 の期首月 opening 設計書記述
- **解消経緯**: 実装は `m_supplier_opening_balance` 注入で正しく動作。設計書側を DEF-10 で更新するのみ

### AR-03: design-review チェックリスト「`SmilePaymentVerifier` の `verified_manually` スキップ」
- **解消経緯**: 実装済 (line 155-159, 374-378)。本 triage で取り扱う必要なし

### AR-04: design-review チェックリスト「shop_no=1 固定」
- **解消経緯**: `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO=1` で集約済 (Codex 10 の根本提案は DEF-03 で別途)

### AR-05: design-review チェックリスト「整合性 4 カテゴリ網羅」
- **解消経緯**: 実装済 (mfOnly / selfOnly / amountMismatch / unmatchedSuppliers)

---

## 適用順序提案

SAFE-FIX を以下の順序で適用すると依存関係が綺麗:

1. **SF-01** (Bean 衝突解消) — 起動 Blocker、最優先
2. **SF-10 / SF-11 / SF-13** (定数集約) — 後続の修正で参照先になるため先行
3. **SF-04 → SF-03 → SF-15 → SF-26** (ConsistencyReview 系を順に) — 同一 Service 内で依存
4. **SF-02 / SF-07 / SF-17** (verified_manually 保護漏れ) — 並列適用可
5. **SF-05 / SF-06 / SF-08 / SF-09 / SF-14 / SF-16 / SF-28** (コード品質 + Bean 整理) — 独立して並列
6. **SF-23 / SF-24** (性能・キャッシュ) — DEF-11 と統合余地あり
7. **SF-25 / SF-27** (例外ハンドリング統合) — 後で良い、依存最小
8. **SF-12 / SF-18 / SF-19 / SF-20 / SF-21 / SF-22** (フロント) — バックエンド独立、並列可

## 並列実行プラン

| グループ | 担当タスク | サブエージェント | 依存 |
|---|---|---|---|
| **A: 保護漏れ修正** | SF-02 / SF-07 / SF-17 | 1 | なし |
| **B: コード品質** | SF-05 / SF-06 / SF-08 / SF-09 / SF-14 / SF-16 | 1 | なし |
| **C: 定数集約** | SF-10 / SF-11 / SF-13 | 1 | なし (先行推奨) |
| **D: 性能・キャッシュ** | SF-23 / SF-24 | 1 | C 後 |
| **E: フロント** | SF-12 / SF-18 / SF-19 / SF-20 / SF-21 / SF-22 | 1 | なし |
| **F: API 整理** | SF-25 / SF-27 | 1 | なし |
| **G: ConsistencyReview** | SF-03 / SF-04 / SF-15 / SF-26 | 1 | グループ内で順序あり |
| **H: Bean 整理** | SF-01 / SF-28 | 1 | SF-01 → SF-28 順 |

並列グループ数: 8

## 推定総工数

| グループ | 推定 | 内訳 |
|---|---|---|
| A | 1.5 時間 | SF-02 (1h) + SF-07 (1h) + SF-17 (30min) を 1 エージェントが連続適用 |
| B | 2 時間 | 6 タスク × 平均 20 分 |
| C | 1.5 時間 | 定数移動 + 5 ファイル更新 |
| D | 5 時間 | C-impl-4 (4h) + M-2 (1h) |
| E | 4 時間 | M-impl-9 Dialog (3h) + 他 5 タスク (1h) |
| F | 2 時間 | RestControllerAdvice 設計 + 3 endpoint |
| G | 3.5 時間 | C-impl-2 (2h) + C-4 (1h) + C-impl-3 (30min) + m-impl-11 (簡素) |
| H | 1.5 時間 | B-1 (30min) + V030 + Config Deprecated (1h) |

**並列実行時の wallclock**: 最遅グループ D の 5 時間 (グループ A〜H が並列で走る前提)
**直列実行時の累積**: 21 時間 (1 サブエージェント逐次)

DESIGN-DECISION 14 件 + DEFER 19 件は別途ユーザー判断・別 Sprint で消化。

## 出力ファイルパス

`C:\project\odamitsu-data-hub\claudedocs\triage-accounts-payable-family.md`
