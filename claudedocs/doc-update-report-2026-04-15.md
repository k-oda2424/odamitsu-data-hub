# ドキュメント更新レポート 2026-04-15

今セッションのコード変更（ShopNoAwareItemReader バグ修正 / 第2事業部月次集約行除外 /
買掛金集計ロジック改修 / 買掛金画面3ボタン化 / `verified_amount` カラム追加 /
振込明細MF変換の取引月統一 等）を、以下7ドキュメントへ反映。

## 更新サマリ

| ファイル | 変更章 | 追加/修正行 | 要点 |
|---|---|---|---|
| `docs/detailed-design/DD_04_仕入管理.md` | §6.1, §6.2, §6.4 | +60 / -20 | ShopNoAwareItemReader のリソース追跡方式とバグ修正履歴を追記、§6.4 に `00000021/00000023` 除外を明記 |
| `docs/detailed-design/DD_07_財務会計.md` | §1.1.2, §1.1.4, §2.4, §4.4.2, §4.4.3, §5.4, §11.1 | +80 / -15 | Entity に `verified_manually` / `verification_note` / `verified_amount` 追加、集計アルゴリズムに除外4段 + ACCOUNTS_PAYABLE_SHOP_NO 固定化を反映、§11.1 に新Next.js画面の差分サマリ追記 |
| `docs/04_purchase_management.md` | §8.1 Reader詳細 | +7 / -4 | ShopNoAwareItemReader のバグ修正履歴と事業部別ファイルを記載 |
| `docs/07_finance.md` | §3.3, §3.4, §3.5, §8.2 | +30 / -8 | 除外対象4種 / ACCOUNTS_PAYABLE_SHOP_NO 固定 / 運用前提 / verified_* カラム追加 |
| `docs/09_smile_integration.md` | §5 概要, §5.2 findNewPurchases | +40 / -2 | 事業部別CSV仕様 + ShopNoAwareItemReader バグ修正 + `NOT IN ('00000021','00000023')` 追記 |
| `claudedocs/design-accounts-payable.md` | §4.5, §4.7, §6.1, §7.2, §12 | +45 / -20 | 3ボタン化・状態可視化・`verified_amount` カラム・決定事項更新 |
| `claudedocs/design-payment-mf-import.md` | §5.1, §7 エンドポイント, §8 BulkVerifyDialog/rules 画面, §11 確定 | +60 / -15 | 取引月前月20日統一、backfill-codes、BulkVerifyDialog 強化、ルール画面機能追加 |

## 変更内容の対応表

### 1. ShopNoAwareItemReader のバグ修正
- `DD_04_仕入管理.md §6.2`: 動作原理を setResource フック方式に書き換え、過去のバグ詳細を追記
- `docs/04_purchase_management.md §8.1`: Reader 詳細のバグ修正履歴と事業部別ファイル説明を追加
- `docs/09_smile_integration.md §5`: 事業部別CSV表と修正履歴を新設

### 2. 第2事業部月次集約の除外
- `DD_04 §6.4`: SmilePurchaseImportTasklet セクションに `NOT IN ('00000021','00000023')` と設計思想を追記
- `docs/09_smile_integration.md §5.2`: `findNewPurchases` クエリに `NOT IN` を明記、`goods_code` 意味表を追加
- `docs/07_finance.md §3.3`: 除外対象一覧に追加

### 3. 買掛金集計仕様（AccountsPayableSummaryCalculator）
- `DD_07 §4.4.2`: 除外フィルタ4段を明示（supplier_no=303 / 00000021-23 / 論理削除 / payment_supplier_no NULL）。集計キーの shopNo を `ACCOUNTS_PAYABLE_SHOP_NO = 1` 固定に書き換え
- `DD_07 §4.4.3`: 「仕入先・税率単位」→「支払先・税率単位」と訂正
- `docs/07_finance.md §3.4`: 同等の内容を概要版で反映

### 4. 買掛金一覧画面 / 新3ボタン
- `DD_07 §11.1.5`: 新Next.js画面の差分をテーブルで追記（3ボタン・ポーリング可視化・カラム2段・デフォルトソート・手動確定解除等）
- `design-accounts-payable.md §4.5 §4.7`: カラムに「税抜/消費税」「振込明細額」、アクションボタンを3つに再編、状態可視化ロジック、バッチ呼び出し変更（`accountsPayableSummary` → `accountsPayableAggregation`）

### 5. `verified_amount` カラム追加
- `DD_07 §1.1.2, §1.1.4`: Entity フィールド表と金額カラムの使い分けに追加
- `docs/07_finance.md §8.2`: エンティティ表に3カラム追加（`verified_manually` / `verification_note` / `verified_amount`）+ 用途説明
- `design-accounts-payable.md §6.1`: Liquibase DDL を 3 カラム構成に更新、`verifiedAmount` の用途説明を追加。TS 型定義も更新

### 6. 手入力保護の SMILE 再検証スキップ
- `DD_07 §2.4, §5.4`: `verifiedManually=true` スキップを明記
- `docs/07_finance.md §3.5`: 「手入力保護」段落を追加

### 7. 振込明細MF変換（Payment MF Import）
- `design-payment-mf-import.md §5.1`: 取引月決定ルールを「5日払い・20日払いともに前月20日締め」に統一する仕様変更を明示（旧ルールを break の上書き）
- `design-payment-mf-import.md §7`: `bulk-verify` と `backfill-codes` エンドポイント、正規化ユーティリティを追記
- `design-payment-mf-import.md §8`: BulkVerifyDialog の強化UI、payment-mf-rules 画面のルール複製・自動補完ボタンを追記
- `design-payment-mf-import.md §11`: 確定事項に一括検証永続化・コード正規化・reconcile fallback・バックフィル運用を追加

## 対応していない変更（意図的）

以下は既存の記述と重複、または個別対応不要と判断し、追加更新しなかった:

- **BatchController の `targetDate` / `REQUIRES_TARGET_DATE` / `normalizeTargetDate`**:
  バッチ起動 API の仕様。`docs/07_finance.md §7.2` 以降の各ジョブに
  `targetDate` パラメータ記述はすでにある。実装詳細の Controller 変更はコードのみで管理。
- **PurchaseController の `includeTaxOrFallback`**:
  仕入一覧の税込 fallback ロジック。`claudedocs/design-purchase-list.md` に
  記載すべき局所仕様のため本一括更新では対象外（必要なら別タスクで反映）。
- **運用データ変更（m_payment_supplier 統合・論理削除、t_purchase_detail 重複削除等）**:
  運用オペレーションであり、ドキュメント仕様には該当しない。移行ログは
  Git 履歴 / `claudedocs/analysis-*.md` で追跡済み。

## 検証

全ファイルの変更は Markdown 構造（見出しレベル・テーブル整形）を保持。新規見出しは
既存の章階層に合わせて `####` / `#####` レベルで追加した。
