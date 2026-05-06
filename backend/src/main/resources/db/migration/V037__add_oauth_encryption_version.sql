-- ============================================================
-- V037: MF OAuth 関連 _enc カラムに encryption version 列を追加
-- ============================================================
-- C1 (Codex 批判 Critical): V033 (Java migration) は旧鍵 → 新鍵への一括再暗号化を
-- 行う際、Flyway 履歴記録前にプロセス停止すると、暗号文は新鍵化済みなのに
-- V033 未適用扱い → 次回起動で旧鍵 decrypt 試行 → 復旧困難になっていた。
--
-- 本 migration で「行ごとの暗号化バージョン」を追跡できる列を追加し、
-- V033 を idempotent (途中再開可能) にする土台を提供する。
--
--   version=1: 旧鍵 (CryptoUtil / APP_CRYPTO_KEY+SALT) で暗号化
--   version=2: 新鍵 (OauthCryptoUtil / APP_CRYPTO_OAUTH_KEY+SALT) で暗号化
--
-- V033 が既に成功適用済の環境では、列追加後に「全行 version=2」とマークする
-- (DEFAULT 1 のままだと再実行時に旧鍵 decrypt 試行してしまうため)。
-- 新規環境では V033 → V037 の順で適用される: V033 は version 列が無いため
-- 「全行旧鍵」前提で動作 (version 列存在チェック付き)、その後 V037 が DEFAULT 1
-- で列追加 → V033 適用済マーカーで全行 2 に更新する。
--
-- @since 2026-05-04
-- ============================================================

ALTER TABLE m_mf_oauth_client
    ADD COLUMN IF NOT EXISTS oauth_encryption_version SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE t_mf_oauth_token
    ADD COLUMN IF NOT EXISTS oauth_encryption_version SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN m_mf_oauth_client.oauth_encryption_version IS
    'OAuth 暗号化バージョン (1=旧鍵 APP_CRYPTO_KEY/SALT, 2=新鍵 APP_CRYPTO_OAUTH_KEY/SALT)。V033 idempotent 化用。';
COMMENT ON COLUMN t_mf_oauth_token.oauth_encryption_version IS
    'OAuth 暗号化バージョン (1=旧鍵 APP_CRYPTO_KEY/SALT, 2=新鍵 APP_CRYPTO_OAUTH_KEY/SALT)。V033 idempotent 化用。';

-- V033 既適用環境のマーカー: 全行を version=2 にセット
-- (V037 適用前の本番では V033 が一括再暗号化済み = 全行新鍵)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '033' AND success = true) THEN
        UPDATE m_mf_oauth_client SET oauth_encryption_version = 2;
        UPDATE t_mf_oauth_token SET oauth_encryption_version = 2;
        RAISE NOTICE 'V037: V033 適用済のため全行を oauth_encryption_version=2 にマークしました';
    ELSE
        RAISE NOTICE 'V037: V033 未適用 (新規環境 or V037 先行適用)。oauth_encryption_version=1 default のまま';
    END IF;
END $$;

-- 部分再暗号化時の partial scan 用 index (件数は少ないが filter 効率化のため)
CREATE INDEX IF NOT EXISTS idx_mf_oauth_client_enc_ver
    ON m_mf_oauth_client (oauth_encryption_version);
CREATE INDEX IF NOT EXISTS idx_mf_oauth_token_enc_ver
    ON t_mf_oauth_token (oauth_encryption_version);
