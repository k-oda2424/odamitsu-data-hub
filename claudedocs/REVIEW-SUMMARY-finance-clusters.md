# 経理機能 全クラスター レビュー総合サマリー

レビュー日: 2026-05-04
対象ブランチ: `refactor/code-review-fixes`
レビュアー: Opus (Phase 1/2/3) + Codex GPT-5.5 (Phase 4)
スコープ: 経理関連 8 クラスター 16 機能

## 1. 実施概要

| Phase | 内容 | 並列数 | 成果物 |
|---|---|---|---|
| 1 | 設計書未存在クラスター 3 件の逆生成 | 3 | `claudedocs/design-{invoice-management,supplier-opening-balance,accounting-workflow}.md` |
| 2 | 全 8 クラスター Opus 設計レビュー | 8 | `claudedocs/design-review-{cluster}.md` |
| 3 | 全 8 クラスター Opus コードレビュー | 8 | `claudedocs/code-review-{cluster}.md` |
| 4 | 重要 5 クラスターに Codex 批判レビュー | 順次 | `claudedocs/codex-adversarial-{cluster}.md` |

**Codex 実施判断**: 指摘多発・重要機能 (D/F/A/C/E)。スキップ (B/H/G) は中規模・UI 中心のため Opus レビューで充分と判断。
**Codex リミット**: 計約 60 万トークン消費するも全件成功 (リミット未到達)。

---

## 2. クラスター別 Severity 集計

| # | Cluster | 機能数 | Phase 2 設計 (C/M/m) | Phase 3 コード (B/C/M/m) | Phase 4 Codex (C/M/m) | 計 |
|---|---|---|---|---|---|---|
| A | 請求 | 1 | 3/7/8 | 0/4/6/7 | 4/6/1 | 46 |
| B | Cashbook | 3 | 1/6/5 | 0/2/6/8 | スキップ | 28 |
| C | Payment-MF | 3 | 3/6/7 | 0/2/5/7 | 3/7/2 | 42 |
| D | 買掛金ファミリー | 4 | 4/9/12 | 1/5/10/12 | 5/8/0 | 66 |
| E | 売掛金 | 1 | 2/8/6 | 0/3/5/8 | 3/10/0 | 45 |
| F | MF 連携基盤 | 2 | 3/7/8 | 0/2/8/11 | 3/11/2 | 55 |
| G | 期首残 | 1 | 2/5/6 | 0/1/4/5 | スキップ | 23 |
| H | 経理ワークフロー | 1 | 2/7/6 | 0/1/4/6 | スキップ | 26 |
| **合計** | | **16** | **20/55/58** | **1/20/48/64** | **18/42/5** | **331** |

**承認状態**: 全 8 クラスター → **Needs Revision**
**マージ可否**: A は Block 判定 (Critical 4 件含む)、その他は Conditional

---

## 3. 横断的に発見された 7 大テーマ

複数クラスターで繰り返し指摘された問題群。設計レベルで対処するとレバレッジ大。

### T1. 数字の権威階層 (source of truth) が未定義
- 関連: A, C, D, E
- 「画面の数字」「MF の数字」「請求書の数字」「Excel の数字」のどれが会計上の正かが設計上明文化されていない
- D の Codex 指摘 #2、A の Codex 指摘 #1、E の Codex 指摘 #5 が同一テーマ
- **対処**: `source_of_truth_hierarchy.md` ドキュメント化 + UI に「この数字の根拠」表示

### T2. 監査証跡 (audit trail) の DB 永続化が不足
- 関連: D, E, F, A, G
- `add_user_no` / `modify_user_no` だけで「何を変更したか」は追跡不能
- D Codex #12 (finance_audit_log 提案)、F Codex #12、A Codex #5 が共通
- **対処**: 共通 `finance_audit_log` テーブル + Entity Listener (設計書ドラフト `claudedocs/design-audit-trail-accounts-payable.md` あり)

### T3. Effective dating (有効期間) の欠落
- 関連: D (期首日 4 種混在)、E (cutoff_date 履歴なし)、A (partner_group 履歴なし)、G (opening_date 5 箇所ハードコード)
- 全て「現在マスタで過去を再解釈」する構造で、過去月再集計時に汚染
- **対処**: `m_partner_billing_terms_history`, `m_partner_group_member_history`, `FinancePeriodConfig` 集約クラス導入

### T4. 認可漏れ (IDOR / missing @PreAuthorize)
- 関連: A (bulk-payment-date IDOR)、B (mf-client-mappings#create 一般開放)、C (`/convert` `/verify` 認可漏れ)、F (authorize_url admin 編集)
- shop 境界 + 機能ロール (admin/経理/一般) の二軸での権限設計欠如
- **対処**: 統一の認可マトリクス + Spring Security AOP + ShopCheckAop の機能拡張

### T5. CSV 出力の再生成可能性 / 永続化方針
- 関連: B (csv_content BOM/CRLF 復元保証なし)、C (検証済 CSV の取引日ズレ)、E (Sales CSV を summary 参照)
- 後日マスタ変更で CSV を再生成すると当時と異なる結果
- **対処**: `t_*_journal_export_lot` + `t_*_journal_export_line` (生成済み仕訳行を固定保存)

### T6. MF 連携の根本前提 (tenant binding / token 寿命)
- 関連: F (Codex 指摘 #1-3 が最重要)
- MF tenant id / number / name を保存しておらず、別会社 MF への誤接続が静かに業務データ汚染
- refresh token 540 日寿命の予兆管理なし → 月次締め当日に「期限切れで業務停止」リスク
- **対処**: F Codex 推奨アクションを優先実装 (`mfc/admin/tenant.read` scope 追加 + `last_refresh_succeeded_at` 等永続化)

### T7. 部分支払 / 入金消込の追跡可能性
- 関連: D (Codex #3)、E (Codex #8)
- 買掛側: 請求 100 万に対し 60 万支払時の充当先不明
- 売掛側: 入金消込テーブル不在、複数請求への按分追跡不可
- **対処**: `payable_settlement_allocation` / `t_receipt_allocation` テーブル新設

---

## 4. 緊急対応推奨 (Blocker + 即時修正 Critical) トップ 10

| # | クラスター | 種別 | 内容 | ファイル |
|---|---|---|---|---|
| 1 | D | Blocker | `accountsPayableSummaryInitStep` Bean 重複定義 (起動失敗 or サイレント上書き) | `AccountsPayableSummaryConfig.java:83` / `AccountsPayableAggregationConfig.java:66` |
| 2 | F | Critical | `app.crypto.salt` の dev fallback がリポジトリにハードコード (token 暗号鍵漏洩) | `application-dev.yml:21` |
| 3 | F | Critical | MF tenant binding 不在 (別会社 MF 接続でデータ汚染) | `MMfOauthClient.java`, `TMfOauthToken.java` |
| 4 | F | Critical | RestClient timeout 無限 (MF hang で全 worker thread DoS) | `MfApiClient.java:34` |
| 5 | A | Critical | `PUT /invoices/bulk-payment-date` shopNo 権限ガードなし (IDOR) | `FinanceController.java` |
| 6 | C | Critical | `/convert` `/verify` API に `@PreAuthorize` なし (一般ユーザー verified 一括書換可能) | `PaymentMfImportController.java:66-97` |
| 7 | B | Critical | `mf-client-mappings#create` admin 限定でなく仕訳取引先改ざん可 | (ChartName コード, 設計書) |
| 8 | E | Critical | CSV DL 失敗でも `markExported` 焼付け (二重計上リスク) | `AccountsReceivableController#exportMfCsv` |
| 9 | F | Critical | `MfTokenResponse` (record) auto `toString()` で access/refresh token 漏洩源 | `MfTokenResponse.java:8` |
| 10 | D | Critical | 期首日 4 種混在 (`MF_PERIOD_START=2025-05-20` / `MF_FIRST_BUCKET=2025-07-20` / `OPENING_DATE=2025-06-20` / MEMORY `2025-06-21`) | 複数ファイル |

---

## 5. 設計レベル推奨 (中期 — 1〜3 ヶ月)

### 5-1. event sourcing 風モデルへの段階的移行 (D / E / A 共通)
現状: `t_accounts_payable_summary` / `t_accounts_receivable_summary` / `t_invoice` が「集計値+検証結果+運用状態+判断結果」を 1 行に混在
推奨: `payable_ledger_event` / `receivable_ledger_event` を immutable event 保存 → summary は projection 化
短期対応: 全 summary 更新に `calculation_run_id` / `source_type` / `source_fetched_at` / `fallback_reason` を追加

### 5-2. ConsistencyReview の actionType 拡張 (D)
現状: `IGNORE` / `MF_APPLY` のみ
推奨: `PARTIAL_APPLY` / `REVIEW_LATER` / `LINK_SUPPLIER` / `MF_FIX_PENDING` / `SPLIT_APPLY`

### 5-3. MF 連携を「入力源」から「照合先」に戻す (D 案 A 再考)
Codex の最重要指摘: `MfPaymentAggregator` で MF debit を payment_settled に注入する案 A は MF 突合が自己参照になる
推奨: 支払事実は SMILE/銀行/振込明細を主系、MF は照合先

### 5-4. MF 連携の運用整合 (F)
- 毎朝の scheduled health probe + Critical 条件即時通知
- refresh token 期限 60/30/14/7 日前の段階的アラート
- `mf_integration_audit_log` (actor / operation / before/after hash / tenant / scope / IP / user-agent / reason)

---

## 6. ファイル一覧

### 設計書 (Phase 1 で逆生成 + 既存)
- `design-invoice-management.md` (新規, 350 行, TODO 14 件)
- `design-supplier-opening-balance.md` (新規, TODO 9 件)
- `design-accounting-workflow.md` (新規, TODO 15 件)
- 既存 18 本 (B/C/D/E/F の設計書群)

### 設計レビュー (Phase 2)
- `design-review-invoice-management.md`
- `design-review-mf-cashbook-import.md`
- `design-review-payment-mf-import.md`
- `design-review-accounts-payable-family.md` (最大、25 件指摘)
- `design-review-accounts-receivable.md`
- `design-review-mf-integration.md`
- `design-review-supplier-opening-balance.md`
- `design-review-accounting-workflow.md`

### コードレビュー (Phase 3)
- `code-review-invoice-management.md` (Block 判定)
- `code-review-mf-cashbook-import.md`
- `code-review-payment-mf-import.md`
- `code-review-accounts-payable-family.md` (最大、28 件指摘 + Blocker)
- `code-review-accounts-receivable.md`
- `code-review-mf-integration.md`
- `code-review-supplier-opening-balance.md`
- `code-review-accounting-workflow.md`

### Codex 批判レビュー (Phase 4)
- `codex-adversarial-accounts-payable-family.md` (D, 13 件)
- `codex-adversarial-mf-integration.md` (F, 15 件、MF 公式 doc 参照)
- `codex-adversarial-payment-mf-import.md` (C, 12 件)
- `codex-adversarial-invoice-management.md` (A, 11 件)
- `codex-adversarial-accounts-receivable.md` (E, 13 件)

⚠ Codex 出力は raw stdout を含み 4-13K 行と大きい。「codex」マーカー以降が実レビュー。後でクリーンアップ推奨。

---

## 7. 次アクション提案

### A. 緊急 (1 週間以内)
- §4 トップ 10 の Blocker + Critical (F のセキュリティ系、認可漏れ系) を順次修正
- 期首日 4 種混在 (D §4 #10) は migration を含めて V030+ で統一

### B. 短期 (1 ヶ月)
- T4 (認可) の統一マトリクス策定
- T6 (MF tenant binding + token 寿命管理) の Phase 1 実装
- 各クラスターの Major 指摘の対応表作成 (Accept/Adapt/Reject+代替/Defer)

### C. 中期 (2-3 ヶ月)
- T2 (audit_log) 共通テーブル + Entity Listener 実装
- T3 (effective dating) の `*_history` テーブル群導入
- T1 (権威階層) ドキュメント化 + UI 表示

### D. 長期 (半年〜)
- T5 (CSV 仕訳ロット固定保存)
- T7 (入金/支払の按分追跡テーブル)
- 5-1 event sourcing 移行
- 5-3 MF 連携を「照合先」に戻す (D 案 A 再考)

---

## 8. メタ情報

- 設計書 6 本の整合性 (D クラスター) は **要再同期**: 期首日・100 円閾値・累積残/closing 用語が揃っていない
- B-CART → SMILE 連携 (memory 記載) や stock-app 残存バグは今回スコープ外
- Codex GPT-5.5 は MF 公式ドキュメントを WebFetch 風に参照する能力があり、F の指摘で「MF token 540 日寿命」「revoke endpoint 仕様」など外部知識を組み合わせた高品質指摘を出した
