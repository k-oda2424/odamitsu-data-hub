-- 買掛金集計テーブルに手入力保護フラグと備考カラムを追加
-- 2026-04-14: design-accounts-payable.md §6.1

ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS verified_manually BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS verification_note VARCHAR(500);

COMMENT ON COLUMN t_accounts_payable_summary.verified_manually IS '手動確定フラグ: trueならSMILE再検証バッチで上書きしない';
COMMENT ON COLUMN t_accounts_payable_summary.verification_note IS '検証時の備考（請求書番号、確認経緯など）';
