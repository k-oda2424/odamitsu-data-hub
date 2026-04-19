-- 売掛金集計テーブルに検証関連カラムを追加
-- 2026-04-17: design-accounts-receivable-mf.md §4.1
-- 買掛側 t_accounts_payable_summary と対称な運用系カラム

ALTER TABLE t_accounts_receivable_summary
    ADD COLUMN IF NOT EXISTS verification_result INTEGER;

ALTER TABLE t_accounts_receivable_summary
    ADD COLUMN IF NOT EXISTS mf_export_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_accounts_receivable_summary
    ADD COLUMN IF NOT EXISTS verified_manually BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_accounts_receivable_summary
    ADD COLUMN IF NOT EXISTS verification_note VARCHAR(500);

ALTER TABLE t_accounts_receivable_summary
    ADD COLUMN IF NOT EXISTS invoice_amount NUMERIC(15, 2);

ALTER TABLE t_accounts_receivable_summary
    ADD COLUMN IF NOT EXISTS verification_difference NUMERIC(15, 2);

ALTER TABLE t_accounts_receivable_summary
    ADD COLUMN IF NOT EXISTS invoice_no INTEGER;

COMMENT ON COLUMN t_accounts_receivable_summary.verification_result IS '検証結果: 1=一致, 0=不一致, NULL=未検証';
COMMENT ON COLUMN t_accounts_receivable_summary.mf_export_enabled IS 'MF連携CSV出力対象フラグ: true=出力, false=出力対象外';
COMMENT ON COLUMN t_accounts_receivable_summary.verified_manually IS '手動確定フラグ: trueなら再集計・再検証で上書きしない';
COMMENT ON COLUMN t_accounts_receivable_summary.verification_note IS '検証時の備考（手動確定時の理由など）';
COMMENT ON COLUMN t_accounts_receivable_summary.invoice_amount IS '突合した請求書金額（税込, t_invoice.net_sales_including_tax）';
COMMENT ON COLUMN t_accounts_receivable_summary.verification_difference IS '差額（invoice_amount - tax_included_amount_change）';
COMMENT ON COLUMN t_accounts_receivable_summary.invoice_no IS '突合した請求書ID（t_invoice.invoice_id, 監査用）';

CREATE INDEX IF NOT EXISTS idx_ars_verification_result
    ON t_accounts_receivable_summary (shop_no, transaction_month, verification_result);

CREATE INDEX IF NOT EXISTS idx_ars_mf_export
    ON t_accounts_receivable_summary (shop_no, transaction_month, mf_export_enabled)
    WHERE mf_export_enabled = TRUE;
