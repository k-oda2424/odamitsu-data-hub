# Triage: 残 3 クラスター B/G/H 修正対応

triage 日: 2026-05-04
対象指摘総数: **77 件** (B 28 + G 23 + H 26)
出典:
- Cluster B (MF Cashbook Import):
  - design-review-mf-cashbook-import.md (Critical 1 / Major 6 / Minor 5 = 12 件)
  - code-review-mf-cashbook-import.md (Critical 2 / Major 6 / Minor 8 = 16 件)
- Cluster G (supplier opening balance):
  - design-review-supplier-opening-balance.md (Critical 2 / Major 5 / Minor 6 = 13 件)
  - code-review-supplier-opening-balance.md (Critical 1 / Major 4 / Minor 5 = 10 件)
- Cluster H (経理ワークフロー):
  - design-review-accounting-workflow.md (Critical 2 / Major 7 / Minor 6 = 15 件)
  - code-review-accounting-workflow.md (Critical 1 / Major 4 / Minor 6 = 11 件)

これら 3 クラスターは Codex 批判パスをスキップしているため、Opus 設計+コードレビューのみが対象。

---

## サマリー (3 クラスター合算)

| 分類 | B | G | H | 計 |
|---|---|---|---|---|
| SAFE-FIX | 13 | 10 | 12 | 35 |
| DESIGN-DECISION | 7 | 5 | 7 | 19 |
| DEFER | 5 | 4 | 4 | 13 |
| ALREADY-RESOLVED | 3 | 4 | 3 | 10 |
| 合計 | 28 | 23 | 26 | 77 |

優先度概観:
- **Critical 系で SAFE-FIX 化できたもの: 6 件** (B-Critical-1 認可ガード追加 / B-impl-2 admin gating UI / G-impl-1 journal #1 skip 統一 / G-impl-3 `@Generated` event 拡張 / H-design-C1 ショップ別 MAX 修正 / H-impl-1 `accountsReceivableSummary` 追加)
- **DESIGN-DECISION** は accountancy 判定 (G-design-C1 整合性レポートの opening 注入方針) や MF API 取り扱い (B-Major-4 ストリーミング解析) など方針合意が必要なもの
- **DEFER** は別 Sprint で扱うべき長期課題 (B-Major-5 PII 永続化見直し / H-design-M5 `m_accounting_schedule` マスタ化 等)
- **ALREADY-RESOLVED** は Cluster D/F/A/C/E の先行修正で解消済み (`FinanceConstants` 集約 / `LoginUserUtil.resolveEffectiveShopNo` / `FinanceExceptionHandler` 委譲 / `IEntity` 規約 等)

---

## Cluster B SAFE-FIX

### SF-B01: `MfClientMappingController#create` を admin 限定化 (Critical 業務影響)
- **元レビュー**: design B-Critical-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/MfClientMappingController.java:27-31`
- **修正内容**: `create` メソッドに `@PreAuthorize("authentication.principal.shopNo == 0")` を付与し、書き込み系 3 メソッド (POST/PUT/DELETE) で認可仕様を統一。設計書 §セキュリティを正解として実装側を寄せる。コメント `// 一般ユーザでも追加可（現金出納帳取込のマッピング補正UX）` を削除
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 一般ユーザで POST 403 が返ることを curl で確認 + cashbook-import.tsx の "マッピング追加" ボタンが admin 以外で動作しなくなる影響をフロント側でも UX 確認
- **依存関係**: SF-B02 と同期 (UI も admin 限定にする)
- **担当推奨**: グループ A (Critical 認可)

### SF-B02: `MfClientMappingsPage` 編集ボタンを admin gating に統一 (Critical UX)
- **元レビュー**: code B-impl-2
- **対象ファイル**: `frontend/components/pages/finance/mf-client-mappings.tsx:135-148`
- **修正内容**: Pencil ボタン (編集) を `{isAdmin && (...)}` でラップ。delete ボタンの既存パターンと揃え、`MfJournalRulesPage` (`mf-journal-rules.tsx:150-163`) と一貫させる
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + e2e: 一般ユーザでログイン → mf-client-mappings 画面で Pencil ボタン非表示
- **依存関係**: SF-B01 と同期
- **担当推奨**: グループ A

### SF-B03: 設計書 §CSV出力の改行を CRLF に修正
- **元レビュー**: design B-Major-1
- **対象ファイル**: `claudedocs/design-mf-cashbook-import.md` §非機能 / §CSV出力
- **修正内容**: 「LF」表記を「CRLF」に修正し、決定理由 (Python `pandas.to_csv` 出力との意味等価維持) を追記。MEMORY.md の「CRLF+BOM CSV」と整合
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: 文書差分のみ
- **依存関係**: なし
- **担当推奨**: グループ B (文書整合)

### SF-B04: 設計書シード件数とコメントを 18 件に統一
- **元レビュー**: design B-Major-2
- **対象ファイル**:
  - `claudedocs/design-mf-cashbook-import.md` §シード
  - `backend/src/main/resources/db/migration/V008__create_mf_cashbook_tables.sql:43`
- **修正内容**: 設計書の「全17件」「（実質ルール19件中 ユニーク desc_c 10種）」を「18件」に統一。SQL コメント `(19件、priority小=高優先度)` を `(18件, priority小=高優先度)` に修正。表 #13 (重複) の扱いを「正規化により集約されるためシード化不要」と明記
- **想定影響範囲**: 設計書 1 + SQL コメント 1
- **テスト確認**: 文書差分のみ
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-B05: 設計書税リゾルバ表に `PURCHASE_AUTO_WIDE` を追加
- **元レビュー**: design B-Major-3
- **対象ファイル**: `claudedocs/design-mf-cashbook-import.md` §税区分リゾルバ表 / §シード表
- **修正内容**: リゾルバ表に `PURCHASE_AUTO_WIDE`（D列「軽8%」「軽８％」含む / `課税仕入 (軽)8%` / else `課税仕入 10%`）行を追加。シード表 #15(雑費) / #19(福利厚生費) の借方税を `PURCHASE_AUTO_WIDE` に修正。脚注で「雑費/福利厚生費のみ全角％吸収」根拠を明記
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: 文書差分のみ
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-B06: `extractPeriodLabel` の波ダッシュ (`〜` U+301C) 対応 + 厳格な period 不在ガード
- **元レビュー**: code B-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:100-119`, `:137`
- **修正内容**: (1) `v.contains("～") || v.contains("〜")` 両方許容。(2) period 抽出失敗時は履歴を保存しない (period == null で saveHistory skip) ガードを追加。`saveHistory` 内の `period = cached.getFileName()` フォールバックを撤去
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew test --tests *CashBookConvertServiceGoldenMasterTest` + period が `〜` の fixture 追加
- **依存関係**: なし
- **担当推奨**: グループ C (Service ロジック)

### SF-B07: `selectSheet` のフォールバックを撤去 (silent 誤データ取込防止)
- **元レビュー**: code B-impl-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:222-243`
- **修正内容**: 「先頭シート (MF を除く)」へのフォールバックを撤去し、`null` を返して呼び出し側で `IllegalArgumentException` (現行動作) に倒す
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew test` + 現行ゴールデンマスタ (記入 / 現金出納帳のみ) は影響なし
- **依存関係**: SF-B06 と同 Service なので同 PR で扱える
- **担当推奨**: グループ C

### SF-B08: `findRule` の Comparator を分解しオーバーフロー余地排除
- **元レビュー**: code B-impl-5
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:520-527`
- **修正内容**: `priority * 2 + kwBonus` を `Comparator.comparingInt(MMfJournalRule::getPriority).thenComparingInt(...)` に書き換え。可読性とオーバーフロー耐性を確保
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew test --tests *CashBookConvertServiceGoldenMasterTest` (動作等価性維持)
- **依存関係**: SF-B06/B07 と同 Service
- **担当推奨**: グループ C

### SF-B09: `cashbook-import.tsx` の死 `invalidateQueries` 削除 + rePreview pending guard
- **元レビュー**: code B-impl-6
- **対象ファイル**: `frontend/components/pages/finance/cashbook-import.tsx:52-64`
- **修正内容**: (1) `invalidateQueries({ queryKey: ['mf-client-mappings'] })` を削除 (この画面では useQuery していない死 invalidate)。(2) `rePreviewMutation.mutate` を `if (preview && !rePreviewMutation.isPending)` でガード
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + cashbook-import 既存 e2e
- **依存関係**: なし
- **担当推奨**: グループ D (フロント)

### SF-B10: `MfTaxResolver` の例外を `IllegalStateException` 化 (マスタ起因システムエラーは 500/422 化)
- **元レビュー**: design B-Minor-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/MfTaxResolver.java:28`
- **修正内容**: 未知の `tax_resolver` コードを受けたときの `IllegalArgumentException` を `IllegalStateException` に変更。`FinanceExceptionHandler#handleIllegalState` (Cluster F SF-25 で既存) が 422 + 汎用メッセージで処理するため Controller 側修正は不要
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: Cluster F の `FinanceExceptionHandler` (確認済 commit 済)
- **担当推奨**: グループ C

### SF-B11: `validateFile` の `application/octet-stream` 受容コメント追加 + XSSF 例外を 400 翻訳
- **元レビュー**: code B-impl-1 (m-impl-1 として記載)
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:181-198`
- **修正内容**: `application/octet-stream` 許可に Windows 互換性コメントを追加。`XSSFWorkbook(InputStream)` の例外を 400 翻訳する catch を `convert`/`buildPreview` に追加 (現行は 500 になる)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 異常 xlsx を upload して 400 が返ることを curl で確認
- **依存関係**: なし
- **担当推奨**: グループ C

### SF-B12: `cleanExpired` のロック整合性を `synchronized` で統一
- **元レビュー**: code B-impl-2 (M-impl-2 として記載)
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:173-177`
- **修正内容**: `cleanExpired` にも `synchronized(this)` を付与し、`enforceCacheLimit` の対称性を保つ。早期 return (`if (cache.isEmpty()) return;`) も追加 (m-impl-2 と同時解消)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: グループ C

### SF-B13: フロント `mf-cashbook.ts` の `TaxResolver` literal union 型導入
- **元レビュー**: code B-Minor (m-impl-5)
- **対象ファイル**: `frontend/types/mf-cashbook.ts:73-101`
- **修正内容**: `export type TaxResolver = typeof TAX_RESOLVERS[number]` を追加し、`debitTaxResolver`/`creditTaxResolver` の型を `string` から `TaxResolver` に変更
- **想定影響範囲**: 1 ファイル + Select の onValueChange 型推論改善
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: グループ D

---

## Cluster G SAFE-FIX

### SF-G01: `SupplierBalancesService.accumulateMfJournals` で journal #1 を skip 追加 (Critical 二重計上防止)
- **元レビュー**: code G-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:233-258`
- **修正内容**: `MfSupplierLedgerService.java:116` / `AccountsPayableIntegrityService.java:147` と同様に `if (MfJournalFetcher.isPayableOpeningJournal(j)) continue;` を MF 集計ループ冒頭に追加。コメントも「3 サービスで journal #1 除外を統一、opening は `m_supplier_opening_balance.mf_balance` 経由で注入」に書換
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 累積残一覧で diff が変化しないこと (期首残込み self vs MF cumulative の対称性が成立するため数値は一致のはず) を実 DB で確認
- **依存関係**: G-impl-1 修正後の数値検証は実バックエンド疎通必須 (MEMORY.md `feedback_incremental_review` ルール)
- **担当推奨**: グループ A (Critical 業務影響)

### SF-G02: `MSupplierOpeningBalance#effectiveBalance` の `@Generated` を INSERT+UPDATE 化 (Critical 構造バグ予防)
- **元レビュー**: code G-impl-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MSupplierOpeningBalance.java:48-51`
- **修正内容**: `@Generated` を `@Generated(event = { EventType.INSERT, EventType.UPDATE })` に明示。`org.hibernate.generator.EventType` の import 追加。設計レビュー Major-3 の修正 (利用側を `getEffectiveBalance()` に統一) の前提条件
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 手動補正 → MF 再取込シナリオで `effectiveBalance` がメモリ上で stale にならないことを単体テストで担保
- **依存関係**: SF-G07 (利用側統一) より先に必須
- **担当推奨**: グループ A

### SF-G03: `MfOpeningBalanceService` の zombie row 復活 (`existing.isPresent()` 分岐で `setDelFlg("0")`)
- **元レビュー**: code G-impl-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:147-156`
- **修正内容**: 既存 entity 取得後、`mfBalance` 等の更新前に `entity.setDelFlg("0");` を追加し論理削除状態を解除。silent zombie の発生を防ぐ
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + `del_flg='1'` 行を SQL で作成 → fetch-from-mf → `del_flg='0'` に戻ることを確認
- **依存関係**: なし
- **担当推奨**: グループ A

### SF-G04: `SupplierOpeningBalanceController#list` に shop 権限ガード追加
- **元レビュー**: design G-Major-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/SupplierOpeningBalanceController.java:32-37`
- **修正内容**: `list` メソッドに `@PreAuthorize("authentication.principal.shopNo == 0 or authentication.principal.shopNo == #shopNo")` を付与。または `LoginUserUtil.resolveEffectiveShopNo(shopNo)` (Cluster F 既存) で正規化し、不一致時 403。Cluster F SF-02/03 の IDOR ガードパターンと統一
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 一般ユーザ shop=1 で `?shopNo=2` 渡して 403 が返ることを curl で確認
- **依存関係**: なし (Cluster F の `LoginUserUtil` は確認済)
- **担当推奨**: グループ A

### SF-G05: `OPENING_DATE` ハードコード集約 — `FinancePeriodConfig` 新設 (5 箇所統合)
- **元レビュー**: design G-Major-1
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/constant/FinancePeriodConfig.java` (`fiscalYearStartDate=2025-06-21` / `openingBucketDate=2025-06-20` / `mfPeriodStartDate=2025-05-20`)
  - 利用変更: `MfSupplierLedgerService.java:52`, `SupplierBalancesService.java:56`, `AccountsPayableBackfillTasklet.java:63`, `MfBalanceReconcileService.java:46`, `frontend/types/supplier-opening-balance.ts:85`
- **修正内容**: `FinanceConstants` (Cluster D 既存) と同パターンで static final 集約。フロントは `/api/v1/finance/period-config` を新設して取得 (admin がフォームで上書き可能化は別 Sprint = DESIGN-DECISION)
- **想定影響範囲**: 1 新規 + 5 既存ファイル
- **テスト確認**: `./gradlew compileJava` + 全 batch / web e2e で日付が変わらないことを実 DB で確認
- **依存関係**: なし
- **担当推奨**: グループ B (定数集約)

### SF-G06: 取込ループ `findById` の N+1 を `Map<Integer, MSupplierOpeningBalance>` 一括 fetch 化
- **元レビュー**: design G-Minor-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:144`
- **修正内容**: ループ前に `findByPkShopNoAndPkOpeningDateAndDelFlg(shopNo, openingDate, "0")` 等で 1 query 取得し `Map<Integer, MSupplierOpeningBalance>` 構築。ループ内では `map.get(supplierNo)` で参照
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + fetch-from-mf 実行時の SQL ログで select 件数が 1 + N → 1 + 1 になることを確認
- **依存関係**: SF-G03 と同 method なので同 PR で扱える
- **担当推奨**: グループ C

### SF-G07: `MfOpeningBalanceService` の `mf+manual` add 計算を `getEffectiveBalance()` に統一
- **元レビュー**: design G-Major-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:329, 273, 391-395`
- **修正内容**: `getEffectiveBalanceMap` / `sumEffectiveBalance` / `list` 内の `nz(e.getMfBalance()).add(nz(e.getManualAdjustment()))` を `e.getEffectiveBalance()` に置換
- **想定影響範囲**: 1 ファイル (3 箇所)
- **テスト確認**: `./gradlew test`
- **依存関係**: **SF-G02 必須前提** (`@Generated` UPDATE 化なしで適用すると stale 値を返すため)
- **担当推奨**: グループ C (SF-G02 と同 PR でセット)

### SF-G08: `findOpeningJournal` / `isPayableOpeningJournal` 共通化 — `MfOpeningJournalDetector` util 新設 (Critical 翌期事故予防)
- **元レビュー**: design G-Critical-2 + code G-impl-7 (sub_account_name 非対称)
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningJournalDetector.java`
  - 既存: `MfOpeningBalanceService.java:336-363`, `MfJournalFetcher.java:180-194`
- **修正内容**: 判定の base 述語を「`hasPayableCredit && !hasPayableDebit` (sub_account_name の null チェックは判定 base には含めない)」で 1 箇所に集約。取込側のみ「最大件数選好」を adapter として残す。`AccountsPayableIntegrityService:147` も util 経由に置換。ユニットテスト 4 ケース (純粋 opening / 支払のみ / 混在 / 空) を追加
- **想定影響範囲**: 1 新規 + 3 既存ファイル
- **テスト確認**: `./gradlew test --tests *MfOpeningJournalDetector*` (新規) + 既存 e2e 全件
- **依存関係**: なし
- **担当推奨**: グループ A (Critical 翌期事故予防、2026-06-21 切替前に必須)

### SF-G09: フロント `submitEdit` の `manualAdjustment` 数値変換でコンマ・全角数字対応
- **元レビュー**: code G-impl-9
- **対象ファイル**:
  - `frontend/lib/utils.ts` (新ヘルパ `parseAmount` 追加)
  - `frontend/components/pages/finance/supplier-opening-balance.tsx:114-132, 336`
- **修正内容**: `parseAmount(s)` で `replace(/[,，]/g, '').replace(/[０-９]/g, c => String.fromCharCode(c.charCodeAt(0)-0xFEE0))` 後 `Number()`。`(editing.mfBalance ?? 0) + (Number(form.adj) || 0)` を `parseAmount(form.adj)` ベースに置換
- **想定影響範囲**: 1 ヘルパ + 1 ページ
- **テスト確認**: `npx tsc --noEmit` + 単体: `parseAmount('1,000,000') === 1000000`, `parseAmount('１２３４') === 1234`
- **依存関係**: なし
- **担当推奨**: グループ D (フロント)

### SF-G10: 設計書 §7.2 / §8.1 / §5.4 の記述と実装を整合
- **元レビュー**: design G-Critical-1 (記述のみ部分) + design G-Major-1 (5 箇所表記) + design G-Major-5
- **対象ファイル**: `claudedocs/design-supplier-opening-balance.md`
- **修正内容**:
  - §7.2 表 "TODO 確認" を「現状未注入。月次 per-period diff のみ意味を持つ。cumulative 系は `SupplierBalancesService` を別途参照」と確定 (DESIGN-DECISION DD-G01 の決定が「未注入で正しい」ならこの記述で確定。「注入する」なら別タスクで実装、ここでは記述のみ)
  - §8.1 「3 箇所」→「5 箇所、命名が異なる 5/20 値を含む」(SF-G05 で集約済の前提で)
  - §5.4 表に「manual のみで作った行に MF が後乗りする可能性」を運用上の注意として追記
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: 文書差分のみ
- **依存関係**: DD-G01 の方針確定後
- **担当推奨**: グループ B (文書整合)

---

## Cluster H SAFE-FIX

### SF-H01: `invoiceLatest` SQL をショップ別 `MAX(closing_date)` に修正 (Critical 業務バグ)
- **元レビュー**: design H-Critical-1 (C-1)
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:60-63`
- **修正内容**: SQL を `SELECT shop_no, MAX(closing_date) AS latest, COUNT(*) FILTER (WHERE closing_date = MAX(closing_date) OVER (PARTITION BY shop_no)) FROM t_invoice GROUP BY shop_no` に変更 (相関サブクエリ or Window 関数)。あるいは `TInvoiceRepository#findLatestClosingDatePerShop()` を `@Query` で集約メソッドとして新設
- **想定影響範囲**: 1 ファイル (Service) or 1 + 1 (Repository 新設)
- **テスト確認**: `./gradlew test` + 実 DB で複数店舗の最新締日が個別に出ることを確認 + フロント `invoiceLatest` 配列の構造変化に伴い E2E mock 更新
- **依存関係**: フロント側 SF-H10 (型) と同期
- **担当推奨**: グループ A (Critical 業務バグ)

### SF-H02: `accountsReceivableSummary` をワークフローステップ4に追加 (Critical 監視抜け)
- **元レビュー**: code H-impl-1
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:85-87` (IN-list に `accountsReceivableSummary` 追加)
  - `frontend/components/pages/finance/accounting-workflow.tsx:60-68` (`BatchChip.names` に `accountsReceivableSummary: '売掛集計'` 追加)
  - `frontend/components/pages/finance/accounting-workflow.tsx:186-189` (ステップ4 statusRenderer に `.filter(j => ['accountsReceivableSummary','salesJournalIntegration'].includes(j.jobName))` 適用)
- **修正内容**: 売掛側を買掛側 (3 ジョブ監視) と対称な構造に揃える
- **想定影響範囲**: 1 backend + 1 frontend
- **テスト確認**: `./gradlew compileJava` + `npx tsc --noEmit` + E2E モック (`MOCK_STATUS.batchJobs`) に追加
- **依存関係**: 設計書 §3.2 / §3.3 注記の更新も同時 (H-design-D2)
- **担当推奨**: グループ A

### SF-H03: `useQuery` error/pending 分岐 + refresh ボタン (Major 業務影響)
- **元レビュー**: code H-impl-3 + design H-Major-6 (M-6)
- **対象ファイル**: `frontend/components/pages/finance/accounting-workflow.tsx:262-268, 326`
- **修正内容**:
  - `staleTime: 30000` + `refetchOnWindowFocus: true` に変更
  - `statusQuery.isError` 分岐で `<Alert variant="destructive">取得失敗。再試行</Alert>` 表示
  - PageHeader 右側に手動 refresh ボタン (`queryClient.invalidateQueries(['accounting-status'])`) 追加
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + E2E で 500 シナリオ追加
- **依存関係**: なし
- **担当推奨**: グループ D (フロント)

### SF-H04: `getAccountingStatus` に `@PreAuthorize("hasRole('ADMIN')")` 追加 (Major 情報統制)
- **元レビュー**: code H-impl-4 + design H-Minor-6 (m-6)
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:506-509`
- **修正内容**: `@PreAuthorize("hasRole('ADMIN')")` をメソッド単位で付与し、`BatchController` 系 (`BatchController.java:126, 143, 173`) の admin 限定方針と一致させる
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 一般ユーザで GET 403 が返ることを curl で確認
- **依存関係**: なし
- **担当推奨**: グループ A

### SF-H05: `BatchController` と `AccountingStatusService` のジョブ取得を `JobExplorer` 経由に統一 (Critical 二重実装解消)
- **元レビュー**: code H-impl-1 (C-impl-1) + design H-Critical-2 (C-2) + design H-Major-5 (M-5)
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:81-92`
  - 新規: `backend/src/main/java/jp/co/oda32/constant/BatchJobCatalog.java` (or `@ConfigurationProperties`)
  - `backend/src/main/java/jp/co/oda32/api/batch/BatchController.java:34-54` (`JOB_DEFINITIONS` を `BatchJobCatalog` から参照)
  - `frontend/components/pages/finance/accounting-workflow.tsx:60-68` (`BatchChip.names` を `/api/v1/batch/job-catalog` 取得に変更)
- **修正内容**: `BatchJobCatalog` enum に `name`, `category`, `description`, `requiresShopNo`, `monitoredInWorkflow`, `workflowStep`, `shortLabel` を集約。`AccountingStatusService` の Native SQL を撤去し `JobExplorer.findJobInstancesByJobName(jobName, 0, 1)` を catalog の monitored ジョブ分ループ。3 重同期 + 2 重実装 + Window 関数化提案 (M-5) を 1 リファクタで同時解消
- **想定影響範囲**: 1 新規 + 2 backend + 1 frontend
- **テスト確認**: `./gradlew test` + `npx tsc --noEmit` + 既存 batch 起動 e2e 全件
- **依存関係**: SF-H02 (`accountsReceivableSummary` 追加) はこの集約後だと catalog に `monitoredInWorkflow=true` 1 行追加で済む
- **担当推奨**: グループ A (Critical 二重実装、ただし工数大なので group A 内で先行) — 工数 2-3 時間

### SF-H06: `Map<String, Object>` 戻り値を `AccountingStatusResponse` record に DTO 化
- **元レビュー**: design H-Major-1 (M-1) + code H-impl-2 (M-impl-2)
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/dto/finance/AccountingStatusResponse.java` (record + 子 record `CashbookHistoryRow`, `InvoiceLatestRow`, `BatchJobStatus`)
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:21`
  - `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:506-509`
  - `frontend/types/finance.ts` (新規 / 既存に `AccountingStatus` 型切り出し)
  - `frontend/components/pages/finance/accounting-workflow.tsx:22-38`
- **修正内容**: `BigDecimal` フィールドは `@JsonFormat(shape = JsonFormat.Shape.NUMBER)` + `stripTrailingZeros()` で fractional 桁を排除。`@Schema` で OpenAPI 注釈付与
- **想定影響範囲**: 1 新規 backend + 2 既存 backend + 1 新規 frontend + 1 既存 frontend
- **テスト確認**: `./gradlew test` + `npx tsc --noEmit` + E2E `MOCK_STATUS` も型付け
- **依存関係**: SF-H05 と同 service なので同 PR で扱う方が良い (が、独立にも可能)
- **担当推奨**: グループ A (SF-H05 とセット)

### SF-H07: `querySingle` の silent failure に log + 例外区別追加
- **元レビュー**: design H-Major-4 (M-4)
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:108-115` および各 catch (51-54, 72-75, 102-105)
- **修正内容**: 全 catch に `log.warn("...", e)` を追加。`SQLGrammarException` は本番で再 throw (テーブル定義変更検出)、`SQLException` (接続障害) のみ swallow + warn
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 意図的 SQL エラー injection で warn 出力確認
- **依存関係**: なし (SF-H05 で Repository 経由化したら不要になる部分もあるが、まず SAFE-FIX として)
- **担当推奨**: グループ A

### SF-H08: 既存 Repository 経由化 (DRY 違反解消)
- **元レビュー**: design H-Major-2 (M-2) + design H-Major-3 (M-3)
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TCashbookImportHistoryRepository.java` (`findTop3ByOrderByProcessedAtDesc()` 追加)
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TInvoiceRepository.java` (`findLatestClosingDatePerShop()` 追加)
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:25-28, 60-63`
- **修正内容**: 既存 Repository に必要メソッドを追加し、Service の NativeQuery を撤去 (`EntityManager` 直叩きを排除)。`@RequiredArgsConstructor` で Repository 注入
- **想定影響範囲**: 2 Repository + 1 Service
- **テスト確認**: `./gradlew test` + 数値変化なしを確認
- **依存関係**: SF-H05 (BatchJobCatalog) と SF-H06 (DTO) と同 PR で扱うのが効率的
- **担当推奨**: グループ A

### SF-H09: m-impl-1, m-impl-2, m-impl-3 (date-fns 利用) を一括修正
- **元レビュー**: code H-Minor m-impl-1 / m-impl-2 / m-impl-3
- **対象ファイル**: `frontend/components/pages/finance/accounting-workflow.tsx:117-123, 121, 260`
- **修正内容**:
  - `useMemo(() => makeSteps(), [])` を削除し直接 `makeSteps()` 呼び出し
  - `key={i}` を `key={h.processedAt ?? i}` に変更
  - `processedAt.split('T')[0].split('.')[0]` を `format(parseISO(h.processedAt), 'yyyy-MM-dd')` に変更 (date-fns)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + 既存 E2E
- **依存関係**: なし
- **担当推奨**: グループ D

### SF-H10: E2E `MOCK_STATUS` 型付け (回帰検知強化)
- **元レビュー**: code H-Minor m-impl-6
- **対象ファイル**: `frontend/e2e/finance-workflow.spec.ts:5-24`
- **修正内容**: `frontend/types/finance.ts` (SF-H06 で新設) の `AccountingStatus` を import し、`const MOCK_STATUS: AccountingStatus = {...}` と型付け
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: SF-H06 必須前提
- **担当推奨**: グループ D

### SF-H11: `m-impl-4` `m-impl-5` 暫定対応
- **元レビュー**: code H-Minor m-impl-4 (Tailwind theme) + m-impl-5 (Text Block)
- **対象ファイル**:
  - `frontend/components/pages/finance/accounting-workflow.tsx:83-96, 107-110, 137-139, 158-161, 184-185, 203-205`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:81-91`
- **修正内容**:
  - フロント: `colorTheme: 'amber' | 'sky' | 'violet' | 'emerald' | 'rose' | 'blue'` 型 + `getStepColors(theme)` ヘルパ化
  - バックエンド: SQL 文字列連結を Java 21 Text Block (`"""..."""`) に書換 (SF-H05/H08 で Repository 化されない場合の暫定)
- **想定影響範囲**: 1 backend + 1 frontend
- **テスト確認**: `./gradlew compileJava` + `npx tsc --noEmit`
- **依存関係**: SF-H05 が完了するなら backend 側は不要
- **担当推奨**: グループ D (フロントのみ)

### SF-H12: 設計書 §7.5 を 3 重同期に修正 + §3.2 監視ジョブ表更新
- **元レビュー**: design H-Critical-2 (C-2) + design H-D2/D5
- **対象ファイル**: `claudedocs/design-accounting-workflow.md`
- **修正内容**:
  - §7.5 「2 箇所」を「`BatchController.JOB_DEFINITIONS` を含む 3 箇所」に修正
  - §3.2 監視対象 7 ジョブに `accountsReceivableSummary` 追加 (SF-H02 の確定後)
  - SF-H05 完了後は「`BatchJobCatalog` で集約済」に書き直し
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: 文書差分のみ
- **依存関係**: SF-H05 / SF-H02 完了後
- **担当推奨**: グループ B (文書整合)

---

## DESIGN-DECISION (3 クラスター合算、優先度順)

### DD-BGH-01: Cluster G — `AccountsPayableIntegrityService` の opening 注入方針確定 (G-Critical-1)
- **判断軸**: 整合性レポートが「opening 込み」「opening 抜き」のどちらで diff を計算すべきか
- **選択肢**:
  - A: 注入する (`SupplierBalancesService` と統一、cumulative 系の意味が一貫)
  - B: 注入しない (現状実装通り、月次 per-period diff のみ意味を持つことを設計書で明示)
- **影響**: 整合性レポート画面のユーザ理解、累積残一覧との整合性
- **推奨**: ユーザ判断 → 確定後に SF-G10 設計書修正で確定
- **元レビュー**: design G-Critical-1

### DD-BGH-02: Cluster B — `csv_content` 永続化方針確定 (B-Major-5 + C-impl-1)
- **判断軸**: PII / 取引先名 / 金額を含む CSV 全文を DB に保存し続けるか
- **選択肢**:
  - A: 廃止 + ハッシュ (SHA-256) + サマリのみ保存 (再生成は uploadId キャッシュ or 再アップロード)
  - B: `byte[]` (BYTEA) 化 + `t_cashbook_import_history` に `shop_no` 追加 + admin 限定 RBAC
  - C: Base64 文字列で保存 (バイト一致保証のみ目的)
- **影響**: GDPR / 個人情報保護法対応 + 再ダウンロード API の将来要件
- **推奨**: ユーザ判断 → 確定後に Migration + Entity + Service 修正
- **元レビュー**: design B-Major-5 + code B-Critical-1 (C-impl-1)

### DD-BGH-03: Cluster H — 月度クローズ機能・ステップ完了自動判定の設計合意
- **判断軸**: 経理担当が「2026年4月度は完了したか」を画面から判定できる仕組みを作るか
- **選択肢**:
  - A: `m_accounting_period_close` テーブル新設 + admin が月度クローズマークを押す
  - B: 各ステップに「完了予定日 vs 最終実行日時」の自動判定ロジック (例: ステップ5 = `accountsPayableSummary` の最新月 == 当月)
  - C: 現状通り経理担当の人的チェックに任せる (DEFER)
- **影響**: 経理運用フロー / `StatusChip.warn` プロパティ (m-3) の活用方針
- **推奨**: ユーザ判断 (中期)
- **元レビュー**: design H-Major-6 周辺 + Workflow 固有観点 §「月度クローズ機能」

### DD-BGH-04: Cluster B — `mf_client_mappings` 認可仕様確定 (B-Critical-1 関連)
- **判断軸**: SF-B01 で「create も admin 限定」と決定したが、運用 UX (現金出納帳取込中に未マッピング得意先を発見したとき一般ユーザが追加できる利便性) を取り戻す必要があるか
- **選択肢**:
  - A: 純 admin only (SF-B01 通り、運用は「admin に依頼」)
  - B: pending/approved ステータス導入 (一般ユーザは pending 起票のみ、admin が approved 化、cashbook 取込で `approved` のみヒット)
- **影響**: 経理運用 + マスタの整合性
- **推奨**: ユーザ判断 → B を選択した場合は別 Sprint で `m_mf_client_mapping.status` 列追加 migration
- **元レビュー**: design B-Critical-1

### DD-BGH-05: Cluster B — POI XSSFWorkbook ストリーミング解析と同時実行ガード方針 (B-Major-4)
- **判断軸**: cashbook の Excel が大規模化したときの OOM 対策
- **選択肢**:
  - A: `OPCPackage.open(InputStream)` + `XSSFReader` で SAX 風ストリーミング解析に変更
  - B: preview API に同時実行 Semaphore (permit=2~3) + `MAX_UPLOAD_BYTES` を 5MB に縮小
  - C: 現状維持 (実態 1-2MB なので問題化していない、DEFER)
- **影響**: backend stability + 開発工数 (A は半日〜1日、B は 1 時間)
- **推奨**: ユーザ判断 (B が短期実用的)
- **元レビュー**: design B-Major-4

### DD-BGH-06: Cluster G — `MfOpeningBalanceService.fetchFromMfJournalOne` の Tx 分割方針 (G-impl-2)
- **判断軸**: MF API 呼び出しを `@Transactional` 外に出す代わりに、エラー時の rollback 範囲をどう設計するか
- **選択肢**:
  - A: 2 段階に分割 (`fetchFromMfJournalOne` 公開: Tx なし / `private @Transactional upsertOpeningBalances`: parsed_data 引数)
  - B: `@Transactional(propagation = REQUIRES_NEW, timeout = 30)` で局所 Tx + connection pool に upper bound 設定
- **影響**: 将来の batch 化 (定期実行) 時の HikariCP 安定性
- **推奨**: A (アンチパターン解消、明示的)
- **元レビュー**: code G-impl-2

### DD-BGH-07: Cluster G — `updateManualAdjustment` の shop 検証ロジック仕様確定 (G-impl-5)
- **判断軸**: 太幸 (shop=2) のような複数 shop 共有 supplier の手動補正ルート
- **選択肢**:
  - A: `MEMORY.md` 方針 (shop=1 統合) に従い「manual_adjustment は shop=1 のみ受け付け、shop=2 は SQL escape hatch」
  - B: `m_supplier_shop_mapping` 経由で shop に紐づくか検証 (shop=2 が `MPaymentSupplier` 側に shop=2 専用エントリを持つ前提)
  - C: 検証を「supplier が存在するか」のみに緩める
- **影響**: 太幸 ¥742,720 シナリオ (設計書 §1.4) の運用方法
- **推奨**: ユーザ判断
- **元レビュー**: code G-impl-5

### DD-BGH-08: Cluster B — INCOME/PAYMENT amountSource ガードの正式化 (B-Major-6)
- **判断軸**: `findRule:511-513` の amountSource ガードを「将来の保険」として残すか「現状デッドコード」として削除するか
- **選択肢**:
  - A: 設計書 §Excel解析ロジック step 5 に正式仕様として明記 + テスト追加
  - B: デッドコードコメント + 削除
- **推奨**: A (将来 INCOME/PAYMENT 同名ルールを追加する余地を残す)
- **元レビュー**: design B-Major-6

### DD-BGH-09: Cluster G — `getEffectiveBalanceMap` キャッシュ戦略 (G-Major-2)
- **判断軸**: `@Cacheable("supplier-opening-balance")` を適用するか、現状維持で supplier 数増加時に対応するか
- **選択肢**:
  - A: Spring Cache 標準で `@Cacheable` + `@CacheEvict`
  - B: `MfJournalCacheService` パターンに揃える独自キャッシュ
- **推奨**: A (標準ライブラリ)
- **元レビュー**: design G-Major-2

### DD-BGH-10〜13: Cluster H — 既存 issue 系 (4 件)
- **DD-BGH-10**: `m_accounting_schedule` マスタ化 (design H-Minor m-4) — DEFER 寄り
- **DD-BGH-11**: `payment-mf-import` を ステップ 4.5 として新設 (design H-Minor m-1) — 設計合意必要
- **DD-BGH-12**: `endTime` 表示・`StatusChip.warn` 活用 (design H-Minor m-2/m-3) — UI 設計合意
- **DD-BGH-13**: 363 行ファイル分割粒度 (design H-Minor m-5) — `components/pages/finance/workflow/` 配下構成

### DD-BGH-14: Cluster B — `t_cashbook_import_history` の `period_label UNIQUE` 上書き仕様
- **判断軸**: 同 period の 2 回目アップロードを UPSERT (現行) するか、UNIQUE 違反で blocked にするか
- **影響**: 過去履歴の保護
- **元レビュー**: code B-impl-1 (M-impl-1) 関連

### DD-BGH-15: Cluster H — `payment-mf-import` をステップ 4.5 とするか、ステップ 5 内のサブセクションとするか
- **元レビュー**: design H-Minor m-1
- **影響**: ワークフロー構造

### DD-BGH-16〜19: Cluster G — 設計書記述追加系 (4 件)
- **DD-BGH-16**: 設計書 §10 Future Work に「定期 batch 化 (`fetchFromMfJournalOne` を `@Scheduled` で月次)」を追加するか
- **DD-BGH-17**: §6.3 用語定義の `unmatched` 二系統明確化 (code G-impl-9 の "unmatched" 用語混在)
- **DD-BGH-18**: `tryGetMfTrialBalanceClosing` を DB キャッシュ列化するか `@Cacheable` で済ませるか (G-impl-8)
- **DD-BGH-19**: `MfOpeningBalanceFetchResponse` に `Row[]` を含めて 1 RTT 化 (G-impl-10) — フロント実装パターン変更を伴う

---

## DEFER (3 クラスター合算、別 Sprint)

### DEF-BGH-01: Cluster B — `CashBookConvertServiceRuleTest` / `MfJournalRuleServiceTest` / `MfClientMappingServiceTest` の実装
- **元レビュー**: design B-Minor m-3
- **理由**: ゴールデンマスタ + e2e で意味等価担保済み、CRUD 単体テストは別 Sprint で網羅

### DEF-BGH-02: Cluster B — `ParsedRow` / `CachedUpload` の record 化 + Service 外切り出し
- **元レビュー**: design B-Minor m-4
- **理由**: 動作影響なし、リファクタ Sprint で

### DEF-BGH-03: Cluster B — `MMfJournalRule` / `MMfClientMapping` / `TCashbookImportHistory` の `@EqualsAndHashCode(of="id")` 化
- **元レビュー**: code B-Minor m-impl-3
- **理由**: マスタ件数小なので実害なし、Hibernate アンチパターン整理 Sprint で一括対応

### DEF-BGH-04: Cluster B — `Field` コンポーネントを `frontend/components/features/common/FormField.tsx` に切り出し
- **元レビュー**: code B-Minor m-impl-4
- **理由**: 再利用機会増加時に対応

### DEF-BGH-05: Cluster B — `parseSheet` の右半分検出ロジック厳格化
- **元レビュー**: code B-Minor m-impl-7
- **理由**: 現状 fixture 全件 PASS、誤検出は仮想的

### DEF-BGH-06: Cluster G — `MSupplierOpeningBalance#addDateTime` を `@Generated(event = INSERT)` 化
- **元レビュー**: code G-impl-6 (Minor)
- **理由**: DB default 動作で問題は出ていない、entity 規約整理 Sprint で

### DEF-BGH-07: Cluster G — `MSupplierOpeningBalanceRepository#findByPkShopNoAndDelFlg` 削除 (YAGNI)
- **元レビュー**: design G-Minor-3
- **理由**: 動作影響なし

### DEF-BGH-08: Cluster G — `claudedocs/_spike_mf_opening_*.json` の repo 残存処理
- **元レビュー**: design G-Minor-6
- **理由**: 本人確認後に削除可否判断、別タスク

### DEF-BGH-09: Cluster G — `tryGetMfTrialBalanceClosing` のキャッシュ化 (毎 GET で MF API 叩く)
- **元レビュー**: code G-impl-8
- **理由**: validation API 失敗は warn ログのみで吸収されており実害なし、DB キャッシュ列追加 (DD-BGH-18) で本格対応

### DEF-BGH-10: Cluster H — `m_accounting_schedule` マスタ化 (締日・実施日のフロント分離)
- **元レビュー**: design H-Minor m-4
- **理由**: 短期は `frontend/lib/finance/workflow-schedule.ts` 分離で十分

### DEF-BGH-11: Cluster H — `accounting-workflow.tsx` 363 行のファイル分割
- **元レビュー**: design H-Minor m-5
- **理由**: 規約上限 800 行内、機能追加が積み重なってから

### DEF-BGH-12: Cluster H — `payment-mf-import` をステップ 4.5 として新設 (実装作業)
- **元レビュー**: design H-Minor m-1
- **理由**: DD-BGH-15 の方針確定後

### DEF-BGH-13: Cluster B/G/H 共通 — JVM 再起動を伴う Bean 追加系 (`@Cacheable` 設定 / `BatchJobCatalog` 配信 endpoint 等) のセットアップ
- **理由**: 個別 SAFE-FIX 内で扱うが、全体としての疎通確認は別 Sprint で

---

## ALREADY-RESOLVED (3 クラスター合算)

### AR-BGH-01: Cluster B — `MfClientMappingController#update`/`delete` の admin 限定は実装済
- **元レビュー**: design B-Critical-1 の一部 (write 系の update/delete)
- **対応箇所**: `MfClientMappingController.java:33-37` に `@PreAuthorize("authentication.principal.shopNo == 0")` 既存
- **背景**: 元指摘は「create だけ抜けている」点。Cluster F の認可ガード一斉点検で update/delete 側は既に統一済

### AR-BGH-02: Cluster B — `IllegalStateException` 422 化は `FinanceExceptionHandler` で既存統一
- **元レビュー**: design B-Minor m-1 (`MfTaxResolver` 例外の Controller 翻訳) の一部
- **対応箇所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java:52-57` で `@RestControllerAdvice(basePackages = "jp.co.oda32.api.finance")` + `IllegalStateException → 422 + 汎用メッセージ`
- **背景**: Cluster F SF-25 で導入済。SF-B10 で `MfTaxResolver` を `IllegalStateException` 化すれば自動的に 422 へ翻訳される

### AR-BGH-03: Cluster B — `IEntity` 規約・del_flg / add_date_time / modify_date_time 共通フィールド
- **元レビュー**: code B-Minor m-impl-3 のうち Hibernate Entity 規約部分
- **対応箇所**: `TCashbookImportHistory` / `MMfJournalRule` / `MMfClientMapping` は CLAUDE.md の `IEntity` 規約に準拠済
- **背景**: Cluster C SF-C13 のパターンと同一、追加対応不要

### AR-BGH-04: Cluster G — `LoginUserUtil.resolveEffectiveShopNo` による shop ガードパターンが既存
- **元レビュー**: design G-Major-4 の実装手段部分
- **対応箇所**: `backend/src/main/java/jp/co/oda32/domain/service/util/LoginUserUtil.java:40-55` (Cluster F C-N5 round 2 fix で fail-closed 化済)
- **背景**: SF-G04 で `SupplierOpeningBalanceController#list` に適用するだけで Cluster F と同パターンを再利用可能

### AR-BGH-05: Cluster G — `FinanceConstants` 集約パターンが既存 (Cluster D で先行)
- **元レビュー**: design G-Major-1 の実装手段部分
- **対応箇所**: `backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java` が `EXCLUDED_SUPPLIER_NO`, `ACCOUNTS_PAYABLE_SHOP_NO`, `MATCH_TOLERANCE` 等を集約
- **背景**: SF-G05 で `FinancePeriodConfig` を新設するときに同パターンを踏襲

### AR-BGH-06: Cluster G — Spring Batch 5.x スキーマ準拠 (`parameter_name` / `create_time`)
- **元レビュー**: 設計書 vs 実装の乖離調査時に確認
- **対応箇所**: CLAUDE.md 規約 (Spring Batch 5.x スキーマ) で既に統一
- **背景**: 別途対応不要

### AR-BGH-07: Cluster G — `@RequiredArgsConstructor` + final field によるコンストラクタ注入
- **元レビュー**: design G レビューチェックリスト DI 項目
- **対応箇所**: `MfOpeningBalanceService` / `SupplierBalancesService` / 関連 Controller 全て準拠済
- **背景**: 既に CLAUDE.md 規約準拠、追加対応不要

### AR-BGH-08: Cluster H — `BatchController` 系の `@PreAuthorize("hasRole('ADMIN')")` ガードは実装済
- **元レビュー**: code H-impl-4 (M-impl-4) のうち BatchController 側
- **対応箇所**: `BatchController.java:126, 143, 173` に既存
- **背景**: SF-H04 で同パターンを `getAccountingStatus` にも適用するだけ

### AR-BGH-09: Cluster H — Spring Batch `JobExplorer` 利用パターンが `BatchController#getJobStatus` で実装済
- **元レビュー**: code H-impl-1 (C-impl-1) のうち JobExplorer 側
- **対応箇所**: `BatchController.java:142-170` で `JobExplorer.findJobInstancesByJobName` 既存
- **背景**: SF-H05 で `AccountingStatusService` 側を JobExplorer 経由に統一する際にロジック流用可能

### AR-BGH-10: Cluster H — `IllegalStateException` 422 化が `FinanceController` 全体で統一済
- **元レビュー**: design H レビュー Spring Boot 観点 例外ハンドリング項目の一部
- **対応箇所**: `FinanceExceptionHandler` (Cluster F SF-25)
- **背景**: AR-BGH-02 と同根

---

## 並列実行プラン

3 クラスター合算で **4 グループ** に分割。ファイル衝突を回避するため、Cluster B/G/H 内でも同 Service / 同 ファイルを触る SAFE-FIX は同 group に集約する。

### グループ A: Critical / 認可・契約・業務バグ修正 (即時、サブエージェント1)
**触るファイル**: 認可・SQL・DTO 系。サブエージェントは backend 認可とビジネスロジック修正を一括対応
- SF-B01: `MfClientMappingController#create` admin 化
- SF-G01: `SupplierBalancesService.accumulateMfJournals` skip 追加
- SF-G02: `MSupplierOpeningBalance#@Generated` event 拡張
- SF-G03: `MfOpeningBalanceService` zombie row 復活
- SF-G04: `SupplierOpeningBalanceController#list` shop 権限
- SF-G08: `MfOpeningJournalDetector` util 新設 + 3 サービス置換
- SF-H01: `invoiceLatest` SQL ショップ別修正
- SF-H02: `accountsReceivableSummary` 追加 (backend 部分)
- SF-H04: `getAccountingStatus` admin 限定
- SF-H05: `BatchJobCatalog` 集約 + `JobExplorer` 統一 (大物)
- SF-H06: `AccountingStatusResponse` DTO 化 (SF-H05 とセット)
- SF-H07: `querySingle` silent failure 修正
- SF-H08: 既存 Repository 経由化 (SF-H05/H06 とセット)

**依存**: SF-G02 → SF-G07 (group C) は順序必須。SF-H05 → SF-H06 → SF-H08 は同 service なので 1 commit 推奨

### グループ B: 文書整合・定数集約 (サブエージェント2)
**触るファイル**: 設計書 + 新規 constant ファイル
- SF-B03: 設計書 §CSV 出力 CRLF 修正
- SF-B04: シード件数 18 件統一 (設計書 + SQL コメント)
- SF-B05: 設計書税リゾルバ表 PURCHASE_AUTO_WIDE 追加
- SF-G05: `FinancePeriodConfig` 新設 + 5 箇所統合
- SF-G10: 設計書 §7.2/§8.1/§5.4 整合
- SF-H12: 設計書 §7.5/§3.2 修正

**依存**: SF-G05 は SF-G02/G03/G07 (Group A/C) と独立、SF-G10 は DD-BGH-01 確定後

### グループ C: バックエンド Service ロジック修正 (サブエージェント3)
**触るファイル**: `CashBookConvertService.java`, `MfOpeningBalanceService.java`, `MfTaxResolver.java`
- SF-B06: `extractPeriodLabel` 波ダッシュ + period 不在ガード
- SF-B07: `selectSheet` フォールバック撤去
- SF-B08: `findRule` Comparator 分解
- SF-B10: `MfTaxResolver` を `IllegalStateException` 化
- SF-B11: `validateFile` 例外 400 翻訳
- SF-B12: `cleanExpired` ロック整合性
- SF-G06: `findById` N+1 を Map 化
- SF-G07: `getEffectiveBalance()` に統一 (SF-G02 後)

**依存**: SF-G07 は SF-G02 (Group A) 必須前提。`MfOpeningBalanceService.java` は SF-G03 (Group A) と SF-G06 (Group C) と SF-G07 (Group C) で同 ファイル → 順序付け必要 (Group A → Group C)

### グループ D: フロントエンド (サブエージェント4)
**触るファイル**: `frontend/` 配下
- SF-B02: `MfClientMappingsPage` admin gating
- SF-B09: `cashbook-import.tsx` 死 invalidate 削除 + rePreview gard
- SF-B13: `mf-cashbook.ts` literal union 型
- SF-G09: `parseAmount` ヘルパ + `supplier-opening-balance.tsx` 適用
- SF-H02: `accountsReceivableSummary` 追加 (frontend 部分)
- SF-H03: `useQuery` error/pending + refresh ボタン
- SF-H09: `m-impl-1/2/3` (date-fns)
- SF-H10: E2E `MOCK_STATUS` 型付け
- SF-H11: `m-impl-4` Tailwind theme

**依存**: SF-H10 は SF-H06 (Group A) 必須前提。SF-H02 は backend 側 SF-H02 と同期コミット推奨

### 同 ファイル衝突マトリクス
| ファイル | 触る SAFE-FIX |
|---|---|
| `CashBookConvertService.java` | SF-B06, SF-B07, SF-B08, SF-B11, SF-B12 (全部 Group C 内、シーケンシャル commit) |
| `MfOpeningBalanceService.java` | SF-G03 (A), SF-G06 (C), SF-G07 (C) → A → C 順 |
| `SupplierBalancesService.java` | SF-G01 (A) のみ |
| `AccountingStatusService.java` | SF-H01, SF-H05, SF-H07, SF-H08 (全部 Group A 内、SF-H05/H08 で大改修) |
| `accounting-workflow.tsx` | SF-H02, SF-H03, SF-H09, SF-H10, SF-H11 (全部 Group D 内、シーケンシャル commit) |
| `mf-client-mappings.tsx` | SF-B02 のみ |
| `cashbook-import.tsx` | SF-B09 のみ |
| `supplier-opening-balance.tsx` | SF-G09 のみ |
| `MfClientMappingController.java` | SF-B01 のみ |
| `MSupplierOpeningBalance.java` | SF-G02 のみ |
| `SupplierOpeningBalanceController.java` | SF-G04 のみ |
| `FinanceController.java` | SF-H04, SF-H06 (Group A 内シーケンシャル) |
| `MfTaxResolver.java` | SF-B10 のみ |
| 新規 `FinancePeriodConfig.java` | SF-G05 (Group B) |
| 新規 `BatchJobCatalog.java` | SF-H05 (Group A) |
| 新規 `MfOpeningJournalDetector.java` | SF-G08 (Group A) |
| 新規 `AccountingStatusResponse.java` | SF-H06 (Group A) |

ファイル衝突なし (Group A/B/C/D 並列実行可、ただし Group A 内で `AccountingStatusService` と `MfOpeningBalanceService` は順序付け commit)。

---

## 完了後の検証チェックリスト

1. `./gradlew compileJava` — Group A/B/C 完了後
2. `./gradlew test --tests *CashBookConvertServiceGoldenMasterTest` — SF-B06/B07/B08/B11/B12 後 (12 ゴールデンマスタ PASS 維持)
3. `./gradlew test --tests *MfOpeningBalanceService*` — SF-G02/G03/G06/G07/G08 後 (新規ユニットテスト追加)
4. `./gradlew test --tests *SupplierBalancesService*` — SF-G01 後 (二重計上 = 0 を assert する 3 ケース追加)
5. `./gradlew test --tests *MfOpeningJournalDetector*` — SF-G08 後 (4 ケース新規)
6. `npx tsc --noEmit` — Group D 完了後
7. 実バックエンド疎通 (MEMORY.md `feedback_incremental_review` ルール):
   - SF-B01: 一般ユーザで POST /api/v1/finance/mf-client-mappings → 403
   - SF-G01: `/finance/supplier-balances` 画面で MAJOR フラグが意味的に減少することを確認
   - SF-G04: 一般ユーザ shop=1 で `?shopNo=2` → 403
   - SF-H01: 複数店舗で異なる closing_date のレコードを投入 → 全店舗最新締日が個別表示
   - SF-H02: ステップ4 に `accountsReceivableSummary` chip が表示
   - SF-H04: 一般ユーザで GET /api/v1/finance/accounting-status → 403
8. JVM 再起動を伴う変更 (`@Cacheable` 設定 / `BatchJobCatalog` 配信 endpoint / `FinanceExceptionHandler` 経路変更) はユーザに再起動依頼を明示

---

## 参考: 引用元レビュー指摘 ID 一覧

### Cluster B (28 件)
- 設計: B-Critical-1 / B-Major-1〜6 / B-Minor m-1〜5
- コード: B-Critical-impl-1, impl-2 / B-Major-impl-1〜6 / B-Minor m-impl-1〜8

### Cluster G (23 件)
- 設計: G-Critical-1, 2 / G-Major-1〜5 / G-Minor-1〜6
- コード: G-Critical-impl-1 / G-Major-impl-2〜5 / G-Minor m-impl-6〜10

### Cluster H (26 件)
- 設計: H-Critical-1 (C-1), 2 (C-2) / H-Major M-1〜7 / H-Minor m-1〜6
- コード: H-Critical-impl-1 / H-Major-impl-1〜4 / H-Minor m-impl-1〜6
