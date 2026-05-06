-- G1-M4 (2026-05-06): refresh_token の真の発行日を持つカラムを追加。
-- rotation OFF (= MF レスポンスに refresh_token なし、旧 token 流用) でも
-- 新 row を insert する仕様 (persistToken の softDeleteActiveTokens + insert) のため、
-- add_date_time は実発行日と乖離する。本カラムは MF レスポンスに refresh_token があれば now()、
-- なければ旧 row 値を継承して、真の発行日 (= 540 日寿命の起点) を保持する。

ALTER TABLE t_mf_oauth_token
    ADD COLUMN IF NOT EXISTS refresh_token_issued_at TIMESTAMP;

-- backfill: 既存 row は add_date_time をコピー (= 旧仕様の挙動と一致)
UPDATE t_mf_oauth_token
SET refresh_token_issued_at = add_date_time
WHERE refresh_token_issued_at IS NULL;

-- backfill 完了後 NOT NULL 化
ALTER TABLE t_mf_oauth_token
    ALTER COLUMN refresh_token_issued_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_mf_oauth_token_refresh_issued
    ON t_mf_oauth_token (refresh_token_issued_at);

COMMENT ON COLUMN t_mf_oauth_token.refresh_token_issued_at IS
    'G1-M4 (2026-05-06): refresh_token の実発行日。rotation 動作時 = now()、'
    'rotation OFF (流用) 時 = 旧 row の値を継承。getStatus() の 540 日判定で使用。';
