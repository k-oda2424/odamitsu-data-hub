-- P1-09 案 D: verified_amount の不変条件を DB レベルで明示
COMMENT ON COLUMN t_accounts_payable_summary.verified_amount IS
'振込実績の集約値(税込)。supplier × transaction_month の集約値で、税率別行(tax_rate違い)には同一値が書き込まれる。'
'不変条件: 同 (shop, supplier, transactionMonth) の全税率行で本値は同値であること。'
'単一行のみ SQL UPDATE すると read 側 sumVerifiedAmountForGroup の重複カウント回避が崩れ過大計上の原因となる。'
'手動修正は必ず supplier × txMonth の全行同時に行うこと。'
'仕訳生成: 振込仕訳(借方買掛/貸方普通預金、対象外)では集約値として1度参照、仕入仕訳(借方仕入高)では verified_amount_tax_excluded を税率別参照。';

COMMENT ON COLUMN t_accounts_payable_summary.verified_amount_tax_excluded IS
'振込実績の税抜金額(税率別逆算)。V026 (2026-04-23) 追加。verified_amount(税込)を各行の tax_rate で逆算した値で、同 supplier × month の税率別行で異なる値が入る。'
'用途: MF CSV 仕入仕訳(借方仕入高)の金額として使用。'
'計算式: verified_amount × 100 / (100 + tax_rate) 端数切捨。';
