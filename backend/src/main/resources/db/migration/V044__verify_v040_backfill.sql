-- Codex Major fix (2026-05-06): V040 backfill 検査用診断 SQL。
--
-- 背景:
--   V040 で verification_source 列を導入した際、過去の verified_amount/verified_manually の
--   書込経路を推定するため verification_note の文字列接頭辞 "振込明細検証 %" で
--   BULK_VERIFICATION に backfill した。
--   しかし note は手入力可能なため、ユーザーが偶然 "振込明細検証 ..." で始まる note を
--   入力していた行は MANUAL_VERIFICATION を期待していたのに BULK_VERIFICATION に
--   誤判定される可能性がある。
--
-- 対応方針:
--   本 migration は **検査のみ** を実施し、自動修復は行わない。
--   note 接頭辞で BULK_VERIFICATION 判定された行のうち、
--   t_payment_mf_import_history (applied_at != NULL) に対応する一括検証実行記録が
--   存在しない (= 突合が取れない) 行を NOTICE で報告する。
--
--   突合キーの考え方:
--     t_accounts_payable_summary.transaction_month と
--     t_payment_mf_import_history.transfer_date は
--     PaymentMfImportService.deriveTransactionMonth により
--     transaction_month = transfer_date - 1 month (day=20) の関係。
--     一致する shop_no + (transfer_date - 1 month, day=20) の applied 履歴があれば
--     正規 BULK_VERIFICATION とみなす。
--
-- 検査結果:
--   - suspect_count = 0: 全 BULK 行が一括検証履歴と整合 (V040 backfill OK)
--   - suspect_count > 0: 業務担当者の手動確認が必要 (= note 接頭辞偽装の疑い)
--     自動修復は業務判断 (verified_amount の値取扱いが BULK と MANUAL で異なる) を
--     伴うため運用側で個別確認 → UPDATE する。
--
-- 関連:
--   - V040__add_verification_source.sql (backfill 本体)
--   - claudedocs/design-payment-mf-import.md §5.6 (verification_source 設計)

DO $$
DECLARE
    suspect_count   INTEGER;
    bulk_total      INTEGER;
BEGIN
    SELECT COUNT(*) INTO bulk_total
    FROM t_accounts_payable_summary
    WHERE verification_source = 'BULK_VERIFICATION'
      AND verification_note LIKE '振込明細検証 %';

    SELECT COUNT(*) INTO suspect_count
    FROM t_accounts_payable_summary aps
    WHERE aps.verification_source = 'BULK_VERIFICATION'
      AND aps.verification_note LIKE '振込明細検証 %'
      AND NOT EXISTS (
          SELECT 1
          FROM t_payment_mf_import_history hist
          WHERE hist.shop_no = aps.shop_no
            AND hist.applied_at IS NOT NULL
            AND hist.del_flg = '0'
            -- transaction_month = transfer_date - 1 month (day=20)
            AND aps.transaction_month = (date_trunc('month', hist.transfer_date) - INTERVAL '1 month' + INTERVAL '19 days')::date
      );

    IF suspect_count > 0 THEN
        RAISE NOTICE 'V044: V040 backfill 検査 NG: BULK_VERIFICATION % 行のうち % 行に一括検証履歴 (applied_at != NULL) なし。手動確認推奨。',
            bulk_total, suspect_count;
        RAISE NOTICE 'V044: 確認 SQL: SELECT shop_no, supplier_no, transaction_month, tax_rate, verification_note FROM t_accounts_payable_summary aps WHERE verification_source = ''BULK_VERIFICATION'' AND verification_note LIKE ''振込明細検証 %%'' AND NOT EXISTS (SELECT 1 FROM t_payment_mf_import_history h WHERE h.shop_no = aps.shop_no AND h.applied_at IS NOT NULL AND h.del_flg = ''0'' AND aps.transaction_month = (date_trunc(''month'', h.transfer_date) - INTERVAL ''1 month'' + INTERVAL ''19 days'')::date) ORDER BY shop_no, supplier_no, transaction_month;';
        RAISE NOTICE 'V044: 修復は業務判断 (BULK / MANUAL で verified_amount 取扱いが異なる) のため自動 UPDATE せず。';
    ELSE
        RAISE NOTICE 'V044: V040 backfill 検査 OK: BULK_VERIFICATION % 行は全て一括検証履歴 (applied_at != NULL) と整合。',
            bulk_total;
    END IF;
END $$;
