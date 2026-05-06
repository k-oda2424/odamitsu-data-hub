-- P1-08: 再取込防止 (警告 + 確定保護) のため 3 列追加
-- L1: 同一ハッシュの過去取込検知用 (preview 警告)
-- L2: applyVerification 実行済み判定用 (preview 警告 + 手動確定行保護)
ALTER TABLE t_payment_mf_import_history
    ADD COLUMN IF NOT EXISTS source_file_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS applied_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS applied_by_user_no INTEGER NULL;

CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_hash
    ON t_payment_mf_import_history (source_file_hash);

CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_applied
    ON t_payment_mf_import_history (shop_no, transfer_date) WHERE applied_at IS NOT NULL;

COMMENT ON COLUMN t_payment_mf_import_history.source_file_hash
    IS 'P1-08: 取込元 Excel の SHA-256 (hex)。同一 hash 再取込時に preview 警告';
COMMENT ON COLUMN t_payment_mf_import_history.applied_at
    IS 'P1-08: applyVerification 実行タイムスタンプ。NULL=未確定';
COMMENT ON COLUMN t_payment_mf_import_history.applied_by_user_no
    IS 'P1-08: applyVerification 実行ユーザー (m_user.user_no)';
