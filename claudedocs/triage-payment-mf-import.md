# Triage: 買掛仕入 MF 変換 (Cluster C) 修正対応

triage 日: 2026-05-04
対象指摘総数: 約 42 件 (設計 16 + コード 14 + Codex 12)
出典:
- design-review-payment-mf-import.md (Critical 3 / Major 6 / Minor 7)
- code-review-payment-mf-import.md (Critical 2 / Major 5 / Minor 7)
- codex-adversarial-payment-mf-import.md (Critical 3 / Major 7 / Minor 2)

## サマリー
| 分類 | 件数 |
|---|---|
| SAFE-FIX | 21 件 |
| DESIGN-DECISION | 12 件 |
| DEFER | 6 件 |
| ALREADY-RESOLVED | 3 件 |

優先度概観:
- **Critical 系で SAFE-FIX 化できたもの: 5 件** (C-1 admin 認可付与 / C-CODE-2 fmtAmount null 防御 / Codex-1 検証済 CSV 取引日修正 / C-2 Javadoc/設計書整合 / Codex-3 import_history_id 紐付けの一部)
- **DESIGN-DECISION** は Codex 由来 (会計判断・監査要件) と Opus 設計レビューの「複数税率 supplier 書込スキーマ」「DIRECT_PURCHASE 自動降格の責務」等
- **DEFER** は MF API 直接連携、ML 不要なルール候補スコアリング、event sourcing 等の長期課題
- **ALREADY-RESOLVED** は Cluster D が先行集約した FinanceConstants / FinanceExceptionHandler / shop_no 集約により Payment MF 側で重複対応不要となったもの

---

## SAFE-FIX (即適用)

### SF-C01: `PaymentMfImportController#convert` / `verify` に `@PreAuthorize("hasRole('ADMIN')")` 付与 (Critical)
- **元レビュー**: design-review C-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:66, 87`
- **修正内容**: `convert` (L66) と `verify` (L87) のメソッド先頭に `@PreAuthorize("hasRole('ADMIN')")` を追加。同 Controller の `/export-verified` `/aux-rows` `/rules` PUT/DELETE 系と統一
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + Spring Security 起動 + 一般ユーザで 403 が返ることを curl で確認
- **依存関係**: なし
- **担当推奨**: グループ A "Critical 認可・契約修正"

### SF-C02: `PaymentMfCsvWriter#fmtAmount` の null 時に末尾スペースを保証 (Critical 防御)
- **元レビュー**: code-review C-CODE-2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCsvWriter.java:95-98`
- **修正内容**:
  ```java
  private static String fmtAmount(Long v) {
      long amount = v == null ? 0L : v;
      return amount + " ";
  }
  ```
  契約 (末尾半角スペース必須) を不変条件として強制
- **想定影響範囲**: 1 ファイル + ゴールデンマスタ CSV ハッシュは現状経路で null が出ないため変化なし (golden test PASS のはず)
- **テスト確認**: `./gradlew test --tests *PaymentMfImportServiceGoldenMasterTest`
- **依存関係**: なし
- **担当推奨**: グループ A

### SF-C03: `exportVerifiedCsv` の CSV 取引日列を `transactionMonth` 固定に統一 (Critical 業務影響)
- **元レビュー**: Codex 1 (Opus 見落とし最重要 #1)
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:518-545` 周辺 (`exportVerifiedCsv` の PAYABLE 行 / aux 行 build 部)
- **修正内容**: PAYABLE 行 build 時の `mfTransferDate` セット、aux 行 build 時の `transferDate` セットを **CSV `transactionDate` 用途では使わない** に変更。`PaymentMfPreviewRow.transactionDate` 既定値を `transactionMonth` (締め日) に固定。送金日は MF 連携で自動付与される運用に揃える。設計書 §4.4 / `FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX` Javadoc / UI ツールチップに「CSV 取引日 = 締め日固定 / 送金日は監査用 DB 列のみ」を明記
- **想定影響範囲**: 1 service + ゴールデンマスタ更新 (検証済 CSV 用 fixture が存在する場合は再生成)
- **テスト確認**: `./gradlew test` + 検証済 CSV を MF テスト環境で取込み、期間帰属が `transactionMonth` になることを確認
- **依存関係**: なし
- **担当推奨**: グループ A (Critical 業務影響)

### SF-C04: 設計書 §5.1 と `deriveTransactionMonth` Javadoc の整合 (Critical 文書整合)
- **元レビュー**: design-review C-2 / D-1
- **対象ファイル**:
  - `claudedocs/design-payment-mf-import.md` §5.1 末尾の「旧仕様 (〜2026-04-14)」マーク追加
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:996-1013` (`deriveTransactionMonth` Javadoc) の「§5.1 旧記述は是正対象」コメント削除
- **修正内容**: 運用実態 = 「両方とも前月20日締め」を確定として、設計書本文と Javadoc コメントを同じ表現に揃える。Javadoc は「現行仕様: 5日/20日とも前月20日締め (設計書 §5.1 と一致)」に書換
- **想定影響範囲**: 設計書 1 ファイル + Service 1 ファイル
- **テスト確認**: `./gradlew compileJava` (Javadoc コメントなので動作変化なし)
- **依存関係**: なし
- **担当推奨**: グループ A

### SF-C05: `PaymentMfImportController` の `IllegalStateException` catch ブロックを `FinanceExceptionHandler` に委譲
- **元レビュー**: code-review C-CODE-1 関連 (例外設計) + Cluster F SF-25 と同パターン
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:78-79, 142-143`
- **修正内容**: `convert` / `exportVerified` の `catch (IllegalStateException e)` ブロックを削除し、`FinanceExceptionHandler#handleIllegalState` (basePackages = `jp.co.oda32.api.finance`) に委譲。422 + 汎用メッセージ ("内部エラーが発生しました") に統一
- **想定影響範囲**: 1 ファイル (Controller) + レスポンス body 構造変化 (旧: `{"message":"具体エラー"}` → 新: `{"message":"内部エラーが発生しました","code":"INTERNAL_ERROR"}`)
- **テスト確認**: `./gradlew compileJava` + 422 系のレスポンス body 構造をフロント (`payment-mf-import.tsx`) のエラー表示で確認 (フロントは toast 表示のみなので互換)
- **依存関係**: Cluster F の SF-25 (FinanceExceptionHandler) が既に commit 済 (確認済) なので即適用可
- **担当推奨**: グループ A

### SF-C06: `PaymentMfImportController#history` の shop_no=1 リテラルを `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` に置換
- **元レビュー**: code-review M-CODE-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:188-193`
- **修正内容**: `historyRepository.findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(1, "0")` の `1` を `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` に置換 + import 追加
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし (Cluster D の SF-13 と同パターン)
- **担当推奨**: グループ B "コード品質"

### SF-C07: `MPaymentMfRule` 動的 DIRECT_PURCHASE 降格時の `summaryTemplate` / `tag` / `creditDepartment` 完全継承
- **元レビュー**: code-review M-CODE-3 (+ design M-1 の代替案部分)
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:794-806`
- **修正内容**: 動的降格 builder で `summaryTemplate(rule.getSummaryTemplate() != null ? rule.getSummaryTemplate() : "{source_name}")` + `tag(rule.getTag())` + `creditDepartment(rule.getCreditDepartment())` を追加。設計レビュー M-1 (責務分散) は DESIGN-DECISION 側 (DDC-04) に分離するが、欠落フィールド補完は SAFE
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew test --tests *PaymentMfImportServiceGoldenMasterTest` (運用上 PAYABLE→DIRECT_PURCHASE 降格 supplier の summaryTemplate が NULL なので CSV 不変のはず)
- **依存関係**: DDC-04 (責務分離) が承認されたら関数化 (`MPaymentMfRule#deriveDirectPurchaseRule`) するが、それまでは builder 拡張のみ
- **担当推奨**: グループ B

### SF-C08: `PaymentMfImportService#deriveTransactionMonth` を `static` に変更
- **元レビュー**: design-review m-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:1011-1013`
- **修正内容**: `LocalDate deriveTransactionMonth(LocalDate transferDate)` を `static LocalDate deriveTransactionMonth(LocalDate transferDate)` に変更。呼出側 (`applyVerification` L211 / `saveHistory` L1075 等) は無変更で OK
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-C09: `PaymentMfRuleService#normalizeCompanyName` の正規表現 4 本を `static final Pattern` に切り出し
- **元レビュー**: code-review m-CODE-2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfRuleService.java:185-201`
- **修正内容**: 4 つの `replaceAll` 用 Pattern を `private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*\\]");` 等で切り出し、`p.matcher(s).replaceAll("")` で再利用
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + `backfillPaymentSupplierCodes` の dryRun 結果が同一であること確認
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-C10: `PaymentMfImportService#cleanExpired` に削除件数ログ追加
- **元レビュー**: code-review m-CODE-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:1114-1118`
- **修正内容**:
  ```java
  int before = cache.size();
  cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
  int removed = before - cache.size();
  if (removed > 0) log.debug("PaymentMf cache: {}件期限切れ削除 (残{}件)", removed, cache.size());
  ```
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-C11: `PaymentMfRuleService#backfillPaymentSupplierCodes` の dryRun=true パスに `@Transactional(readOnly = true)` 追加
- **元レビュー**: design-review m-2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfRuleService.java:97`
- **修正内容**: `backfillPaymentSupplierCodes(boolean dryRun, ...)` を 2 メソッドに分割するか、または method-level `@Transactional(readOnly = true)` を維持しつつ dryRun=false 時のみ別 method (`@Transactional`) で update を委譲
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-C12: `PaymentMfCellReader#readLongCell` のオーバーフロー / NaN ガード追加
- **元レビュー**: design-review m-7
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCellReader.java:64-82`
- **修正内容**:
  ```java
  double d = cell.getNumericCellValue();
  if (Double.isNaN(d) || Double.isInfinite(d) || d > Long.MAX_VALUE || d < Long.MIN_VALUE) return null;
  return Math.round(d);
  ```
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + ゴールデンマスタ PASS
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-C13: `MPaymentMfRule` Entity に `IEntity` 実装追加 (規約準拠)
- **元レビュー**: design-review M-6
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MPaymentMfRule.java:11-83`
- **修正内容**: `implements IEntity` 追加 + `getDelFlg()`/`setDelFlg()` 既存ゲッタ/セッタを @Override 化。`PaymentMfRuleService.delete` で手動セットしている `del_flg='1'` パターンは互換維持
- **想定影響範囲**: 1 ファイル (Entity)。`CustomService` への移行は別タスク (DEF-C03) で実施
- **テスト確認**: `./gradlew compileJava` + 既存テスト
- **依存関係**: なし
- **担当推奨**: グループ B

### SF-C14: `TPaymentMfAuxRow` の物理削除運用方針を設計書 §3.3 にも追記
- **元レビュー**: design-review m-4 / D-2
- **対象ファイル**: `claudedocs/design-payment-mf-aux-rows.md` §3.3
- **修正内容**: 設計書サンプルコードから `implements IEntity` の no-op 部分を削除し、「物理削除運用、`IEntity` 不実装」と明記。Entity と設計書の表現を一致させる
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: なし (doc 更新のみ)
- **依存関係**: なし
- **担当推奨**: グループ G "設計書整合"

### SF-C15: 設計書 §4.1 のサンプルコード (N+1 ナイーブ実装) を実装に合わせて更新 + §10 PAYABLE 件数を 93 件に修正
- **元レビュー**: design-review D-3 / D-4
- **対象ファイル**: `claudedocs/design-payment-mf-import.md` §4.1 / §11
- **修正内容**: §4.1 を「事前 codesToReconcile 集約 + payablesByCode 一括ロード (N+1 解消済 / B-W11)」に書き換え。§11 確定事項の PAYABLE 件数を 74 件 → 93 件 (V011 シード実数) + 確認日付を脚注追加
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: なし
- **依存関係**: なし
- **担当推奨**: グループ G

### SF-C16: 設計書 §7 に advisory lock の存在 + single-instance 前提を追記
- **元レビュー**: design-review M-2 / M-3 / D-5 / D-6
- **対象ファイル**: `claudedocs/design-payment-mf-import.md` §7
- **修正内容**: 「`uploadId キャッシュ` は single-instance 前提。マルチノード時は Redis/Postgres に寄せる」「`pg_advisory_xact_lock(transactionMonth.toEpochDay)` で `applyVerification` / `exportVerifiedCsv` を直列化」を §7 に追記
- **想定影響範囲**: 設計書 1 ファイル
- **テスト確認**: なし
- **依存関係**: なし
- **担当推奨**: グループ G

### SF-C17: フロント `payment-mf-rules.tsx` の検索フィルタ正規化を統一
- **元レビュー**: code-review M-CODE-5
- **対象ファイル**: `frontend/components/pages/finance/payment-mf-rules.tsx:95-105`
- **修正内容**: `debitSubAccount` も `normalizeName` で正規化。`paymentSupplierCode` は数値 6 桁なので case 統一は不要だが、`toLowerCase()` で他項目と表面挙動を揃える
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: グループ E "フロント"

### SF-C18: フロント `payment-mf-import.tsx` の `confirmVerify` ダイアログを `verifyMut.isPending` で disabled 化
- **元レビュー**: code-review m-CODE-5
- **対象ファイル**: `frontend/components/pages/finance/payment-mf-import.tsx:404-411`
- **修正内容**: `ConfirmDialog` の `disabled={verifyMut.isPending}` または `onConfirm` 内で `setConfirmVerify(false)` を即時呼ぶ
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: グループ E

### SF-C19: フロント `PaymentMfAuxRowsTable` の useEffect 依存を `query.data?.length` に変更
- **元レビュー**: code-review m-CODE-6
- **対象ファイル**: `frontend/components/pages/finance/PaymentMfAuxRowsTable.tsx:46-53`
- **修正内容**: `useEffect(... , [query.data])` を `[query.data?.length]` に変更
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: グループ E

### SF-C20: `PaymentMfImportController#convert` のファイル名を `payment_mf_${yyyymmdd}.csv` 形式に統一
- **元レビュー**: design-review m-1 / code-review m-CODE-7
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:71-77`
  - `frontend/components/pages/finance/payment-mf-import.tsx:86-107`
- **修正内容**: バックエンド: `payment_mf.csv` を `payment_mf_${yyyymmdd}.csv` (cached.transferDate ベース) に変更。日本語ファイル名側も `買掛仕入MFインポートファイル_${yyyymmdd}.csv` に揃える。フロント fallback も整合
- **想定影響範囲**: 2 ファイル
- **テスト確認**: `./gradlew compileJava` + `npx tsc --noEmit` + DL されるファイル名を確認
- **依存関係**: なし
- **担当推奨**: グループ E (フロントと一括)

### SF-C21: `PaymentMfImportService#exportVerifiedCsv` の Javadoc に advisory lock 自動解放を明記
- **元レビュー**: design-review m-5
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:460-475`
- **修正内容**: メソッド Javadoc に「advisory lock は @Transactional 境界で自動解放されるため、early return しても解放漏れなし」を追記
- **想定影響範囲**: 1 ファイル (Javadoc のみ)
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: グループ B

---

## DESIGN-DECISION (要ユーザー判断)

### DDC-01: 振込明細経由 `verified_amount` の税率行書込仕様 (Critical / 業務影響大)
- **元レビュー**: code-review C-CODE-1 / design-review C-3
- **論点**: 現行は同一 supplier の税率別 N 行に **同じ invoice (税込総額) を全行書込**。`sumVerifiedAmountForGroup` の「全行同値なら代表値、そうでなければ SUM」で読取側で吸収しているが、書込スキーマ自体が二重計上を内包
- **選択肢**:
  - A: **代表行のみ書込** (list.get(0) のみ verifiedAmount セット、他行は null) — 集計は「非 null 1 行のみ採用」で固定
  - B: **税率別按分書込** (DB 側 `taxIncludedAmountChange` 比率で invoice を按分) — 各税率行に正しい税抜が入る、複数税率 supplier を支援
  - C: **集約値専用カラム** (`verified_amount_total` 追加) を切出し `verified_amount` は税率別保持
  - D: 現状維持 + 設計書 / Entity Javadoc / DB COMMENT に「全税率行同値書込 ＋ `sumVerifiedAmountForGroup` 必須」を不変条件として明記 (短期回避)
- **影響範囲**: `applyVerification`, `sumVerifiedAmountForGroup`, `exportVerifiedCsv`, `AccountsPayableLedgerService.aggregateMonth` (Cluster D の DD-14 と関連)
- **推奨**: **A** (代表行のみ書込) が最もシンプル。複数税率 supplier はその時点で手動 verify に回す
- **マージ可否**: A/B/C は破壊的変更を伴うため、確定までは D (Javadoc 明示) を SAFE-FIX として先行

### DDC-02: PAYABLE→DIRECT_PURCHASE 自動降格を経理判断として明示承認化 (Critical / 業務判断)
- **元レビュー**: Codex 2 (Opus 見落とし最重要 #2) / design-review M-1
- **論点**: `afterTotal=true` セクションの PAYABLE ヒットを **黙って DIRECT_PURCHASE (仕入高/課税仕入10%) に降格**。会計判断の無承認自動化で、「なぜ買掛金でなく仕入高になったか」を後から説明困難
- **選択肢**:
  - A: 自動降格を完全廃止し、20日払いセクションで PAYABLE ヒットしたら未登録扱い → admin がルール側で DIRECT_PURCHASE 明示登録を促す
  - B: 降格候補としてプレビューに「降格対象」セクションで表示 + admin の確認ボタンで適用 (CSV 出力は降格後)
  - C: 現状維持 + 監査証跡 (`payment_mf_demote_log` テーブル) で降格件数・対象 supplier・降格時刻を記録
  - D: 現状維持 + 設計書 §5.3 に降格仕様 (PAYABLE vs 明示 DIRECT_PURCHASE のどちらが優先かを含む) を明記
- **影響範囲**: `buildPreview` (L794-806) + `applyVerification` (L230 skipped) の対称化、設計書 §5.3 / §11
- **推奨**: **B** (preview に降格セクション + admin 確認) が監査要件と UX のバランス良
- **マージ可否**: A/B は機能変更、C は仕様追加。当面 D (設計書追記) のみ SAFE-FIX 化

### DDC-03: 同一振込明細の再取込 / 古い Excel 上書き防止キー (Critical / 監査要件)
- **元レビュー**: Codex 3 (Opus 見落とし最重要 #3) / Codex 8
- **論点**: aux 行は `(shop, transaction_month, transfer_date)` 単位で洗い替えだが、Excel 内容ハッシュ・import batch ID・確定状態の世代管理がない → 古い Excel 再 upload で現行 PAYABLE / aux を静かに上書き可能
- **選択肢**:
  - A: `t_payment_mf_import_batch` テーブル新設 (source file hash + transferDate + transactionMonth + 適用 user/timestamp)。PAYABLE / aux / 履歴 CSV を同 batch ID 紐付け (Codex 8 と統合)
  - B: ファイルハッシュのみ `t_payment_mf_import_history` に追加 + applyVerification で「同 hash の reapply」「異 hash の上書き」を判定して警告
  - C: 現状維持 + 設計書 §6 に「再取込時は最新 Excel が常に正」と運用ルール明記
- **影響範囲**: 大: 新テーブル + 4 service / 小: history テーブル拡張のみ
- **推奨**: **A** (audit trail 軸 F と統合実装) が中期解。当面 B (hash 比較警告) を Phase 1
- **マージ可否**: 全選択肢が機能追加 → SAFE-FIX 化不可、ユーザー判断必須

### DDC-04: DIRECT_PURCHASE 動的降格の責務分離
- **元レビュー**: design-review M-1 / code-review M-CODE-3
- **論点**: 降格ロジックを `MPaymentMfRule#deriveDirectPurchaseRule()` に切出し、`buildPreview` と `applyVerification` の両方から呼ぶ整理。SF-C07 (フィールド完全継承) で症状緩和は可能だが、責務分離は API 設計判断
- **選択肢**:
  - A: メソッド切出し + applyVerification も DIRECT_PURCHASE 反映 (現状 skip と非対称)
  - B: SF-C07 のみ実施 + 設計書 §5.3 に「降格は preview/CSV のみ、PAYABLE 反映は skip」と現状仕様を明記
- **推奨**: DDC-02 と一括判断。DDC-02 で B (preview 表示) を採用するなら DDC-04 も A が整合

### DDC-05: 複数税率 supplier 検出時の UI 警告追加
- **元レビュー**: design-review C-3 (項目 2/3)
- **論点**: 現状複数税率 supplier の `verified_amount_tax_excluded` が誤算でも UI 警告無し。検出ロジックを `VerifyResult.multiTaxRateSuppliers: List<String>` で返すか
- **選択肢**:
  - A: 検出 + UI Badge 表示 (現行 verify は通すが警告)
  - B: 検出 + verify 時にエラーで強制ブロック (admin 個別 verify を必須化)
  - C: 現状維持 + 運用注記 ("複数税率 supplier は手動 verify")
- **推奨**: **A** (警告のみ) — DDC-01 A 採用なら自動的に複数税率 supplier は代表行のみで誤算消失するが、UI 警告は監査痕跡として有用

### DDC-06: 振込明細経由 `applyVerification` の税率別書込仕様の文書化
- **元レビュー**: design-review m-10 / code-review M-CODE-2
- **論点**: 「全税率行に同額」を仕様として明文化 + `LedgerService.aggregateMonth` の平均化ロジック依存を仕様化
- **選択肢**: Cluster D の DD-14 と統合 → A (現行を仕様化) を採用するなら、Payment MF 側設計書 §5.2 にも追記
- **推奨**: Cluster D DD-14 と一括判断

### DDC-07: 100 円閾値一致と自動調整の監査分類 (税理士監査要件)
- **元レビュー**: Codex 6
- **論点**: 100 円以内一致を `EXACT_MATCH` / `THRESHOLD_MATCH` に分け、閾値一致は承認ログと差額理由 (税込/税抜差・振込手数料・端数調整) を必須化
- **選択肢**:
  - A: enum 拡張 (`verification_match_type` カラム追加) + `auto_adjust_reason` enum 列追加
  - B: 現状維持 + `verification_note` 自由テキストで運用カバー
- **推奨**: 監査強度に応じて A (税理士確認時に詰める)

### DDC-08: CP932 未マップ文字の `?` 置換を REPORT に変更
- **元レビュー**: Codex 7
- **論点**: `OutputStreamWriter(MS932)` のデフォルトは置換 (`REPLACE`)。`㎡` / 丸数字 / 異体字が摘要・タグ・取引先名に入ると CSV 上で `?` 化、履歴 CSV にも破損後の値が残る
- **選択肢**:
  - A: `CharsetEncoder` を `CodingErrorAction.REPORT` で使い、変換不能文字を行番号・項目名付きで preview エラーにする
  - B: 現状維持 + 運用ルール「異体字は事前置換」
- **推奨**: **A** — 監査痕跡保護のために重要だが、業務 Excel の文字解析が必要 (適用前にプロダクション Excel での `?` 出現有無を調査)

### DDC-09: Excel フォーマット変更検知の強化
- **元レビュー**: Codex 5
- **論点**: 現状必須列は `送り先` / `請求額` のみ。`送料相手` / `早払い` / `振込金額` 欠落は無警告で 0 円 SUMMARY 化
- **選択肢**:
  - A: 5日/20日別に必須列セット定義 + preview 段階でブロック
  - B: 現状維持 + 警告ログのみ
- **推奨**: **A** (防御として必要だが、振込明細フォーマット改訂頻度の確認後に実装)

### DDC-10: ルール候補スコアリング (deterministic、ML なし)
- **元レビュー**: Codex 10
- **論点**: 未登録 supplier に対して類似候補・支払先コード一致・過去 Excel 等を deterministic スコアリングで提示
- **選択肢**:
  - A: 候補一覧 API + UI (`/finance/payment-mf-rules?candidate=...`) を実装
  - B: 現状維持 (admin が手動でマスタ整備)
- **推奨**: B (DEFER 候補) — 現状運用で支障少なし

### DDC-11: 一般ユーザのルール追加権限 (会計マスタ変更責任)
- **元レビュー**: Codex 12
- **論点**: 未登録送り先の追加 (POST `/rules`) は一般ユーザに開放されているが、借方勘定・税区分・摘要テンプレを含むため会計処理の変更
- **選択肢**:
  - A: 一般ユーザは「候補ルール申請」(`m_payment_mf_rule_candidate` 別テーブル) → admin 承認で本テーブル反映
  - B: 現状維持 (UX 優先 / コメント L222 で意図明示)
- **推奨**: 経理責任者の運用判断次第。当面 B、不正登録が観測されたら A

### DDC-12: MF API 直接連携の代替案 ADR
- **元レビュー**: Codex 4
- **論点**: Excel/CSV 経路を恒久前提にしているが、MF API 直接連携を採らない理由が ADR (Architecture Decision Record) として残っていない
- **選択肢**:
  - A: ADR を `claudedocs/adr/0001-payment-mf-csv-route.md` で残す (採らない理由・再検討条件・CSV 運用の責任範囲)
  - B: 現状維持
- **推奨**: **A** (低コスト、文書化のみ) — DEFER 寄りの SAFE 候補

---

## DEFER (将来課題)

### DEF-C01: PaymentMfImportService#cache のマルチインスタンス対応 (Redis/Postgres)
- **元レビュー**: design-review M-2 / B-W9 (コードコメント)
- **理由**: 当面 single-instance 運用、設計書 §7 への注記 (SF-C16) で当面カバー

### DEF-C02: Advisory lock キーに shop_no 含意
- **元レビュー**: design-review M-3
- **理由**: shop_no=1 固定 (`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO`) なので不要。マルチショップ展開時に再検討。Cluster D の DEF-03 と統合管理

### DEF-C03: `MPaymentMfRule` を `CustomService` 経由化
- **元レビュー**: design-review M-6
- **理由**: SF-C13 (IEntity 実装) で規約準拠は完了。CustomService 移行は中規模リファクタ

### DEF-C04: aux 行 `sequence_no` を Excel 物理出現順 (rowIndex) ベースに再設計
- **元レビュー**: design-review M-5
- **理由**: 現状運用通り動作中。Excel 形式変更時に対応

### DEF-C05: PAYABLE シードの payment_supplier_code backfill を migration / ApplicationRunner 化
- **元レビュー**: design-review m-6
- **理由**: 既存環境は backfill 適用済。新規 deploy 環境向け onboarding doc 整備で代替

### DEF-C06: `PaymentMfImportController#saveHistory` 履歴 ID を Response Header で返す
- **元レビュー**: code-review M-CODE-1
- **理由**: 監査追跡性向上だが現状運用に支障少なし。DDC-03 (import_batch_id) 実装時に統合検討

---

## ALREADY-RESOLVED

### AR-C01: `FinanceExceptionHandler` (basePackages = `jp.co.oda32.api.finance`) で IllegalStateException → 422 ハンドル
- **解消経緯**: Cluster F の SF-25 で実装済 (`backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java`)。Payment MF Controller の try/catch (L78-79, L142-143) は SF-C05 で削除可能 (RestControllerAdvice に委譲)
- **対応**: SF-C05 で Payment MF Controller を整理するのみ

### AR-C02: `FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE` (100円閾値) と `MATCH_TOLERANCE` 集約
- **解消経緯**: Cluster D の SF-11 で `FinanceConstants` に集約済 (確認済: `MATCH_TOLERANCE = BigDecimal.valueOf(100)` L91)。Payment MF 側は既に `FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE` を参照しており重複定数なし
- **対応**: なし (確認済)

### AR-C03: `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` (shop_no=1) 集約
- **解消経緯**: Cluster D で集約済。Payment MF Service 側は完全置換済 (Grep 結果: 18 箇所で参照)。Controller `history` のみリテラル `1` 残存 → SF-C06 で解消
- **対応**: SF-C06 のみ

---

## 適用順序提案

SAFE-FIX を以下の順序で適用すると依存関係が綺麗:

1. **SF-C01 / SF-C02 / SF-C03 / SF-C04 / SF-C05** (Critical 群) — マージ前必須、優先
2. **SF-C06 / SF-C07 / SF-C08** (Service / Controller 整理) — Critical 後に並列
3. **SF-C09 / SF-C10 / SF-C11 / SF-C12 / SF-C13 / SF-C21** (コード品質) — 独立、並列
4. **SF-C14 / SF-C15 / SF-C16** (設計書整合) — 独立、並列
5. **SF-C17 / SF-C18 / SF-C19 / SF-C20** (フロント) — バックエンド独立、並列

## 並列実行プラン

| グループ | 担当タスク | サブエージェント | 依存 |
|---|---|---|---|
| **A: Critical 認可・契約修正** | SF-C01 / SF-C02 / SF-C03 / SF-C04 / SF-C05 | 1 | なし (マージ前必須) |
| **B: Service / Entity 品質** | SF-C06 / SF-C07 / SF-C08 / SF-C09 / SF-C10 / SF-C11 / SF-C12 / SF-C13 / SF-C21 | 1 | A 後推奨 (同 Service ファイル) |
| **E: フロント** | SF-C17 / SF-C18 / SF-C19 / SF-C20 | 1 | SF-C20 はバックエンド変更も含む |
| **G: 設計書整合** | SF-C14 / SF-C15 / SF-C16 | 1 | なし |

並列グループ数: **4**

ファイル衝突対策:
- グループ A と B は両方とも `PaymentMfImportController.java` / `PaymentMfImportService.java` を触るため、A → B の順で直列実行
- グループ E の SF-C20 はバックエンド `PaymentMfImportController.java` も触るため、グループ A と排他

## 推定総工数

| グループ | 推定 | 内訳 |
|---|---|---|
| A | 3 時間 | SF-C01 (15min) + SF-C02 (15min) + SF-C03 (1.5h, golden 再生成含) + SF-C04 (15min) + SF-C05 (45min) |
| B | 4 時間 | 9 タスク × 平均 25 分 |
| E | 1.5 時間 | 4 タスク × 平均 20 分 |
| G | 1 時間 | 設計書 3 セクション更新 |

**並列実行時の wallclock**: 最遅グループ B の 4 時間 (A → B 直列、E / G 並列)
**直列実行時の累積**: 9.5 時間

DESIGN-DECISION 12 件 + DEFER 6 件は別途ユーザー判断・別 Sprint で消化。

## 出力ファイルパス

`C:\project\odamitsu-data-hub\claudedocs\triage-payment-mf-import.md`
