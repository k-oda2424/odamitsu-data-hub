# 経理機能 修正対応 完了サマリー

完了日: 2026-05-04
対象ブランチ: `refactor/code-review-fixes`
対応範囲: 8 クラスター (D 買掛金 / F MF基盤 / A 請求 / C Payment-MF / E 売掛金 / B Cashbook / G 期首残 / H ワークフロー)

## 1. 全体実績

| 指標 | 数値 |
|---|---|
| 全レビュー指摘数 | 約 330 件 (Critical 57 / Major 145 / Minor 127 + Blocker 1) |
| **適用済 SAFE-FIX** | **151 件** |
| **Round 2 追加修正** | **19 件** |
| **総修正件数** | **170 件** |
| DESIGN-DECISION (要ユーザー判断) | 95 件 |
| DEFER (将来課題) | 65 件 |
| ALREADY-RESOLVED (Cluster 間で解消) | 24 件 |

## 2. クラスター別実績

| Cluster | SAFE-FIX | Round 2 | 残 DESIGN-DECISION | 残 DEFER |
|---|---|---|---|---|
| D 買掛金ファミリー | 28 | 4 | 14 | 19 |
| F MF 連携基盤 | 22 | 4 | 19 | 12 |
| A 請求機能 | 24 | 3 | 14 | 7 |
| C 買掛仕入 MF 変換 | 21 | 2 | 12 | 6 |
| E 売掛金 | 21 | 0 | 17 | 8 |
| B Cashbook Import | 13 | 0 | 7 | 5 |
| G 期首残 | 10 | 2 | 5 | 4 |
| H 経理ワークフロー | 12 | 4 | 7 | 4 |

## 3. 主要解消 Critical/Blocker

| ID | クラスター | 内容 | 状態 |
|---|---|---|---|
| D-Blocker | D | `accountsPayableSummaryInitStep` Bean 重複定義 | ✅ 解消 |
| F-Crit-1 | F | RestClient timeout 無限 (DoS リスク) | ✅ 解消 |
| F-Crit-2 | F | `MfTokenResponse` record auto toString() で token 漏洩 | ✅ 解消 |
| A-Crit-IDOR | A | bulk-payment-date / 取込 endpoint shopNo IDOR | ✅ 解消 |
| C-Crit-Auth | C | `/convert` `/verify` API 認可漏れ | ✅ 解消 |
| B-Crit-1 | B | `mf-client-mappings#create` 一般開放 | ✅ 解消 |
| E-Crit-1 | E | CSV DL 失敗で markExported 焼付け (二重計上) | ✅ 解消 |
| E-Crit-2 | E | bulkVerify 3 分割 tx → BulkVerifyService 1 tx 化 | ✅ 解消 |
| G-Crit-1 | G | `SupplierBalancesService` journal #1 skip 抜け | ✅ 解消 |
| H-Crit-1 | H | ショップ跨ぎ MAX 取得バグ | ✅ 解消 |
| H-Crit-2 | H | ジョブ名 3 重定義 → `BatchJobCatalog` 集約 | ✅ 解消 |
| D-Crit | D | 期首日 4 種混在 → `MfPeriodConstants` 集約 | ✅ 解消 |
| A-CN5 | A | `LoginUserUtil` IDOR バイパス → fail-closed 化 | ✅ 解消 |
| F-Round2-Tenant | F | (Codex) MF tenant binding 不在 | ⏸ DESIGN-DECISION |

## 4. 主な新規ファイル / 共通基盤強化

### 新規共通定数 / Util
- `MfPeriodConstants.java` (Cluster D) — 期首日 4 種定数
- `FinancePeriodConfig.java` (Cluster G) — opening 関連定数 (予約)
- `MfOpeningJournalDetector.java` (Cluster G) — journal #1 判定統一 util
- `BatchJobCatalog.java` (Cluster H) — 19 ジョブ集約 (List of Record)

### 新規 Service / Handler
- `FinanceExceptionHandler.java` (Cluster F) — `@RestControllerAdvice` で 401/403/422 統一
- `MfHttpClientConfig.java` (Cluster F) — RestClient timeout 設定
- `SensitiveLogMasker.java` (Cluster F) — token 漏洩防止 masker
- `MfDebugApiClient.java` + `MfIntegrationDebugController.java` (Cluster F) — `@Profile({"dev","test"})` 分離
- `BulkVerifyService.java` (Cluster E) — 売掛金 1 tx 化
- `CutoffType.java` enum (Cluster E)
- `AccountingStatusResponse.java` record + 3 child records (Cluster H) — Map → 型安全 DTO

### 新規 DTO
- `ErrorResponse.java` record (Cluster F) — `{message, code, timestamp, requiredScope}`
- `AccountsPayableSummaryProjection.java` record (Cluster D) — JPQL 集計

### 新規 migration
- `V030__deprecate_old_payable_summary_jobs.sql` (Cluster D) — 旧 Bean 廃止
- `V031__create_t_invoice_and_m_partner_group.sql` (Cluster A) — DDL 欠落補填
- `claudedocs/runbook-v031-baseline.md` (Cluster A) — 本番適用手順

### 新規 Frontend
- `frontend/lib/payment-type.ts` (Cluster E) — 共通ヘルパ
- `frontend/lib/utils.ts` の `parseAmount` 拡張 (Cluster G)
- `frontend/types/partner-group.ts` (Cluster A) — 型集約
- `frontend/components/pages/finance/ConsistencyReviewDialog.tsx` (Cluster D)

## 5. 横断的に適用された改善パターン

| パターン | 適用クラスター | 効果 |
|---|---|---|
| `LoginUserUtil.resolveEffectiveShopNo` (fail-closed) | A→F→C→E→G→H | IDOR バイパス防止 |
| `FinanceConstants.MATCH_TOLERANCE` 集約 | D→C→E | マジックナンバー 5 → 1 |
| `FinanceExceptionHandler` 委譲 | D→F→C→E→B→H | 例外処理統一 |
| `IllegalArgumentException` 利用 (業務メッセージ保持) | C→E→B | UX 改善 (Cluster C round 2 学び) |
| `IEntity` 規約準拠 | C→B/G/H | CLAUDE.md 規約遵守 |
| `verified_manually` 保護 | D→F | 手動確定行の自動上書き防止 |

## 6. 未対応事項 (要ユーザー判断)

### 緊急性高 (DESIGN-DECISION のうち優先度高)
- **DD-BGH-01**: `AccountsPayableIntegrityService` の opening 注入方針確定
- **CR-G01 DB 検証**: `summary.totalDiff` の改修前後比較 (実環境で要 curl)
- **DD-BGH-04**: `mf_client_mappings` の pending/approved status 導入判断

### 中期 (3 ヶ月以内推奨)
- T1 数字の権威階層明文化 (DD-D-02 / DD-A-07 / DD-E-05 等)
- T2 監査証跡 `finance_audit_log` 共通テーブル (軸 F)
- T6 MF tenant binding (Codex F-1) — 翌期 (2026-06-21) 切替前必須
- DD-D-01: MF debit を payment_settled に注入する案 A の継続/破棄判断

### 長期 (半年〜)
- T5 CSV 仕訳ロット永続化 (DEF-D-01)
- T7 入金消込 / 部分支払按分テーブル (DEF-E-08)
- event sourcing 移行 (Codex D-11)

## 7. 検証チェックリスト (本番反映前)

### コードレベル
- [x] 全 SAFE-FIX で `compileJava` + `tsc --noEmit` PASS
- [x] 既存テスト全 PASS (12 ゴールデンマスタ含む)
- [ ] **JVM 再起動** + Spring context 起動確認
- [ ] **新規 Bean** (`BatchJobCatalog` / `MfHttpClientConfig` / `BulkVerifyService` 等) の DI 確認

### 認可疎通確認 (admin / shop user / 一般ユーザの 3 系統)
- [ ] `POST /api/v1/finance/mf-client-mappings` で 一般ユーザ 403
- [ ] `PUT /invoices/bulk-payment-date` で他 shop の invoice 混入時 403
- [ ] `POST /api/v1/finance/payment-mf-import/convert` で 一般ユーザ 403
- [ ] `GET /api/v1/finance/accounting-status` で 一般ユーザ 403
- [ ] `GET /api/v1/finance/supplier-opening-balance?shopNo=2` で shop=1 ユーザ 403

### 業務挙動確認
- [ ] **CR-G01 DB 検証**: `GET /supplier-balances?shopNo=1&asOfMonth=2026-04-20` の `summary.totalDiff` 改修前後比較
- [ ] 検証済 CSV 出力で取引日が締め日 (transactionMonth) になることを MF テスト環境で確認 (SF-C03)
- [ ] 整合性レポート → MF_APPLY → 買掛金一覧で `auto_adjusted_amount` 消滅 (SF-D-03)
- [ ] 認可フロー: `/finance/mf-integration/oauth/authorize-url` → callback → token 永続化
- [ ] `/finance/mf-health` で `apiReachable` 表示 (SF-F-22 + MA-02 fix)

### Migration
- [ ] V030 (旧 Bean 廃止) — 起動時 Bean 登録なし確認
- [ ] V031 (t_invoice DDL) — 既存 prod に対しては `runbook-v031-baseline.md` の手順で baseline 引き上げ

## 8. 次セッション引き継ぎ

### 優先 1 (即時)
- **CR-G01 DB 検証**: 累積残一覧の数値整合確認 → 結果次第で revert または続行判断
- **DD-BGH-01 確定**: AccountsPayableIntegrityService opening 注入方針 → 設計書 §7.2 のプレースホルダ解消
- **JVM 再起動 + 認可疎通**: §7 検証チェックリストの全項目

### 優先 2 (1 週間以内)
- DESIGN-DECISION 95 件のうち、Critical 隣接 6 件 (DD-D-01 / DD-F-01 tenant / DD-BGH-04 / DD-A-07 権威階層 / DD-C-01 / DD-E-05) のユーザー判断
- BatchJobCatalog の `/api/v1/batch/job-catalog` endpoint 化 (MJ-H02)
- MfPaymentAggregator cache の `MfAccountSyncService` 連動 invalidate (MJ-D-MF-3)

### 優先 3 (1 ヶ月以内)
- 軸 F 監査証跡基盤 (`finance_audit_log`) 設計確定 + 実装着手
- 残 DEFER 65 件のうち T2/T3/T4 の優先判断

## 9. 関連ドキュメント

### Triage 文書 (8 件)
- `claudedocs/triage-accounts-payable-family.md` (D)
- `claudedocs/triage-mf-integration.md` (F)
- `claudedocs/triage-invoice-management.md` (A)
- `claudedocs/triage-payment-mf-import.md` (C)
- `claudedocs/triage-accounts-receivable.md` (E)
- `claudedocs/triage-bgh-clusters.md` (B/G/H)

### Round 2 レビュー (7 件)
- `claudedocs/code-review-{cluster}-round2.md` (D / F / A / C / E / bgh-clusters)

### Runbook
- `claudedocs/runbook-v031-baseline.md` (A migration baseline 手順)

### 元レビュー (Phase 1-4 産出物、計 21 本)
- 設計書 21 本 (うち 3 本は逆生成)
- Opus 設計レビュー 8 本
- Opus コードレビュー 8 本
- Codex 批判レビュー 5 本
