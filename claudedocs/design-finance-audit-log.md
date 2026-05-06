# T2: finance 監査証跡基盤 (Audit Log) 設計書

作成日: 2026-05-04
対象ブランチ: `refactor/code-review-fixes`
実装方式: 案 B (Service 層 AOP)
旧ドラフト: `claudedocs/design-audit-trail-accounts-payable.md` (買掛側専用、Hibernate `@EntityListeners` 想定)。本書はそれを全 finance に拡張し、AOP 実装に切替えた版。

---

## 1. 概要

T1 (権威階層 / 不変条件文書化) で「どの値がどの操作で書き換わるか」は明文化済み。
本 T2 は「**いつ・誰が・何を変更したか**」を補完する。

### 1.1 採用方式: AOP (案 B)

`@AuditLog` アノテーションを付与した Service メソッドを Aspect で横断し、呼び出し前後の引数・戻り値を `finance_audit_log` (JSONB) に書き込む。

| 比較項目 | 案 A: Entity Listener | **案 B: Service AOP** |
|---|---|---|
| 設置箇所 | Entity 単位 (`@EntityListeners`) | Service メソッド単位 (`@AuditLog`) |
| 監視粒度 | 全 INSERT/UPDATE/DELETE 自動 | 明示マーク必須 |
| ビジネス操作の意味付け | 困難 (verify と batch update の区別不可) | `operation="verify"` で明示 |
| バッチ経由 SQL UPDATE 補足 | できる | できない (Service 経由のみ) |
| 将来の業務拡張時の手間 | 自動 | 都度 `@AuditLog` 追加が必要 |

ビジネス操作の意味 (verify / mf_apply / import 等) を残せる方が監査ログの実用性が高いため案 B を採用。バッチ経由の集計上書きは別途 `t_payment_mf_import_history` 等に既存履歴があり、本基盤の対象外とする。

---

## 2. インフラ

### 2.1 テーブル: `finance_audit_log` (V036)

```sql
CREATE TABLE finance_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_no   INTEGER,                              -- NULL = SYSTEM/BATCH
    actor_type      VARCHAR(20) NOT NULL DEFAULT 'USER',  -- USER / SYSTEM / BATCH
    operation       VARCHAR(50) NOT NULL,
    target_table    VARCHAR(100) NOT NULL,
    target_pk       JSONB NOT NULL,
    before_values   JSONB,
    after_values    JSONB,
    reason          TEXT,
    source_ip       VARCHAR(45),
    user_agent      VARCHAR(500)
);
```

3 つの index: `(target_table, occurred_at DESC)` / `(actor_user_no, occurred_at DESC)` / `(occurred_at DESC)`。

### 2.2 アノテーション

- `@AuditLog(table="...", operation="...", pkExpression="...", pkArgIndex=-1, captureArgsAsAfter=true|false, captureReturnAsAfter=true|false)`
  - Service の public メソッドに付与
  - **`pkExpression`** (C4 修正、推奨): SpEL で複合 PK を `Map` として組み立て、`target_pk` に投入。
    引数を `#a0`, `#a1`, ... または引数名で参照 (例: `"{'shopNo': #a0, 'supplierNo': #a1, 'transactionMonth': #a2, 'taxRate': #a3}"`)
  - `pkArgIndex` (旧 API、後方互換): 単一引数 PK を JSONB 化。既定 `-1` (= `pkExpression` を優先)
  - `captureArgsAsAfter`: 引数 JSON を `reason` 列の補助情報として記録 (誤誘導防止のため after_values へは入れない)
  - `captureReturnAsAfter`: Loader が無い table で戻り値 JSON を after にフォールバック保存
- `@AuditExclude` (フィールド): 機密 / 大きい blob を JSON 出力から除外。`@JacksonAnnotationsInside @JsonIgnore` のメタアノテーションなので REST API レスポンスからも自動除外される (Entity を直接 API で返している箇所がないことを事前確認のうえ採用)

### 2.3 AOP + Loader (C5 修正)

- `FinanceAuditAspect`: `@Around("@annotation(auditLog)")` で前後フック
  - SecurityContext から `LoginUser` を取得し `actor_user_no` 解決 (取れなければ SYSTEM)
  - HttpServletRequest の `X-Forwarded-For` / `User-Agent` を IP/UA 列に記録
  - 専用 ObjectMapper (JavaTimeModule + FAIL_ON_SELF_REFERENCES disabled) で循環参照を回避
  - `@ApplicationType("web")` で web プロファイル限定 (バッチでは起動しない)
  - **C4**: `pkExpression` を SpEL で評価して PK JSON を生成。複合 PK (4-5 列) を `Map<String,Object>` として表現
  - **C5**: `AuditEntityLoaderRegistry` 経由で実 Entity を before/after に再 fetch。Loader が無い table では before=null (旧の引数 JSON は誤誘導を避けるため記録しない)、after は `captureReturnAsAfter` のみフォールバック
- `FinanceAuditWriter`: 別 Bean で `@Transactional(REQUIRES_NEW)`。業務 tx の rollback でも失敗操作の証跡を残す
- `AuditEntityLoader` (C5): table 名 → Repository 解決 interface。`@Component` で実装すると自動登録
- 例外時: `reason="FAILED:<exception>"` で記録してから rethrow (before snapshot は残す、after は null)
- 監査ログ書込み失敗: 業務 tx 成功は巻き戻さず WARN ログのみ (P1 = 業務優先)

#### C5 適用済 Loader (= 7 個)

| Loader | table | PK 形式 |
|---|---|---|
| `TAccountsPayableSummaryAuditLoader` | `t_accounts_payable_summary` | `{shopNo, supplierNo, transactionMonth, taxRate}` |
| `TAccountsReceivableSummaryAuditLoader` | `t_accounts_receivable_summary` | `{shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag}` |
| `TConsistencyReviewAuditLoader` | `t_consistency_review` | `{shopNo, entryType, entryKey, transactionMonth}` |
| `MSupplierOpeningBalanceAuditLoader` | `m_supplier_opening_balance` | `{shopNo, openingDate, supplierNo}` (bulk fetch は empty) |
| `TMfOauthTokenAuditLoader` | `t_mf_oauth_token` | active client の active token を返す (token_enc は `@AuditExclude`) |
| `MPartnerGroupAuditLoader` | `m_partner_group` | `{partnerGroupId}` (INSERT 時は empty) |
| `TInvoiceAuditLoader` | `t_invoice` | bulk import のため常に empty (戻り値 `InvoiceImportResult` を after に) |

---

## 3. 適用 Service (Phase 2)

| Service | メソッド | table | operation |
|---|---|---|---|
| `TAccountsPayableSummaryService` | `verify` | `t_accounts_payable_summary` | `verify` |
| 〃 | `releaseManualLock` | 〃 | `release_manual_lock` |
| 〃 | `updateMfExport` | 〃 | `mf_export_toggle` |
| `TAccountsReceivableSummaryService` | `verify` | `t_accounts_receivable_summary` | `verify` |
| 〃 | `releaseManualLock` | 〃 | `release_manual_lock` |
| 〃 | `updateMfExport` | 〃 | `mf_export_toggle` |
| `AccountsReceivableBulkVerifyService` | `execute` | `t_accounts_receivable_summary` | `bulk_verify` |
| `ConsistencyReviewService` | `upsert` | `t_consistency_review` | `upsert` |
| 〃 | `delete` | 〃 | `delete` |
| `PaymentMfImportService` | `applyVerification` | `t_accounts_payable_summary` | `payment_mf_apply` |
| `MfOauthService` | `handleCallback` | `t_mf_oauth_token` | `auth_callback` |
| 〃 | `revoke` | 〃 | `revoke` |
| `MfOpeningBalanceService` | `fetchFromMfJournalOne` | `m_supplier_opening_balance` | `mf_fetch` |
| 〃 | `updateManualAdjustment` | 〃 | `manual_adjust` |
| `InvoiceImportService` | `importFromExcel` | `t_invoice` | `import` |
| `MPartnerGroupService` | `create` / `update` / `delete` | `m_partner_group` | `INSERT` / `UPDATE` / `DELETE` |

Phase 2 = 16 メソッド・8 Service。

---

## 4. 機密フィールド除外 (Phase 3)

`@AuditExclude` 付与:
- `MMfOauthClient.clientSecretEnc`
- `TMfOauthToken.accessTokenEnc` / `refreshTokenEnc`
- `TPaymentMfImportHistory.csvBody`
- `TCashbookImportHistory.csvContent`

(計 5 フィールド)

これらは API 経由で返却されないことを事前確認のうえ `@JsonIgnore` 同等の挙動を許容。

---

## 5. 閲覧画面 (Phase 4)

### 5.1 Backend API

`AuditLogController` (`/api/v1/admin/audit-log`、`@PreAuthorize("@loginUserSecurityBean.isAdmin()")`)
- `GET /search` : フィルタ (actor_user_no / operation / target_table / 日付範囲) + ページング
- `GET /{id}` : 詳細 (before/after JSONB 含む)
- `GET /operations` / `GET /tables` : フィルタ用 distinct 値

`AuditLogQueryService` で actor 名の batch JOIN (N+1 回避)。

### 5.2 Frontend

- ルート: `/admin/audit-log` (admin 限定 + サイドバー adminOnly メニュー)
- フィルタ: 対象テーブル / 操作 / From / To
- 一覧: 日時 / 種別 (USER/SYSTEM/BATCH) / 実行者 / 操作 / 対象 / PK / [詳細]
- 詳細 Dialog: PK + before/after を左右並列で `JSON.stringify(value, null, 2)` 表示。reason があれば警告バー表示

---

## 6. AOP オーバーヘッド

| 想定値 | 内訳 |
|---|---|
| 1 操作あたり | +5〜30 ms |
| 内訳 | Jackson `valueToTree` (引数/戻り値の JSON 化) + `repository.save(FinanceAuditLog)` (1 INSERT) |
| 業務影響 | 経理操作は秒単位の手動操作が多く許容範囲 |
| 例外パス | +5 ms 程度 (Jackson + 1 INSERT は同じ) |

ボトルネックになりうるケース:
- `payment_mf_apply` (1 操作で 100+ supplier 上書き) → 戻り値 `VerifyResult` 1 つのシリアライズなので問題なし。Aspect は呼び出し回数ベース (1 回) で課金される
- `bulk_verify` (得意先全件) → 同上、引数は (shopNo, fromDate, toDate) のみで軽量

---

## 7. 残課題 / 将来拡張

| # | 項目 | 優先度 |
|---|---|---|
| F1 | ~~バッチ経由の操作 (集計バッチ等) を補足する Step Listener / Job Listener (T2 案 C 相当)~~ → **G3-M8 で Job summary listener 実装済 (2026-05-06)。§G3-M8 参照。tasklet 個別の細粒度 audit は別タスク** | 完了 (Job summary)、tasklet 細粒度は中 |
| F2 | 案 A (Entity Listener) との併用で 直接 SQL UPDATE も網羅 | 低 (現状アプリ経由のみ想定) |
| F3 | finance_audit_log のパーティション / アーカイブ (7 年保存要件) | 中 (ストレージ試算後) |
| F4 | 閲覧履歴 (`*_access_log`) — 個人情報アクセス証跡 | 低 (内部統制要件次第) |
| F5 | 月次決算締めテーブル `t_accounting_closure` で締め後 UPDATE を 409 化 | 中 (旧 M7 設計を踏襲) |
| F6 | ~~before snapshot を PK で再 fetch する `AuditEntityLoader` 追加~~ → **C5 で実装済 (2026-05-04)** | 完了 |
| F7 | ~~バッチ経由 Service 呼び出し補足 (現状 web プロファイル限定、@AuditLog 起動せず)~~ → **G3-M8 で Job summary listener により補足。tasklet 個別 @AuditLog は SecurityContext 不在で起動しないため別途検討** | 完了 (Job 単位)、tasklet 単位は中 |
| F8 | `finance_audit_log` のパーティション (occurred_at by month) | 中 |
| F9 | `actor_type='SYSTEM'` 系 (= scheduler / cron 等の自動起動で userNo 不在時の区別) | 低 |
| F10 | tasklet 個別の細粒度 audit (Step Listener + 操作意味付け) | 中 |

---

## G3-M8: Spring Batch Job summary audit (2026-05-06)

### G3-M8.1 背景

§2.3 の `FinanceAuditAspect` は **`@ApplicationType("web")`** で web プロファイル限定だった。これは `SecurityContext` / `HttpServletRequest` の取得が前提であり、バッチ起動 (= `BatchApplication`、`@Profile("batch")`) では Aspect 自体が Bean 登録されない。

結果として以下が監査対象外になっていた:

- `accountsPayableSummary` / `accountsPayableAggregation` / `accountsPayableVerification` / `accountsPayableBackfill` 等の集計再生成系
- `smilePaymentImport` / `purchaseFileImport` / `goodsFileImport` の取込系
- `bCartProductsImport` / `bCartOrderImport` 等の B-CART 系
- `partnerPriceChangePlanCreate` (見積価格反映) 等

このうち集計バッチは `t_accounts_payable_summary` を直接 UPDATE/INSERT するため、誰がいつ走らせたか (= 手動再集計だったのか定時実行だったのか) の証跡が無かった。

### G3-M8.2 採用方式: 案 A (Job summary)

| 案 | 粒度 | 工数 | 課題 |
|---|---|---|---|
| **A: JobExecutionListener** | Job 起動/終了の 1 row ずつ (= 2 row / 1 起動) | 小 (新規 Listener + 自動配線) | 個別 tasklet の意味付けは別途必要 |
| B: StepExecutionListener | Step 単位 (50+ tasklet) | 中 | log volume 大、ビジネス意味の付与が困難 |
| C: 各 tasklet で `@AuditLog` 相当 | tasklet 単位 (50+) | 大 | SecurityContext 不在のため Aspect 起動不可、別経路必要 |

今回は <b>案 A のみ実装</b>。tasklet 個別の細粒度 audit は将来課題 (F10)。

### G3-M8.3 実装

```
backend/src/main/java/jp/co/oda32/audit/
├── BatchAuditJobListener.java          (JobExecutionListener、@Component)
└── BatchAuditListenerAutoRegistrar.java (BeanPostProcessor、全 Job 自動配線)
```

- **`BatchAuditJobListener`**: `JobExecutionListener#beforeJob/afterJob` を実装し、`FinanceAuditWriter#write` (REQUIRES_NEW) 経由で 1 row ずつ書込む
- **`BatchAuditListenerAutoRegistrar`**: `BeanPostProcessor` で全 `Job` Bean (= 19 個) を捕捉し、`AbstractJob#registerJobExecutionListener` で listener を**追加**登録する。各 *JobConfig.java は変更不要 (= 既存の `JobStartEndListener` 等は維持)

**fail-open**: `writer.write` が例外を投げても WARN ログのみで握り潰す (= バッチ自身を失敗させない)。audit は副次責務。

### G3-M8.4 記録仕様

| 列 | beforeJob (起動時) | afterJob (終了時) |
|---|---|---|
| `occurred_at` | `now()` | `now()` |
| `actor_type` | `BATCH` | `BATCH` |
| `actor_user_no` | jobParameters の `userNo` (Long/Integer) があれば、なければ NULL | 〃 |
| `operation` | `batch_run_started` | `batch_run_finished` |
| `target_table` | `jobName` (例: `accountsPayableSummary`) | 〃 |
| `target_pk` | `{jobName, jobInstanceId, params:{...}}` | 〃 (同じ) |
| `before_values` | jobParameters JSON | NULL |
| `after_values` | NULL | `{exitCode, status, startTime, endTime, totalReadCount, totalWriteCount, totalSkipCount, stepCount}` |
| `reason` | NULL | `exitDescription` (FAILED 時、500 文字で切り詰め) または `exitCode` |
| `source_ip` / `user_agent` | NULL | NULL |

**target_table = jobName** にしているのは既存 distinct 一覧 (`/admin/audit-log` フィルタ) で「誰が `accountsPayableSummary` を何回起動したか」をそのまま絞り込めるようにするため。集計対象テーブル (= `t_accounts_payable_summary` 等) ではなく **Job 名** を target にすることで、web 経由の Service audit (= table 名 target) と用途が明確に分かれる。

### G3-M8.5 actor_type の役割分離

| `actor_type` | 起動経路 | actor 解決 | 記録元 |
|---|---|---|---|
| `USER` | Web UI (admin/operator) | SecurityContext の `LoginUser` | `FinanceAuditAspect` (@AuditLog AOP) |
| `SYSTEM` | Web 経由だが SecurityContext 取得不可 (= 内部 API 呼び出し等) | NULL | `FinanceAuditAspect` (@AuditLog AOP、認証不在ケース) |
| `BATCH` | Spring Batch Job 起動 | `jobParameters.userNo` (手動起動時のみ)、なければ NULL | `BatchAuditJobListener` (G3-M8) |

将来的に scheduler / cron での自動起動を追加する場合は `actor_type='SYSTEM'` を `BatchAuditJobListener` 側でも採用するかを検討 (= F9)。

### G3-M8.6 import history との役割分離

`t_payment_mf_import_history` / `t_cashbook_import_history` 等は **業務メタデータ** (= ファイル名、ハッシュ、検証結果、適用 supplier 数) を記録する目的のテーブルであり、**audit log ではない**。役割は明確に分離する:

| 軸 | finance_audit_log | t_*_import_history |
|---|---|---|
| 目的 | 操作証跡 (誰がいつ何をした) | 業務メタデータ (ファイル取込履歴) |
| 行粒度 | 操作 1 回 = 1 row | 取込ファイル 1 つ = 1 row |
| 保存内容 | before / after / actor / operation | ファイル名 / hash / 適用件数 / status |
| クエリ | actor / 日時範囲で監査 | 取込状態の業務確認 |
| 削除可否 | 7 年保存 (改竄不可) | 業務上の都合で削除可 |

audit log と import history を **両方** 残すことで、「誰が手動再取込ボタンを押したか」(audit) と「そのとき何を取り込んだか」(history) を別々の責務で追跡できる。

### G3-M8.7 テスト

`BatchAuditJobListenerTest` (7 件) + `BatchAuditListenerAutoRegistrarTest` (4 件) すべて PASS:

- before / after で想定どおりの引数で `writer.write` が呼ばれる
- `userNo` jobParameter からの actor_user_no 抽出
- exitDescription の 500 文字切り詰め
- `writer` 例外時に listener が例外を伝播させない (fail-open)
- `JobInstance` 不在時の `(unknown)` フォールバック
- BeanPostProcessor が `AbstractJob` には登録、それ以外には登録しないこと
- Listener Bean 不在時 noop

### G3-M8.8 残課題

- **F10**: tasklet 個別の細粒度 audit (= どの step が何件 INSERT/UPDATE したか)。SecurityContext 不在のため `@AuditLog` AOP は使えない。Step Listener + 各 Service への明示的な書込み呼び出し、または直接 SQL ログ (= Hibernate event listener) のいずれかが必要。
- **F9**: `actor_type='SYSTEM'` の運用導入 (現状 `BatchAuditJobListener` は常に `BATCH`)。scheduler / cron 経路を追加した時に検討。

---

## 8. 関連設計書

- `design-audit-trail-accounts-payable.md` (旧ドラフト、買掛側のみ)
- `design-accounts-payable.md` / `design-accounts-payable-ledger.md`
- `design-consistency-review.md`
- `design-payment-mf-import.md`
- `design-mf-integration.md`

---

## 9. リリースチェックリスト

- [x] V036 migration 適用
- [x] backend compileJava + test 全 PASS
- [x] frontend tsc PASS
- [ ] 実 DB に対する E2E (実際にログが書き込まれることを確認、推奨 1 ケース)
- [ ] admin login で `/admin/audit-log` UI 表示・フィルタ・詳細 dialog 動作確認
- [ ] 非 admin でアクセスして 403 が返ることを確認
- [x] G3-M8: `BatchAuditJobListener` + `BatchAuditListenerAutoRegistrar` 実装、test 全 PASS (11 件)
- [ ] G3-M8: 実バッチ (= 例えば `smilePaymentImport`) を手動起動して `actor_type='BATCH'` の row が 2 件記録されることを確認
