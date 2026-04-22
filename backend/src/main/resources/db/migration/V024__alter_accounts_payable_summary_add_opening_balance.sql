-- 2026-04-22: 買掛金累積残管理のための前月繰越カラム追加
-- 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.1
--
-- カミ商事の 12月仕入 → 1月値引による 1月買掛負残 (-298,097) が月をまたいで繰り越される問題
-- に対応。各月 summary 行に前月末時点の累積残 (opening_balance) を保持し、
-- closing_balance = opening_balance + change は DTO 層で算出する（Entity には持たせない）。
--
-- 符号規約: 買掛金は貸方正 (credit - debit) の純増減。負残 = 値引超過による前払相当。

ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS opening_balance_tax_included NUMERIC NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS opening_balance_tax_excluded NUMERIC NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_accounts_payable_summary.opening_balance_tax_included IS
    '前月末時点の累積残 (税込・符号あり)。前月 closing = opening + effectiveChange。手動確定行は change 列は保護されるが、opening 列は常にバッチで上書きする。';
COMMENT ON COLUMN t_accounts_payable_summary.opening_balance_tax_excluded IS
    '前月末時点の累積残 (税抜・符号あり)。';

-- 累積残ドリルダウン用インデックス (仕入先 × 月 順)
CREATE INDEX IF NOT EXISTS idx_aps_supplier_month_cum
    ON t_accounts_payable_summary (shop_no, supplier_no, transaction_month);
