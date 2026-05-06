# DESIGN-DECISION 集約 + 優先順位

集約日: 2026-05-06
対象指摘総数: 95 件
出典: 6 triage ドキュメント (D / F / A / C / E / B+G+H)

## サマリー

| 優先度 | 件数 | 説明 |
|---|---|---|
| **P1 (Critical 隣接)** | 13 | セキュリティ・業務直結・Critical 残課題・翌期切替前必須 |
| **P2 (横断テーマ)** | 23 | 複数クラスターを横断する設計判断 (8 テーマに集約) |
| **P3 (クラスター内設計)** | 39 | クラスター内の中規模設計判断 |
| **P4 (将来設計)** | 20 | 中長期 (3 ヶ月以降)・event sourcing 系 |
| **計** | **95** | |

統計:
- **翌期 (2026-06-21) 切替前に必須**: 5 件 (P1-01 / P1-02 / P1-03 / P1-04 / P1-13)
- **横断テーマ数**: 8 (T1〜T8)
- **推奨 default 明確 (A/B 一意推奨)**: 62 件
- **ユーザー判断必須 (業務情報なしには判断不能)**: 33 件

出典 triage 別 DESIGN-DECISION 件数:
| クラスター | DD 件数 |
|---|---|
| D 買掛金 | 14 |
| F MF 連携 | 19 |
| A 請求機能 | 14 |
| C 買掛仕入 MF 変換 | 12 |
| E 売掛金 | 17 |
| B+G+H 残 3 クラスター | 19 |
| **計** | **95** |

---

## P1: Critical 隣接 (即判断推奨)

### P1-01: MF tenant binding 必須化 (翌期切替前必須)
- **元 DD ID**: DD-F-04 (Cluster F)
- **論点**: `mfc/admin/tenant.read` を取得し `mf_tenant_id/name` を保存しないと、別会社 MF への誤接続が静かに業務データ不整合を起こす。翌期 (2026-06-21) 切替時に他社 MF への誤接続検知が不可
- **選択肢**:
  - A: scope に `mfc/admin/tenant.read` 追加 + `m_mf_oauth_client` に `mf_tenant_id`, `mf_tenant_name` 追加 + callback 直後取得 + refresh 前後で一致検証 (推奨)
  - B: 現状維持 (運用で吸収)
- **推奨**: **A 強く推奨** (Codex 最重要 3 件の 1 つ)
- **理由**: 別会社 MF 誤接続は静かに業務データ不整合を起こす。再認可必須なので翌期切替直前は混乱大、今 sprint で対応すべき
- **クロス影響**: DD-F-08 (scope 変更検知 = T6) と連動、DD-F-18 (last_authorized_at) と統合スキーマ拡張可
- **判断締切**: **緊急** (2026-06-21 切替前)

### P1-02: opening 注入方針確定 (整合性レポート vs 累積残)
- **元 DD ID**: DD-BGH-01 (Cluster G)
- **論点**: `AccountsPayableIntegrityService` の cumulative diff 計算で `m_supplier_opening_balance` を注入するか否か。`SupplierBalancesService` は注入、整合性レポートは未注入 → 同じ supplier の cumulative 値が画面ごとに食い違う
- **選択肢**:
  - A: 注入する (`SupplierBalancesService` と統一、cumulative 系の意味が一貫)
  - B: 注入しない (現状実装通り、月次 per-period diff のみ意味を持つことを設計書で明示)
- **推奨**: **ユーザー判断必須** (経理運用の意図次第)
- **理由**: A/B どちらも実装可能だが、業務上の opening 概念 (期首残高をどう扱うか) によって正解が変わる
- **クロス影響**: T1 数字の権威階層、SF-G10 設計書修正と連動
- **判断締切**: **緊急** (実運用検証中、運用定着前に方針確定すべき)

### P1-03: MF debit を payment_settled の入力源にするか
- **元 DD ID**: DD-D-01 (Cluster D, Codex 1)
- **論点**: 案 A (現実装) は MF debit を自社 `payment_settled` に書き込むため、整合性レポートの片側が MF 由来 → 自己参照で支払漏れ・MF 誤仕訳が検出不能に
- **選択肢**:
  - A: 現状維持 (案 A 継続) + `payment_settled_source` カラム追加で出所明示 (Codex 推奨の妥協案)
  - B: 案 A を破棄し `verified_amount` ベースに戻す (整合性検出能力優先)
  - C: 段階的移行
- **推奨**: **A** (`payment_settled_source` enum 追加だけで監査説明可能)
- **理由**: 案 A は MEMORY.md 記載「間欠取引 supplier の業務実態と一致」の根拠あり、放棄は副作用大。出所明示で妥協
- **クロス影響**: DD-D-05 (MF 障害時 fallback)、DD-F-15 (cause 細分化)、T1 権威階層
- **判断締切**: **短期** (V026/V027 の延長で 1 sprint 内)

### P1-04: refresh token 540 日寿命の予兆管理
- **元 DD ID**: DD-F-05 (Cluster F)
- **論点**: refresh_token の `issued_at` カラム不在のため、`reAuthRequired` が常に false で 540 日満了に気付けない。本番運用後 1 年以内に静かに失効する
- **選択肢**:
  - A: `t_mf_oauth_token` に 4 列 (`refresh_token_issued_at`, `last_refresh_succeeded_at`, `refresh_failure_count`, `reauth_deadline_at`) + 60/30/14/7 日前段階アラート
  - B: 暫定: `add_date_time` を `refresh_token_issued_at` として使い、29 日経過で `reAuthRequired=true` (近似)
  - C: 現状維持
- **推奨**: **B → A** (Phase 1 で B、Phase 2 で A)
- **理由**: B は 1 sprint 内で実装可能、A は alert 基盤 (DEF-01) と統合
- **クロス影響**: DD-F-15 (cause 細分化)、DD-F-18 (last_authorized_at) とスキーマ統合
- **判断締切**: **短期** (実運用 1 年経過前に必須)

### P1-05: dev salt fallback 据え置きをどこまで例外扱いするか
- **元 DD ID**: DD-F-01 (Cluster F)
- **論点**: `MEMORY.md feedback_dev_config_fallbacks` で「触らない方針」だが、暗号化対象が高機微 OAuth 資産 (`client_secret`/`access_token`/`refresh_token`) になった以上、ガード強化を再評価すべき
- **選択肢**:
  - A: 現状維持 (dev fallback 据置)。`runbook-mf-key-rotation.md` を作成して鍵更新手順だけ整備
  - B: dev fallback を「明らかにダミー」(`__SET_APP_CRYPTO_KEY_AND_SALT_VIA_ENV__`) に変更し、起動はするが暗号化を試みると例外で開発者が即気付く構成に
  - C: OAuth 関連 `_enc` 用に専用 KMS / Vault 鍵を導入 (DD-F-02 と統合)
- **推奨**: **A + runbook 作成** を最低限。C は中期検討
- **理由**: MEMORY.md `feedback_dev_config_fallbacks` の方針が明確に存在する。C への昇格は別判断
- **クロス影響**: DD-F-02 (Vault/KMS)、DD-F-03 (rotation)、T1 セキュリティ系
- **判断締切**: **短期** (sprint 内)

### P1-06: 検証済 CSV 取引日列の運用整合 (transactionMonth 固定)
- **元 DD ID**: SF-C03 (Cluster C, Codex 1) — Critical 業務影響、SAFE-FIX 化済だが要マージ前確認
- **論点**: `exportVerifiedCsv` の CSV 取引日列が一部経路で送金日にセットされ、MF 期間帰属と運用方針 (締め日固定) が食い違う
- **選択肢**:
  - A: SF-C03 通り `transactionMonth` (締め日) 固定、送金日は MF 連携で自動付与
  - B: 送金日と締め日を切替可能に (運用 UI 追加)
- **推奨**: **A** (SF-C03 で確定済の方針を改めて確認)
- **理由**: MEMORY.md「2026-04-15 確定」記述と一致。ゴールデンマスタも更新済
- **クロス影響**: 設計書 §5.1 と Javadoc 整合 (SF-C04 で実施)
- **判断締切**: **緊急** (マージ前必須)

### P1-07: PAYABLE→DIRECT_PURCHASE 自動降格を経理判断として明示承認化
- **元 DD ID**: DDC-02 (Cluster C, Codex 2) — Critical 業務判断
- **論点**: `afterTotal=true` セクションの PAYABLE ヒットを **黙って DIRECT_PURCHASE (仕入高/課税仕入10%) に降格**。会計判断の無承認自動化で監査時に説明困難
- **選択肢**:
  - A: 自動降格を完全廃止し未登録扱い → admin がルール側で DIRECT_PURCHASE 明示登録を促す
  - B: 降格候補として preview に表示 + admin の確認ボタンで適用 (推奨)
  - C: `payment_mf_demote_log` で監査証跡記録のみ
  - D: 現状維持 + 設計書 §5.3 に降格仕様明記 (短期)
- **推奨**: **B** (preview に降格セクション + admin 確認) が監査要件と UX のバランス良
- **理由**: 自動会計判断は「税理士監査で説明困難」リスク大。経理運用 UX も損なわない
- **クロス影響**: DDC-04 (責務分離) と整合判断、T2 監査証跡基盤
- **判断締切**: **短期** (実運用フェーズ中、税理士確認時に詰める)

### P1-08: 同一振込明細の再取込 / 古い Excel 上書き防止キー
- **元 DD ID**: DDC-03 (Cluster C, Codex 3) — Critical 監査要件
- **論点**: aux 行は `(shop, transaction_month, transfer_date)` 単位で洗い替え。Excel 内容ハッシュ・import batch ID・確定状態の世代管理がない → 古い Excel 再 upload で現行 PAYABLE / aux を静かに上書き可能
- **選択肢**:
  - A: `t_payment_mf_import_batch` テーブル新設 (source file hash + transferDate + transactionMonth + 適用 user/timestamp)
  - B: ファイルハッシュのみ `t_payment_mf_import_history` に追加 + 警告判定 (短期)
  - C: 現状維持 + 運用ルール明記
- **推奨**: **B (Phase 1 短期)** → **A (Phase 2 中期、軸 F audit trail と統合)**
- **理由**: 上書きリスクは経理運用上現実、最低限の hash 比較は短期で実装すべき
- **クロス影響**: T2 監査証跡基盤 (軸 F)、T7 import batch ID
- **判断締切**: **短期** (Excel 再 upload 運用が始まる前に B を入れる)

### P1-09: 振込明細経由 verified_amount の税率行書込仕様
- **元 DD ID**: DDC-01 (Cluster C, Critical 業務影響大)
- **論点**: 現行は同一 supplier の税率別 N 行に **同じ invoice (税込総額) を全行書込**。`sumVerifiedAmountForGroup` で読取側吸収しているが、書込スキーマ自体が二重計上を内包
- **選択肢**:
  - A: 代表行のみ書込 (list.get(0) のみ verifiedAmount セット、他行は null) — 集計は「非 null 1 行のみ採用」固定
  - B: 税率別按分書込 (DB 側 `taxIncludedAmountChange` 比率で invoice を按分)
  - C: 集約値専用カラム (`verified_amount_total`) 切出し
  - D: 現状維持 + 設計書 / Entity Javadoc / DB COMMENT で不変条件明記 (短期回避)
- **推奨**: **D (短期 SAFE)** → **A (中期、最もシンプル)**
- **理由**: A/B/C は破壊的変更を伴うため、Javadoc 明示で短期回避し、A への移行は別 sprint
- **クロス影響**: DD-D-14 (税率別同額 vs 個別)、DDC-06 (書込仕様文書化) と統合判断必須
- **判断締切**: **短期** (Cluster D DD-14 と一括判断)

### P1-10: AR テーブルの「現在値キャッシュ」から「台帳/履歴」モデル分離
- **元 DD ID**: DDE-03 (Cluster E, Codex 1, Critical 大規模スキーマ変更)
- **論点**: 集計値 / 検証結果 / 手動確定値 / MF 出力可否 / 突合結果が `t_accounts_receivable_summary` 1 行に圧縮 → 「いつ誰がなぜ MF 出力対象にしたか」を時系列復元できない
- **選択肢**:
  - A: `t_ar_verification_event` + `t_ar_export_lot` + `t_ar_export_line` を新設し、summary は派生ビュー化 (大規模)
  - B: 軸 F (audit trail) と統合し `history_*` テーブルを Entity Listener で自動記録
  - C: 現状維持 + `verification_note` 自由テキストで運用カバー
- **推奨**: **B (Cluster F audit trail と統合)** → 当面 C
- **理由**: 軸 F の方針未確定、確定後に AR/AP 同時設計が効率的
- **クロス影響**: T2 監査証跡基盤 (軸 F)、T3 effective dating
- **判断締切**: **中期** (軸 F 設計確定後)

### P1-11: CSV DL 成功確定後に markExported する `StreamingResponseBody` 化
- **元 DD ID**: DDE-01 (Cluster E, Critical 業務影響大)
- **論点**: SF-E01 (0件 422 + saveAll) で「サーバ側で CSV を完成させてから一気にマーク」までは閉じるが、ブラウザがネットワーク切断・5xx 経由で受け取り損ねた場合、サーバ側はマーク済 → 再 DL で凍結値出力の窓は残る
- **選択肢**:
  - A: `StreamingResponseBody` で書き出し callback 完了後に `markExported` (HTTP 200 確定保証)
  - B: 現状維持 (SF-E02 で markExported を常時上書きにすれば再 DL 時に最新値出力されるため業務影響小)
  - C: `markExported` を撤廃し読取専用化 (Codex 6 export_lot 案と統合)
- **推奨**: **A (中期解)** もしくは Codex 6 採用なら C。当面 SF-E02 で症状緩和
- **理由**: 業務影響大だが SF-E02 で当面の症状は緩和済。A は別 sprint で実装可能
- **クロス影響**: DDE-02 (二段焼き)、DDE-03 (台帳/履歴) と整合
- **判断締切**: **短期** (実運用観察後、別 sprint で A)

### P1-12: ショップ別 invoiceLatest SQL 修正 (Cluster H Critical)
- **元 DD ID**: DD-H-Critical-1 (SF-H01 で SAFE-FIX 化、ただし業務影響大のため P1 として明示)
- **論点**: `invoiceLatest` SQL がショップ単位の MAX 集約を取らず、複数店舗の最新締日を 1 つしか返さない。経理ワークフロー画面の店舗別状態表示が誤る
- **選択肢**: SF-H01 で確定 (Window 関数 or `findLatestClosingDatePerShop`)
- **推奨**: **SF-H01 通り実施**
- **理由**: SAFE-FIX 化済、確認のみ
- **クロス影響**: SF-H10 (E2E mock 更新)、SF-H06 (DTO 化)
- **判断締切**: **緊急** (マージ前必須)

### P1-13: `MfOpeningJournalDetector` 共通化 (翌期事故予防)
- **元 DD ID**: SF-G08 (Cluster G, Critical 翌期事故予防、SAFE-FIX 化済だが翌期切替前必須のため P1)
- **論点**: `findOpeningJournal` / `isPayableOpeningJournal` の判定述語が 3 サービス (`MfOpeningBalanceService` / `MfJournalFetcher` / `AccountsPayableIntegrityService`) で散在し非対称。`sub_account_name` null チェック有無で挙動差。翌期 (2026-06-21) の opening journal 取得時に判定不一致で二重計上事故
- **選択肢**: SF-G08 通り util 化 + 全サービス置換 + ユニットテスト
- **推奨**: **SF-G08 通り実施**
- **理由**: 翌期切替直前に修正すると影響範囲読みづらい。今 sprint で確定すべき
- **クロス影響**: SF-G01 (二重計上 skip)、SF-G07 (effectiveBalance 統一)
- **判断締切**: **緊急** (2026-06-21 切替前)

---

## P2: 横断テーマ (複数クラスター連動)

### Theme T1: 数字の権威階層 (source of truth)
- **関連 DD**: DD-D-02 (権威階層明文化), DDC-06 (書込仕様文書化), DD-A-07 (Verifier 承認分離), DDC-05 (複数税率検出 UI 警告), P1-02 (opening 注入), P1-03 (MF debit), P1-09 (税率行書込)
- **共通論点**: 「画面の数字 / MF の数字 / 請求書の数字 / Excel の数字 / opening 残」のうちどれが正かが画面・サービスごとに異なる。仕入先一覧・支払一覧・請求一覧で同じ supplier の cumulative 値が異なる事象が複数クラスターで発生
- **統合選択肢**:
  - A: 階層明文化 (請求書 > SMILE > 売掛集計 > MF / verified_amount > MF debit > opening) を design doc + UI ツールチップに反映
  - B: 現状維持 (運用慣行で対処)
- **推奨**: **A** (Codex 最重要)
- **影響**: 全クラスター (D / E / A / C / G の cumulative 表示)
- **判断締切**: **短期** (1 sprint で doc 更新)

### Theme T2: 監査証跡基盤 (audit_log / 軸 F)
- **関連 DD**: DD-D-12 (Codex 12), DD-F-DEF02 (mf_integration_audit_log), DD-A-12 (M-3 入金日履歴 / DEF-03), DDE-03 (AR 履歴), DDE-08 (訂正 immutable), DDE-17 (検証ジョブ履歴), P1-08 (import batch), P1-10 (AR 台帳/履歴)
- **共通論点**: 各クラスターで「いつ誰がなぜ操作したか」の監査証跡が個別実装 or 未実装。設計書 `claudedocs/design-audit-trail-accounts-payable.md` ドラフト済だが買掛側のみ
- **統合選択肢**:
  - A: 軸 F として全 cluster (D/A/E/F/C) を統合する `history_*` テーブル基盤を別 sprint で構築 (Entity Listener 駆動)
  - B: 各クラスターで個別実装
- **推奨**: **A** (基盤統合)
- **影響**: D/A/E/F/C の 5 クラスター
- **判断締切**: **中期** (別 sprint、設計書ドラフト確定 → 実装)

### Theme T3: effective dating / 履歴モデル
- **関連 DD**: DD-D-06 (supplier 履歴), DD-A-04 (partner_group 履歴), DDE-06 (締め日履歴), DDE-08 (訂正 immutable), DD-A-08 (m_invoice_rule 有効期間), DD-BGH-16 (定期 batch)
- **共通論点**: マスタ (supplier / partner / cutoff_date / 特殊得意先ルール) の現在値のみ保持で、過去月再集計時に当時の状態を再現できない
- **統合選択肢**:
  - A: 各マスタに `effective_from` / `effective_to` 列を追加し履歴モデル化 (大規模)
  - B: 設計書に「過去月の変更は対象外」と運用ルール明記 (短期回避)
  - C: マスタ別に Reconciler で救済 (現状の partner_group 救済パターンを踏襲)
- **推奨**: **B + C (短期)** → **A (中期、監査要件発生時)**
- **影響**: D/A/E の 3 クラスター
- **判断締切**: **中期** (監査要件発生時)

### Theme T4: 認可マトリクス統一 (admin / shop user)
- **関連 DD**: DD-A-10 (admin/shop user 操作差), DDC-11 (一般ユーザのルール追加), DD-BGH-04 (mf_client_mapping 認可), DDE-14 (PathVariable boolean), SF-G04, SF-H04, SF-B01, SF-B02
- **共通論点**: 各 endpoint で `@PreAuthorize("hasRole('ADMIN')")` の付与有無が散在。admin / shop user の操作可能範囲がドキュメント化されていない
- **統合選択肢**:
  - A: 設計書に「admin: 全 shop / shop user: 自 shop のみ」のロール権限表を追加。SF-02/03 で実装済の境界を明記 + 全 endpoint 監査
  - B: ロール別 endpoint 分離 (`/admin/...`)
  - C: 現状維持 (個別 SAFE-FIX のみ)
- **推奨**: **A** (Phase 1 doc、Phase 2 で個別endpoint 整備)
- **影響**: 全クラスター
- **判断締切**: **短期** (1 sprint で doc 化)

### Theme T5: 例外ハンドリング統一 (業務メッセージ保持)
- **関連 DD**: SF-25 (Cluster D), SF-15 (Cluster F), SF-E08 (Cluster E), SF-C05 (Cluster C), SF-B10 (Cluster B), DD-F-15 (cause 細分化)
- **共通論点**: `IllegalArgumentException` (業務 validation エラー) と `IllegalStateException` (内部状態異常) を一括 422 にすると業務メッセージが消える。`MfReAuthRequiredException` / `MfScopeInsufficientException` のような業務固有例外との共存が必要
- **統合選択肢**:
  - A: `FinanceExceptionHandler` (Cluster F SF-25 で導入済) を全 finance Controller に展開し、例外型ごとに HTTP status / body 構造を分離 (`IllegalArgumentException` → 400 + 業務メッセージ、`IllegalStateException` → 422 + 汎用、`MfReAuthRequiredException` → 401 + cause)
  - B: 現状維持 (個別 catch)
- **推奨**: **A** (既に Cluster F で実装済、対称展開のみ)
- **影響**: D/F/E/C/B 5 クラスター
- **判断締切**: **短期** (SAFE-FIX として並列実施可)

### Theme T6: scope/契約変更検知 + tenant binding
- **関連 DD**: P1-01 (DD-F-04 tenant binding), DD-F-08 (scope 変更検知), DD-F-11 (contract test), DD-F-13 (URL admin 編集禁止)
- **共通論点**: MF 側の scope 設定変更・契約変更・API 仕様変更が「設定変更だけで token 側は旧 scope のまま」となり、機能毎に 403 で初検知。tenant binding 不在で別会社 MF 誤接続も検知不能
- **統合選択肢**:
  - A: 必須 scope を typed list で定義 + status に `missingScopes`/`extraScopes` 返却 + tenant_id 検証 + contract test (DD-F-04, DD-F-08, DD-F-11 の統合実装)
  - B: 個別実装
- **推奨**: **A** (P1-01 を起点に統合実装)
- **影響**: F クラスター (B/G にも波及可能性)
- **判断締切**: **緊急** (P1-01 と同タイミング、翌期切替前)

### Theme T7: import batch / 取込ロット永続化
- **関連 DD**: DD-A-02 (Excel ステージング 2 段階), DD-A-01 (t_invoice 責務分離 import_run_id), DD-A-06 (再取込ポリシー), P1-08 (DDC-03 import batch), DD-BGH-02 (csv_content 永続化), DD-BGH-14 (period_label 上書き), DDE-17 (verification_job 履歴)
- **共通論点**: Excel/CSV 取込操作の「いつ誰がどのファイルを取り込んだか」+「再取込時の世代管理」+「ファイルハッシュ」が各クラスターで個別実装 or 未実装
- **統合選択肢**:
  - A: 共通 `import_batch` テーブル基盤を構築し、各取込 (Invoice / Cashbook / Payment MF / Sales Journal) で参照
  - B: 各クラスターで個別実装
- **推奨**: **B (短期)** → **A (中期、軸 F audit trail と統合)**
- **理由**: 各クラスターの取込フォーマットが大きく異なるため、共通基盤化は早すぎる。短期は各クラスターで minimal hash 比較
- **影響**: A/C/B/E の 4 クラスター
- **判断締切**: **短期** (P1-08 で C 先行)

### Theme T8: 状態遷移の enum 化 (boolean 潰し)
- **関連 DD**: DDE-07 (mfExportEnabled → export_decision_status), DD-D-07 (ConsistencyReview action 拡張), DDC-07 (verification_match_type), DD-F-15 (re-auth cause 細分化), DDE-10 (verify API verificationResult), DDE-11 (verificationDifference 痕跡)
- **共通論点**: `boolean` (mfExportEnabled / verifiedManually 等) が「自動一致 / 手動 / 特殊運用 / 再集計 OFF / 一致 / 不一致 / 業務確定」を一括圧縮 → 監査時に出所説明不能
- **統合選択肢**:
  - A: 各 boolean を enum (`AUTO_MATCHED, MANUAL_APPROVED, SPECIAL_RULE_APPROVED, BLOCKED_MISMATCH, AUTO_RESET` 等) に拡張し、`*_reason` 自由テキストも追加
  - B: UI バッジで見え方を変えて誤解を防ぐ (DDE-10 B 案)、Service / DB は現状維持
  - C: 現状維持
- **推奨**: **B (Phase 1)** → **A (Phase 2、軸 F と統合)**
- **影響**: D/E/F/C の 4 クラスター
- **判断締切**: **中期** (軸 F 確定後)

---

## P3: クラスター内設計 (中規模、クラスター単位で判断)

### Cluster D 残 P3 (8 件)

| ID | タイトル | 推奨選択肢 |
|---|---|---|
| DD-D-03 | 部分支払を別テーブル `payable_settlement_allocation` で表現 | **B** (Codex 妥協案: 設計書に集計モデル記載のみ) |
| DD-D-04 | MF 訂正/取消仕訳のスナップショット記録 | **A** (`mf_snapshot_fetched_at`, `mf_journal_hash` 追加 + 締め済月自動上書き禁止) |
| DD-D-05 | MF API 障害時の fallback 履歴記録 | **A** (DD-D-01 と統合、`mf_payment_source` enum) |
| DD-D-07 | ConsistencyReview の action 種別拡張 | **B** (IGNORE 配下に reason enum 追加のみ、Codex 妥協案) |
| DD-D-09 | 旧 `AccountsPayableSummaryTasklet` 物理削除タイミング | **B** (本格運用 1 ヶ月後に判断、2-3 sprint 様子見) |
| DD-D-10 | supplier_no=303 除外戦略 | **A** (共通 util `PayableExclusionFilter` 全 service 適用) |
| DD-D-11 | `MfHealthCheckService` anomaly 集計 0 固定 | **B** (`PayableAnomalyCounter` 実装) |
| DD-D-12 | 設計書 B' §6.2 UI 列構成の現実への追従 | **A** (設計書修正、実装は機能要件満たしている) |

その他 D の DD-D-08 (MF debit 意味分類) は P2 T1 に集約。
DD-D-13 (整合性レポート §10 決定表補完) は doc-update 系で P3。
DD-D-14 (税率別同額 vs 個別) は P1-09 と統合。

### Cluster F 残 P3 (8 件)

| ID | タイトル | 推奨選択肢 |
|---|---|---|
| DD-F-02 | `client_secret` を Vault/KMS に外出し (Codex 5) | **B 維持 + Phase 2 で A 検討** (DEFER 寄り) |
| DD-F-03 | `client_secret` rotation 移行期間対応 | **B** (運用手順で吸収、Phase 2) |
| DD-F-06 | refresh 後の 2 相 commit (永続化失敗時の token 喪失対策) | **B** (Critical アラート、DEF-01 と統合) |
| DD-F-07 | マルチテナント設計 (shop / MF 契約) を今着手 | **B** (doc 明文化で Phase 1 OK) |
| DD-F-13 | `authorize_url`/`token_url` を admin 編集不可 | **B** (`*.moneyforward.com` 正規表現バリデーション、SAFE 寄り) |
| DD-F-14 | redirect_uri ALLOW list の env 化 | **A** (env 化が望ましい、`feedback_dev_config_fallbacks` 方針要確認) |
| DD-F-16 | `MMfOauthClient` を `@Builder(toBuilder=true)` で immutable copy 化 | **A** (CLAUDE.md 規約遵守、JPA managed state 検証必要) |
| DD-F-17 | `mf_account_master` を物理削除から論理削除に変更 | **A** (`del_flg` 追加、参照経路全洗い出し必要) |
| DD-F-19 | state 403 メッセージ詳細化 | **A** (UX 文言は運用責任者承認後に SAFE-FIX 化) |

DD-F-08 (scope 変更検知) は P2 T6。
DD-F-09 (mf_sync_job 化) → P4。
DD-F-10 (callback BFF 化) → P4。
DD-F-11 (contract test) → P4 / T6。
DD-F-12 (anomaly 0 固定) → P2 T8。
DD-F-15 (cause 細分化) → P2 T8。
DD-F-18 (last_authorized_at) は P1-04 と統合。

### Cluster A 残 P3 (8 件)

| ID | タイトル | 推奨選択肢 |
|---|---|---|
| DD-A-01 | `t_invoice` 責務分離 (Codex 1) | **B (軽量 import_run_id / source_hash / confirmed_status 追加)** Phase 1、A は中期 |
| DD-A-02 | Excel 直 UPSERT vs ステージング 2 段階 | **C (DD-A-01 B と統合)** Phase 1、A は中期 |
| DD-A-03 | 業務請求書キーの導入 (Codex 3) | **B (機能名改名「請求書管理」→「得意先別請求集計管理」短期) + A の調査開始 (中期)** |
| DD-A-04 | `m_partner_group` の履歴モデル | **B (削除を論理削除化 + snapshot_at_apply 列追加 Phase 1)** |
| DD-A-05 | 入金日に「確定」「締め」「取消」業務状態導入 | **B (`locked_at` のみ Phase 1)** |
| DD-A-06 | SMILE 修正後の再取込ポリシー | **B (設計書明記 + 警告 dialog Phase 1)** |
| DD-A-08 | 特殊得意先の `m_invoice_rule` マスタ化 | **B (application.yml に逃がす Phase 1)** → **A Phase 2** |
| DD-A-09 | virtual `TInvoice` を `t_invoice_reconciliation_group` に永続化 | **B (`is_quarterly=true` メタ列 Phase 1)** → **A Phase 2 (DD-A-08 A と同タイミング)** |
| DD-A-11 | ファイル名による shop_no 推定 → 必須 UI セレクト化 | **A** (UX 確認のため DESIGN-DECISION 分類だが実質 SAFE-FIX) |
| DD-A-13 | `closingDate` の `LocalDate + is_month_end:boolean` 化 | **B (CHECK 制約のみ短期、SF-01 で実施済)** → **A 中期 backlog** |

DD-A-07 (Verifier 承認分離) → P2 T1。
DD-A-10 (admin/shop user) → P2 T4。
DD-A-12 (入金日履歴) → P2 T2。
DD-A-14 (virtual TInvoice 廃止) は DD-A-09 と統合。

### Cluster C 残 P3 (5 件)

| ID | タイトル | 推奨選択肢 |
|---|---|---|
| DDC-04 | DIRECT_PURCHASE 動的降格の責務分離 | DDC-02 と一括判断 (P1-07 で B 採用なら A) |
| DDC-05 | 複数税率 supplier 検出時の UI 警告 | **A (警告のみ、監査痕跡として有用)** |
| DDC-08 | CP932 未マップ文字の `?` 置換を REPORT に変更 | **A (監査痕跡保護のため重要、適用前にプロダクション Excel 調査)** |
| DDC-09 | Excel フォーマット変更検知の強化 | **A (5日/20日別必須列セット定義 + preview 段階でブロック)** |
| DDC-10 | ルール候補スコアリング (deterministic) | **B (DEFER 候補)** |
| DDC-11 | 一般ユーザのルール追加権限 | **B 当面、不正登録が観測されたら A** |
| DDC-12 | MF API 直接連携の代替案 ADR | **A (低コスト、文書化のみ、DEFER 寄りの SAFE 候補)** |

DDC-01 (税率行書込) は P1-09。
DDC-02 (PAYABLE→DIRECT_PURCHASE) は P1-07。
DDC-03 (import batch) は P1-08。
DDC-06 (税率別書込文書化) は P2 T1。
DDC-07 (100 円閾値分類) は P2 T8 連動。

### Cluster E 残 P3 (10 件)

| ID | タイトル | 推奨選択肢 |
|---|---|---|
| DDE-02 | `applyMatched` の二段焼きと `markExported` の関係を一本化 | **B (現状維持 + 仕様化 SF-E21 で部分対応済)** |
| DDE-04 | 入金消込テーブル `t_receipt` / `t_receipt_allocation` 新設 | **B (Phase 1 はスコープ外明示)** → **Phase 2 として A** |
| DDE-05 | 残高管理 (前月残 + 当月売上 - 入金 = 繰越) を AR に組み込む | **A (新画面 `/finance/accounts-receivable-balance`、AP 累積残対称)** |
| DDE-06 | 締め日履歴モデル `m_partner_billing_terms_history` | **B + C (Reconciler 救済 + 設計書明記)** P2 T3 連動 |
| DDE-07 | `mfExportEnabled` の状態遷移を enum 化 | **B (`m_partner.always_export_to_mf` で吸収 短期)** P2 T8 連動 |
| DDE-08 | 訂正請求の immutable + adjustment モデル | **C (設計書 §13 リスクに記載)** P2 T3 連動 |
| DDE-09 | `aggregate` API レスポンスの `Map<String, Object>` を専用 DTO 化 | **A (フロント側型同期確認後 SAFE)** |
| DDE-10 | `verify` API の `verificationResult=1` 強制 | **B (UI バッジ分岐)** P2 T8 連動 |
| DDE-11 | `applyMatched` の差額調整痕跡 | **B (`verificationNote` 自動メモ)** SAFE 寄り |
| DDE-12 | ゴミ袋 goods_code リスト 業務確認 | **業務確認後 SF-E13 実施** |
| DDE-13 | 上様 (999999) の集計 0 円ハンドリング | **A (`applyNotFound` 相当に倒す、運用フロー確認必要)** |
| DDE-14 | PathVariable の `boolean` 仕様変更 | **A (クエリパラメータ化、フロント・バックエンド両方変更)** |
| DDE-15 | `BatchJobLauncherService` 切出し | **A (Cluster D とまとめて refactor)** |
| DDE-16 | B-CART / shop=1 統合 vs AR 集計キー | **B (運用観察)** → C は中期 |

DDE-01 (StreamingResponseBody) → P1-11。
DDE-03 (AR 台帳/履歴) → P1-10 / P2 T2。
DDE-17 (検証ジョブ非同期化) → P4。

### Cluster B/G/H 残 P3 (8 件)

| ID | タイトル | 推奨選択肢 |
|---|---|---|
| DD-BGH-02 | csv_content 永続化方針 (PII 含む CSV 全文保存) | **ユーザー判断 (A: ハッシュ + サマリ / B: BYTEA + admin RBAC / C: Base64)** |
| DD-BGH-04 | mf_client_mappings 認可仕様 (一般ユーザの create UX) | **ユーザー判断 (A: 純 admin only / B: pending/approved ステータス)** |
| DD-BGH-05 | POI XSSFWorkbook ストリーミング解析 / 同時実行ガード | **B (Semaphore + MAX_UPLOAD_BYTES 縮小、短期実用的)** |
| DD-BGH-06 | `MfOpeningBalanceService.fetchFromMfJournalOne` の Tx 分割 | **A (アンチパターン解消、明示的 2 段階)** |
| DD-BGH-07 | `updateManualAdjustment` の shop 検証ロジック (太幸 shop=2) | **ユーザー判断 (A: shop=1 統合 / B: m_supplier_shop_mapping / C: 緩和)** |
| DD-BGH-08 | INCOME/PAYMENT amountSource ガードの正式化 | **A (将来の保険として残す + テスト追加)** |
| DD-BGH-09 | `getEffectiveBalanceMap` キャッシュ戦略 | **A (Spring Cache `@Cacheable`)** |
| DD-BGH-14 | `t_cashbook_import_history` の `period_label UNIQUE` 上書き仕様 | **ユーザー判断 (UPSERT 維持 vs blocked)** P2 T7 連動 |

DD-BGH-01 (opening 注入) → P1-02。
DD-BGH-03 (月度クローズ) → P4 (中期)。
DD-BGH-10 (m_accounting_schedule マスタ化) → P4。
DD-BGH-11/15 (payment-mf-import ステップ位置) → P3 UI 設計。
DD-BGH-12 (endTime 表示) → P3 UI 改善。
DD-BGH-13 (363 行ファイル分割) → P4。
DD-BGH-16-19 (G 設計書追加 4 件) → P4 doc-update。

---

## P4: 将来設計 (中長期)

各項目: タイトル + 出典 ID + 中長期理由

### スキーマ大改造系
- **DEF-D-01**: `payable_ledger_event` (event sourcing) 移行 — Codex 11 — 中期課題、設計コスト大
- **DEF-D-09**: `m_payment_supplier_history` 履歴モデル (DD-D-06 実装) — Codex 7 — DEFER 確定後の実装
- **DEF-D-03**: shop マルチテナント拡張 (mf_account_master scope) — Codex 10 — 当面 shop=1 固定
- **DEF-A-04**: `t_invoice` 責務完全分離 (DD-A-01 A 案実装) — DD-A-01 B で凌いだ後の中期スキーマ改造
- **DEF-A-05**: `closingDate` 中期型化 (DD-A-13 A 案実装) — schema 大改造
- **DEF-A-06**: ステージング 2 段階方式 (DD-A-02 A 案実装) — SMILE 側調査必要
- **DEF-A-07**: 業務請求書キー (DD-A-03 A 案実装) — SMILE フォーマット拡張依頼
- **DEF-E-01**: AR `summary()` 集計を JPQL 化 — m-1 / m-impl-6 — 数千行規模で問題化していない
- **DEF-E-04**: aggregate 非同期エラー通知パス — A-3 — SSE / 通知 channel 整備
- **DEF-E-08**: `IEntity` 実装で AOP `ShopCheck` 強制 — m-impl-8 — Cluster A 認可基盤と統合検討
- **DDE-17**: bulkVerify 非同期ジョブ化 + Reconciler 独立化 (Codex 11/12) — Cluster F audit trail 統合時に再評価

### 監査・統制系
- **DEF-D-02**: 監査証跡 `finance_audit_log` 新設 — Codex 12 / 軸 F (P2 T2 と統合)
- **DEF-F-02**: `mf_integration_audit_log` 監査基盤 — Codex 12 (P2 T2 と統合)
- **DEF-F-03**: MF データの個人情報分類 + 保持期限定義 — Codex 13 — GDPR/個人情報保護法対応
- **DEF-A-02**: PII / 取引先情報の取扱方針明文化 — Codex 11 — 全社方針必要
- **DEF-A-03**: 監査証跡基盤 (軸 F) との統合 (DD-A-12 実装側) — 別 sprint
- **DEF-D-04**: 「期末解消済み」を別ステータス + 期間 delta 異常を別シグナル化 — Codex 13

### 運用・通知系
- **DEF-F-01**: scheduled health probe + push 通知基盤 — Codex 11
- **DEF-F-04**: degraded mode (provider_status) 表示 — Codex 14
- **DEF-F-08**: refresh_token rotation 後の MF 側 `POST /oauth/revoke` — ベストエフォート省略済
- **DEF-F-10**: 複数 admin 同時操作の application-level rate limiter (Bucket4j) — `pg_advisory_xact_lock` でほぼ吸収済

### 性能・キャッシュ系
- **DEF-F-07**: `MfJournalCacheService` を Caffeine 化 — Major-5 / M-impl-5 — OOM リスク中長期
- **DEF-D-07**: `MfJournalCacheService` 永続化 / TTL 検討 — m-7 — 設計書通り (永続化不要)
- **DEF-BGH-09**: `tryGetMfTrialBalanceClosing` のキャッシュ化 — G-impl-8

### 設計書・規約整理系
- **DEF-D-05〜18** (14 件): 設計書間の用語統一 / migration 命名規約 / Javadoc 整理 / Deprecated 整理 等 — doc-update タスクとして別途
- **DEF-F-06**: 設計書 `design-mf-integration-status.md` 更新 — 一括更新
- **DEF-F-09**: `MfBalanceReconcileService` の Cluster F 統合 — doc-update
- **DEF-F-12**: `runbook-mf-key-rotation.md` 作成 — Critical-1 §修正案 3
- **DEF-A-01**: パーサー / 検証 / 永続化の責務分離 — Codex 10 — 大規模 refactor
- **DEF-C-01**: PaymentMfImportService#cache のマルチインスタンス対応 (Redis/Postgres) — single-instance 運用
- **DEF-C-02**: Advisory lock キーに shop_no 含意 — shop_no=1 固定
- **DEF-C-03**: `MPaymentMfRule` を `CustomService` 経由化 — 中規模リファクタ
- **DEF-C-04**: aux 行 `sequence_no` を Excel rowIndex ベースに再設計 — 現状運用通り動作中
- **DEF-C-05**: PAYABLE シードの payment_supplier_code backfill を migration / ApplicationRunner 化 — onboarding doc で代替
- **DEF-C-06**: `saveHistory` 履歴 ID を Response Header で返す — 監査追跡性向上
- **DEF-BGH-01-13**: 設計書記述追加 / テスト追加 / Field コンポーネント切出し 等 — 機能影響なし

### 業務拡張系
- **DDE-04 (Phase 2)**: 入金消込テーブル `t_receipt` / `t_receipt_allocation` — Codex 8 — Phase 2 計画
- **DDE-05 (Phase 2)**: AR 残高管理画面 `/finance/accounts-receivable-balance` — Codex 2
- **DD-BGH-03**: 月度クローズ機能・ステップ完了自動判定 — H-Major-6 — 経理運用フロー
- **DD-BGH-10**: `m_accounting_schedule` マスタ化 — H-Minor m-4
- **DD-F-09**: `mf_sync_job` 化 (Codex 8) — 新規ジョブ基盤 + UI 大改造
- **DD-F-10**: callback を BFF/backend で完結 (Codex 9) — OAuth フロー全面再設計
- **DD-F-11**: MF API 仕様変更検知 (contract test, Codex 10) — Phase 2

---

## 判断バッチ提案

優先度別に判断を進める順序:

### Batch 1 (P1, 即時 — 5 件 yes/no で進める)
1. **P1-01** (MF tenant binding 必須化) → A 採用 yes/no
2. **P1-02** (opening 注入方針) → A or B ユーザー判断必須
3. **P1-12** (invoiceLatest SQL ショップ別) → SF-H01 通り yes/no
4. **P1-13** (MfOpeningJournalDetector 共通化) → SF-G08 通り yes/no
5. **P1-06** (検証済 CSV 取引日 transactionMonth 固定) → SF-C03 通り yes/no

### Batch 2 (P1 短期 — 8 件、推奨 default あるが業務確認推奨)
6. **P1-03** (MF debit を payment_settled の入力源にするか) → 推奨 A (`payment_settled_source` enum 追加)
7. **P1-04** (refresh token 540 日寿命予兆管理) → 推奨 B → A
8. **P1-05** (dev salt fallback 据え置き) → 推奨 A + runbook
9. **P1-07** (PAYABLE→DIRECT_PURCHASE 自動降格) → 推奨 B (preview 表示 + admin 確認)
10. **P1-08** (Excel 再取込時のハッシュ比較) → 推奨 B (短期) → A (中期)
11. **P1-09** (税率別行書込仕様) → 推奨 D (短期 SAFE Javadoc 明示) → A (中期)
12. **P1-10** (AR 台帳/履歴モデル) → 推奨 B (Cluster F audit trail と統合)
13. **P1-11** (CSV DL StreamingResponseBody 化) → 推奨 A (中期)、当面 SF-E02 で症状緩和

### Batch 3 (P2 横断テーマ — 8 テーマ)
- **T1** 数字の権威階層 → A (doc + UI ツールチップ)
- **T2** 監査証跡基盤 → A (軸 F として統合別 sprint)
- **T3** effective dating → B+C (短期) → A (中期)
- **T4** 認可マトリクス統一 → A (Phase 1 doc)
- **T5** 例外ハンドリング統一 → A (Cluster F 既存基盤を全展開)
- **T6** scope/契約変更検知 + tenant binding → A (P1-01 と統合)
- **T7** import batch 永続化 → B (短期) → A (中期、軸 F 統合)
- **T8** 状態遷移 enum 化 → B (Phase 1) → A (Phase 2 軸 F 統合)

### Batch 4 (P3 クラスター内 — クラスター単位で判断)
- Cluster D: 8 件
- Cluster F: 8 件
- Cluster A: 10 件 (うち 4 件は実質 SAFE-FIX 寄り)
- Cluster C: 7 件
- Cluster E: 14 件 (うち 5 件は SAFE 寄り、`B+C+業務確認` 推奨)
- Cluster B/G/H: 8 件 (うち 4 件はユーザー判断必須)

各 P3 は **クラスター単位の sprint** で消化。同一クラスターの DD は 1 sprint で一括判断するのが効率的

### Batch 5 (P4 中長期 — 判断保留可)
- 20 件、別 backlog として保持
- 業務要件発生 (監査要件 / マルチテナント / event sourcing) のトリガで再評価

---

## 関連ドキュメント

### 出典 triage docs
- `claudedocs/triage-accounts-payable-family.md` — D クラスター (DD 14 件)
- `claudedocs/triage-mf-integration.md` — F クラスター (DD 19 件)
- `claudedocs/triage-invoice-management.md` — A クラスター (DD 14 件)
- `claudedocs/triage-payment-mf-import.md` — C クラスター (DD 12 件)
- `claudedocs/triage-accounts-receivable.md` — E クラスター (DD 17 件)
- `claudedocs/triage-bgh-clusters.md` — B/G/H クラスター (DD 19 件)

### MEMORY.md 関連 feedback
- `feedback_dev_config_fallbacks` — DD-F-01 (dev salt 据え置き) / DD-F-14 (env 化) の判断軸
- `feedback_incremental_review` — 全 SAFE-FIX 適用時の確認義務
- `feedback_design_doc_vs_operations` — 設計書 vs 運用実態の優先度 (P1-06 等の根拠)
- `feedback_bcart_types_mirror_api` — DDE-16 (B-CART / shop=1 統合) の判断軸

### 既存設計ドラフト
- `claudedocs/design-audit-trail-accounts-payable.md` — P2 T2 (軸 F 監査証跡) の起点ドキュメント
- `claudedocs/design-consistency-review.md` — DD-D-07 (action 拡張) の起点
- `claudedocs/design-payment-mf-import.md` — DDC-01 / DDC-02 / DDC-03 の起点
- `claudedocs/design-payment-mf-aux-rows.md` — DDC-03 (import batch) の起点
- `claudedocs/design-supplier-opening-balance.md` — P1-02 (opening 注入) の起点

### Round 2 レビュー (Critical 残課題の出典)
- `claudedocs/code-review-accounts-payable-family-round2.md`
- `claudedocs/code-review-bgh-clusters-round2.md`
- `claudedocs/code-review-payment-mf-import-round2.md`
- `claudedocs/code-review-accounts-receivable-round2.md`
- `claudedocs/code-review-invoice-management-round2.md`
- `claudedocs/code-review-mf-integration-round2.md`
