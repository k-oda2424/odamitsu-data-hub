# commit a0bbcac 経理ロジック批判的レビュー

レビュー実施日: 2026-05-06
対象 commit: a0bbcac feat(finance) — 203 files / 11354 insertions

---

## 指摘事項

### 1. BULK 不変条件違反時に WARN + SUM で過大計上し得る

- **Severity**: Critical
- **カテゴリ**: 金額計算 / データ整合性
- **内容**: `PaymentMfImportService.sumVerifiedAmountForGroup` は全行 `BULK_VERIFICATION` の場合、本来は代表 1 行採用だが、同値でない場合は `log.warn(...)` 後に `SUM` へフォールバックする。BULK は税率別行に同一集約値を冗長保持する経路なので、不一致時に SUM すると税率行数分または部分更新分を含めて振込仕訳が膨らむ。ログ警告だけで MF CSV 出力が継続するため、買掛金支払額の過大計上が実データに流れる。
- **推奨アクション**: BULK 全行不一致は CSV 生成を fail closed にする。少なくとも `SUM` ではなく、該当 supplier/month を隔離して修復させる。

---

### 2. `verification_source` は優先度制御ではなく実質 last-write-wins

- **Severity**: Major
- **カテゴリ**: データ整合性
- **内容**: `applyVerification` は `BULK_VERIFICATION`、`TAccountsPayableSummaryService.verify` は `MANUAL_VERIFICATION`、`ConsistencyReviewService.applyMfOverride` は `MF_OVERRIDE` を保存する。`applyVerification` には transactionMonth の advisory lock があるが、手動 verify / MF override 側に同じ lock は確認できない。保護は `isAnyManuallyLocked` で MANUAL 行のみを skip するため、MF_OVERRIDE は後続 bulk に上書きされ得る。
- **推奨アクション**: supplier × month 単位の排他または optimistic lock を 3 書込経路すべてに適用する。BULK/MANUAL/MF_OVERRIDE の優先度を明文化する。

---

### 3. force audit の不整合詳細が 50 件で失われる

- **Severity**: Major
- **カテゴリ**: データ整合性 / 業務継続
- **内容**: `FORCE_AUDIT_MISMATCH_DETAIL_LIMIT = 50` で、`buildForceAppliedReason` は 50 件以降を `...(+M more)` に切り詰める。`writeForceAppliedAuditRow` は reason 1 行を `finance_audit_log` に書くだけで、全 mismatch の別 DB 保存は確認できなかった。フロントは「全違反明細は finance_audit_log に記録」と表示しており、実装と説明が一致しない。
- **推奨アクション**: mismatch 全件を JSONB または専用 audit detail テーブルへ保存する。全件保存まで UI 文言も修正する。

---

### 4. 20日払いのみ Excel が 5日払い section として処理される

- **Severity**: Major
- **カテゴリ**: 金額計算 / Excel parser
- **内容**: `PaymentMfExcelParser.parseSheet` は常に `currentSection = PAYMENT_5TH` で開始し、最初の合計行で `PAYMENT_5TH` summary を保存してから `PAYMENT_20TH` に遷移する。20日払いのみ Excel で先頭から明細、最後に合計という構造だと、明細も合計も `PAYMENT_5TH` として扱われる。例外にも空 section 継続にもならず、20日払いを PAYABLE 突合対象として誤反映する可能性がある。
- **推奨アクション**: ファイル名、シート名、送金日などから初期 section を判定する。想定外なら明示的に 422 で止める。

---

### 5. OFFSET マスタ欠落時は業務処理が止まる

- **Severity**: Major
- **カテゴリ**: 業務継続
- **内容**: OFFSET 副行生成時、`offsetJournalRuleRepository.findByShopNoAndDelFlg(...).orElseThrow(...)` で active 行がない場合に `IllegalStateException` を投げる。V041 seed は shop_no=1 の 1 行だけで、将来 shop_no=2 を対象化した場合や shop_no=1 の行を admin UI で論理削除した場合、OFFSET を含む Excel の preview/apply がブロックされる。
- **推奨アクション**: shop ごとの default seed を保証し、最後の active 行を delete できない制約を入れる。欠落時 fallback またはヘルスチェックも必要。

---

### 6. P1-02 opening 注入は journal #1 欠損時に 0 スタートになり得る

- **Severity**: Major
- **カテゴリ**: 金額計算 / 業務継続
- **内容**: `MfOpeningBalanceService.fetchFromMfJournalOne` は opening journal が見つからない場合 422 を返す。一方、`SupplierBalancesService.generate` と `AccountsPayableIntegrityService` は `getEffectiveBalanceMap(...)` が空ならそのまま処理を続行する。MF 側累積では `MfJournalFetcher.isPayableOpeningJournal(j)` を除外するため、opening table 未投入なら期首残が silent に 0 扱いになる。
- **推奨アクション**: opening table 未取得または stale の場合、累積残/整合性レポートに明示警告を出す。初回運用では fetch 成功を前提条件にする。

---

### 7. V040 backfill は手入力 note の接頭辞衝突を誤って BULK 化する

- **Severity**: Major
- **カテゴリ**: データ整合性
- **内容**: V040 は `verification_note LIKE '振込明細検証 %'` を先に `BULK_VERIFICATION` に更新し、その後 `verified_manually=true` の残りを `MANUAL_VERIFICATION` にする。過去にユーザーが同じ接頭辞で手入力 note を残していた場合、その行は BULK と誤分類され、再 upload の手動保護から外れる。DB 側でこの仮定を検証する条件は確認できなかった。
- **推奨アクション**: import history / audit log など複数条件で BULK を特定する。移行前に衝突候補を SELECT し、0 件でない場合は migration を止める。

---

### 8. V038 CHECK 拡張は既存不正値があると migration 失敗する

- **Severity**: Minor
- **カテゴリ**: データ整合性 / PostgreSQL
- **内容**: V038 は既存 CHECK を DROP し、`rule_kind` を VARCHAR(30) に拡張した後、新しい `CHECK (rule_kind IN (...))` を ADD する。既存行に許可外値が入っていた場合、PostgreSQL は ADD CONSTRAINT 時に既存行を検証するため migration は失敗する。transactional DDL なら rollback されるはずだが、事前検査 SQL は確認できなかった。
- **推奨アクション**: migration 冒頭に許可外 `rule_kind` の検出を入れ、具体値を出して中断する。

---

### 9. force 経路は admin 制御のみで、force 専用の二段認可がない

- **Severity**: Major
- **カテゴリ**: データ整合性 / DX
- **内容**: `PaymentMfImportController.verify` は `@PreAuthorize("@loginUserSecurityBean.isAdmin()")` があり、非 admin の API 直叩きは防いでいる。一方で `PaymentMfApplyRequest.force` は boolean だけで、admin であれば理由なしに常に `force=true` を送れる。誤操作や自動化スクリプトの誤送信に対する二段階制御は未実装。
- **推奨アクション**: `force=true` では reason 文字列を必須フィールドにする。さらに通常 admin とは別権限または feature flag を要求する。

---

### 10. Frontend の force 承認内容と実処理対象が乖離する

- **Severity**: Major
- **カテゴリ**: Frontend / DX / データ整合性
- **内容**: Frontend は mismatch 一覧を `slice(0, 100)` で表示し、100 件超は残件数だけ省略表示する。ConfirmDialog は件数だけを示し、ユーザーが未表示の個別内容を確認できないまま `force=true` で全件反映する。さらに backend audit は 50 件で切り詰めるため、承認 UI と監査証跡の両方で実処理対象との乖離がある。
- **推奨アクション**: force 実行前に全 mismatch をダウンロード可能にするか、ページングで全件確認を必須化する。

---

## 最重要 3 件（Opus 見落とし）

1. **BULK 不変条件違反時の SUM フォールバックは fail-safe ではなく過大計上を実行する**（Critical。ログ警告のみで MF CSV 生成が継続し、買掛金が実際に膨らむ）
2. **20日払いのみ Excel の初期 section が PAYMENT_5TH 固定で、誤って PAYABLE として反映され得る**（Major。例外にならず silent に誤分類される）
3. **force の監査・UX が全件確認/全件保存になっていない**（Major。フロント 100 件省略 + バックエンド 50 件切り詰めで、承認内容と実処理が乖離する）

---

*Codex CLI 自動レビュー — 2026-05-06*
