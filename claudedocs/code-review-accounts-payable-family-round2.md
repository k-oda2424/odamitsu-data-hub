# 再レビュー: 買掛金ファミリー (Cluster D) 修正後

レビュー日: 2026-05-04
対象: triage SF-01〜SF-28 適用後の差分
レビュアー: Opus サブエージェント (round 2)

## サマリー
- 新規発見: Critical 1 / Major 5 / Minor 4
- 既存修正の評価: ほぼ triage 通り適用されているが、SF-25 で API 契約が静かに変質した点と SF-19 で toast 多重発火するエッジケースは要対応。SF-24 は通常運用パスでは問題ないが summary() の `transactionMonth=null` 互換性が壊れる。
- ロジック等価性 (SF-03 / SF-04 / SF-15 / SF-23) はおおむね triage 意図通り。新規バグは検出されず。

## Critical (新規発見、即修正必要)

### CR-01: SF-24 JPQL 集計 — `transactionMonth=null` 入力で結果が「全件」→「0 件」に変化
- 元 SAFE-FIX: SF-24
- 場所: `backend/src/main/java/jp/co/oda32/domain/repository/finance/TAccountsPayableSummaryRepository.java:111-127` + `TAccountsPayableSummaryService.java:51-69`
- 問題:
  旧 `summary()` は `buildSpec(shopNo, null, transactionMonth, "all")` を経由し、`transactionMonth==null` の場合は WHERE 句に当該条件を追加しなかった (= 全月を集計対象)。
  新 JPQL は `WHERE (:shopNo IS NULL OR s.shopNo = :shopNo) AND s.transactionMonth = :transactionMonth` を強制するため、`transactionMonth==null` を渡すと全行ヒット 0 になり `unverified=0 / matched=0` の空サマリーを返す。
  Controller (`FinanceController.java:124-129`) は `@RequestParam(required = false) LocalDate transactionMonth` を受けるため、URL 省略時にこの差異が顕在化する。
- 現状フロントが必ず `transactionMonth` を付けているため (accounts-payable.tsx:233) 実害はないが、**API 契約を静かに変えた**。直 fetch / curl 検証 / 過去スクリプトが壊れる。
- 修正案:
  - (A 推奨) JPQL を `(:transactionMonth IS NULL OR s.transactionMonth = :transactionMonth)` に書換。
  - (B) Controller で `transactionMonth` を `@RequestParam(required = true)` 化し 400 を返す（明示的破壊）。設計 doc 追記必須。
- triage 文言「互換 Response 構造を維持する」の "構造" は守られているが "意味" が変質しているため Critical 扱い。

## Major

### MJ-01: SF-25 — 422 → 500 の status code 変更 + `requiredScope` フィールド消失
- 元 SAFE-FIX: SF-25
- 場所: `backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java:42-54` + `dto/finance/ErrorResponse.java`
- 問題:
  1. 旧 endpoint は `IllegalStateException → 422 (UNPROCESSABLE_ENTITY)` を返していた。新 advice は **500 (INTERNAL_SERVER_ERROR)** にマッピング。triage の「401/500 系のレスポンス body 構造維持確認」記述と乖離（旧コードは 422 を使っていた）。
  2. 旧 `MfScopeInsufficientException` レスポンス body は `{message, requiredScope}` を含んでいた (FinanceController.java git diff 参照)。新 `ErrorResponse` は `{message, code, timestamp}` で `requiredScope` が落ちる。
- 影響:
  - フロント `integrity-report.tsx:127-135` は `status === 401/403/400` のみ分岐し他は generic toast。500 にマッピングされても message は表示されるため UX 破壊なし。だが運用監視・ログ集計で 422 → 500 増加が見えるためアラート閾値が上振れする。
  - `requiredScope` は現状フロントで未使用 (Grep 確認済み) なので即時影響無し。だが Controller 旧仕様が API doc に残っていれば外部利用者が壊れる。
- 修正案:
  - (A) `IllegalStateException` を 422 のまま返すように `HttpStatus.UNPROCESSABLE_ENTITY` に変更。
  - (B) `ErrorResponse` に `requiredScope` (Optional) を追加し、`handleMfScopeInsufficient` で詰める。

### MJ-02: SF-19 — `queryFn` 内の `toast.success/warning` がキャッシュ無効化のたびに多重発火
- 元 SAFE-FIX: SF-19
- 場所: `frontend/components/pages/finance/integrity-report.tsx:108-116`
- 問題:
  TanStack Query v5 では `queryFn` 内副作用は不適切。
  - `invalidateReport()` が review 保存後に呼ばれる (line 162)
  - 結果として、`review 保存 → invalidateQueries → refetch → queryFn → toast` で**毎回 success/warning toast が再発火**する。
  - 旧 `runMutation.onSuccess` は明示的に 1 回だけだった。
- 影響: ユーザーが review 操作を行うと toast がガンガン出る (UX ノイズ)。
- 修正案: toast 発火を `useEffect(() => { ... }, [reportQuery.dataUpdatedAt])` に移し、初回 fetch のみ通知するか、`isLoading` 完了直後の単発化を検討。

### MJ-03: SF-23 — `MfReverseIndexCache` がマスタ更新時に invalidate されない (TTL 5 分のみ)
- 元 SAFE-FIX: SF-23
- 場所: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:155-178`
- 問題:
  `MfAccountSyncService.run()` は `mf_account_master` を `deleteAllInBatch + saveAll` で全洗替えする (`MfAccountSyncService.java:63-75`)。
  その直後 5 分以内に backfill / supplier-balances / integrity を走らせると、`MfPaymentAggregator` 内 cache が古い `mfSubToCodes` を返し、最新追加 supplier の MF debit を集計取りこぼす可能性。
  triage SF-23 で「TTL 5 分」を明示しているため SAFE-FIX としては設計範囲内だが、master 同期 → 再集計の運用フローでは事故源。
- 影響: 業務フロー「MF 同期 → 即 backfill」で集計値が 5 分間ズレる。再集計操作で一見正常 → 5 分後に値が変わる「flaky」現象。
- 修正案:
  - (A) `MfAccountSyncService.run()` 終端で `mfPaymentAggregator.invalidateCache(shopNo)` を呼ぶ public メソッドを追加。
  - (B) TTL を短くする (1 分) — ただし backfill 12 ヶ月で同一 shop に 12 回呼ぶケースで効果薄まる。
  - (C) Spring Cache (`@Cacheable("mfSubToCodes")` + `@CacheEvict`) に置換し、`MfAccountSyncService` で evict をトリガ。triage では「依存追加回避」と書いてあるが Spring Boot 既定で有効化可能。

### MJ-04: SF-25 — `requiredScope` 情報損失で「どのスコープが足りないか」のクライアント自動判定不可
- 元 SAFE-FIX: SF-25
- 場所: `FinanceExceptionHandler.java:35-40`
- 問題:
  `MfScopeInsufficientException.getRequiredScope()` を log には出すが response には載せない。今後フロントで「scope 自動再認可ボタン」を作る際に backend を再修正する必要が出る。
- 修正案: MJ-01 (B) と統合。`ErrorResponse.of(message, code, Map<String,Object> details)` に拡張するか、scope 専用 record (`MfScopeInsufficientErrorResponse`) を別途定義。

### MJ-05: SF-15 saveAll への変更で `applyVerification` 周辺のフラッシュタイミング変化 (低リスク)
- 元 SAFE-FIX: SF-15
- 場所: `ConsistencyReviewService.java:193, 247, 278`
- 問題:
  旧コードは `for (...) summaryRepository.save(r);` で各反復内で `EntityManager.flush()` 候補だった (Spring Data の `save` は `merge`/`persist` をディスパッチするだけで明示 flush はしないが、ID 生成が必要な場合はその時点で flush)。
  新コードは `saveAll(rows)` を 1 回。本ケースは PK が `@Embeddable` で複合 KEY (id 自動生成なし) のため挙動同一。
  ただし、もし将来 `@PostUpdate` Listener や trigger 連動が入った場合、フラッシュ順序が変わる可能性あり。
- 影響: 現状リスクなし。ただし設計 doc (audit-trail-accounts-payable.md ドラフト) で Entity Listener を追加する予定があるため、その時点でテスト網羅必要。
- 修正案: `audit-trail` 実装時に「flush タイミング保証が必要なら明示 `saveAndFlush`」をコーディング規約として明文化。

## Minor

### MN-01: ConsistencyReviewService.applyMfOverride: `taxRate=null` で NPE (SF-03 適用前から既存)
- 場所: `ConsistencyReviewService.java:174` (`r.getTaxRate().toPlainString()`)
- 問題: previous Map 構築時に `r.getTaxRate()` が null だと NPE。`TAccountsPayableSummaryService.applyVerification` (line 163-168) は明示的 fail-fast しているが、本サービスはそれが無い。
- triage SF-03 はこの latent bug を見落とし。
- 修正案: previous 構築前に同等の null check を追加し IllegalStateException で fail-fast。

### MN-02: SupplierBalancesService の opening 注入 — `accumulateSelf` の opening 検出キーが `MF_JOURNALS_FETCH_FROM` (2025-05-20) のまま
- 場所: `SupplierBalancesService.java:259`
- 問題:
  triage SF-10 で 4 つの定数を分離したが、`accumulateSelf` 内で旧 `MF_PERIOD_START` を `MF_JOURNALS_FETCH_FROM` に機械的置換した結果、本来は `SELF_BACKFILL_START` (2025-06-20) で判定すべき箇所が 2025-05-20 のままになっている。
  実害は小: `t_accounts_payable_summary` は 20日締めバケットなので 2025-05-20 行は通常存在しない (期首は 2025-06-20)。よって opening が 2 重カウントされない。
- 修正案: `accumulateSelf` の比較を `SELF_BACKFILL_START` に変更し意味を明確化。コメントで「過去データに 2025-05-20 行が混入していたら拾う互換」を残す。

### MN-03: Controller try/catch 撤去後、ResponseStatusException 経路は GlobalExceptionHandler 経由
- 場所: `FinanceController.java:213-303` (3 endpoint)
- 問題:
  `ResponseStatusException` (BAD_REQUEST 等) は `GlobalExceptionHandler.handleResponseStatus` でハンドルされるため `FinanceExceptionHandler` を通らない。これは設計通りだが、レスポンス body が `{message: "..."}` 形式 (Map<String,String>) で `ErrorResponse` 形式と不一致。
- 影響: 整合性レポートで supplier_no 数値変換 BAD_REQUEST 等が他の MF 例外と body 構造異なる → フロントで分岐コードが必要。現状は問題ないが、`ErrorResponse` 統一が triage 意図ならここも揃えたい。
- 修正案: `GlobalExceptionHandler.handleResponseStatus` を `ErrorResponse` 形式に揃えるか、`FinanceExceptionHandler` で `ResponseStatusException` も拾う (basePackages 制約あり)。本 SAFE-FIX 範囲外として DEFER 推奨。

### MN-04: `ConsistencyReviewDialog` の `maxLength=NOTE_MAX_LENGTH` で `overLimit` ロジックは到達不能
- 場所: `frontend/components/pages/finance/ConsistencyReviewDialog.tsx:64, 81-86`
- 問題:
  `<Textarea maxLength={500} ...>` を渡しているため、ブラウザレベルで 500 文字超入力ができない。よって `note.length > 500` (overLimit) は常に false で、destructive 表示と disabled は dead code。
- 影響: 機能上問題なし、UI 表示は冗長。
- 修正案: `maxLength` を Textarea から外して JS 側で警告のみ出すか、overLimit 関連 state を削除。triage 「文字数 counter (max 500)」要件は前者の方が近い。

## SF-XX 別の確認結果

| SF-XX | 適切に適用されたか | 備考 |
|---|---|---|
| SF-01 | OK | `accountsPayableAggregationInitStep` rename 整合。Bean 衝突解消 |
| SF-02 | OK | 3 ループ全て先頭で `verifiedManually` skip 追加 (line 113-114, 132-133, 161-162) |
| SF-03 | OK (1 latent bug 既存、MN-01 参照) | selfOnly / amountMismatch 両ブロックで V026 列リセット + paymentDifference 再計算 |
| SF-04 | OK | snapshot 有無で判定 (line 64-70, 102-104)、IGNORE 経路で previous=null 明示 |
| SF-05 | OK | `computeIfAbsent` パターンに統一 (`SupplierBalancesService.java:240-249`) |
| SF-06 | OK | `MfPaymentAggregator.java:117-119` で `if (codes.size() > 1)` 警告ログ追加 |
| SF-07 | OK | `PayableMonthlyAggregator.java:355-359` で payment-only 上書き skip |
| SF-08 | OK | `Set<String> existingKeys` で O(N²) → O(N) に解消 (`AccountsPayableBackfillTasklet.java:137-145`) |
| SF-09 | OK | `findAll()` 削除、grep で呼出元なきこと確認済 |
| SF-10 | OK | `MfPeriodConstants` 4 定数集約完了。注: MN-02 の semantic 整合は別途要対応 |
| SF-11 | OK | `MATCH_TOLERANCE = BigDecimal.valueOf(100)` を `FinanceConstants` に集約、5 箇所参照置換 |
| SF-12 | OK | `report?.mfStartDate ?? '...'` 動的表示化 |
| SF-13 | OK | SQL を `?` プレースホルダ化 + `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` 注入 |
| SF-14 | OK | `record SubNameMonthKey(String, LocalDate)` 導入、`Set` 型置換 |
| SF-15 | OK (MJ-05 補足) | `saveAll` で 1 回永続化に集約 |
| SF-16 | OK | `@PositiveOrZero` 追加 |
| SF-17 | OK | `verifiedManually=true` 行を分離し非手動行に按分 (`PayableMonthlyAggregator.java:182-247`) |
| SF-18 | OK (MN-04 補足) | `ConsistencyReviewDialog` 新規。Textarea + 文字数 counter |
| SF-19 | OK (MJ-02 toast 多重発火) | `useQuery` + `invalidateQueries` 化 |
| SF-20 | OK | `${supplierNo ?? 'null'}-${status}-${mfSubAccountNames.join('|')}` に変更 |
| SF-21 | OK | `data-testid={\`batch-button-${job}\`}` 追加 |
| SF-22 | OK | `BALANCE_FILTER_LABELS as const` + `keyof typeof` 型導出 |
| SF-23 | OK (MJ-03 invalidation 不在) | TTL 5 分の private map cache |
| SF-24 | NG (CR-01 transactionMonth=null 互換破壊) | JPQL 集計 |
| SF-25 | NG (MJ-01 / MJ-04 422→500 + requiredScope 消失) | RestControllerAdvice 移行 |
| SF-26 | OK | lambda → if-block に展開 |
| SF-27 | OK | `MfReAuthRequiredException` / `MfScopeInsufficientException` を re-throw |
| SF-28 | OK | V030 migration (no-op placeholder)、Config 2 つに `@ConditionalOnProperty` 付与 |

## 推奨アクション

### 即修正 (今ループ内で対応すべき)
- **CR-01**: `aggregateSummary` JPQL を `(:transactionMonth IS NULL OR s.transactionMonth = :transactionMonth)` に修正、または Controller で `required=true` に変更
- **MJ-01**: `FinanceExceptionHandler.handleIllegalState` を 422 に戻す
- **MJ-02**: `integrity-report.tsx` の `queryFn` 内 toast を `useEffect([dataUpdatedAt])` に外出し
- **MJ-04**: `ErrorResponse` に `requiredScope` (Optional) を追加

### 次ループで対応 (DEFER)
- **MJ-03**: `MfPaymentAggregator.invalidateCache(shopNo)` public メソッド + `MfAccountSyncService` から呼出 (運用フロー要件次第)
- **MN-01**: `applyMfOverride` の `taxRate=null` fail-fast (latent bug、緊急性低)
- **MN-02**: `accumulateSelf` の opening 検出キーを `SELF_BACKFILL_START` に変更
- **MN-04**: `ConsistencyReviewDialog` の `maxLength` 削除して JS 側で警告のみ

### 受容 (現状で OK)
- **MJ-05** SF-15 saveAll 化のフラッシュタイミング: 現コードでは挙動同一、Entity Listener 導入時に再評価
- **MN-03** ResponseStatusException が ErrorResponse 形式と不一致: スコープ外、別 SAFE-FIX で統一

## 全体評価

triage SF-01〜SF-28 のうち **26 件は適切に適用済み**。残り 2 件 (SF-24 / SF-25) で API 契約変質バグが混入しており、**次ループ必要**。

最も重要な発見は CR-01 (SF-24 の `transactionMonth=null` 互換性破壊) と MJ-01 (SF-25 の 422→500 status code drift)。どちらも triage が「互換維持」と書きながら実装で意味が変質した類のバグで、ロジックは正しいが API 契約レベルで silent break。
