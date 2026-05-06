-- G2-M8 (2026-05-06): OFFSET 副行の貸方科目をマスタ化。
-- 従来 PaymentMfImportService にハードコードされていた「仕入値引・戻し高 / 物販事業部 /
-- 課税仕入-返還等 10%」を本テーブルから lookup する。税理士確認後に admin が値を変更可。

CREATE TABLE m_offset_journal_rule (
    id                      SERIAL PRIMARY KEY,
    shop_no                 INTEGER NOT NULL,
    credit_account          VARCHAR(100) NOT NULL,
    credit_sub_account      VARCHAR(100),
    credit_department       VARCHAR(100),
    credit_tax_category     VARCHAR(100) NOT NULL,
    summary_prefix          VARCHAR(100) NOT NULL DEFAULT '相殺／',
    del_flg                 CHAR(1) NOT NULL DEFAULT '0',
    add_date_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    add_user_no             INTEGER,
    modify_date_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modify_user_no          INTEGER,
    CONSTRAINT uq_offset_journal_rule_shop UNIQUE (shop_no, del_flg)
);

CREATE INDEX idx_offset_journal_rule_shop ON m_offset_journal_rule (shop_no, del_flg);

COMMENT ON TABLE m_offset_journal_rule IS
    'G2-M8: PaymentMfImport の OFFSET 副行 (PAYABLE_OFFSET/DIRECT_PURCHASE_OFFSET) の貸方科目マスタ。1 shop につき 1 行運用。';
COMMENT ON COLUMN m_offset_journal_rule.credit_account IS
    '貸方勘定科目 (例: 仕入値引・戻し高)';
COMMENT ON COLUMN m_offset_journal_rule.credit_tax_category IS
    '貸方税区分 (例: 課税仕入-返還等 10%)';
COMMENT ON COLUMN m_offset_journal_rule.summary_prefix IS
    '摘要欄プレフィックス (例: 相殺／ → 「相殺／sourceName」となる)';

-- shop_no=1 のデフォルト値を seed (現状のハードコード値を保持)
INSERT INTO m_offset_journal_rule
    (shop_no, credit_account, credit_sub_account, credit_department, credit_tax_category, summary_prefix)
VALUES
    (1, '仕入値引・戻し高', NULL, '物販事業部', '課税仕入-返還等 10%', '相殺／');
