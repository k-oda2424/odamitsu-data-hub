-- G2-M2 fix (Codex Major #3, 2026-05-06): force=true 時の per-supplier mismatch を全件 JSONB で保存する。
--
-- 背景:
--   PaymentMfImportService.writeForceAppliedAuditRow が finance_audit_log.reason 列に
--   FORCE_APPLIED: per-supplier mismatches count=N, details=[...50件...] を組み立てて記録するが、
--   先頭 50 件で切り詰めていたため、UI の 100 件省略表示と合わせて
--   「承認 (force=true) 内容」「実際の処理対象 (= 全件)」「監査証跡 (= 50 件)」の 3 者が乖離していた。
--   税務監査・内部監査で「結局どの supplier で何円ずれたのか」を後追いで再現できなくなる。
--
-- 対応:
--   reason 列は件数表示用 (varchar 容量節約) のままにし、
--   force_mismatch_details JSONB 列を追加して全件を構造化保存する。
--   GIN index で「特定 supplier_no が含まれる force apply ログ」の検索に備える。
--
-- 関連:
--   PaymentMfImportService.writeForceAppliedAuditRow / buildForceAppliedReason
--   設計書: claudedocs/design-payment-mf-import.md §5.6 (G2-M2)
--   runbook: claudedocs/runbook-payment-mf-force-apply.md (運用手順、本対応で詳細列を確認できる旨追記)

ALTER TABLE finance_audit_log
    ADD COLUMN IF NOT EXISTS force_mismatch_details JSONB;

CREATE INDEX IF NOT EXISTS idx_finance_audit_log_force_details
    ON finance_audit_log USING GIN (force_mismatch_details);

COMMENT ON COLUMN finance_audit_log.force_mismatch_details IS
    'G2-M2 fix (Codex Major #3): force=true 時の per-supplier mismatch 全件 JSON 配列。'
    'reason 列の 50 件切り詰め (容量配慮) との乖離を防ぐため、監査追跡用に全件構造化保存する。'
    '形式: [{"line": "[5日払い] supplier=10001 ..."}, ...] 等の string entries 配列。'
    'force=false の通常 audit 行や、force=true でも mismatch 0 件の行では NULL のまま。';
