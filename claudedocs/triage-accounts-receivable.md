# Triage: 売掛金 (Cluster E) 修正対応

triage 日: 2026-05-04
対象指摘総数: 約 53 件 (設計 16 + コード 24 + Codex 13)
出典:
- design-review-accounts-receivable.md (Critical 2 / Major 8 / Minor 6)
- code-review-accounts-receivable.md (Critical 3 / Major 5 / Minor 8 + 追加 8)
- codex-adversarial-accounts-receivable.md (Critical 3 / Major 10)

## サマリー
| 分類 | 件数 |
|---|---|
| SAFE-FIX | 22 件 |
| DESIGN-DECISION | 17 件 |
| DEFER | 8 件 |
| ALREADY-RESOLVED | 6 件 |

優先度概観:
- **Critical 系で SAFE-FIX 化できたもの: 4 件**
  - C-1 (markExported 焼付け): 部分対応 → SF-E01 (saveAll + 0件 422 ガード) と SF-E02 (markExported 常時上書き) で実装側の二重書き込み窓を閉じる。`StreamingResponseBody` への切替は DDE-01 で判断
  - C-2 (Embeddable PK 列名 camelCase 誤宣言): SF-E03 で snake_case に修正
  - C-impl-1 (markExported 片側更新): SF-E02 で常時上書きに変更
  - C-impl-3 (FileWriter プラットフォーム既定 charset): SF-E04 で `OutputStreamWriter(CP932)` 化
- **Cluster D 対称適用で SAFE-FIX 化できたもの: 4 件**
  - SF-E07 (FinanceConstants.MATCH_TOLERANCE 参照) — Cluster D SF-11 の対称展開
  - SF-E08 (FinanceExceptionHandler への ResponseStatusException 委譲) — Cluster F SF-25 の対称展開
  - SF-E09 (AssertShopAccess を `bulkVerify` にも適用) — Cluster A SF-02/03 の対称展開
  - SF-E11 (PartnerService bulk fetch) — `mPaymentSupplierService.findAllByPaymentSupplierNos` パターン (Cluster D) の対称展開
- **DESIGN-DECISION** は Codex 由来の業務モデル変更 (台帳/履歴責務分離、入金消込テーブル新設、締め日履歴、訂正請求 immutable) と、Opus 設計レビューの「auto-reconcile UX」「verifiedManually 副作用整理」「verify API の verificationResult 強制」等
- **DEFER** は監査証跡基盤 (Cluster F 軸) との統合実装、Sales Journal export_lot/line 永続化、AR ライフサイクル分解 等の長期課題
- **ALREADY-RESOLVED** は Cluster D/F/A の修正で間接対応された 6 件

---

## SAFE-FIX (即適用)

### SF-E01: `exportMfCsv` の 0 件 422 ガード + `saveAll` 一括保存 (Critical 業務影響)
- **元レビュー**: design-review C-1 (修正案 2/3) / S-6 / S-11
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:277-300`
- **修正内容**:
  1. `summaries.isEmpty()` のとき 422 で abort (買掛側 `FinanceController#exportPurchaseJournalCsv` と動作対称化)
  2. `markExported` ループの `for-each summaryService.save(s)` を `summaryService.saveAll(summaries)` 1 tx に置換
- **想定影響範囲**: 1 ファイル (Controller) + `TAccountsReceivableSummaryService` に `saveAll` が無ければ追加
- **テスト確認**: `./gradlew compileJava` + 0 件期間で 422 が返ることを curl で確認
- **依存関係**: なし (StreamingResponseBody 対応 = DDE-01 とは独立)
- **担当推奨**: グループ A "Critical 認可・契約修正"

### SF-E02: `markExported` を常時上書きに変更 (Critical 経年バグ)
- **元レビュー**: code-review C-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/SalesJournalCsvService.java:227-236`
- **修正内容**:
  ```java
  public void markExported(List<TAccountsReceivableSummary> summaries) {
      for (TAccountsReceivableSummary s : summaries) {
          if (s.getTaxIncludedAmountChange() != null) {
              s.setTaxIncludedAmount(s.getTaxIncludedAmountChange());
          }
          if (s.getTaxExcludedAmountChange() != null) {
              s.setTaxExcludedAmount(s.getTaxExcludedAmountChange());
          }
      }
  }
  ```
  null guard を **`*_change` 側だけ** に残し、`*_amount` 既存値による no-op を解消。再 DL 時に最新 `*_change` で必ず更新される
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew test` + 既存 PASS。手動確定行 (verifiedManually=true) は Tasklet/Service の `findByDateRangeAndMfExportEnabled` がそのまま含めるが、`*_change` も同期されているはずなので結果は同じ。万一不安なら `verifiedManually=true` をスキップする条件を追記
- **依存関係**: DDE-02 (二段焼き解消の方針 = `applyMatched` 側で焼かない選択) を採用するなら本 SAFE-FIX は不要化。当面、二段焼きは残したまま markExported の null guard だけ撤去
- **担当推奨**: グループ A

### SF-E03: `TAccountsReceivableSummaryPK#transactionMonth` の `@Column(name)` を snake_case に修正 (Critical 地雷除去)
- **元レビュー**: design-review C-2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/model/embeddable/TAccountsReceivableSummaryPK.java:30-31`
- **修正内容**: `@Column(name = "transactionMonth")` を `@Column(name = "transaction_month")` に修正。`isOtakeGarbageBag` は `is_otake_garbage_bag` で正しいので変更不要
- **想定影響範囲**: 1 ファイル (PK)。現在 `@IdClass` のため動作差は無いが、将来 `@EmbeddedId` 移行・Specification での Embeddable path 参照で動かなくなる地雷を除去
- **テスト確認**: `./gradlew test` (動作変化なし)
- **依存関係**: なし (`AccountsPayableSummaryPK` も同様の問題が無いか確認 → 別 cluster で triage 済)
- **担当推奨**: グループ A

### SF-E04: `AccountsReceivableToSalesJournalTasklet` の `FileWriter` を `OutputStreamWriter(CP932)` に変更 (Critical 環境依存)
- **元レビュー**: code-review C-impl-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsReceivableToSalesJournalTasklet.java:74,81`
- **修正内容**:
  ```java
  try (OutputStreamWriter writer = new OutputStreamWriter(
          new FileOutputStream(fileName), Charset.forName("windows-31j"))) {
      salesJournalCsvService.writeCsv(summaries, writer, initialTransactionNo);
  }
  ```
  Controller 側 (`AccountsReceivableController.java:68` の `CP932` 定数) を共通化したい場合は `SalesJournalCsvService` 内に `public static final Charset CP932` を持たせ、Controller も Tasklet も参照
- **想定影響範囲**: 1 ファイル + 共通化で 2 ファイル
- **テスト確認**: `./gradlew test` + 本番 (Linux 想定) で CSV 中の日本語列が CP932 で出力されることを `file -i` で確認
- **依存関係**: なし
- **担当推奨**: グループ A

### SF-E05: `bulkVerify` を `@Transactional` 化して部分コミット破綻を解消 (Critical 業務影響)
- **元レビュー**: design-review M-2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:171-209`
- **修正内容**:
  - Controller 側に `@Transactional` を付ける、または `BulkVerifyService` を新設して全工程を 1 tx に閉じる
  - `Reconciler` の `@Transactional` (`Propagation.REQUIRED` デフォルト) はそのまま親 tx に乗る
  - 推奨: `BulkVerifyService` 切出し (`reconcile + reload + verify + saveAll` を 1 メソッド) で Controller 薄化と両立
- **想定影響範囲**: 1 Controller (薄化) + 1 Service 新規 1 ファイル
- **テスト確認**: `./gradlew test` + bulkVerify 中途エラー時に Reconciler 結果も rollback されることを integration test で確認
- **依存関係**: M-impl-1 (BatchJobLauncherService 切出し / DDE-04) と方向性が同じ
- **担当推奨**: グループ A

### SF-E06: `bulkVerify` の `fromDate > toDate` を 400 で弾く
- **元レビュー**: code-review A-6
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:171-180`
- **修正内容**: `if (request.getFromDate().isAfter(request.getToDate())) throw new ResponseStatusException(BAD_REQUEST, ...);` 追加。`exportMfCsv` (L274-276) と同じパターン
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + curl で 400 確認
- **依存関係**: なし
- **担当推奨**: グループ A

### SF-E07: `InvoiceVerifier#invoiceAmountTolerance` フォールバック値を `FinanceConstants` 参照に統一 (Cluster D 対称展開)
- **元レビュー**: design-review S-9 派生 / Cluster D SF-11 対称
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java` (`@Value("${batch.accounts-receivable.invoice-amount-tolerance:3}")` 周辺) + `backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java`
- **修正内容**: `FinanceConstants` に `INVOICE_AMOUNT_TOLERANCE_DEFAULT = BigDecimal.valueOf(3)` を追加。`@Value` のデフォルト値を文字列リテラル "3" → SpEL `${batch.accounts-receivable.invoice-amount-tolerance:#{T(jp.co.oda32.constant.FinanceConstants).INVOICE_AMOUNT_TOLERANCE_DEFAULT}}` に変更 (定数集約強制が複雑な場合は `FinanceConstants.INVOICE_AMOUNT_TOLERANCE_DEFAULT` を Javadoc で参照する形に留める)
- **想定影響範囲**: 1 Service + 1 定数
- **テスト確認**: `./gradlew test`
- **依存関係**: なし
- **担当推奨**: グループ B "コード品質"

### SF-E08: Controller の `ResponseStatusException` (400/422) を `IllegalArgumentException` 委譲化 (Cluster F 対称展開)
- **元レビュー**: code-review A-2 関連 / Cluster F SF-25 対称
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:122-137, 271-276`
- **修正内容**: `aggregate` の `ResponseEntity.badRequest().body(Map.of("message", ...))` 路と `exportMfCsv` の `ResponseStatusException(BAD_REQUEST, ...)` を、Cluster F の方針に合わせて整理:
  - 業務メッセージを残したい validation は `throw new IllegalArgumentException(message)` + `GlobalExceptionHandler` で 400 ハンドル (Cluster C M1 で指摘された業務メッセージ消失問題を回避)
  - `IllegalStateException` (内部状態異常) は `FinanceExceptionHandler#handleIllegalState` で 422 + 汎用メッセージ
  - `ResponseStatusException` 自体は Spring MVC 既定のレンダリングなので互換維持
- **想定影響範囲**: 1 Controller。aggregate の Map<String,Object> response (A-2) は別途 DTO 化 (DDE-09 候補)
- **テスト確認**: `./gradlew compileJava` + 400 系の response body 構造をフロントで確認
- **依存関係**: Cluster F SF-25 (FinanceExceptionHandler) が commit 済 (確認済)
- **担当推奨**: グループ B

### SF-E09: `bulkVerify` に `assertShopAccess(request.getShopNo())` を追加 (Cluster A 対称展開)
- **元レビュー**: code-review A-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:171-180`
- **修正内容**: `verify`/`releaseManualLock`/`toggleMfExport` で使われている `assertShopAccess` を `bulkVerify` でも request.shopNo に対して呼出。admin が `shopNo=null` を投げた場合のフォールバック (effectiveShopNo=null = 全店舗) は `LoginUserUtil.resolveEffectiveShopNo` の現行ロジックに従う (admin の意図的全店舗操作)。一般ユーザの shop 漏出を防ぐためのガードレール
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 一般ユーザで他店舗 shopNo を投げると 403 が返ることを確認
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-E10: `aggregate` の `cutoffType` 文字列 enum 化 / tasklet 定数を共有 (Cluster D 対称: マジックリテラル排除)
- **元レビュー**: code-review M-impl-2
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:130-137`
  - `backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java:63-66`
  - `backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableAggregateRequest.java:18`
- **修正内容**: `enum CutoffType { ALL("all"), DAY_15("15"), DAY_20("20"), MONTH_END("month_end") }` を `jp.co.oda32.domain.model.finance` に新設。`AccountsReceivableAggregateRequest.cutoffType: String` のまま受けて Service 層で enum 変換 (Spring Converter は overkill)。Tasklet の `CUTOFF_TYPE_*` 定数は enum 移行
- **想定影響範囲**: 3 ファイル + 1 enum 新規
- **テスト確認**: `./gradlew test` (既存 cutoffType 値テスト)
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-E11: `MPartnerService.findAllByPartnerNos(Set<Integer>)` 追加して `list` API の N+1 解消 (Cluster D 対称展開)
- **元レビュー**: design-review M-7 / S-7
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/service/master/MPartnerService.java`
  - `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:96-104`
- **修正内容**:
  - `MPartnerService` に `findAllByPartnerNos(Set<Integer> partnerNos): List<MPartner>` を追加 (Cluster D の `mPaymentSupplierService.findAllByPaymentSupplierNos` パターン対称)
  - Controller `list` の `partnerNos.stream().map(mPartnerService::getByPartnerNo)...` を `findAllByPartnerNos(partnerNos)` の bulk fetch + Map 化に置換
- **想定影響範囲**: 1 Service + 1 Controller
- **テスト確認**: `./gradlew compileJava` + 一覧 API のレスポンスタイム改善確認
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-E12: `TAccountsReceivableSummaryTasklet` の二重例外ハンドリング撤去
- **元レビュー**: code-review M-impl-5
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java:112-116`
- **修正内容**: `try/catch (Exception e) { contribution.setExitStatus(FAILED); throw new RuntimeException(...) }` を撤去し、tasklet 内例外をそのまま throw。Spring Batch framework が `ExitStatus.FAILED` を自動セット
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew test` + tasklet 失敗時の Job ステータスを確認
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-E13: `AccountsReceivableCutoffReconciler` の OTAKE ゴミ袋 goods_code を Tasklet と統一定数化 (Major 業務影響)
- **元レビュー**: design-review M-8 (追加発見)
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java:55-60` (`00100001/3/5/00100101/103/105`)
  - `backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsReceivableCutoffReconciler.java:60-64` (`00100001/3/5/00100007/9/11`)
- **修正内容**: 両者で異なるリストを業務確認のうえ、正しい方に統一して `FinanceConstants.OTAKE_GARBAGE_BAG_GOODS_CODES` (もしくは `AccountsReceivableConstants` 新設) に集約。両 class はそこを参照
  - **業務確認必須**: どちらが正か (`00100007/9/11` vs `00100101/103/105`)。ヒアリングするまでは triage → DESIGN-DECISION 寄りだが、定数集約自体は SAFE
  - **暫定措置**: Cluster A の確認後に commit。それまでは「両者の和集合」を共通定数に入れる選択も可 (誤陽性は副作用が小さい / 一致する商品が多い側を保守的に採用)
- **想定影響範囲**: 2 ファイル + 1 定数
- **テスト確認**: `./gradlew test` + 該当 goods_code を含む partner で AR の `is_otake_garbage_bag` 値を確認
- **依存関係**: 業務確認 → 完了後 SAFE-FIX、未完了の間は DESIGN-DECISION (DDE-12)
- **担当推奨**: グループ A (Critical 隣接、業務影響大) ※業務確認完了後

### SF-E14: `AccountsReceivableVerifyRequest` の `mfExportEnabled` field initializer 撤去
- **元レビュー**: code-review m-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableVerifyRequest.java:24`
- **修正内容**: `private Boolean mfExportEnabled = Boolean.TRUE;` の initializer を削除。Controller 側 (L227) の `request.getMfExportEnabled() != null ? ... : true` フォールバックに一本化。Javadoc に「省略時 true」を明記
- **想定影響範囲**: 1 DTO
- **テスト確認**: `./gradlew test` + 既存 verify エンドポイントの動作確認 (省略時 true は維持)
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-E15: CSV エンコーディング HTTP ヘッダを `Windows-31J` に統一
- **元レビュー**: design-review m-5
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:297`
- **修正内容**: `MediaType.parseMediaType("text/csv; charset=Shift_JIS")` を `"text/csv; charset=Windows-31J"` に変更 (実体 charset と一致)。買掛側 `FinanceController` の CP932 定義と一本化したい場合は SF-E04 と合わせて実施
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew test` + curl で `Content-Type` ヘッダ確認
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-E16: `defaultDateRange` の境界判定を `< 20` から `<= 20` に変更
- **元レビュー**: code-review m-impl-3
- **対象ファイル**: `frontend/types/accounts-receivable.ts:97-103`
- **修正内容**: `if (d.getDate() < 20)` を `if (d.getDate() <= 20)` に変更。20 日当日に画面を開いたら「当月締め」期間 (前月21日〜当月20日) を初期表示
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + 20 日当日 / 21 日 / 19 日の挙動確認
- **依存関係**: なし
- **担当推奨**: グループ E "フロント"

### SF-E17: `pkToPath` の `taxRate` を `Number(rate).toFixed(2)` で正規化
- **元レビュー**: code-review m-impl-2
- **対象ファイル**: `frontend/types/accounts-receivable.ts:117`
- **修正内容**: `${ar.taxRate}` を `${Number(ar.taxRate).toFixed(2)}` に変更。バックエンド `BigDecimal taxRate` の `scale=2` と URL 表現を揃え、scale 違いの行が複数あった場合の取違いを防止
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + verify/release/toggle の 3 endpoint で taxRate 8.00 を含む行を操作確認
- **依存関係**: なし
- **担当推奨**: グループ E

### SF-E18: `paymentTypeLabel` ユーティリティを `frontend/lib/payment-type.ts` に集約
- **元レビュー**: code-review m-impl-7
- **対象ファイル**:
  - 新規: `frontend/lib/payment-type.ts`
  - `frontend/components/pages/finance/accounts-receivable.tsx:576-581`
  - `frontend/components/pages/finance/accounts-payable.tsx` (買掛側、同パターンか確認のうえ統合)
- **修正内容**: `paymentTypeLabel(cutoff: number | null): string` を `frontend/lib/payment-type.ts` に集約 (`null → '-'`, `0 → '月末'`, `-1 → '都度現金'`, それ以外 → `${n}日`)。買掛側の同等関数があれば統合
- **想定影響範囲**: 1 ユーティリティ新規 + 2 page
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: グループ E

### SF-E19: `AccountsReceivableVerifyDialog` の初期値出典 helper text 表示
- **元レビュー**: code-review m-impl-5
- **対象ファイル**: `frontend/components/pages/finance/AccountsReceivableVerifyDialog.tsx:58-61`
- **修正内容**: `defaultInc` の出典 (`invoiceAmount` / `taxIncludedAmount` / `taxIncludedAmountChange` / 0) を表示するための補助表示を入力欄下に追加 (例: 「初期値は請求書金額から取得しました」)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + 各ケースのダイアログ表示確認
- **依存関係**: なし
- **担当推奨**: グループ E

### SF-E20: `verifyMutation.mutate` の PathVariable を `encodeURIComponent` でラップ
- **元レビュー**: code-review A-8
- **対象ファイル**:
  - `frontend/types/accounts-receivable.ts:117`
  - `frontend/components/pages/finance/accounts-receivable.tsx:138`
- **修正内容**: `pkToPath` 内の各セグメントを `encodeURIComponent` でラップ。現状 `transactionMonth` は `yyyy-MM-dd` 形式で URL 安全だが、将来の形式変更耐性として
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + 既存動作確認
- **依存関係**: なし
- **担当推奨**: グループ E

### SF-E21: 設計書 `design-accounts-receivable-mf.md` に Reconciler / Auto-reconcile / 二段焼きを追記
- **元レビュー**: design-review D-1 / D-2 / D-3 / D-5
- **対象ファイル**: `claudedocs/design-accounts-receivable-mf.md`
- **修正内容**:
  - §5.x に「`AccountsReceivableCutoffReconciler` の存在・目的・自動起動 (`bulkVerify` 内)」セクション追加
  - §9.5 `release-manual-lock` の挙動 (verifiedManually=false のみ書き換え、`*_amount` は据え置き) を明記
  - §5.3 「`*_amount = *_change` 焼付けは `applyMatched` (検証時) と `markExported` (CSV DL 時) の二段」を明記し、SF-E02 で「常時上書き」に統一する旨を追記
  - §13 R2 「CSV DL のメモリ使用量」を「現状 `ByteArrayOutputStream` 全量バッファ。`StreamingResponseBody` 化は DDE-01 で判断」と更新
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: なし (doc 更新のみ)
- **依存関係**: なし
- **担当推奨**: グループ G "設計書整合"

### SF-E22: `TAccountsReceivableSummary.java` / `TAccountsReceivableSummaryRepository.java` のクラスコメント「20日締め売掛金テーブル」修正
- **元レビュー**: design-review チェックリスト 11
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java:15`
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TAccountsReceivableSummaryRepository.java:14`
  - `backend/src/main/java/jp/co/oda32/domain/model/embeddable/TAccountsReceivableSummaryPK.java:15`
- **修正内容**: 「20日締め売掛金テーブル」を「売掛金集計テーブル (15/20/月末締め混在)」に修正
- **想定影響範囲**: 3 ファイル (Javadoc のみ)
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: グループ B

---

## DESIGN-DECISION (要ユーザー判断)

### DDE-01: CSV DL 成功確定後に `markExported` する `StreamingResponseBody` 化 (Critical / 業務影響大)
- **元レビュー**: design-review C-1 (修正案 1) / D-5
- **論点**: SF-E01 (0件 422 + saveAll) で「サーバ側で CSV を完成させてから一気にマーク」までは閉じるが、ブラウザがネットワーク切断・5xx 経由で受け取り損ねた場合、サーバ側はマーク済 → 再 DL で凍結値出力の窓は残る
- **選択肢**:
  - A: `StreamingResponseBody` で書き出し callback 完了後に `markExported` を呼ぶ (HTTP 200 確定保証)
  - B: 現状維持 (SF-E02 で markExported を常時上書きにすれば再 DL 時に最新値出力されるため業務影響小)
  - C: `markExported` を撤廃し、CSV DL は読取専用で運用する (Codex 6 の export_lot 案と統合)
- **影響範囲**: A は 1 Controller、C は新テーブル + Service
- **推奨**: **A** (中期解) もしくは Codex 6 採用なら C。当面 SF-E02 で症状緩和し、A の実装は別 Sprint
- **マージ可否**: A は中規模、当面は SF-E01 + SF-E02 で短期回避

### DDE-02: `applyMatched` の `tax_included_amount` 焼付けと `markExported` の関係を一本化 (Critical / 設計矛盾)
- **元レビュー**: code-review C-impl-1 (修正案 2) / design-review D-3
- **論点**: 一致行は `applyMatched` で `*_amount = *_change` 焼付け済 → `markExported` は `*_change != null` の追加保険。二段焼きの設計意図が不明瞭
- **選択肢**:
  - A: `applyMatched` で焼くのを撤去 (`InvoiceVerifier.java:353-354` 削除) → CSV DL 時に markExported のみで一段焼き
  - B: 現状の二段焼きを維持 + 設計書 §5.3 に「検証一致時は確定 / CSV 出力時に再確定」を仕様化
  - C: 焼付けを廃止し、CSV DL は読取専用 (DDE-01 C と統合)
- **影響範囲**: A は `InvoiceVerifier` + `applyMatched` 関連テスト。整合性レポート (Cluster D) は `*_amount` を「確定値」として参照しているため Cluster D との整合確認必須
- **推奨**: **B** (現状維持 + 仕様化) が破壊的変更を回避。SF-E02 で markExported の片側更新は解消済
- **マージ可否**: A は破壊的、B は SAFE-FIX (SF-E21 で部分対応)

### DDE-03: AR テーブルを「現在値キャッシュ」から「台帳/履歴」モデルに分離 (Codex 1 / Critical / 大規模スキーマ変更)
- **元レビュー**: Codex 1 (Opus 見落とし最重要 #1 派生)
- **論点**: 集計値 / 検証結果 / 手動確定値 / MF出力可否 / 突合結果が `t_accounts_receivable_summary` 1 行に圧縮 → 「いつ誰がなぜ MF 出力対象にしたか」を時系列復元できない
- **選択肢**:
  - A: `t_ar_verification_event` (検証イベント履歴) + `t_ar_export_lot` + `t_ar_export_line` を新設し、summary は派生ビュー化 (大規模)
  - B: 軸 F (audit trail) と統合し、`history_*` テーブルを Entity Listener で自動記録 (Cluster F の DDC-03 / DDF 系と同じ方針)
  - C: 現状維持 + `verification_note` 自由テキストで運用カバー
- **影響範囲**: A は AR モデル全面再設計、B は Cluster F の audit trail 基盤と統合実装
- **推奨**: **B** (Cluster F の audit trail と統合実装) が中期解。当面 C
- **マージ可否**: A/B は機能追加 → SAFE-FIX 化不可、Cluster F 軸 F の方針確定後に判断

### DDE-04: 入金消込テーブル `t_receipt` / `t_receipt_allocation` の新設 (Codex 8 / Critical / 業務スコープ拡張)
- **元レビュー**: Codex 8 (Opus 見落とし最重要 #1)
- **論点**: `t_invoice.totalPayment` のみで「入金額がどの請求書・どの売掛明細に充当されたか」が追跡不可。複数請求一括入金、手数料控除、過入金、相殺で回収状況を説明できない
- **選択肢**:
  - A: `t_receipt` (入金実体) + `t_receipt_allocation` (請求書按分) を新設し、入金 CSV 取込 + 手動調整 UI を実装
  - B: スコープ外として明示 (現状維持)、設計書に「Phase 1 では売上発生額のみ追跡、入金消込は Phase 2」を明記
- **影響範囲**: A は新機能、B は設計書 1 ファイル
- **推奨**: **B** (Phase 1 はスコープ外を明示) → Phase 2 として A を計画
- **マージ可否**: A は新機能 SAFE-FIX 化不可

### DDE-05: 残高管理 (前月残 + 当月売上 - 入金 = 繰越) を AR に組み込むか (Codex 2 / Critical)
- **元レビュー**: Codex 2 (Opus 見落とし最重要 #2)
- **論点**: 「売掛金一覧」の名称に反し、`InvoiceVerifier` は `netSalesIncludingTax` (発生額) のみ突合。`previousBalance` `carryOverBalance` を使った残高検証なし
- **選択肢**:
  - A: 売上仕訳連携と売掛残高検証を別画面/別 batch に分離。AR 一覧は「売上発生額の検証」と明示、別途 `/finance/accounts-receivable-balance` で残高ビュー
  - B: AR の summary に `opening_balance` / `closing_balance` 列を追加し検証ロジックを残高にも展開
  - C: 現状維持 + 画面ラベルを「売上仕訳一覧 (検証用)」に変更
- **影響範囲**: A は新画面、B は schema 変更
- **推奨**: **A** (責務分離が明快、Cluster D の supplier 累積残 (`feature-supplier-balances-health.md`) の AR 版相当)
- **マージ可否**: A/B は新機能、C は SAFE-FIX (ラベル変更のみ) だが業務的に違和感

### DDE-06: 締め日履歴モデル `m_partner_billing_terms_history` の導入 (Codex 4)
- **元レビュー**: Codex 4
- **論点**: `m_partner.cutoff_date` は「現在値」のみ → 過去月再集計時に締め日変更前の条件で計算できない
- **選択肢**:
  - A: `m_partner_billing_terms_history (partner_no, valid_from, valid_to, cutoff_date, payment_type)` 新設、Tasklet/Reconciler は対象期間日時点の条件を引く
  - B: 現状維持 (`Reconciler` が請求書 closing_date に寄せて救済)
  - C: 設計書に「過去月の締め日変更は対象外」と運用ルール明記
- **推奨**: B + C (Reconciler は救済策として残し、設計書で明示)。A は将来の正規モデル

### DDE-07: `mfExportEnabled` の状態遷移を `export_decision_status` enum に分離 (Codex 5)
- **元レビュー**: Codex 5 / design-review M-4
- **論点**: `mfExportEnabled` boolean は「自動一致 / 手動 / 特殊運用 / 再集計 OFF」を全て同じ true/false に潰す
- **選択肢**:
  - A: `export_decision_status enum (AUTO_MATCHED, MANUAL_APPROVED, SPECIAL_RULE_APPROVED, BLOCKED_MISMATCH, AUTO_RESET)` + `export_decision_reason` を追加
  - B: 現状維持 + クリーンラボ等の特殊 partner を `m_partner.always_export_to_mf` で設定駆動 (M-4 修正案)
  - C: 現状維持 + 設計書に既存仕様を明記
- **推奨**: **B** (クリーンラボ案件を `m_partner` で吸収、`updateMfExport` から特殊副作用を切り離す) が短期解。A は中期で audit trail と統合
- **マージ可否**: B は schema 変更 + Service refactor で SAFE-FIX 化困難 → DESIGN-DECISION

### DDE-08: 訂正請求の immutable + adjustment モデル (Codex 7)
- **元レビュー**: Codex 7
- **論点**: 締め後訂正・返品・値引が来たとき「過去月を更新する」現行モデルでは監査困難
- **選択肢**:
  - A: 締め済み期間は immutable にし、訂正は `t_ar_adjustment` 別明細で表現
  - B: 現状維持 (再集計で `*_change` を更新)
  - C: 設計書 §13 リスクに記載
- **推奨**: A は中期、当面 C

### DDE-09: `aggregate` API レスポンスの `Map<String, Object>` を専用 DTO 化
- **元レビュー**: code-review A-2
- **論点**: `Map.of("status", "STARTED", ...)` は型契約が無く、フロント側 `AggregateResponse` 型と乖離するリスク
- **選択肢**:
  - A: `AccountsReceivableAggregateResponse` DTO 新設
  - B: 現状維持
- **推奨**: **A** (SAFE 寄りだが、フロント側との型同期が必要なので DESIGN-DECISION 扱い)

### DDE-10: `verify` API の `verificationResult=1` 強制 (M-3)
- **元レビュー**: design-review M-3
- **論点**: 手動確定で `verificationResult=1` を強制すると、業務都合で「不一致だが MF に出す」運用ケースが「自動一致」と区別できなくなる
- **選択肢**:
  - A: Request DTO に `verificationResult` を追加 + UI で「一致/不一致/業務確定」を選択
  - B: `verifiedManually=true` のときは UI バッジを「手動」(青) に分岐し、`verificationResult` の意味を「自動検証結果」に限定 (現行値変更なし)
  - C: 現状維持 + 設計書 §9.4 に「手動確定 = 一致扱い」を明記
- **推奨**: **B** (UI 側で見え方を変えて誤解を防ぐ)。A は中期

### DDE-11: `applyMatched` の差額調整痕跡 (M-5)
- **元レビュー**: design-review M-5
- **論点**: 許容誤差内の按分で `verificationDifference` が 0 になり、「3円差額調整したこと」が運用者に見えない
- **選択肢**:
  - A: 元値 (`*_change` のオリジナル) を持ち回って `verificationDifference = 按分後請求書 - 按分前 AR` を記録
  - B: `verificationNote` に自動メモ「許容誤差 -2円 自動吸収」を追加
  - C: 現状維持
- **推奨**: **B** (note 自動記録は SAFE 寄りで監査痕跡を確保)

### DDE-12: ゴミ袋 goods_code リスト 業務確認 (M-8)
- **元レビュー**: design-review M-8 (追加発見)
- **論点**: tasklet (`00100101/103/105`) と reconciler (`00100007/9/11`) のどちらが正か業務ヒアリング必要
- **選択肢**: ヒアリング → SF-E13 で統一定数化 (本 triage 内 SAFE-FIX 化済)
- **推奨**: 業務確認後 SF-E13 を実施

### DDE-13: 上様 (999999) の集計 0 円ハンドリング (M-6)
- **元レビュー**: design-review M-6
- **論点**: 上様 partner の集計が 0 円のまま `applyMatched` で確定 → 請求書金額分の売上が MF 連携されない
- **選択肢**:
  - A: `totalTaxIncluded == 0` のとき `applyNotFound` 相当 (`mfExportEnabled=false`) に倒す
  - B: 現状維持 + log.error のみ
- **推奨**: **A** (業務影響大なので SAFE 寄りだが、上様 partner 運用フローの確認必要)

### DDE-14: PathVariable の `boolean` 仕様変更 (M-impl-3)
- **元レビュー**: code-review M-impl-3
- **論点**: `/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/verify` の boolean PathVariable は REST アンチパターン (5 階層も深い)
- **選択肢**:
  - A: クエリパラメータ `?isOtakeGarbageBag=true` に変更、PK を 4 階層に減らす
  - B: 単一複合キー文字列 `/verify/{compositeKey}` 形式へ
  - C: 現状維持
- **推奨**: **A** (フロント `pkToPath` 一括変更が必要 → 互換性検討要)
- **マージ可否**: フロント・バックエンド両方の change で SAFE-FIX 化困難

### DDE-15: `BatchJobLauncherService` 切出し (M-impl-1)
- **元レビュー**: code-review M-impl-1
- **論点**: Controller が `JobLauncher` / `ApplicationContext` / `ThreadPoolTaskExecutor` を直接 DI して 30 行のジョブ起動コードを組み立て → CLAUDE.md `Controller は薄く` ルール違反。買掛側にも同パターン
- **選択肢**:
  - A: `BatchJobLauncherService.launchAccountsReceivableSummary(targetDate, cutoffType)` を新設、買掛側 (FinanceController) も対称化
  - B: 現状維持
- **推奨**: **A** (Cluster D とまとめて refactor) → SF-E05 (BulkVerifyService) と並走

### DDE-16: B-CART / shop=1 統合 vs AR 集計キー (Codex 10)
- **元レビュー**: Codex 10
- **論点**: クリーンラボ (`partnerCode=301491`) のみ shop=1 強制 → 第1事業部得意先の B-CART 注文が shop=1 統合される運用が増えると個別特殊コード管理は破綻
- **選択肢**:
  - A: `billing_shop_no` / `source_shop_no` / `accounting_shop_no` を分けるマッピング導入
  - B: 現状維持 + 統合得意先が増えたら都度判定追加
  - C: `m_billing_unit` 請求単位マスタを新設し、特殊得意先コードを廃止
- **推奨**: **B** (運用観察) → C は中期

### DDE-17: bulkVerify の非同期ジョブ化 (Codex 11) / Reconciler の独立化 (Codex 12)
- **元レビュー**: Codex 11 / Codex 12
- **論点**: `bulkVerify` を HTTP 同期で実行 → 実行者・対象条件・件数サマリが永続化されない。Reconciler は「締め確定」操作として独立すべき
- **選択肢**:
  - A: `t_ar_verification_job` テーブル新設 + 非同期化、Reconciler を `/cutoff-confirm` 別 endpoint に分離
  - B: 現状維持
- **推奨**: B (現状運用で支障少なし)。Cluster F audit trail 統合時に再評価

---

## DEFER (将来課題)

### DEF-E01: `summary()` の全件ロード集計を JPQL `count(case when)` / `sum(case when)` 化 (m-1, m-impl-6)
- **元レビュー**: design-review m-1 / code-review m-impl-6
- **理由**: 月次数千行の規模では問題化していない。最適化リファクタ時に test 追加と合わせて対応

### DEF-E02: `InvoiceVerifierTest` / `AccountsReceivableCutoffReconcilerTest` 単体テスト追加 (m-6, m-impl-6)
- **元レビュー**: design-review m-6 / code-review m-impl-6
- **理由**: 設計書 §11.1 の完了条件として残置。8 ケース最低限実装すべきだが、Cluster A の重要度が下がっているため別 Sprint

### DEF-E03: `MPartner.cutoffDate` を `PartnerIndex` に持たせて Reconciler の検索順序を「マスタ優先」に変更 (M-impl-4)
- **元レビュー**: code-review M-impl-4
- **理由**: 現状の「末→20→15」順は誤マッチリスクあるが、運用観察で問題なし

### DEF-E04: `aggregate` 非同期 submit のエラー通知パス (A-3)
- **元レビュー**: code-review A-3
- **理由**: SSE / 通知 channel 整備は中規模機能、別 Sprint

### DEF-E05: `releaseManualLock` で `verificationDifference` / `invoiceAmount` のリセット (A-5 / D-2)
- **元レビュー**: design-review D-2 / code-review A-5
- **理由**: DDE-10 と統合判断

### DEF-E06: `summary` エンドポイントに `partnerNo` 受付追加 (m-3)
- **元レビュー**: design-review m-3
- **理由**: フロント既存運用で問題化していないが、検索条件と表示の乖離は将来要対応

### DEF-E07: `tableTotals` (画面集計) と CSV 出力金額の出典整理 (m-impl-4)
- **元レビュー**: code-review m-impl-4
- **理由**: UI/UX 改善、Tooltip 追加で部分対応可

### DEF-E08: `IEntity` 実装で `findByDateRange` の AOP `ShopCheck` 強制 (m-impl-8)
- **元レビュー**: code-review m-impl-8
- **理由**: 現状の呼び出し元 (Tasklet, admin 限定 export) は問題ないが、将来新規 Controller で誤呼出のリスクあり。Cluster A の認可基盤と統合検討

---

## ALREADY-RESOLVED

### AR-E01: `LoginUserUtil.resolveEffectiveShopNo` での shop_no 解決
- **解消経緯**: Cluster A SF-02/03 で `LoginUserUtil` 集約済。AR Controller は既に `effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo)` パターン採用 (L92, L114, L175)
- **対応**: SF-E09 (bulkVerify への `assertShopAccess` 適用) のみ追加

### AR-E02: `FinanceConstants` への `MATCH_TOLERANCE` / `ACCOUNTS_PAYABLE_SHOP_NO` 集約
- **解消経緯**: Cluster D SF-11 で集約済。AR 側は `MATCH_TOLERANCE` を直接参照していないが、`InvoiceVerifier` の `invoice-amount-tolerance` (3 円) は別概念 (許容誤差 vs 一致閾値) → SF-E07 で統合
- **対応**: SF-E07

### AR-E03: `FinanceExceptionHandler` (basePackages = `jp.co.oda32.api.finance`)
- **解消経緯**: Cluster F SF-25 で実装済 (確認済)。AR Controller の `IllegalStateException` は `FinanceExceptionHandler#handleIllegalState` で 422 に統一される
- **対応**: SF-E08 で `IllegalArgumentException` 委譲化のみ追加

### AR-E04: `assertShopAccess` の Controller 実装
- **解消経緯**: AR Controller (L310-315) で既に実装済。Cluster A の `LoginUserUtil` パターンを使った正しい実装
- **対応**: SF-E09 で `bulkVerify` にも適用

### AR-E05: 設計書「対称構造」表 (S-1〜S-12)
- **解消経緯**: 設計レビュー §「買掛金との対称性 / 非対称性」表で全 12 項目を整理済。
  - S-1 (Controller 配置): 改善方向 (DDE-15 で買掛側も切出し)
  - S-2 (`@PreAuthorize` 粒度): AR が ADMIN 化済 → DDE-07 で AP 側も検討
  - S-12 (検証ロジック Service 化): 達成済
- **対応**: 設計書 §1 対称構造表の「現状」列を最新化 (SF-E21 と統合)

### AR-E06: `cutoffType` のマジックリテラル排除
- **解消経緯**: Tasklet 側に `CUTOFF_TYPE_*` 定数定義済 (L63-66)。Controller 側がそれを参照していないだけの問題
- **対応**: SF-E10 で enum 化 (定数の共通化)

---

## 適用順序提案

SAFE-FIX を以下の順序で適用すると依存関係が綺麗:

1. **SF-E01 / SF-E02 / SF-E03 / SF-E04 / SF-E05 / SF-E06** (Critical 群) — マージ前必須、優先
2. **SF-E13** (M-8 ゴミ袋定数統一) — 業務確認完了後
3. **SF-E07 / SF-E08 / SF-E09 / SF-E10 / SF-E11 / SF-E12 / SF-E14 / SF-E15 / SF-E22** (Service / Controller 整理) — Critical 後に並列
4. **SF-E16 / SF-E17 / SF-E18 / SF-E19 / SF-E20** (フロント) — バックエンド独立、並列
5. **SF-E21** (設計書整合) — 独立、並列

## 並列実行プラン

| グループ | 担当タスク | サブエージェント | 依存 |
|---|---|---|---|
| **A: Critical 認可・契約・データ整合修正** | SF-E01 / SF-E02 / SF-E03 / SF-E04 / SF-E05 / SF-E06 / SF-E13 | 1 | なし (マージ前必須) |
| **B: Service / Entity 品質** | SF-E07 / SF-E08 / SF-E09 / SF-E10 / SF-E11 / SF-E12 / SF-E14 / SF-E15 / SF-E22 | 1 | A 後推奨 (同 Controller / Service ファイル) |
| **E: フロント** | SF-E16 / SF-E17 / SF-E18 / SF-E19 / SF-E20 | 1 | なし (バックエンドと独立) |
| **G: 設計書整合** | SF-E21 | 1 | なし |

並列グループ数: **4**

ファイル衝突対策:
- グループ A と B は両方とも `AccountsReceivableController.java` を触るため、**A → B の順で直列実行**
- グループ A 内で SF-E05 (BulkVerifyService 切出し) は SF-E01/E06 と Controller 同ファイル → 直列推奨
- SF-E13 (ゴミ袋定数) は Tasklet と Reconciler の両方を触るため、Cluster A 業務確認待ち
- グループ E はバックエンド変更を含まず純粋にフロント

## 推定総工数

| グループ | 推定 | 内訳 |
|---|---|---|
| A | 5 時間 | SF-E01 (45min) + SF-E02 (30min) + SF-E03 (15min) + SF-E04 (30min) + SF-E05 (1.5h) + SF-E06 (15min) + SF-E13 (1h, 業務確認除く) |
| B | 4 時間 | 9 タスク × 平均 25 分 |
| E | 1.5 時間 | 5 タスク × 平均 18 分 |
| G | 1 時間 | 設計書 4-5 セクション更新 |

**並列実行時の wallclock**: 最遅グループ A の 5 時間 (A → B 直列、E / G 並列)
**直列実行時の累積**: 11.5 時間

DESIGN-DECISION 17 件 + DEFER 8 件は別途ユーザー判断・別 Sprint で消化。

## 出力ファイルパス

`C:\project\odamitsu-data-hub\claudedocs\triage-accounts-receivable.md`
