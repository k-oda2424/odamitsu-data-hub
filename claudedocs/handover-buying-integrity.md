# 買掛帳 MF 整合性プロジェクト 引き継ぎ資料

最終更新: 2026-04-24
ブランチ: `refactor/code-review-fixes` (未 merge、commit 7 本積み上げ)
前回セッション: 2026-04-22 〜 2026-04-24 (3 日間) で 6 軸 (B+C / D+E / 一部 F) + 差分確認機能を実装

---

## 1. プロジェクトのゴール

「**MF (MoneyForward) と自社 DB の買掛帳を完全に整合させ、過剰計上や連携漏れを検出・解消する**」

背景:
- 旧 stock-app (Spring Boot 2.1.1) がまだ一部運用中、新 oda-data-hub (Spring Boot 3.3) への移行期
- 旧バッチのバグで過剰計上が残存 (goods_no=10240 値引が summary に反映されない)
- 仕入先請求書 → 振込明細 Excel → MF 連携 のパイプラインで消費税丸め差や月ズレが頻発

---

## 2. 実装済み機能 (本セッション)

### 機能画面
| Route | 機能 | commit |
|---|---|---|
| `/finance/accounts-payable-ledger/integrity` | 全仕入先整合性レポート (4 タブ + 累積差 + 確認ボタン) | `6e1439f` + `3561d3c` |
| `/finance/accounts-payable-ledger/supplier-balances` | supplier 累積残一覧 (軸 D) | `ddfc303` |
| `/finance/mf-health` | MF 連携ヘルスチェック ダッシュボード | `ddfc303` |
| `/finance/accounts-payable-ledger` | 買掛帳 (MF 累積残 + 期間表示 + 自動調整バッジ強化) | `ae82913` + `fb29a4f` |
| `/finance/accounts-payable` | 買掛金一覧 (自動調整バッジ追加) | `fb29a4f` |

### DB スキーマ追加
- **V026**: `t_accounts_payable_summary` に `auto_adjusted_amount` / `verified_amount_tax_excluded` 追加。過去 388 行遡及 UPDATE
- **V027**: `t_consistency_review` 新設 (差分確認履歴、IGNORE / MF_APPLY、スナップショット比較)

### バックエンド追加
- `MfJournalCacheService` — MF journals の月単位累積キャッシュ (TTL なし、シングルインスタンス、shop 単位分離)
- `MfJournalFetcher` — fiscal year 30 候補 fallback + 429 retry の共通 helper
- `AccountsPayableIntegrityService` — 4 カテゴリ分類 (mfOnly/selfOnly/amountMismatch/unmatched) + 累積差 + 確認履歴 join
- `SupplierBalancesService` — asOfMonth 時点の全 supplier 累積残
- `MfHealthCheckService` — OAuth/summary/anomaly/cache の 1 画面集約
- `ConsistencyReviewService` — IGNORE / MF_APPLY (税率別按分 + previous_verified_amounts ロールバック)
- `MfPaymentAggregator` — MF debit を supplier × 月 単位集計
- `PayableMonthlyAggregator.overrideWithMfDebit` — paymentSettled を MF debit で上書き
- `AccountsPayableBackfillTasklet` + `AccountsPayableAggregationTasklet` が MF debit 連動

### 運用成果
- MF #2419 / #4516 / #5757 三友商事 / #2437 / #2451 / #4535 やしき・ハウスホールド の **過剰計上 7 件 ¥86,466 を特定 → 6 件削除済み**
- **V026 遡及 UPDATE で 89 件 (23%) の自動調整記録** (1〜13,750 円)
- **paymentSettled backfill で過去月の穴抜け解消** (2025-06-20 〜 2026-04-20)

---

## 3. 直近 commit (新しい順)

```
4ca40fb  feat(finance): paymentSettled を MF journal debit 由来に切替 (案 A)
3561d3c  feat(finance): 整合性レポート差分確認機能 (案 X+Y、V027) + UI 改善
ae82913  feat(finance): 買掛帳の MF 比較を累積残 + 期間明示で強化
fb29a4f  feat(finance): 振込明細 Excel の税抜確定額と自動調整額を DB 保持 (V026)
ddfc303  feat(finance): supplier 累積残一覧 + MF 連携ヘルスチェック (軸 D+E)
1e08219  feat(finance): MF journals 月単位累積キャッシュで API 通信を最小化
6e1439f  feat(finance): 買掛帳 整合性検出機能 (軸 B+C)
```

---

## 4. 未解決課題 / 保留

### 税理士確認待ち
- **MF #9607** 三興化学工業 2025-05-20 ¥28,050 — **前期末決算前**、削除可否の顧問判断待ち
- **期首残 journal #1 に埋没**の可能性 (合計 ¥26,686):
  - やしき 2025-05-20 ¥1,331
  - 太幸 (shop=2) 2025-05-20 ¥11,495
  - ハウスホールドジャパン 2025-05-20 ¥13,860

### 技術的な未対応
- **複数税率 supplier の CSV 分割**: 尚美堂 (023300, 10%+8%) / 竹の子の里 (030302, 10%+0%)。現状は supplier 単位 1 行で税区分は代表値。MF 側税抜/消費税内訳が ±数百円〜千円ズレる可能性
- **MF 期首前 supplier 単位累積残**: `trial_balance_bs` に sub_account 粒度なし。期首 ¥15,448,920 を supplier 単位で取り込めない (軸 A 調査で確定)。Phase B''' 相当の機能未着手
- **軸 F (監査証跡)**: 設計書ドラフトあり (`claudedocs/design-audit-trail-accounts-payable.md`)、実装未

### 他の未コミット変更 (本プロジェクト外)
- MF API Phase 1 (OAuth + 勘定科目同期) の一部追加変更
- MF Enum 翻訳 V022 migration + 関連
- MF Reconcile サービス系
- これらは別スコープでコミット予定 (次回整理)

---

## 5. 環境情報

| 項目 | 値 |
|---|---|
| Branch | `refactor/code-review-fixes` (origin より 10 commit 先行、未 push) |
| Backend | **停止中** — 起動: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=web,dev'` |
| Backend port | 8090 |
| Frontend | `cd frontend && npm run dev` (port 3000) |
| 新 DB | `oda32-postgres17` container (PG 17, port 55544, db=oda32db) |
| 旧 DB | `oda32-postgres` container (PG 9.6, port 55543) ※ 並行運用、stock-app 依存 |
| 認証 (dev) | `k_oda / asdfasdf` |
| Shop No | `1` 固定 (B-CART も一律 shop=1 計上、`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO`) |
| MF 会計期首 | `2025-06-21` (journals bucket で 2025-07-20 以降取得可、以前は fallback) |

---

## 6. 重要な設計書 / memory 参照先

### 設計書 (`claudedocs/`)
- `design-integrity-report.md` (軸 B+C)
- `design-supplier-balances-health.md` (軸 D+E)
- `design-consistency-review.md` (案 X+Y 差分確認)
- `design-supplier-partner-ledger-balance.md` (Phase A/B/B'/B''(light))
- `design-phase-b-prime-payment-settled.md` (T 勘定)
- `design-accounts-payable-ledger.md` (買掛帳画面)
- `design-audit-trail-accounts-payable.md` (軸 F ドラフト、未実装)

### テスト計画
- `test-plan-integrity-report.md`

### MEMORY (`~/.claude/projects/C--project-odamitsu-data-hub/memory/`)
- `MEMORY.md` (インデックス)
- `feature-integrity-report.md`
- `feature-supplier-balances-health.md`
- `project_residual_overcount.md` — 旧 stock-app 残存過剰計上 7 件の経緯
- `project_ap_ar_cumulative_balance.md` — Phase A〜B''(light) の経緯
- `reference_mf_api.md` — MF API / MCP メモ
- `feedback_*.md` — ユーザー指示の保存

---

## 7. 主要な実装判断 (設計思想)

| 判断 | 選択 | 理由 |
|---|---|---|
| paymentSettled のソース | **MF journal debit** (案 A) | MF が支払の真実源。振込明細 Excel は支払予定に近く、MF 手修正で乖離する |
| verified_amount のソース | 振込明細 Excel | MF に送る前の確定額なので Excel が真実 |
| 消費税差 ±100 円以内 | **自動調整** (Excel 金額で上書き) | インボイス制度の積上げ計算で仕入先請求書優先が妥当 (税理士スキル確認) |
| MF 期首前データ | verified_amount で fallback | supplier 単位の期首残が MF API で取得不可 |
| 差分確認の snapshot 閾値 | **±¥100** (MATCH_TOLERANCE 揃え) | 整合性レポートの MATCH 判定と整合 |
| 複数税率 supplier | 代表税率 1 行 (現状維持) | 対象 2 件のみ、MF 手動調整で許容 |

---

## 8. UI 確認ポイント (動作検証用)

### 買掛金一覧 (`/finance/accounts-payable?transactionMonth=2026-02-20&shopNo=1`)
- 「調整 +¥1」「調整 -¥13,750」バッジ (三興化学工業、ユニ・チャーム等 8 件)

### 買掛帳 (`/finance/accounts-payable-ledger?supplierNo=10&shopNo=1&fromMonth=2025-06-20&toMonth=2026-04-20`)
- 「MF と比較を取得」→ **MF 累積残 / 累積差** 列が表示
- 「MF 取得期間: 2025-05-20 〜 2026-04-20」注記
- 各月行に「手動」「調整 ±¥N」「BRK」「MFM」等バッジ

### 整合性レポート (`/finance/accounts-payable-ledger/integrity?shopNo=1&fromMonth=2025-06-20&toMonth=2026-04-20`)
- 4 タブ日本語化 (MF 側のみ / 自社側のみ / 金額差 / MF 未登録)
- 金額差タブに「累積差」列、末尾に `[✓確認済] [MF確定] [取消]` ボタン
- 「期末解消済を隠す」「確認済みを隠す」フィルタ

### supplier 累積残一覧 (`/finance/accounts-payable-ledger/supplier-balances`)
- asOfMonth 指定で全 supplier の self vs MF 累積残

### MF ヘルスチェック (`/finance/mf-health`)
- 🟢/🟡/🔴 判定、cache stats、anomaly 集計

---

## 9. 次回候補タスク (優先度順)

| 優先度 | タスク | 概要 |
|---|---|---|
| 🔴 高 | **実運用検証** | 今のまま業務で使い、気付いた点を次セッションで修正 |
| 🟡 中 | **税理士確認結果の反映** | #9607 含む期首前 ¥28,050 + ¥26,686 の扱い決定後、対応実装 |
| 🟡 中 | **軸 F (監査証跡)** | `design-audit-trail-accounts-payable.md` ベースで V028 + Entity Listener + 履歴テーブル |
| 🟢 低 | **軸 D' 売掛金版** | supplier → partner に置き換えた `/accounts-receivable-ledger/partner-balances` (Phase C 相当) |
| 🟢 低 | **複数税率 CSV 分割** | 尚美堂 / 竹の子の里 対応 (現状 2 件のみで影響小) |
| 🟢 低 | **MF 期首残 supplier 単位取込** | Phase B''' 相当、MF 画面 CSV or 手動インポート基盤 |
| 🟢 低 | **他の未コミット変更整理** | MF Enum 翻訳 / MF Reconcile 等の別 commit 分離 |

---

## 10. 次セッションのプロンプト例

### A. 実運用フィードバック用 (最も一般的)
```
買掛帳 MF 整合性プロジェクトの続きです。
C:\project\odamitsu-data-hub\claudedocs\handover-buying-integrity.md を先に読んで現状把握してください。

今回の相談:
<<ここに実運用で気付いた点や修正希望を記載>>

例:
- 「整合性レポートで◯◯ supplier の差分が残っているので調査してほしい」
- 「買掛金一覧のバッジ表示を◯◯に変えたい」
- 「MF #XXXX の中身と自社計上の照合をしてほしい」
```

### B. 税理士確認結果反映用
```
買掛帳プロジェクト、税理士確認結果を反映します。
handover-buying-integrity.md を読んでから:

- MF #9607 (三興化学工業 2025-05-20 ¥28,050) は < 残す / 削除 / 調整仕訳追加 > の方針
- やしき / 太幸 / ハウスホールド の 2025-05-20 期首残 ¥26,686 は < 対応内容 >

これを自社 DB と MF に反映し、整合性レポートで確認してください。
```

### C. 軸 F (監査証跡) 実装用
```
軸 F (監査証跡基盤) を /sdlc-auto で実装してください。
設計書は claudedocs/design-audit-trail-accounts-payable.md のドラフトをベースに。
handover-buying-integrity.md で現状把握後、設計書を確定してから実装に入ってください。

実装範囲:
- V028 migration (t_accounts_payable_history テーブル)
- Hibernate Entity Listener or Envers
- UI: 各 summary 行に「履歴を見る」リンク + 差分表示
```

### D. 複数税率 CSV 分割用
```
MF CSV 出力の複数税率 supplier 分割に対応してください。
handover-buying-integrity.md を読んでから:

対象: 023300 尚美堂 (10%+8%), 030302 竹の子の里 (10%+0%)

PaymentMfImportService.applyVerification の税率別 verified_amount 按分 +
MF CSV 出力を税率別 2 行構成に変更する実装を /sdlc-auto で進めてください。
```

### E. 純粋な現状確認用 (軽め)
```
買掛帳 MF 整合性プロジェクトの現状を教えてください。
C:\project\odamitsu-data-hub\claudedocs\handover-buying-integrity.md を読んで:
1. 直近 7 commit の要点を整理
2. 未解決課題一覧
3. backend を起動して smoke test (整合性レポートが動くか)
```

---

## 11. 注意点

- **ブランチは未 push**。リモート反映が必要な場合は `git push` 明示依頼を
- **旧 stock-app が並行稼働中**。新システムの変更が旧システムに影響しないことを常に確認
- **MF OAuth token 期限**: 本セッション終了時点で残 0h。次回起動時に再認証が必要な可能性 (`/finance/mf-integration` 画面から再認証)
- **未コミット変更** (`git status` で M 表示) は本プロジェクト外の作業、意図せずコミットしないよう注意
- **日本語を含む curl は bash で UTF-8 エンコード問題あり** — `--data-binary @file` 経由で送信
