-- T2: 監査証跡基盤
-- 設計書: claudedocs/design-finance-audit-log.md (T1 と組み合わせて運用)
-- @AuditLog アノテーション付き Service メソッドの呼び出し前後を記録する。
-- AOP (FinanceAuditAspect) が before/after の Entity スナップショットを JSONB で保存する。

CREATE TABLE finance_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_no   INTEGER,                                  -- NULL = SYSTEM/BATCH
    actor_type      VARCHAR(20) NOT NULL DEFAULT 'USER',      -- USER / SYSTEM / BATCH
    operation       VARCHAR(50) NOT NULL,                     -- INSERT / UPDATE / DELETE / verify / mf_apply ...
    target_table    VARCHAR(100) NOT NULL,
    target_pk       JSONB NOT NULL,
    before_values   JSONB,
    after_values    JSONB,
    reason          TEXT,
    source_ip       VARCHAR(45),
    user_agent      VARCHAR(500)
);

CREATE INDEX idx_finance_audit_log_target ON finance_audit_log (target_table, occurred_at DESC);
CREATE INDEX idx_finance_audit_log_actor ON finance_audit_log (actor_user_no, occurred_at DESC);
CREATE INDEX idx_finance_audit_log_occurred ON finance_audit_log (occurred_at DESC);

COMMENT ON TABLE finance_audit_log IS
    'T2: finance Service 層の監査証跡。@AuditLog アノテーション付きメソッドが自動記録。';
COMMENT ON COLUMN finance_audit_log.actor_user_no IS
    '操作実行者 (m_login_user.login_user_no)。NULL = SYSTEM/BATCH';
COMMENT ON COLUMN finance_audit_log.actor_type IS
    'USER / SYSTEM / BATCH。AOP は SecurityContext 取得可否で USER/SYSTEM を判定する';
COMMENT ON COLUMN finance_audit_log.target_pk IS
    '複合 PK 対応 JSONB (例: {"shopNo":1,"supplierNo":123,"transactionMonth":"2026-04-20","taxRate":10})';
COMMENT ON COLUMN finance_audit_log.before_values IS
    'UPDATE/DELETE 時の変更前値 JSONB (大きなフィールド・@AuditExclude 付与フィールドは除外)';
COMMENT ON COLUMN finance_audit_log.after_values IS
    'INSERT/UPDATE 時の変更後値 JSONB';
COMMENT ON COLUMN finance_audit_log.reason IS
    '失敗 (FAILED:<msg>) または操作理由 (note 等)';
