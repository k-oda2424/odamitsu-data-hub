-- 2026-04-15: 手動verify行で mf_export_enabled が未設定のまま残っていたデータを整合化。
-- 手動verify(TAccountsPayableSummaryService.verify)は従来 mf_export_enabled をセットしておらず、
-- 一致行でも CSV エクスポートフィルタ(verificationResult=1 AND mfExportEnabled=true) に
-- 引っかからず除外されていたため、一致＝ON / 不一致＝OFF に揃える。
-- 以降は service 側で自動セットされるため本マイグレーションは1回のみ実行。

UPDATE t_accounts_payable_summary
   SET mf_export_enabled = TRUE
 WHERE verification_result = 1
   AND verified_manually = TRUE
   AND (mf_export_enabled IS NULL OR mf_export_enabled = FALSE);

UPDATE t_accounts_payable_summary
   SET mf_export_enabled = FALSE
 WHERE verification_result = 0
   AND verified_manually = TRUE
   AND mf_export_enabled IS NULL;
