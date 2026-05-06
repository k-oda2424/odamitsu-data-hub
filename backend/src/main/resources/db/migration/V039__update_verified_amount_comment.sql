-- C3 (2026-05-06): verified_amount の不変条件記述を「applyVerification 由来のみ」に限定
--
-- 背景:
--   V035 で「同 (shop, supplier, transactionMonth) の全税率行で verified_amount は同値」
--   を絶対不変条件として記述したが、実装上は以下 3 経路で書込まれる:
--   (1) PaymentMfImportService.applyVerification: 全税率行同値 (集約値書込)
--   (2) TAccountsPayableSummaryService.verify: 単一 PK 行のみ更新 (税率別に異なる)
--   (3) ConsistencyReviewService.applyMfOverride: 税率別按分 (税率別に異なる)
--
--   不変条件は (1) のみで成立し、(2)(3) では崩れる。read 側 sumVerifiedAmountForGroup は
--   「全行同値=代表値、不一致=SUM」フォールバックで両ケースを扱っており、書込側 invariant
--   ではなく read 側 reconciliation logic として機能している。
--
-- 設計書: claudedocs/design-payment-mf-import.md §5.6

COMMENT ON COLUMN t_accounts_payable_summary.verified_amount IS
'振込実績の集約値(税込)。書込経路で挙動が異なる: '
'(1) PaymentMfImportService.applyVerification 由来: supplier × transaction_month の集約値を全税率行に同値で書込 (税率別行に同値冗長保持)。'
'(2) TAccountsPayableSummaryService.verify 由来: 単一 PK (shop, supplier, txMonth, taxRate) 行のみ更新、税率別に異なる値が入る。'
'(3) ConsistencyReviewService.applyMfOverride 由来: MF debit を税率別按分して書込、税率別に異なる値が入る。'
'read 側 PaymentMfImportService.sumVerifiedAmountForGroup は「全行同値=代表値、不一致=SUM」フォールバックで両系をハンドリング。'
'仕訳生成: 振込仕訳(借方買掛/貸方普通預金、対象外)では集約値として1度参照、仕入仕訳(借方仕入高)では verified_amount_tax_excluded を税率別参照。'
'C3 (2026-05-06): V035 の絶対不変条件記述を実装実態に合わせ書込経路ごとの挙動記述に修正。';
