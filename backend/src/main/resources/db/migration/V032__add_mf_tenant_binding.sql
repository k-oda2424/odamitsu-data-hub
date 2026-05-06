-- V032: m_mf_oauth_client に MF クラウド会計 tenant binding 列を追加
--
-- 背景 (DESIGN-DECISION P1-01 / Cluster F DD-F-04):
--   現行 OAuth フローは access_token / refresh_token のみを保存しており、
--   別会社 MF を誤認可しても callback / refresh とも成功扱いになる。
--   翌期 (2026-06-21) の MF アプリ切替前に、tenant API (/v2/tenant) で取得した
--   tenant_id を保存し、callback 時 / refresh 後 / 任意のタイミングで一致検証を
--   行えるようにする (別会社誤接続検知)。
--
-- 列:
--   - mf_tenant_id     : MF tenant API のレスポンス id。callback 直後に保存し、
--                        以後の access_token 利用時に一致確認する。
--                        NULL は「未バインド (旧データ互換)」を示し、初回 callback で
--                        binding 確定する扱い。
--   - mf_tenant_name   : 同 tenant 名 (UI 表示用)。
--   - tenant_bound_at  : binding 確定タイムスタンプ。NULL なら未バインド。
--
-- 互換性:
--   既存 prod/dev 環境の m_mf_oauth_client レコードは初回適用時 NULL のままとなる。
--   `MfOauthService` 側で「mf_tenant_id IS NULL なら次回 callback で確定」扱いに
--   フォールバックする (Cluster F DD-F-04 設計)。
ALTER TABLE m_mf_oauth_client
    ADD COLUMN IF NOT EXISTS mf_tenant_id    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mf_tenant_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS tenant_bound_at TIMESTAMP;

COMMENT ON COLUMN m_mf_oauth_client.mf_tenant_id    IS 'MF tenant API (/v2/tenant) で取得した tenant id。callback 直後保存、refresh 前後で一致検証';
COMMENT ON COLUMN m_mf_oauth_client.mf_tenant_name  IS '同 tenant 名 (表示用)';
COMMENT ON COLUMN m_mf_oauth_client.tenant_bound_at IS 'tenant binding 確定タイムスタンプ。NULL なら未バインド (旧データ互換)';
