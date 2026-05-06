-- 前期繰越 (supplier 毎の期首残) 管理テーブル: 2026-04-24
-- 設計書: claudedocs/design-supplier-opening-balance.md
--
-- MF fiscal year start (journal #1, transactionDate=2025-06-21) の各 branch creditSub/creditValue から
-- supplier 毎の前期繰越を抽出し、買掛帳・整合性レポート等で累積残の初期値として使用する。
-- opening_date = journal #1 前日 (= 20 日締めバケット日)。例: 2025-06-20。
--
-- mf_balance: MF journal #1 から取得した税込残 (NULL = 未取得)
-- manual_adjustment: 手動補正額 (journal #1 に含まれない shop=2 太幸や税理士確認差分用、 signed)
-- effective_balance: 合算値 (generated column、アプリ側から読む canonical な値)

CREATE TABLE m_supplier_opening_balance (
    shop_no                   INTEGER       NOT NULL,
    opening_date              DATE          NOT NULL,
    supplier_no               INTEGER       NOT NULL,
    mf_balance                NUMERIC(15,0),
    manual_adjustment         NUMERIC(15,0) NOT NULL DEFAULT 0,
    effective_balance         NUMERIC(15,0) GENERATED ALWAYS AS (COALESCE(mf_balance, 0) + manual_adjustment) STORED,
    source_journal_number     INTEGER,
    source_sub_account_name   VARCHAR(200),
    last_mf_fetched_at        TIMESTAMP,
    adjustment_reason         VARCHAR(500),
    note                      VARCHAR(500),
    add_date_time             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    add_user_no               INTEGER       NOT NULL,
    modify_date_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modify_user_no            INTEGER       NOT NULL,
    del_flg                   CHAR(1)       NOT NULL DEFAULT '0',
    PRIMARY KEY (shop_no, opening_date, supplier_no)
);

CREATE INDEX idx_supplier_opening_balance_shop_date
    ON m_supplier_opening_balance (shop_no, opening_date);

COMMENT ON TABLE m_supplier_opening_balance IS
    'supplier 毎の前期繰越 (期首残)。MF journal #1 の credit branch から取得。 buying ledger / integrity / supplier-balances の累積初期値に使用。';
COMMENT ON COLUMN m_supplier_opening_balance.opening_date IS
    '基準日 (20 日締めバケット日)。この日終了時点の残高を保持。例: 2025-06-20 = fiscal year 2025-06-21 の直前日。';
COMMENT ON COLUMN m_supplier_opening_balance.mf_balance IS
    'MF journal #1 の該当 supplier creditSub/creditValue から取得した税込残 (NULL = 未取得)。再取得で上書き、manual_adjustment は保持。';
COMMENT ON COLUMN m_supplier_opening_balance.manual_adjustment IS
    '手動補正額 (税込、signed)。journal #1 未掲載 (shop=2 太幸等) や税理士確認差分の吸収用。';
COMMENT ON COLUMN m_supplier_opening_balance.effective_balance IS
    'アプリが参照する canonical 値 = COALESCE(mf_balance, 0) + manual_adjustment。';
COMMENT ON COLUMN m_supplier_opening_balance.source_journal_number IS
    'MF journal 番号 (期首残高仕訳は通常 #1)。mf_balance の出どころ追跡用。';
COMMENT ON COLUMN m_supplier_opening_balance.source_sub_account_name IS
    'MF 側の sub_account_name (creditSub 値)。再取得時のマッチングおよび監査証跡用。';
