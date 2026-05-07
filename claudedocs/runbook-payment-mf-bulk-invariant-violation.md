# Runbook: BULK_VERIFICATION 不変条件違反時の対応

**対象**: `PaymentMfImportService.sumVerifiedAmountForGroup`
**関連**: Codex Critical #1 (2026-05-06), V040 (verification_source)
**Severity**: HIGH (= MF CSV 生成 / 検証済み export を業務継続停止)

## 何が起きるのか

`PaymentMfImportService` が `t_accounts_payable_summary` から MF 出力用の合計額を集計するときに、
全行の `verification_source = 'BULK_VERIFICATION'` であるにも関わらず `verified_amount` が
全税率行で同値になっていない、という不変条件違反を検出した場合、
`FinanceInternalException("BULK_VERIFICATION 不変条件違反 ...")` を throw して業務処理を停止します。

クライアントには 422 + "内部エラーが発生しました" の汎用メッセージで応答され、
当該 (shop, supplier, transactionMonth) の検証済み export / MF CSV 生成は失敗します。

## なぜ fail-closed か

旧版は WARN ログ + SUM フォールバックでしたが、SUM すると税率行数分に金額が膨らみ、
MF CSV に過大計上された仕訳が流出するリスクがありました。

`BULK_VERIFICATION` の書込ルール (`PaymentMfImportService.applyVerification`) は
「同 (shop, supplier, transactionMonth) の全税率行に同値の集約値を冗長保持する」ため、
不一致 = アプリ経路を通らない異常 (DB 直接 UPDATE / 過去マイグレーション残存 / 手動修復ミス等) です。

過大計上を防ぐには、原因究明と修復が完了するまで CSV 生成を停止する fail-closed が安全です。

## 検出時の対応手順

1. **アラート確認**: 例外メッセージ + サーバーログから該当 supplier_no / txMonth を特定。
   - 例: `BULK_VERIFICATION 不変条件違反 (全税率行同値想定だが不一致): supplier_no=12345 txMonth=2026-01-20 perRow=[1000000, 500000, 1000000]`

2. **DB 状態確認** (本番 DB 読取):
   ```sql
   SELECT shop_no, supplier_no, transaction_month, tax_rate,
          verified_amount, verified_manually, verification_source,
          verification_note, modify_date_time, modify_user_no
     FROM t_accounts_payable_summary
    WHERE shop_no = 1
      AND supplier_no = <該当 supplier_no>
      AND transaction_month = '<該当 txMonth>'
    ORDER BY tax_rate;
   ```

3. **ケース判別**:
   - **(a) 部分手修正**: 一部の税率行だけが過去のマイグレーションや手動 UPDATE で書き換えられた。
     → ケース (b) と組み合わせて検討。
   - **(b) 仕入先請求書の税率別内訳が必要**: 元の振込明細 Excel を再アップロード可能ならそれが最も安全。
   - **(c) 旧版の SUM フォールバックで過大計上された痕跡**: MF 仕訳側に重複計上があれば訂正が必要。

4. **修復オプション** (どれか 1 つ):
   - **A. 振込明細 Excel 再アップロード** (推奨):
     `/api/v1/finance/payment-mf/preview` → `/verify/{uploadId}` で BULK_VERIFICATION を再書込。
     全税率行に同値が再度書かれて不変条件回復。
   - **B. UI から手動 verify 移行**:
     買掛金一覧から各税率行を `MANUAL_VERIFICATION` で個別 verify。
     → source 列 = MANUAL_VERIFICATION 混在になり、`sumVerifiedAmountForGroup` は SUM 経路で正しく集計。
   - **C. SQL で BULK 全行を同値に揃える** (= 元の Excel を取得できないとき):
     ```sql
     -- 例: 代表値 (例えば最大値、または業務確認済の正値) で揃える
     UPDATE t_accounts_payable_summary
        SET verified_amount = <正値>,
            verified_amount_tax_excluded = <税抜正値>,
            modify_date_time = NOW(),
            modify_user_no = <修復担当者 user_no>
      WHERE shop_no = 1
        AND supplier_no = <該当 supplier_no>
        AND transaction_month = '<該当 txMonth>'
        AND verification_source = 'BULK_VERIFICATION';
     ```
     必ずトランザクション + バックアップを取って実行し、最低 2 名で確認。

5. **MF 仕訳側の確認**:
   既に CSV 出力済の場合、`finance_audit_log` から過去の export 履歴を確認し、
   MoneyForward 上で過大仕訳が登録されていないか突合 (= `/api/v1/finance/integrity-report` 等で)。

6. **再開確認**:
   - `GET /api/v1/finance/payment-mf/export-verified/preview?transactionMonth=...` を実行し、
     例外が出ないこと + payable 件数が想定通りであることを確認。
   - 確認後に CSV ダウンロード (`/export-verified`) を実行。

## 予防策

- DB 直接 UPDATE は禁止 (= 監査ログに残らないため)。修復が必要なときは Service 層 API 経由か、
  本 runbook の手順 C のように UPDATE 文 + audit log 手動記録を必ず併用する。
- マイグレーションで `verified_amount` を更新する場合は、必ず `verification_source` も
  整合性のとれた値に更新する。
- 監視: `finance_audit_log.reason LIKE '%BULK_VERIFICATION 不変条件違反%'` の出現件数を
  アラート対象にする (現状 0 件期待値)。

## 関連ドキュメント

- 設計書: `claudedocs/design-payment-mf-import.md` §5.6
- V040 migration: `backend/src/main/resources/db/migration/V040__add_verification_source.sql`
- Service 実装: `PaymentMfImportService.sumVerifiedAmountForGroup`
- 例外マッピング: `FinanceExceptionHandler.handleFinanceInternal` (422)
