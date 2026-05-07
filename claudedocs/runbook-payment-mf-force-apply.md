# Runbook: 振込明細 force=true 強制反映の運用手順

**対象**: `POST /api/v1/finance/payment-mf/verify/{uploadId}` body `{force: true, forceReason: "..."}`
**関連**: G2-M2 (per-supplier 1 円整合性), Codex Major #3 (audit JSONB 全件保存), Codex Major #4 (forceReason 必須化)
**Severity**: HIGH (= per-supplier 1 円整合性違反を許容して反映する破壊的操作)

## 概要

通常の `applyVerification` では preview 段階で per-supplier 1 円整合性違反 (請求 != 振込 + 控除) が
1 件でも検出されると 422 + コード `PER_SUPPLIER_MISMATCH` でブロックされ、
業務担当者は Excel を修正して再アップロードする運用です。

ただし以下のような場合は Excel 側の修正が困難なため、`force=true` で違反を許容して反映できます:

- 仕入先側の請求書送付が遅延しており、振込済の Excel と請求書の整合性が
  事後的に判明している
- Excel に手入力ミスがあるが、業務的に振込は完了済で MF 計上が必要
- 端数処理 (1 円〜数円) の差で運用上問題がない

## 二段認可 (運用 runbook)

`force=true` は破壊的操作のため、実装上は最低 2 名のチェックを runbook で要求します。
コード側の二段認可 (= 別 role) は実装スコープ過大のため Phase 2 以降の検討事項。

### 実行前チェック

1. **担当者 1 名 + 確認者 1 名** を決め、両者で以下を確認:
   - per-supplier 違反の preview 一覧 (UI で全件表示)
   - 違反金額の合計が業務上許容範囲か (= 端数 / 数十円程度)
   - 振込が実際に完了しているか (銀行明細・仕入先からの確認)
   - 請求書が届いている / 翌月精算予定か

2. **forceReason 文字列の作成**:
   ```
   承認: <承認者名>, 確認: <確認者名>, 理由: <業務上の理由>
   ```
   - 例: `承認: yamada, 確認: tanaka, 理由: 仕入先X側 請求書送付遅延、振込済 (端数 ¥3 差)`
   - 100 文字以内が目安 (DB は TEXT なので無制限だが grep 性のため簡潔に)

3. **request body**:
   ```json
   {
     "force": true,
     "forceReason": "承認: yamada, 確認: tanaka, 理由: 仕入先X側 請求書送付遅延、振込済 (端数 ¥3 差)"
   }
   ```

### forceReason 必須化 (Codex Major #4)

- `force=true` かつ `forceReason` が空文字 / null / 空白のみの場合、
  400 + コード `FORCE_REASON_REQUIRED` で拒否されます。
- `force=false` (通常実行) の場合 `forceReason` は無視されます。

## 監査追跡 (audit log)

### finance_audit_log への記録

`force=true` 反映後、以下 2 行が `finance_audit_log` に記録されます:

1. **AOP aspect 記録** (operation=`payment_mf_apply`):
   - 既存の `@AuditLog` aspect が引数 / 戻り値の after snapshot を JSON で記録。

2. **補足 audit 行** (operation=`payment_mf_apply_force`):
   - **reason 列** (TEXT): `FORCE_APPLIED: per-supplier mismatches count=N, details=[...50 件まで...], reason="<forceReason>"`
   - **force_mismatch_details 列** (JSONB, V043 で追加): 全 mismatch を構造化保存
     ```json
     [
       {"line": "[5日払い] supplier=10001 transferAmount=100000 expected=99999 diff=1"},
       {"line": "[5日払い] supplier=10002 transferAmount=50000 expected=49998 diff=2"},
       ...
     ]
     ```
   - **target_pk 列** (JSONB): uploadId / userNo / transferDate / transactionMonth / fileName

### 過去の force apply を検索

```sql
-- 全件
SELECT id, occurred_at, actor_user_no, reason
  FROM finance_audit_log
 WHERE operation = 'payment_mf_apply_force'
 ORDER BY occurred_at DESC;

-- 特定 supplier の force apply 履歴
SELECT id, occurred_at, actor_user_no, reason,
       jsonb_array_length(force_mismatch_details) AS mismatch_count
  FROM finance_audit_log
 WHERE operation = 'payment_mf_apply_force'
   AND force_mismatch_details @> '[{"line": "%supplier=10001%"}]'::jsonb;

-- 過去 30 日の force apply 件数 + reason 抜粋
SELECT date_trunc('day', occurred_at) AS day,
       count(*) AS force_count,
       string_agg(left(reason, 80), ' | ') AS reasons
  FROM finance_audit_log
 WHERE operation = 'payment_mf_apply_force'
   AND occurred_at >= now() - interval '30 days'
 GROUP BY date_trunc('day', occurred_at)
 ORDER BY day DESC;
```

### 全件詳細を取り出す (税務調査・内部監査用)

```sql
SELECT id, occurred_at, actor_user_no, target_pk,
       jsonb_array_elements(force_mismatch_details)->>'line' AS mismatch_line
  FROM finance_audit_log
 WHERE id = <該当 id>;
```

## 想定される本番運用フロー

```
1. 担当者: preview 画面で per-supplier 違反を確認
2. 担当者: Excel 修正可否を判断 → 修正不能と判断
3. 担当者 + 確認者: 違反一覧 + 業務状況をレビュー
4. 確認者: forceReason 文字列を担当者に伝える
5. 担当者: UI から force=true + forceReason で再実行
6. 監査担当者: 月次で finance_audit_log を確認、過大な force 行使がないか点検
```

## 関連ドキュメント

- 設計書: `claudedocs/design-payment-mf-import.md` §5.6
- V043 migration: `backend/src/main/resources/db/migration/V043__add_force_mismatch_details.sql`
- DTO: `PaymentMfApplyRequest`
- Controller: `PaymentMfImportController.verify`
- Service: `PaymentMfImportService.applyVerification(uploadId, userNo, force, forceReason)`
- BULK 不変条件違反 runbook: `claudedocs/runbook-payment-mf-bulk-invariant-violation.md`
