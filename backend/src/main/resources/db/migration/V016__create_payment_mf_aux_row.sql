-- 2026-04-16: 買掛仕入MF 補助行テーブル新設
-- EXPENSE (荷造運賃・消耗品費・車両費等) / SUMMARY (振込手数料値引・早払収益) /
-- DIRECT_PURCHASE (20日払いセクション仕入高) を保持し、検証済みCSV出力から
-- PAYABLE(t_accounts_payable_summary) と結合して完全な MF 仕訳CSVを再生成する。
-- 設計書: claudedocs/design-payment-mf-aux-rows.md

CREATE TABLE IF NOT EXISTS t_payment_mf_aux_row (
    aux_row_id            BIGSERIAL PRIMARY KEY,
    shop_no               INTEGER      NOT NULL DEFAULT 1,
    transaction_month     DATE         NOT NULL,
    transfer_date         DATE         NOT NULL,
    rule_kind             VARCHAR(20)  NOT NULL,
    sequence_no           INTEGER      NOT NULL,
    source_name           VARCHAR(255) NOT NULL,
    payment_supplier_code VARCHAR(20),
    amount                NUMERIC      NOT NULL,
    debit_account         VARCHAR(50)  NOT NULL,
    debit_sub_account     VARCHAR(50),
    debit_department      VARCHAR(50),
    debit_tax             VARCHAR(30)  NOT NULL,
    credit_account        VARCHAR(50)  NOT NULL,
    credit_sub_account    VARCHAR(50),
    credit_department     VARCHAR(50),
    credit_tax            VARCHAR(30)  NOT NULL,
    summary               VARCHAR(255),
    tag                   VARCHAR(50),
    source_filename       VARCHAR(255),
    add_date_time         TIMESTAMP    NOT NULL,
    add_user_no           INTEGER,
    modify_date_time      TIMESTAMP,
    modify_user_no        INTEGER,
    CONSTRAINT chk_payment_mf_aux_rule_kind
        CHECK (rule_kind IN ('EXPENSE','SUMMARY','DIRECT_PURCHASE'))
);

CREATE INDEX IF NOT EXISTS idx_payment_mf_aux_tx_month
    ON t_payment_mf_aux_row(shop_no, transaction_month);

CREATE INDEX IF NOT EXISTS idx_payment_mf_aux_transfer
    ON t_payment_mf_aux_row(shop_no, transaction_month, transfer_date);

COMMENT ON TABLE  t_payment_mf_aux_row IS '買掛仕入MF 補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE)。振込明細Excel applyVerification 時に (shop_no, transaction_month, transfer_date) 単位で洗い替え保存。検証済みCSV出力で PAYABLE と結合される。';
COMMENT ON COLUMN t_payment_mf_aux_row.transaction_month IS '小田光締め日(前月20日)。CSV 取引日列にも使用。';
COMMENT ON COLUMN t_payment_mf_aux_row.transfer_date     IS '出処 Excel の送金日 (5日 or 20日)。同一 (transaction_month, transfer_date) で再アップロードされたら物理削除→再挿入で洗い替え。';
COMMENT ON COLUMN t_payment_mf_aux_row.rule_kind         IS 'EXPENSE / SUMMARY / DIRECT_PURCHASE';
COMMENT ON COLUMN t_payment_mf_aux_row.sequence_no       IS 'Excel 内の出現順 (CSV 出力順序維持用)';
COMMENT ON COLUMN t_payment_mf_aux_row.source_filename   IS 'トレーサビリティ用 Excel ファイル名';
