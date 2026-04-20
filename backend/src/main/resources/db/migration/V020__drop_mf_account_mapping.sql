-- 2026-04-20: Phase 1 方針変更
-- mf_account_master テーブルが既に運用されており、MF 側の科目名と完全一致している。
-- 仕訳突合は journal レスポンスの account_name で直接分類すれば十分なので、
-- V019 で作成した m_mf_account_mapping は不要となった。
-- m_mf_oauth_client / t_mf_oauth_token は OAuth 基盤として引き続き使用する。

DROP TABLE IF EXISTS m_mf_account_mapping;
