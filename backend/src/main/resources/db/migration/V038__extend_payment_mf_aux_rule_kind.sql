-- C2 (2026-05-06): t_payment_mf_aux_row.rule_kind の CHECK 制約を拡張
-- PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET および
-- DIRECT_PURCHASE_FEE / DIRECT_PURCHASE_DISCOUNT / DIRECT_PURCHASE_EARLY / DIRECT_PURCHASE_OFFSET
-- 副行を aux テーブルに保存可能にする。
--
-- 背景:
--   exportVerifiedCsv (DB-only 経路) は aux 行から副行を再構築する必要があるが、
--   従来 saveAuxRowsForVerification は PAYABLE_*/DIRECT_PURCHASE_* 副行を skip しており、
--   検証済みCSV出力で副行が消失していた (Codex Critical C2)。
--
-- データ整合性:
--   既存行は EXPENSE/SUMMARY/DIRECT_PURCHASE のみで、新制約に引き続き合致する。
--   制約緩和方向のため既存データに対する DROP→ADD 失敗のリスクなし。
--
-- 設計書: claudedocs/design-payment-mf-import.md §5.4 / §5.5.6

ALTER TABLE t_payment_mf_aux_row DROP CONSTRAINT IF EXISTS chk_payment_mf_aux_rule_kind;

-- DIRECT_PURCHASE_DISCOUNT (24文字) を保持できるよう VARCHAR(20) → VARCHAR(30) に拡張。
-- 既存データは EXPENSE/SUMMARY/DIRECT_PURCHASE のみで全て 30 文字以内に収まる。
ALTER TABLE t_payment_mf_aux_row ALTER COLUMN rule_kind TYPE VARCHAR(30);

ALTER TABLE t_payment_mf_aux_row ADD CONSTRAINT chk_payment_mf_aux_rule_kind
    CHECK (rule_kind IN (
        'EXPENSE',
        'SUMMARY',
        'DIRECT_PURCHASE',
        'PAYABLE_FEE', 'PAYABLE_DISCOUNT', 'PAYABLE_EARLY', 'PAYABLE_OFFSET',
        'DIRECT_PURCHASE_FEE', 'DIRECT_PURCHASE_DISCOUNT', 'DIRECT_PURCHASE_EARLY', 'DIRECT_PURCHASE_OFFSET'
    ));

COMMENT ON COLUMN t_payment_mf_aux_row.rule_kind IS
'C2 (2026-05-06): 副行 PAYABLE_*/DIRECT_PURCHASE_* も保存対象に拡張。'
'PAYABLE 主行は t_accounts_payable_summary 由来のため本テーブルには保存しない。'
'PAYABLE_* 副行 (FEE/DISCOUNT/EARLY/OFFSET) は本テーブルに保存し exportVerifiedCsv で再構築。'
'DIRECT_PURCHASE 主行・副行ともに本テーブルに保存。'
'EXPENSE/SUMMARY は従来どおり本テーブルに保存。';
