-- 軸: 振込明細 Excel の仕入先請求金額を税抜側にも反映する (2026-04-23)
--
-- 背景: 既存の verified_amount (税込) は MF CSV 出力の「買掛金」金額として使われているが、
-- 税抜側は自社計算 (tax_excluded_amount_change) のままのため、MF 仕訳の「仕入高」「仮払消費税」が
-- 仕入先請求書とズレる。これを解消するため、verified 対応の税抜金額を保持する。
--
-- auto_adjusted_amount は「Excel 金額 − 自社計算金額 (税込)」= 自動調整された差額。
-- 監査証跡として |diff| ≤ 100 円での自動合わせ込みを明示的に記録する。

ALTER TABLE t_accounts_payable_summary
    ADD COLUMN verified_amount_tax_excluded NUMERIC,
    ADD COLUMN auto_adjusted_amount NUMERIC NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_accounts_payable_summary.verified_amount_tax_excluded IS
    '振込明細 Excel 由来の税抜確定額。verified_amount (税込) に対応する税抜金額で、MF CSV 出力の「仕入高」計算に使用。単一税率前提なら verified_amount × 100/(100+tax_rate) で逆算。';

COMMENT ON COLUMN t_accounts_payable_summary.auto_adjusted_amount IS
    '振込明細 Excel 取込時の自動調整額 (= verified_amount - tax_included_amount_change、符号あり)。消費税丸め差等で ±100 円以内に自動合わせ込みされた金額。0 なら調整なし。';

-- 既存データの遡及更新:
-- 過去に一括検証 (verified_manually=true) された行について、
--   auto_adjusted_amount: verified_amount - tax_included_amount_change を計算
--   verified_amount_tax_excluded: verified_amount × 100/(100 + tax_rate) を FLOOR (切捨て)
-- を一括で埋める。今後の Excel 取込分は applyVerification で自動セットされる。
UPDATE t_accounts_payable_summary
SET auto_adjusted_amount = COALESCE(verified_amount, 0)
                         - COALESCE(tax_included_amount_change, 0),
    verified_amount_tax_excluded = CASE
        WHEN verified_amount IS NULL THEN NULL
        ELSE FLOOR(verified_amount * 100.0 / (100.0 + COALESCE(tax_rate, 10)))
    END
WHERE verified_manually = true AND verified_amount IS NOT NULL;
