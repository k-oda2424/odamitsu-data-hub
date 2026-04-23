-- 整合性レポート差分確認機能 (案 X+Y): 2026-04-23
-- 設計書: claudedocs/design-consistency-review.md
--
-- 差分行の確認済み状態と MF 優先確定時のロールバック情報を保持する。
-- entry_key は selfOnly/amountMismatch では supplier_no 文字列、
-- mfOnly では guessedSupplierNo (未解決なら sub_account_name) を格納する。

CREATE TABLE t_consistency_review (
    shop_no                      INTEGER      NOT NULL,
    entry_type                   VARCHAR(20)  NOT NULL,
    entry_key                    VARCHAR(255) NOT NULL,
    transaction_month            DATE         NOT NULL,
    action_type                  VARCHAR(20)  NOT NULL,
    self_snapshot                NUMERIC,
    mf_snapshot                  NUMERIC,
    previous_verified_amounts    JSONB,
    reviewed_by                  INTEGER      NOT NULL,
    reviewed_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    note                         VARCHAR(500),
    PRIMARY KEY (shop_no, entry_type, entry_key, transaction_month)
);

CREATE INDEX idx_consistency_review_shop_month_type
    ON t_consistency_review (shop_no, transaction_month, entry_type);

COMMENT ON TABLE t_consistency_review IS
    '整合性レポート 差分確認履歴。PK の entry_key は selfOnly/amountMismatch では supplier_no 文字列、mfOnly では guessedSupplierNo または sub_account_name。';
COMMENT ON COLUMN t_consistency_review.action_type IS 'IGNORE | MF_APPLY';
COMMENT ON COLUMN t_consistency_review.previous_verified_amounts IS
    'MF_APPLY 実行時の対象 summary 行の verified_amount 退避 (税率→金額の Map)。DELETE/IGNORE 切替時にロールバックするため。';
