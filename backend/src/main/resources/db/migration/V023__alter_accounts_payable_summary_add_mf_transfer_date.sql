-- 2026-04-21: 買掛支払 MF CSV 出力の取引日を送金日ベースに変更するための列追加
-- 従来は全 PAYABLE 行の CSV 取引日が transactionMonth (締め日, 20日) に固定されていたため、
-- 5日払いの仕入先も 20日払いの仕入先も MF 側で同日 (20日) に買掛金→仕入資金複合が集約され、
-- 仕入資金複合の残高が 0 にならない問題が起きていた。
--
-- Excel (振込明細) アップロード時に、各 PAYABLE 行が属するセクション (5日払い / 20日払い) の
-- 送金日を mf_transfer_date として記録し、CSV 出力時の取引日に使う。

ALTER TABLE t_accounts_payable_summary
  ADD COLUMN IF NOT EXISTS mf_transfer_date DATE;

COMMENT ON COLUMN t_accounts_payable_summary.mf_transfer_date IS
  'MF CSV 出力時の送金日 (取引日列に使用)。Excel 振込明細取込時に set。NULL 時は transactionMonth (締め日) にフォールバック。';
