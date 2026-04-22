-- 2026-04-22: 買掛金残の正確な管理 (Phase B')
-- 設計書: claudedocs/design-phase-b-prime-payment-settled.md §3.1
--
-- Phase A で opening_balance を入れたが、change 列が「仕入のみ」で支払を反映しないため
-- closing が「仕入累計」となり MF 買掛金 closing (残高) と整合しなかった。
-- Phase B' で payment_settled 列 (当月完了した支払額) と is_payment_only フラグを追加し、
-- closing = opening + change - payment_settled の T 勘定定義に変更する。
--
-- 運用ルール (memory/feature-payment-mf-import.md 準拠):
--   当月 5日/20日送金 = 前月 20日締め分の支払 → 当月行の payment_settled は前月 verified_amount 由来
--
-- verified_amount は supplier 単位の合計値を全税率行に同じ値で書き込む既存実装のため、
-- payment_settled は supplier 単位で集計し、当月 change 比で税率別行に按分する。
-- 当月 change=0 supplier には is_payment_only=true 行を生成し stale-delete から除外する。

ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS payment_amount_settled_tax_included NUMERIC NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_amount_settled_tax_excluded NUMERIC NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_payment_only BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN t_accounts_payable_summary.payment_amount_settled_tax_included IS
    '当月完了した支払額 (税込、supplier 単位支払を税率別 change 比で按分)。5日/20日送金は前月20日締めに充てる運用。closing = opening + change - payment_settled の算出要素。';
COMMENT ON COLUMN t_accounts_payable_summary.payment_amount_settled_tax_excluded IS
    '当月完了した支払額 (税抜、同上 change_excl 比で按分)。';
COMMENT ON COLUMN t_accounts_payable_summary.is_payment_only IS
    'payment-only 行フラグ。当月 change=0 だが前月支払があった supplier のために生成された行。stale-delete 対象から除外する目印。';
