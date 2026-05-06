# 買掛金管理 監査証跡 (Audit Trail) 設計書

> **NOTE (2026-05-04)**: 本書は買掛側専用の **旧ドラフト** です。
> 全 finance を対象に AOP (案 B) で実装した版は
> [`design-finance-audit-log.md`](./design-finance-audit-log.md) を参照してください。
> 本書の M2 (Hibernate `@EntityListeners`) は採用せず、`@AuditLog` Service AOP に切替えました。

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes` (ドラフト)
関連設計書:
- `design-accounts-payable.md` (20日締め買掛金集計)
- `design-accounts-payable-ledger.md` (買掛帳画面 / 2026-04-22)
- `design-supplier-partner-ledger-balance.md` (累積残高 Phase A/B')
- `design-phase-b-prime-payment-settled.md` (payment_settled 列)
- `design-payment-mf-import.md` (振込明細 Excel 取込)
- `design-payment-mf-aux-rows.md` (補助行 v2)

---

## 1. 背景と目的

### 1.1 法令・内部統制の要求
| 要求源 | 要点 | 保存期間 |
|---|---|---|
| 電子帳簿保存法 (2024年改正) | 電子取引データは「訂正・削除の履歴が残る or 訂正削除できない」要件 | 7 年 (欠損金繰越で 10 年) |
| 法人税法 基本通達 | 帳簿書類の保存義務 | 7 年 (繰越欠損期は 10 年) |
| インボイス制度 | 適格請求書の保存、仕入税額控除の根拠 | 7 年 |
| 内部統制 (J-SOX 相当, 弊社規程) | 職務分掌・変更管理の証跡 | 社内規程 7 年 |

### 1.2 現状の監査証跡ギャップ
| 領域 | 現状 | ギャップ |
|---|---|---|
| `t_accounts_payable_summary` | `verified_manually` / `verification_note` / `mf_export_enabled` のみ最終状態 | **誰が・いつ・何を変えたか履歴がない**。バッチ (aggregation / backfill / verification) も上書き |
| 振込明細 Excel 取込 | `t_payment_mf_import_history` に CSV 本体 + 件数はあり | Summary 行への反映差分が追えない (Excel 1 本で 100+ supplier の verifiedAmount が書き換わる) |
| 買掛→仕入仕訳 CSV 出力 | ログのみ (`log.info` で rowCount / skippedSuppliers) | DB 履歴がない。再取得不可 |
| 買掛金一覧 / 買掛帳画面 | 閲覧ログなし | 誰が閲覧したか追跡不能 |
| 月次決算締め | 論理的な締め日 (20 日) はあるが、**システム的ロック機構はない** | 締めた後に手動確定が上書きされても検知できない |

### 1.3 目的
1. **変更履歴**: `t_accounts_payable_summary` の全変更 (手動 + バッチ) を時系列で追跡
2. **連携履歴**: CSV 出力 / MF 取込など「外部に出た・入った」事象を一元化
3. **閲覧履歴**: 買掛金データを誰が見たか (税務調査対応、個人情報アクセス証跡)
4. **締め後ロック**: 月次決算確定後の不正な遡及修正を防止
5. **UI 提示**: admin が「どの行がいつ誰に直された」かを即座に遡れる UI

---

## 2. スコープ

### 2.1 MUST (第 1 版 / V028 で完結)
| # | 項目 |
|---|---|
| M1 | 変更履歴テーブル `t_accounts_payable_summary_history` 新設 (jsonb スナップショット) |
| M2 | Hibernate `@EntityListeners` で全 `save/update/delete` を自動記録 |
| M3 | `LoginUserUtil` + `@StepScope` バッチ `batchId` を `changed_by` に記録 |
| M4 | 集計バッチ (`AccountsPayableAggregationTasklet`) は **差分ありのみ記録** (§5.4) |
| M5 | 買掛金一覧・買掛帳に「修正履歴」ポップアップ (admin のみ表示) |
| M6 | API `GET /api/v1/finance/accounts-payable/{pk}/history` (admin only) |
| M7 | 月次決算締めテーブル `t_accounting_closure` 新設 + 締め後の手動編集 API を 409 Conflict |

### 2.2 SHOULD (v2 以降)
| # | 項目 | 先送り理由 |
|---|---|---|
| S1 | 閲覧履歴テーブル `t_accounts_payable_access_log` | MUST 側のストレージ試算後に要否判断 (§8) |
| S2 | 連携履歴集約ビュー `v_finance_integration_log` (既存 t_payment_mf_import_history + 新 t_purchase_journal_export_log) | まず individual ログを書き溜めてからビュー化 |
| S3 | 履歴 CSV/PDF エクスポート | 第 1 版は画面で確認 |
| S4 | 売掛金側 (`t_accounts_receivable_summary`) への横展開 | 買掛側で枠組み固めてから同設計で横展開 (§12.5) |
| S5 | Diff 可視化 (before/after の差分だけ色付け) | 第 1 版は before_snapshot + after_snapshot 並列表示 |

### 2.3 OUT-OF-SCOPE
- SMILE / B-CART / MF 側システムへの監査証跡連携
- リアルタイム通知 (Slack 等)
- 改ざん検知のハッシュチェーン (ブロックチェーン的仕組み)
- 既存 CustomService `insert/update/delete` 以外のエンティティへの横展開 (売掛は v2)
- GDPR 右忘却対応 (日本法人のみ、個人情報薄いため当面不要)

---

## 3. テーブル設計

### 3.1 V028__create_accounts_payable_audit_tables.sql

```sql
-- =============================================================================
-- 2026-04-22: 買掛金監査証跡テーブル (Phase C / Audit Trail)
-- 設計書: claudedocs/design-audit-trail-accounts-payable.md
-- =============================================================================

-- --- 3.1.1 変更履歴テーブル -----------------------------------------------------
CREATE TABLE t_accounts_payable_summary_history (
    id                  BIGSERIAL      PRIMARY KEY,
    -- 元レコードの複合 PK (NULL 不可、DELETE 後も参照キーとして残す)
    shop_no             INTEGER        NOT NULL,
    supplier_no         INTEGER        NOT NULL,
    transaction_month   DATE           NOT NULL,
    tax_rate            NUMERIC(5, 2)  NOT NULL,
    -- 誰が・いつ・何を
    change_type         VARCHAR(10)    NOT NULL CHECK (change_type IN ('CREATE', 'UPDATE', 'DELETE')),
    changed_by          INTEGER        NOT NULL,   -- user_no (手動) / batch_id (バッチ)
    changed_by_kind     VARCHAR(10)    NOT NULL CHECK (changed_by_kind IN ('USER', 'BATCH')),
    changed_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source              VARCHAR(50)    NOT NULL,   -- 'MANUAL_VERIFY', 'EXCEL_IMPORT', 'AGGREGATION_BATCH', 'BACKFILL_BATCH', 'VERIFICATION_BATCH', 'MF_EXPORT_TOGGLE', 'RELEASE_MANUAL_LOCK'
    -- スナップショット (jsonb)
    before_snapshot     JSONB          NULL,       -- CREATE では NULL
    after_snapshot      JSONB          NULL,       -- DELETE では NULL
    changed_fields      TEXT[]         NOT NULL,   -- 変更された列名 (CREATE/DELETE は全列)
    -- コンテキスト
    correlation_id      UUID           NULL,       -- 同一トランザクション / Excel 1 取込をまとめる ID
    note                VARCHAR(500)   NULL        -- オプションのコメント (source に依る)
);

COMMENT ON TABLE  t_accounts_payable_summary_history IS '買掛金集計テーブル変更履歴 (電帳法 / 内部統制対応)';
COMMENT ON COLUMN t_accounts_payable_summary_history.changed_by_kind IS 'USER = m_login_user.login_user_no, BATCH = Spring Batch job (batchId)';
COMMENT ON COLUMN t_accounts_payable_summary_history.source IS '変更トリガ種別 (§5.2)';
COMMENT ON COLUMN t_accounts_payable_summary_history.correlation_id IS 'Excel 1 ファイル取込や CSV 1 回出力など、まとめて扱う単位';
COMMENT ON COLUMN t_accounts_payable_summary_history.changed_fields IS '変更された列名の配列。AGGREGATION_BATCH は差分ありのみ記録 (§5.4)';

-- 典型クエリは「ある行の時系列」「ある月全体の変更」「ある correlation の内訳」
CREATE INDEX idx_apsh_row      ON t_accounts_payable_summary_history (shop_no, supplier_no, transaction_month, tax_rate, changed_at DESC);
CREATE INDEX idx_apsh_month    ON t_accounts_payable_summary_history (transaction_month, changed_at DESC);
CREATE INDEX idx_apsh_corr     ON t_accounts_payable_summary_history (correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_apsh_source   ON t_accounts_payable_summary_history (source, changed_at DESC);
-- after_snapshot を jsonb で絞り込みたい場合のため GIN を 1 本
CREATE INDEX idx_apsh_after_gin ON t_accounts_payable_summary_history USING GIN (after_snapshot jsonb_path_ops);


-- --- 3.1.2 閲覧履歴テーブル (SHOULD スコープ、v2 で有効化) ---------------------
-- MUST では作成のみ、書き込みロジックは v2 で追加
CREATE TABLE t_accounts_payable_access_log (
    id                  BIGSERIAL      PRIMARY KEY,
    user_no             INTEGER        NOT NULL,
    accessed_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    endpoint            VARCHAR(200)   NOT NULL,   -- e.g. 'GET /api/v1/finance/accounts-payable'
    shop_no             INTEGER        NULL,
    supplier_no         INTEGER        NULL,
    transaction_month   DATE           NULL,
    query_string        TEXT           NULL,       -- クエリパラメータ raw (個人情報なし想定)
    result_count        INTEGER        NULL,       -- 返却件数 (list の場合)
    remote_addr         VARCHAR(45)    NULL        -- IPv4/IPv6
);

CREATE INDEX idx_apal_user_time  ON t_accounts_payable_access_log (user_no, accessed_at DESC);
CREATE INDEX idx_apal_time       ON t_accounts_payable_access_log (accessed_at DESC);


-- --- 3.1.3 仕入仕訳 CSV 出力履歴 -----------------------------------------------
-- 既存 t_payment_mf_import_history と対になる「出力側」履歴
CREATE TABLE t_purchase_journal_export_log (
    id                  BIGSERIAL      PRIMARY KEY,
    transaction_month   DATE           NOT NULL,
    exported_by         INTEGER        NOT NULL,
    exported_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    force_export        BOOLEAN        NOT NULL DEFAULT FALSE,
    row_count           INTEGER        NOT NULL,
    total_amount        NUMERIC        NOT NULL,
    skipped_suppliers   TEXT           NULL,      -- 'A|B|C' pipe-separated, header 由来
    csv_filename        VARCHAR(200)   NOT NULL,
    csv_body            BYTEA          NULL,      -- 再取得用 (MUST)
    correlation_id      UUID           NOT NULL   -- summary_history の correlation_id と連結可能
);

CREATE INDEX idx_pjxl_month ON t_purchase_journal_export_log (transaction_month, exported_at DESC);
CREATE INDEX idx_pjxl_corr  ON t_purchase_journal_export_log (correlation_id);


-- --- 3.1.4 月次決算締めテーブル -----------------------------------------------
CREATE TABLE t_accounting_closure (
    shop_no             INTEGER        NOT NULL,
    closure_month       DATE           NOT NULL,  -- 20 日締め (e.g. 2026-03-20)
    closure_kind        VARCHAR(20)    NOT NULL CHECK (closure_kind IN ('PAYABLE', 'RECEIVABLE', 'BOTH')),
    closed_by           INTEGER        NOT NULL,
    closed_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note                VARCHAR(500)   NULL,
    -- ソフトクローズ (解除可能). 解除する場合は別 row を入れるのではなく is_open=true に戻す
    is_open             BOOLEAN        NOT NULL DEFAULT FALSE,
    reopened_by         INTEGER        NULL,
    reopened_at         TIMESTAMP      NULL,
    reopen_reason       VARCHAR(500)   NULL,
    PRIMARY KEY (shop_no, closure_month, closure_kind)
);

COMMENT ON TABLE  t_accounting_closure IS '月次決算締め。is_open=false の行について編集 API は 409 Conflict を返す';
COMMENT ON COLUMN t_accounting_closure.is_open IS 'false=締め済 (編集不可), true=再オープン (admin で解除)';

CREATE INDEX idx_apc_month ON t_accounting_closure (closure_month, shop_no);
```

### 3.2 既存スキーマへの破壊的変更: **なし**
- `t_accounts_payable_summary` は **一切触らない**
- 既存 `t_payment_mf_import_history` も触らない (連携履歴集約ビューは SHOULD で v2)
- 追加のみの migration → Phase A/B'/B''light スキーマを完全温存

---

## 4. Entity / Service 設計

### 4.1 Entity

```java
// TAccountsPayableSummaryHistory.java
@Entity
@Table(name = "t_accounts_payable_summary_history")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TAccountsPayableSummaryHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_no", nullable = false)            private Integer shopNo;
    @Column(name = "supplier_no", nullable = false)        private Integer supplierNo;
    @Column(name = "transaction_month", nullable = false)  private LocalDate transactionMonth;
    @Column(name = "tax_rate", nullable = false)           private BigDecimal taxRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 10)
    private ChangeType changeType;   // CREATE / UPDATE / DELETE

    @Column(name = "changed_by",      nullable = false) private Integer changedBy;
    @Column(name = "changed_by_kind", nullable = false, length = 10) private String changedByKind; // USER / BATCH
    @Column(name = "changed_at",      nullable = false) private LocalDateTime changedAt;
    @Column(name = "source",          nullable = false, length = 50) private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", columnDefinition = "jsonb")
    private String beforeSnapshot;   // JSON 文字列。Jackson で serialize/deserialize

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", columnDefinition = "jsonb")
    private String afterSnapshot;

    @Type(StringArrayType.class)     // hypersistence-utils-hibernate-63
    @Column(name = "changed_fields", columnDefinition = "text[]")
    private String[] changedFields;

    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "note", length = 500) private String note;

    public enum ChangeType { CREATE, UPDATE, DELETE }
}
```

**注**: 既存 `BCartChangeHistory` が同じ jsonb pattern 実装済 → それに合わせる。

### 4.2 Service

```
PayableAuditService                    # 履歴 write/read のファサード
├─ recordChange(before, after, ctx)   # 明示呼出
├─ listHistoryByRow(pk)               # 1 行の時系列
├─ listHistoryByMonth(month, filter)  # 1 月の全履歴
└─ listHistoryByCorrelation(corrId)

PayableAuditListener (EntityListener)  # JPA @PostPersist/@PostUpdate/@PostRemove
AccountingClosureService               # 締め管理
├─ close(shopNo, month, kind)
├─ reopen(...)
└─ assertOpen(shopNo, month)          # Service 層で編集前に呼ぶ
```

### 4.3 AuditContext (ThreadLocal)
明示呼出とバッチの両方に対応するため、Spring の request-scope に依存せず ThreadLocal で context を保持:

```java
public final class AuditContext {
    private static final ThreadLocal<AuditContext> CTX = new ThreadLocal<>();

    private String source;              // AGGREGATION_BATCH / EXCEL_IMPORT / MANUAL_VERIFY ...
    private UUID correlationId;
    private Integer overrideUserNo;     // バッチ用 (LoginUser 不在時)
    private String changedByKind;       // USER / BATCH
    private boolean diffOnly;           // AGGREGATION_BATCH は true

    public static void set(AuditContext ctx) { CTX.set(ctx); }
    public static AuditContext get()         { return CTX.get(); }
    public static void clear()               { CTX.remove(); }

    public static <T> T withContext(AuditContext ctx, Supplier<T> action) {
        set(ctx);
        try { return action.get(); }
        finally { clear(); }
    }
}
```

- API handler は `@Around` AOP で MANUAL_VERIFY / EXCEL_IMPORT 等 source を自動設定
- Tasklet は `execute()` 冒頭で `AuditContext.withContext(...)` ラップ

---

## 5. 履歴記録のタイミング

### 5.1 アプローチ比較

| 方式 | メリット | デメリット | 採否 |
|---|---|---|---|
| A. JPA `@EntityListeners` (`@PostPersist/Update/Remove`) | 網羅性: save 経路を一切取りこぼさない。Entity に 1 行足すだけ | - `@PostUpdate` は **dirty checking 後**。before_snapshot を取るには `@PostLoad` で cache が必要<br>- `saveAll()` バッチ更新で N+1<br>- `deleteAllInBatch` 等の bulk 系では発火しない | **採用 (+ workaround)** |
| B. Service 層明示呼出 | before/after を呼び出し側が確実に持てる。bulk 系も対応 | 呼び忘れリスク。CustomService `insert/update/delete` を通らない直接 save を拾えない | 補完 |
| C. Hibernate Envers | 標準機能、成熟 | `_AUD` テーブルが独自。jsonb snapshot が取れない。revision_type は INT で source 文字列を持てない | 不採用 |
| D. DB トリガー | 最も漏れない、バッチ bulk も拾える | changed_by (user_no) を DB に渡す session var が必要。Spring 側の制御と乖離、テスト困難 | 不採用 |

**採用: A + B 併用**
- 基本は JPA Listener でオート記録
- バッチの saveAll は Listener が発火するが `diffOnly=true` + ノイズ削減ロジック (§5.4)
- `deleteAll` / `deleteAllInBatch` を使う箇所は **明示呼出に切替** (§5.5)

### 5.2 `source` 一覧 (有効値)
| source | トリガ元 | changed_by_kind | 典型 change_type |
|---|---|---|---|
| `MANUAL_VERIFY`         | `FinanceController#verifyAccountsPayable`         | USER  | UPDATE |
| `RELEASE_MANUAL_LOCK`   | `FinanceController#releaseManualLock`             | USER  | UPDATE |
| `MF_EXPORT_TOGGLE`      | `FinanceController#toggleMfExport`                | USER  | UPDATE |
| `EXCEL_IMPORT`          | `PaymentMfImportService#applyVerification`        | USER  | UPDATE (最多) |
| `PURCHASE_JOURNAL_EXPORT` | `FinanceController#exportPurchaseJournalCsv` (markExported) | USER | UPDATE |
| `AGGREGATION_BATCH`     | `AccountsPayableAggregationTasklet`               | BATCH | CREATE / UPDATE / DELETE |
| `BACKFILL_BATCH`        | `AccountsPayableBackfillTasklet`                  | BATCH | UPDATE |
| `VERIFICATION_BATCH`    | `AccountsPayableVerificationTasklet`              | BATCH | UPDATE |
| `PAYMENT_SETTLED_BATCH` | `PayableMonthlyAggregator#applyPaymentSettled` 経由 | BATCH | UPDATE |

### 5.3 before_snapshot を取る workaround (Listener 採用時)

```java
@MappedSuperclass
@EntityListeners(PayableAuditListener.class)
public class TAccountsPayableSummary { ... }

public class PayableAuditListener {

    @PostLoad
    public void onLoad(TAccountsPayableSummary e) {
        // load 時にスナップショットを JSON として Transient フィールドに保存
        AuditSnapshotHolder.remember(e);
    }

    @PostPersist
    public void onPersist(TAccountsPayableSummary e) {
        record(e, CREATE, null, toJson(e));
    }

    @PostUpdate
    public void onUpdate(TAccountsPayableSummary e) {
        String before = AuditSnapshotHolder.getBefore(e);  // PostLoad で記憶した JSON
        String after  = toJson(e);
        if (AuditContext.get() != null && AuditContext.get().isDiffOnly()
                && !hasFunctionalDiff(before, after)) {
            return;   // §5.4 差分なしスキップ
        }
        record(e, UPDATE, before, after);
    }

    @PostRemove
    public void onRemove(TAccountsPayableSummary e) {
        record(e, DELETE, AuditSnapshotHolder.getBefore(e), null);
    }
}
```

- `AuditSnapshotHolder` は `ThreadLocal<IdentityHashMap<Entity, String>>`。リクエスト/バッチ境界で clear
- `toJson` は ObjectMapper + `@JsonPropertyOrder` で安定順序 (diff 判定のため)

### 5.4 バッチ大量更新の扱い

| Tasklet | 方針 | 理由 |
|---|---|---|
| `AccountsPayableAggregationTasklet` | **差分あり行のみ記録** (diffOnly=true) | 月次集計で全 supplier を毎回 save するため、意味のない「同値更新」ノイズを排除 |
| `AccountsPayableBackfillTasklet`   | 差分あり行のみ記録 + correlation_id で 1 回の backfill をまとめる | 複数月再集計で数千行 → correlation で画面フィルタ可能に |
| `AccountsPayableVerificationTasklet` | **全行記録** | 検証結果 (verification_result / payment_difference) が変わらない場合でもタイムスタンプ記録が意味を持つ (税務調査時「この日検証済み」の証拠) |
| `PaymentMfImportService#applyVerification` (Excel 取込) | **全行記録** (correlation_id = Excel 1 ファイル) | 1 Excel = 1 監査イベントとして展開可能にする |

**差分あり判定 (`hasFunctionalDiff`)**:
- 監視対象列を絞る: `verificationResult`, `paymentDifference`, `taxIncludedAmountChange`, `taxExcludedAmountChange`, `openingBalance*`, `paymentAmountSettled*`, `mfExportEnabled`, `verifiedAmount`, `verifiedManually`, `verificationNote`, `mfTransferDate`, `isPaymentOnly`
- 数値は `compareTo` (scale 非依存)、Boolean/String/Date は `equals`

### 5.5 bulk delete の扱い
`AccountsPayableAggregationTasklet` の stale-delete (`repository.deleteAll(stale)`) は Listener が発火する (`deleteAll` は個別 `remove` を回す)。**`deleteAllInBatch` は使用禁止** とし、使う場合は事前に `recordBulkDelete()` を明示呼出する規約をコーディング規約 (CLAUDE.md) に追記。

---

## 6. API 設計

### 6.1 新規エンドポイント

```
GET  /api/v1/finance/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/history
GET  /api/v1/finance/accounts-payable/history                 (クエリで月/supplier フィルタ)
GET  /api/v1/finance/accounts-payable/history/{correlationId} (1 回の Excel 取込全件)
GET  /api/v1/finance/audit/closures                           (月次締め一覧)
POST /api/v1/finance/audit/closures                           (締め)
POST /api/v1/finance/audit/closures/{shopNo}/{month}/{kind}/reopen
```

**全エンドポイント `@PreAuthorize("hasRole('ADMIN')")`**

### 6.2 Response DTO 例

```json
// GET .../history (1 行の時系列)
{
  "pk": {"shopNo":1,"supplierNo":303,"transactionMonth":"2026-03-20","taxRate":10.00},
  "totalCount": 5,
  "items": [
    {
      "id": 120345,
      "changeType": "UPDATE",
      "source": "EXCEL_IMPORT",
      "changedBy": 3,
      "changedByName": "k_oda",
      "changedByKind": "USER",
      "changedAt": "2026-04-15T10:22:13",
      "correlationId": "6a1b...",
      "changedFields": ["verifiedAmount", "verificationResult", "paymentDifference", "mfExportEnabled"],
      "before": {"verifiedAmount": null, "verificationResult": null, ...},
      "after":  {"verifiedAmount": 123456, "verificationResult": 1, ...},
      "note": "Excel: 2026-04-05_payment_detail.xlsx"
    },
    ...
  ]
}
```

- `before` / `after` は **frontend で diff 可視化するため変更列のみ抽出した map** (jsonb 全量ではない)
- Full snapshot が必要なら `?full=true` で返す (admin only)
- `changedByName`: backend で `m_login_user` を JOIN して name 展開 (バッチは `[batch:accountsPayableAggregation]` 等の擬似名)

### 6.3 既存 API への影響: **なし**
- `PUT .../verify`, `DELETE .../manual-lock`, `PATCH .../mf-export` は I/F 変更なし
- 内部で Service が Closure チェック → 閉じていれば 409 Conflict を返す **(変更)**

```json
// 409 Conflict 例
{"message":"2026-03-20 締めのデータは締め済のため編集できません。再オープンが必要です。","closedAt":"2026-04-22T15:30:00"}
```

---

## 7. UI 設計

### 7.1 買掛金一覧 (`/finance/accounts-payable`)

**行に「履歴」アイコン追加** (admin のみ表示):

```
| 仕入先   | 税率  | change   | verified | 検証 | MF出力 | 履歴 |
| 花王プロ | 10%  | 123,456  | 123,456  | ✅  | ON    |  🕒  |   ← click
```

- `🕒` クリック → Sheet (shadcn/ui) が右から slide-in
- Sheet ヘッダ: 仕入先名 / 取引月 / 税率 / 全 N 件
- Timeline (shadcn/ui には無いので Tailwind 自前): 上から新→旧 (降順)
- 各イベントカード:
  - 左カラム: アイコン (source 別、例: `FileSpreadsheet` Excel、`Cpu` バッチ、`User` 手動)
  - 右カラム: 「誰が」「いつ」「何を」
  - 「何を」は変更列の before → after を縦並べ (数値は千円区切り、緑/赤で増減表示)

### 7.2 買掛帳 (`/finance/accounts-payable-ledger`)

**行クリックで既存の月ドリルダウンと併設して「修正履歴」タブを追加**:

```
┌ 2026-03-20 (行) ──────────────────────────┐
│ [内訳] [MF比較] [修正履歴]  ← 新タブ        │
└───────────────────────────────────────┘
```

「修正履歴」タブ = 同月 tax_rate=all の履歴をタイムラインで表示。

### 7.3 月次締めバッジ

- 買掛金一覧の取引月セレクタ右に `Badge`: 「🔒 2026-03-20 締め済」 / 「✅ 2026-04-20 オープン」
- admin は Badge クリック → 締め / 再オープンダイアログ

### 7.4 correlation フィルタ

- Timeline の `correlation_id` クリック → 同じ Excel / 同じバッチ由来の他行もフィルタ表示
- 「この取込を全部見る」導線

---

## 8. パフォーマンス・ストレージ

### 8.1 年間行数試算

| 源 | 頻度 | 1 回あたり行数 | 年間行数 |
|---|---|---|---|
| AGGREGATION_BATCH (差分あり) | 月 1 回 (20 日) × 12 | 約 500 supplier × 2 税率 × 差分率 30% = 300 | **3,600** |
| BACKFILL_BATCH     | 年数回 (調整用) | 24 ヶ月 × 300 = 7,200 | **21,600** (年 3 回想定) |
| VERIFICATION_BATCH | 月 1 回 (全行) | 1,000 | **12,000** |
| EXCEL_IMPORT       | 月 2 回 (5日 / 20日) | 100 supplier ≒ 200 (税率別) | **4,800** |
| MANUAL_VERIFY / RELEASE / MF_TOGGLE | 日 10 件想定 | 1 | **3,650** |
| PURCHASE_JOURNAL_EXPORT markExported | 月 1 回 | 500 | **6,000** |
| 合計 | | | **約 51,650 行/年** |

**7 年で約 36 万行** (+ 余裕見て 50 万行想定)。

### 8.2 ストレージ試算

- 1 行あたり: jsonb 2 つ × 平均 600 bytes + メタ 200 bytes ≒ **1.4 KB**
- 50 万行 × 1.4 KB ≒ **700 MB** (7 年分)
- インデックス含めて **約 1.2 GB**

PostgreSQL 17 の 1 DB としては無視できるサイズ。ただし `jsonb` GIN インデックスが肥大化しがちなので、**半年に 1 度 REINDEX を運用手順に追加**。

### 8.3 パーティショニング方針
- 初版は無し (50 万行なら通常 B-tree で十分)
- 200 万行超過 (≒ 30 年) になる想定があれば `PARTITION BY RANGE (changed_at)` を次版で検討

### 8.4 クエリ性能
最頻クエリ = 「ある 1 行の履歴」→ `idx_apsh_row` で O(log N) + 該当件数分 fetch。通常 1 行あたり 5~20 件 → <10ms。

---

## 9. セキュリティ

| 項目 | 方針 |
|---|---|
| 履歴 GET API | `@PreAuthorize("hasRole('ADMIN')")` |
| 履歴テーブル直接更新 | **なし**。Service 経由でのみ INSERT、UPDATE/DELETE は禁止 (DBA が SQL 直打ちする例外ケースのみ) |
| 削除ポリシー | **物理削除禁止**。電帳法 7 年を満たすまで保持。10 年後の削除は cron batch を別途作成 (今回対象外) |
| jsonb 内の個人情報 | `supplierCode`/`supplierName` は事業者情報のため問題なし。個人名は含まれない |
| 閲覧履歴 (SHOULD) | `remote_addr` に IP を保存 → プライバシーポリシーで明示要 |
| 月次締め操作 | `hasRole('ADMIN')` + 理由 (`note`) 必須 |
| 改ざん抑止 | DB ユーザを分離し、アプリ用ユーザには `UPDATE`/`DELETE t_accounts_payable_summary_history` を GRANT しない (PG 権限運用) |

---

## 10. 既存機能への影響

| 既存機能 | 影響 | 対応 |
|---|---|---|
| `TAccountsPayableSummary` エンティティ | `@EntityListeners` 1 行追加のみ | 互換 |
| `TAccountsPayableSummaryService.save/saveAll` | Listener 経由で history に insert が増える | saveAll 100 行で +100 insert。バッチは許容 (年 1-2 回数秒増 = 無視) |
| `AccountsPayableAggregationTasklet` | AuditContext でラップ | 既存 tx 内で history も書かれる。ロールバック整合性 OK |
| `PaymentMfImportService#applyVerification` | AuditContext (source=EXCEL_IMPORT) でラップ | 同上 |
| `FinanceController` | source 自動設定 AOP + closure チェック | controller 実装は変更なし (AOP + Service で吸収) |
| 既存 E2E テスト | history insert で row count 検証が増える | 既存テストに影響なし (verify/toggle API の I/F 不変) |
| 既存バッチ E2E | `@Transactional` で history も保存 → 読み出し検証で確認 | 既存 goldenmaster を更新 (diff 行を許容) |

---

## 11. リスクと軽減策

| # | リスク | 影響 | 軽減策 |
|---|---|---|---|
| R1 | `@PostUpdate` の before_snapshot 取得が `@PostLoad` cache 依存で壊れやすい | 履歴 null 連発 | ThreadLocal holder + IdentityHashMap、`@PostLoad`/`@PostRefresh` 両方でフック。単体テストで boundary 検証 |
| R2 | `saveAll()` バッチで listener N+1 | バッチ遅延 | 集計バッチは diffOnly=true (§5.4)。history insert を JDBC batch に (hibernate.jdbc.batch_size=100) |
| R3 | jsonb 同値判定が JSON key 順序依存 | 差分なし誤検知 | `@JsonPropertyOrder(alphabetic=true)` + 数値は BigDecimal 正規化 (scale 統一) |
| R4 | 締め後ロック導入で既存運用が詰まる | 運用不可 | closure は **opt-in**。未 close = 従来通り編集可。初期導入時は close 行を作らない |
| R5 | AuditContext が非同期処理で消える (`@Async` 使用箇所) | 履歴の changed_by 欠損 | `@Async` 使用箇所を洗い出し、TaskDecorator で ThreadLocal を伝播 |
| R6 | バッチが tx 途中で失敗 | history のみ残り実データとミスマッチ | history insert は **同一 tx**。ロールバックで history も消える |
| R7 | ストレージ肥大 | DB 遅延 | §8 で試算 7 年 1.2 GB 想定。アラート閾値 5 GB で通知 |
| R8 | deleteAllInBatch 漏れ | 履歴なしで行が消える | コーディング規約追記 + `grep -r deleteAllInBatch` を CI に組込 (optional) |
| R9 | テーブル MInheritance (SINGLE_TABLE) への listener 二重発火 | 重複 insert | Entity 側は 1 クラスしかないため発生しない。将来 subclass 追加時に要注意 |
| R10 | 既存 E2E goldenmaster 差分 | CI 赤 | goldenmaster 生成時に history を除外、または固定 changed_at でマスク |

---

## 12. 実装順序と工数見積

### 12.1 フェーズ分け

| Phase | 内容 | 成果物 | 工数 |
|---|---|---|---|
| **P0** 設計確定 | 本設計書のレビュー + 運用要否の合意 (特に M7 締めロック) | 設計書 approved | 0.5 人日 |
| **P1** V028 migration | `t_accounts_payable_summary_history`, `t_accounts_payable_access_log`, `t_purchase_journal_export_log`, `t_accounting_closure` | V028 sql | 0.5 人日 |
| **P2** Entity + Repository | `TAccountsPayableSummaryHistory`, `TAccountingClosure`, Repos | Entity/Repo クラス | 0.5 人日 |
| **P3** AuditContext + Listener | ThreadLocal holder, `PayableAuditListener`, `AuditSnapshotHolder` | Listener 動作 | 1.5 人日 |
| **P4** Service: `PayableAuditService` | 明示呼出 API, query 関数 | Service + UT | 1 人日 |
| **P5** Controller: `MANUAL_VERIFY` 等の source 注入 | AOP (`@Around` on FinanceController 対象 endpoint) | source 自動設定 | 0.5 人日 |
| **P6** バッチ統合 | 3 Tasklet を AuditContext でラップ、diffOnly 判定、saveAll 通過で正しく listener が発火するかテスト | バッチ動作 | 1 人日 |
| **P7** Excel 取込統合 | `PaymentMfImportService` に correlation_id 注入 | Excel 1 本 = 1 correlation | 0.5 人日 |
| **P8** CSV 出力履歴 | `t_purchase_journal_export_log` に記録、`markExported` 時に source=PURCHASE_JOURNAL_EXPORT | CSV DL 時に履歴 insert | 0.5 人日 |
| **P9** Closure Service + 409 | `AccountingClosureService.assertOpen` を verify/toggle/release の先頭で呼ぶ | closure 動作 | 1 人日 |
| **P10** API (history + closure) | GET /history x3, POST /closures | 6 endpoints | 1 人日 |
| **P11** Frontend: 履歴ポップアップ | `<HistorySheet>` component, Timeline, diff 可視化 | buy-list + ledger に導入 | 2 人日 |
| **P12** Frontend: 締めバッジ + ダイアログ | ClosureBadge + CloseDialog | admin UI | 1 人日 |
| **P13** E2E 更新 | 既存テスト goldenmaster 調整 + 新規 history テスト | playwright PASS | 1 人日 |
| **P14** ドキュメント + 運用 Runbook | claudedocs + memory 更新 | docs | 0.5 人日 |

**合計: 約 12.5 人日 (≒ 2.5 週)**

### 12.2 依存関係

```
P1 (V028)
 └─> P2 (Entity)
      └─> P3 (Listener) ──> P4 (Service) ──> P5 (Controller AOP)
                         │                 └─> P6 (Batch)
                         │                 └─> P7 (Excel)
                         │                 └─> P8 (CSV export)
                         └─> P10 (API) ──> P11 (FE History)
 └─> P9 (Closure Service) ──> P10 (API) ──> P12 (FE Closure)

                                           P13 (E2E) ──> P14 (docs)
```

### 12.3 ロールアウト戦略

1. **P1-P4** を 1 PR (監査基盤、機能影響なし)
2. **P5-P8** を 1 PR (既存コード改修、挙動は history 追加のみ)
3. **P9** は **feature flag 付き** で導入 (`accounting.closure.enforce=false` デフォルト)。数週間運用 → 問題なければ flag 外す
4. **P10-P12** は 1 PR (UI + API)
5. **P13-P14** は最終 PR

### 12.4 テスト戦略

| レベル | 内容 |
|---|---|
| Unit | `PayableAuditListener` の before/after 取得、`hasFunctionalDiff`、`AuditContext` ThreadLocal |
| Integration | `@DataJpaTest` で Listener が発火し history が作られることを検証 |
| Batch | goldenmaster に history 期待データを追加 (固定 UUID, 固定 changedAt for 再現性) |
| E2E | 履歴ポップアップの表示、締めバッジ、締め後 verify 呼び出しが 409 になること |

### 12.5 売掛金 (AR) への横展開 (v2 先送り)

本設計の Entity/Listener/Service は generic 化しやすい構造。買掛側がリリースされたら:
- `T_accounts_receivable_summary_history` を同じ PK + jsonb スキーマで追加
- Listener は共通 `<T extends HasAuditSnapshot>` に一般化
- 工数は V028 実装の半分 (約 5 人日) を想定

---

## 13. 運用・Runbook (抜粋)

| シナリオ | 手順 |
|---|---|
| 「先月の XX さんが verifiedAmount を直したのは誰?」 | 買掛金一覧 → 仕入先行 → 🕒 → Timeline で確認 |
| 「Excel 取込 A の影響範囲を知りたい」 | Timeline で correlation_id クリック → /history/{corrId} で全 supplier list |
| 「締めた後に例外的に 1 行だけ直したい」 | admin が Closure を reopen → 修正 → 再 close (全操作が history に残る) |
| 「税務調査で 2024-03 の編集履歴を出して」 | `GET /history?fromMonth=2024-03-01&toMonth=2024-03-31` で CSV ダウンロード (v2) / DB 直接 SELECT で当面対応 |
| history テーブル肥大アラート | 半年に 1 度 REINDEX、10 年超行は cold archive table へ move (v3) |

---

## 14. オープンイシュー (次レビュー時の確認)

1. **閲覧履歴 (S1) を MUST に昇格するか** (IP 保存の規程整備が必要)
2. **connection.session_authorization** で PG ユーザを分離して改ざん抑止するか (運用負荷 vs 安全性)
3. **correlation_id を UUID vs bigint** → v28 は UUID 採用したが、batch で生成コストがあるなら bigserial も検討
4. **締め機能の粒度** → shopNo × kind(PAYABLE/RECEIVABLE) × month で十分か、supplier 単位まで必要か
5. **markExported の後で hasFunctionalDiff が発火するか確認** → tax_included_amount が change からコピーされる → 差分ありで history insert される想定

---

## 15. 参考

- 既存 jsonb 実装: `BCartChangeHistory` (b_cart_change_history テーブル) → 本設計の雛形
- 既存取込履歴: `TPaymentMfImportHistory` → 連携履歴統合 (v2) で合流
- PG jsonb ベストプラクティス: `jsonb_path_ops` GIN で ~30% サイズ削減
- Hibernate 6.x: `@JdbcTypeCode(SqlTypes.JSON)` が標準、hypersistence-utils-hibernate-63 の `@Type(JsonType.class)` も利用可
