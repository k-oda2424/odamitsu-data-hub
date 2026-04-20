-- 2026-04-21: MF OAuth state を DB 永続化 (B-3)、t_mf_oauth_token に active 一意制約 (B-W2)、PKCE サポート (B-4)
-- 設計書: claudedocs/design-mf-integration-status.md

-- ========================================================================
-- t_mf_oauth_state: OAuth2 認可フロー state + PKCE code_verifier 永続化
-- ========================================================================
-- 旧実装は ConcurrentHashMap 管理 (MfOauthStateStore) だったため
-- マルチ JVM / 再起動で state が消失し認可不可になる問題があった。
-- state + code_verifier (PKCE S256) を DB に短期保管し、expired を sweep する。
CREATE TABLE IF NOT EXISTS t_mf_oauth_state (
    state               VARCHAR(64)   PRIMARY KEY,
    user_no             INTEGER,
    code_verifier       VARCHAR(128)  NOT NULL,
    expires_at          TIMESTAMP     NOT NULL,
    add_date_time       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mf_oauth_state_expires
    ON t_mf_oauth_state(expires_at);

COMMENT ON TABLE  t_mf_oauth_state         IS 'MF OAuth2 認可フローの state (CSRF 防止) + PKCE code_verifier。TTL 10 分。';
COMMENT ON COLUMN t_mf_oauth_state.state   IS '認可リクエスト時に発行 → callback で検証 (verifyAndConsume)';
COMMENT ON COLUMN t_mf_oauth_state.code_verifier IS 'PKCE S256 の verifier。authorize URL には S256(code_verifier) を code_challenge として送る。';

-- ========================================================================
-- t_mf_oauth_token: active 1 件保証 (B-W2)
-- ========================================================================
-- 同一 client_id で del_flg='0' が必ず 1 件以下になるよう partial unique index を追加。
-- softDeleteActiveTokens → save の race や例外コミットでアクティブ 2 件状態になるのを防ぐ。
CREATE UNIQUE INDEX IF NOT EXISTS uq_mf_oauth_token_active
    ON t_mf_oauth_token(client_id)
    WHERE del_flg = '0';
