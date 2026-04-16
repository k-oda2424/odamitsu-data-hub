-- 買掛金集計テーブルに検証時の請求額カラムを追加
-- 2026-04-15: 振込明細 / 手入力で提示された税込金額を保持し、tax_included_amount_change と比較する
-- tax_included_amount（MF出力スナップショット）とは別管理。再集計バッチでは上書きしない。

ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS verified_amount NUMERIC;

COMMENT ON COLUMN t_accounts_payable_summary.verified_amount IS '検証時の請求額（振込明細 or 手入力）。税率別行には同一値が入る（Excel側に税率別内訳が無いため）。レポート集計で SUM すると二重計上になる点に注意';
