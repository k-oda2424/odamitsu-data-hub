-- G2-M1 + G2-M10 (2026-05-06): verified_amount / verified_manually の判定を
-- 文字列・金額パターン推定から、明示的な書込経路 enum に置換する。
--
-- 背景:
--   G2-M1: PaymentMfImportService.sumVerifiedAmountForGroup が「全行同値=代表値」で
--     bulk verify の冗長保持を扱い、「不一致=SUM」で manual verify を扱っていた。
--     しかし「全行同値」は推定であり、(a) manual で偶然全行同値の場合は SUM すべきが
--     代表値で過少計上、(b) bulk verify 後に単行修正を入れた場合は代表値ではなく SUM
--     になり過大計上のリスクがあった。
--   G2-M10: applyVerification の手動保護判定が verification_note の接頭辞文字列に
--     依存しており、ユーザが偶然 "振込明細検証 ..." で始まる note を手入力すると
--     bulk と誤判定され保護が外れるリスクがあった。
--
-- 対応:
--   verification_source 列を enum で持ち、書込時に経路を明示記録する。
--   read 側は note 接頭辞ではなく source 列で判定する。
--
-- 設計書: claudedocs/design-payment-mf-import.md §5.6

ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS verification_source VARCHAR(20);

-- backfill: verification_note 接頭辞 = 振込明細検証 で始まる行は BULK_VERIFICATION、
-- それ以外で verified_manually=true の行は MANUAL_VERIFICATION、未検証は NULL。
-- (移行時点の note 接頭辞は applyVerification がアプリコードで一括書込しているため信頼できる。
--  以後の書込は Service 層で source 列を直接セットする運用に切替える。)
UPDATE t_accounts_payable_summary
SET verification_source = 'BULK_VERIFICATION'
WHERE verification_note LIKE '振込明細検証 %'
  AND verification_source IS NULL;

UPDATE t_accounts_payable_summary
SET verification_source = 'MANUAL_VERIFICATION'
WHERE verified_manually = true
  AND verification_source IS NULL;

ALTER TABLE t_accounts_payable_summary
    ADD CONSTRAINT chk_accounts_payable_verification_source
    CHECK (verification_source IS NULL OR verification_source IN
        ('BULK_VERIFICATION', 'MANUAL_VERIFICATION', 'MF_OVERRIDE'));

CREATE INDEX IF NOT EXISTS idx_apsum_verification_source
    ON t_accounts_payable_summary (verification_source)
    WHERE verification_source IS NOT NULL;

COMMENT ON COLUMN t_accounts_payable_summary.verification_source IS
'検証値 (verified_amount / verified_amount_tax_excluded) の書込経路 (G2-M1/M10): '
'BULK_VERIFICATION: PaymentMfImportService.applyVerification 由来 (Excel 一括検証)。全税率行に同値の集約値が冗長保持される。'
'MANUAL_VERIFICATION: TAccountsPayableSummaryService.verify 由来 (UI 手入力)。単一 PK 行のみ更新。税率別に異なる値が入る。'
'MF_OVERRIDE: ConsistencyReviewService.applyMfOverride 由来 (整合性レポート上書き)。税率別按分。'
'NULL: 未検証 (verified_manually=false)。read 側は taxIncludedAmountChange にフォールバック。';
