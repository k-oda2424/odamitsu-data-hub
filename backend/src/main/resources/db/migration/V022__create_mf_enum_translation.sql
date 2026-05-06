-- 2026-04-21: MF API の英語 enum → 既存 mf_account_master の日本語値 翻訳辞書
-- 勘定科目同期 (m_mf_account_sync) で使用。初回は既存 mf_account_master から自動学習して投入。
-- ユーザーが画面で手動編集することも可能。

CREATE TABLE IF NOT EXISTS m_mf_enum_translation (
    id                  SERIAL       PRIMARY KEY,
    enum_kind           VARCHAR(50)  NOT NULL, -- FINANCIAL_STATEMENT / CATEGORY
    english_code        VARCHAR(100) NOT NULL, -- MF API 返却値 (例: CASH_AND_DEPOSITS)
    japanese_name       VARCHAR(255) NOT NULL, -- 日本語化結果 (例: 現金及び預金)
    del_flg             CHAR(1)      NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER,
    CONSTRAINT chk_mf_enum_kind CHECK (enum_kind IN ('FINANCIAL_STATEMENT','CATEGORY')),
    CONSTRAINT uq_mf_enum_active UNIQUE (enum_kind, english_code, del_flg)
);

COMMENT ON TABLE  m_mf_enum_translation IS 'MF API 英語 enum → 日本語名 翻訳辞書。勘定科目同期で category / financial_statement_type の日本語化に使用。';
COMMENT ON COLUMN m_mf_enum_translation.enum_kind IS 'FINANCIAL_STATEMENT (BALANCE_SHEET/PROFIT_AND_LOSS) / CATEGORY (CASH_AND_DEPOSITS 等)';
COMMENT ON COLUMN m_mf_enum_translation.english_code IS 'MF API が返す英語 enum コード';
COMMENT ON COLUMN m_mf_enum_translation.japanese_name IS '既存 mf_account_master から学習 or 画面で手動設定';

-- 最低限のシード（既存 mf_account_master が空でも基本の貸借対照表/損益計算書だけは効くように）
INSERT INTO m_mf_enum_translation (enum_kind, english_code, japanese_name, add_date_time)
VALUES
  ('FINANCIAL_STATEMENT', 'BALANCE_SHEET',   '貸借対照表',   CURRENT_TIMESTAMP),
  ('FINANCIAL_STATEMENT', 'PROFIT_AND_LOSS', '損益計算書',   CURRENT_TIMESTAMP)
ON CONFLICT (enum_kind, english_code, del_flg) DO NOTHING;
