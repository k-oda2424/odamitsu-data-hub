-- 2026-04-20: マネーフォワードクラウド会計 API 連携 Phase 1
-- 設計書: claudedocs/design-mf-integration-status.md
-- OAuth2 クライアント設定 / トークン永続化 / 勘定科目マッピングの 3 テーブル

-- ========================================================================
-- m_mf_oauth_client: OAuth2 クライアント設定（通常 1 レコード運用）
-- ========================================================================
CREATE TABLE IF NOT EXISTS m_mf_oauth_client (
    id                  SERIAL       PRIMARY KEY,
    client_id           VARCHAR(255) NOT NULL,
    client_secret_enc   VARCHAR(2000) NOT NULL, -- AES-256 (GCM) 暗号化
    redirect_uri        VARCHAR(500) NOT NULL,
    scope               VARCHAR(500) NOT NULL,
    authorize_url       VARCHAR(500) NOT NULL,
    token_url           VARCHAR(500) NOT NULL,
    api_base_url        VARCHAR(500) NOT NULL,
    del_flg             CHAR(1)      NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER
);

COMMENT ON TABLE  m_mf_oauth_client IS 'MF クラウド会計 API OAuth2 クライアント設定。通常 1 レコード。del_flg=0 が有効。';
COMMENT ON COLUMN m_mf_oauth_client.client_secret_enc IS 'AES-256 暗号化済み Client Secret。CryptoUtil で復号。';
COMMENT ON COLUMN m_mf_oauth_client.redirect_uri IS 'アプリポータルに登録する redirect URI。開発は http://localhost:3000/finance/mf-integration/callback';
COMMENT ON COLUMN m_mf_oauth_client.scope IS 'OAuth2 スコープ。Phase 1 は public read。';

-- ========================================================================
-- t_mf_oauth_token: トークン永続化（最新 1 件 active、履歴は論理削除）
-- ========================================================================
CREATE TABLE IF NOT EXISTS t_mf_oauth_token (
    id                  BIGSERIAL    PRIMARY KEY,
    client_id           INTEGER      NOT NULL REFERENCES m_mf_oauth_client(id),
    access_token_enc    VARCHAR(4000) NOT NULL,
    refresh_token_enc   VARCHAR(4000) NOT NULL,
    token_type          VARCHAR(50)  NOT NULL DEFAULT 'Bearer',
    expires_at          TIMESTAMP    NOT NULL,
    scope               VARCHAR(500),
    del_flg             CHAR(1)      NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER
);

CREATE INDEX IF NOT EXISTS idx_mf_oauth_token_active
    ON t_mf_oauth_token(client_id, del_flg);

COMMENT ON TABLE  t_mf_oauth_token IS 'MF API OAuth2 トークン。同一 client_id で del_flg=0 は 1 レコードのみ active 運用（新規取得時は旧レコードを del_flg=1 化）。';
COMMENT ON COLUMN t_mf_oauth_token.access_token_enc IS 'AES-256 暗号化済み access token';
COMMENT ON COLUMN t_mf_oauth_token.refresh_token_enc IS 'AES-256 暗号化済み refresh token';
COMMENT ON COLUMN t_mf_oauth_token.expires_at IS 'access_token の有効期限（UTC）。残り 5 分未満で自動 refresh。';

-- ========================================================================
-- m_mf_account_mapping: MF 勘定科目 → 自社仕訳種別(借方/貸方) マッピング
-- ========================================================================
CREATE TABLE IF NOT EXISTS m_mf_account_mapping (
    id                  SERIAL       PRIMARY KEY,
    journal_kind        VARCHAR(20)  NOT NULL, -- PURCHASE / SALES / PAYMENT
    side                VARCHAR(10)  NOT NULL, -- DEBIT / CREDIT
    mf_account_id       VARCHAR(100) NOT NULL, -- MF /api/v3/accounts の id
    mf_account_name     VARCHAR(100) NOT NULL, -- 最終同期時の name（表示用キャッシュ）
    del_flg             CHAR(1)      NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER,
    CONSTRAINT chk_mf_mapping_kind CHECK (journal_kind IN ('PURCHASE','SALES','PAYMENT')),
    CONSTRAINT chk_mf_mapping_side CHECK (side IN ('DEBIT','CREDIT')),
    CONSTRAINT uq_mf_mapping_active UNIQUE (journal_kind, side, mf_account_id, del_flg)
);

COMMENT ON TABLE  m_mf_account_mapping IS 'MF 勘定科目(id) と自社仕訳種別(PURCHASE/SALES/PAYMENT) × 借方/貸方 のマッピング。admin が画面で編集。洗い替え運用。';
COMMENT ON COLUMN m_mf_account_mapping.mf_account_id IS 'MF GET /api/v3/accounts のレスポンス id';
COMMENT ON COLUMN m_mf_account_mapping.mf_account_name IS '最終同期時の name をキャッシュ。表示用で、突合判定には使わない（id ベース）。';
