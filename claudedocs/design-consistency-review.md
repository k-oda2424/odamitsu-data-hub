# 整合性レポート 差分確認機能 (案 X + Y) 設計書 v2

作成日: 2026-04-23 (v2 = 設計レビュー B1-B3, C1-C4, M1-M5 反映)
ブランチ: `refactor/code-review-fixes` 継続
関連: `design-integrity-report.md` / `design-supplier-balances-health.md` / `feature-integrity-report.md`

---

## 1. 背景と目的

### 課題
整合性レポートで検出された差分について、MF or 自社を修正しても次回再表示される。「対応済み」「未対応」を区別したい。

### ゴール
差分行に 2 つの操作を提供:
1. **IGNORE** — 確認済みマーク、非表示化 (MF 側を手修正済み等の場合もこれで代替)
2. **MF_APPLY** — MF 金額で自社 verified_amount を上書き → 次回 MATCH 化

snapshot 比較で金額再変動時は自動再表示。DELETE でロールバック可能。

**※ v1 の SELF_APPLY は廃止** (IGNORE と副作用が同じため M1 指摘で統合)。

---

## 2. スコープ

### 対象カテゴリ
整合性レポート 3 カテゴリ (mfOnly / selfOnly / amountMismatch)。

### 対象単位 (B1 対応)
mfOnly は `supplier_no=null` ケース (MF 手入力仕訳) があるため、PK は supplier_no/sub_account_name を統合した `entry_key` を使用:

- `selfOnly`, `amountMismatch`: `entry_key = supplier_no.toString()`
- `mfOnly`: `entry_key = guessedSupplierNo?.toString() ?? sub_account_name`

### 非スコープ
- **mfOnly の MF_APPLY**: 自社に supplier 行がないケースで summary 新規作成は複雑。初版は **UI で MF_APPLY ボタン無効化** (M4)
- bulk 操作 (後続改修)
- MF journal の API 経由自動修正 (危険)

---

## 3. API 設計

### 3.1 POST (新規/更新 Upsert)
```
POST /api/v1/finance/accounts-payable/integrity-report/reviews
```
**Request**:
```json
{
  "shopNo": 1,
  "entryType": "mfOnly",          // mfOnly | selfOnly | amountMismatch
  "entryKey": "73",                // supplier_no 文字列 or sub_account_name
  "transactionMonth": "2025-08-20",
  "actionType": "IGNORE",          // IGNORE | MF_APPLY (SELF_APPLY 廃止)
  "selfSnapshot": 0,               // 下記 §7 の定義通り
  "mfSnapshot": 1089,
  "note": "MF #2419 削除済み"
}
```

**バリデーション** (m4 対応):
- note: `@Size(max = 500)`
- entryType: enum validation
- actionType: enum validation
- mfOnly + actionType=MF_APPLY → 400 Bad Request (B1/M4 対応)

### 3.2 アクション別処理と副作用 (B2, C1 対応)

#### IGNORE
- review upsert のみ、副作用なし
- 既存 review が MF_APPLY だった場合は **verified_amount を previous から復元** (C1)

#### MF_APPLY
| entryType | 副作用 |
|---|---|
| `selfOnly` | 該当 supplier × 月 の全税率行で `verified_amount=0, verified_amount_tax_excluded=0, verified_manually=true, verification_result=1, mf_export_enabled=false` に更新 (自社取消) |
| `amountMismatch` | 全税率行の合計 `target_included = mfSnapshot + payment_settled`。既存 `tax_included_amount_change` の比率で**税率別に按分して verified_amount を個別セット** (B2) |
| `mfOnly` | **許可しない** (UI で無効化、Backend で 400) |

**税率別按分の具体式** (amountMismatch、既存 applyVerification 準拠):
```
target = mfSnapshot + sum(payment_settled_tax_included)
changeSumIncl = sum(tax_included_amount_change)
for each rowが税率別:
  ratio = row.tax_included_amount_change / changeSumIncl
  row.verified_amount = target * ratio  (1 円未満切り捨て)
  row.verified_amount_tax_excluded = row.verified_amount * 100 / (100 + row.tax_rate) (切り捨て)
# 端数誤差は最大金額の行で吸収
```

### 3.3 ロールバック用スナップショット (C1)
`t_consistency_review` に `previous_verified_amounts JSONB` 列追加:
- MF_APPLY 時、対象行の既存 verified_amount を `{"10.00": 123, "8.00": 456}` 形式で保存
- DELETE or IGNORE 上書き時に復元

### 3.4 DELETE
```
DELETE /api/v1/finance/accounts-payable/integrity-report/reviews
  ?shopNo=1&entryType=mfOnly&entryKey=73&transactionMonth=2025-08-20
```
MF_APPLY で書き換えた verified_amount は `previous_verified_amounts` から復元。

---

## 4. DB 設計 (V027 migration)

```sql
CREATE TABLE t_consistency_review (
    shop_no                      INTEGER      NOT NULL,
    entry_type                   VARCHAR(20)  NOT NULL,  -- mfOnly | selfOnly | amountMismatch
    entry_key                    VARCHAR(255) NOT NULL,  -- supplier_no or sub_account_name
    transaction_month            DATE         NOT NULL,
    action_type                  VARCHAR(20)  NOT NULL,  -- IGNORE | MF_APPLY
    self_snapshot                NUMERIC,
    mf_snapshot                  NUMERIC,
    previous_verified_amounts    JSONB,                  -- MF_APPLY ロールバック用
    reviewed_by                  INTEGER      NOT NULL,
    reviewed_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),  -- m6 対応
    note                         VARCHAR(500),
    PRIMARY KEY (shop_no, entry_type, entry_key, transaction_month)
);

CREATE INDEX idx_consistency_review_shop_month_type
    ON t_consistency_review (shop_no, transaction_month, entry_type);  -- C4 対応

COMMENT ON TABLE t_consistency_review IS
    '整合性レポート 差分確認履歴。PK の entry_key は selfOnly/amountMismatch では supplier_no 文字列、mfOnly では guessedSupplierNo または sub_account_name。';
```

---

## 5. Backend 実装

### 5.1 Entity
- `TConsistencyReview` (Embedded PK: `TConsistencyReviewPK`)
- JSONB は `@Type(JsonType.class)` (hypersistence-utils) で `Map<String, BigDecimal>` として扱う

### 5.2 Repository
```java
public interface TConsistencyReviewRepository
        extends JpaRepository<TConsistencyReview, TConsistencyReviewPK> {

    @Query("SELECT r, u.userName FROM TConsistencyReview r " +
           "LEFT JOIN MLoginUser u ON u.loginUserNo = r.reviewedBy " +
           "WHERE r.shopNo = :shopNo " +
           "  AND r.transactionMonth BETWEEN :fromMonth AND :toMonth")
    List<Object[]> findWithReviewerName(
        @Param("shopNo") Integer shopNo,
        @Param("fromMonth") LocalDate fromMonth,
        @Param("toMonth") LocalDate toMonth);  // M3 対応 (N+1 回避)

    void deleteByShopNoAndEntryTypeAndEntryKeyAndTransactionMonth(
        Integer shopNo, String entryType, String entryKey, LocalDate transactionMonth);
}
```

### 5.3 Service
- `ConsistencyReviewService`
  - `upsert(req, userNo)`:
    - 既存 review 取得 → 旧 actionType が MF_APPLY なら先にロールバック
    - 新 actionType が MF_APPLY なら対象行の verified_amount 退避 + 上書き (B2 按分)
    - review insert/update
  - `delete(key)`: ロールバック + review 削除
  - `findForPeriod(shopNo, fromMonth, toMonth)`: `Map<ReviewKey, ReviewDto>` 返却 (M3 join 済み)

### 5.4 整合性レポート拡張
`AccountsPayableIntegrityService.generate()` で各 entry に以下付与:
- `reviewStatus`: null / `IGNORED` / `MF_APPLIED` (m1 対応: reviewStatus は過去形、action_type は動詞で分離)
- `reviewedAt`, `reviewedByName`, `reviewNote`
- `snapshotStale`: 現在値と snapshot の差が **±¥100 超** なら true (C3 対応、MATCH_TOLERANCE と揃える)

### 5.5 Controller
```java
@PostMapping("/accounts-payable/integrity-report/reviews")
public ResponseEntity<?> saveReview(@RequestBody @Valid ConsistencyReviewRequest req) {
    assertShopAccess(req.getShopNo());  // M2 対応
    if ("mfOnly".equals(req.getEntryType()) && "MF_APPLY".equals(req.getActionType())) {
        throw new ResponseStatusException(BAD_REQUEST, "mfOnly の MF_APPLY は未対応");  // M4
    }
    return ResponseEntity.status(201).body(consistencyReviewService.upsert(req, currentUserNo()));
}

@DeleteMapping("/accounts-payable/integrity-report/reviews")
public ResponseEntity<Void> deleteReview(
        @RequestParam Integer shopNo, @RequestParam String entryType,
        @RequestParam String entryKey,
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate transactionMonth) {
    assertShopAccess(shopNo);
    consistencyReviewService.delete(shopNo, entryType, entryKey, transactionMonth);
    return ResponseEntity.noContent().build();
}
```

---

## 6. Frontend 実装

### 6.1 UI 変更
各 entry 行に 2 ボタン:
- `✓ 確認済み` (IGNORE)
- `→ MF で確定` (MF_APPLY, mfOnly では無効化)

Confirm dialog + note input 必須。

**DELETE 操作**: 確認済み行の「取り消し」リンクで review 削除 + verified_amount 復元。

### 6.2 表示制御
- デフォルト: `reviewStatus != null AND snapshotStale=false` は非表示
- トグル「確認済みを含む」で全件表示
- `snapshotStale=true` は確認済みでも再表示 (金額変動検出)

### 6.3 Type
```typescript
interface ReviewInfo {
  reviewStatus: 'IGNORED' | 'MF_APPLIED' | null
  reviewedAt: string | null
  reviewedByName: string | null
  reviewNote: string | null
  snapshotStale: boolean
}
// 既存 MfOnlyEntry / SelfOnlyEntry / AmountMismatchEntry に埋め込み
```

---

## 7. Snapshot 比較ロジック (B3 明確化)

各 entry の snapshot 保存値と現在値の定義:

| entryType | self_snapshot | mf_snapshot | 現在値 self / mf | stale 判定 |
|---|---|---|---|---|
| mfOnly | 0 | periodDelta (= credit − debit) | 0 / credit − debit | `|mf_current − mf_snapshot| > ¥100` |
| selfOnly | selfDelta (= change − payment) | 0 | selfDelta / 0 | `|self_current − self_snapshot| > ¥100` |
| amountMismatch | selfDelta | mfDelta | selfDelta / mfDelta | `|self_current − self_snapshot| > ¥100 OR |mf_current − mf_snapshot| > ¥100` |

**stale 閾値**: MATCH_TOLERANCE (±¥100) と一致 (C3)。

---

## 8. Upsert/Delete 副作用フロー (C2 明確化)

```
upsert(req):
  old = findByPK
  if old != null AND old.actionType == MF_APPLY:
    rollbackVerifiedAmount(old.previous_verified_amounts)  # ロールバック先行
  if req.actionType == MF_APPLY:
    previous = captureCurrentVerifiedAmounts()  # JSONB に保存
    applyMfOverride(req)
  save review(req, previous)

delete(pk):
  old = findByPK
  if old != null AND old.actionType == MF_APPLY:
    rollbackVerifiedAmount(old.previous_verified_amounts)
  delete review
```

---

## 9. パフォーマンス (C4)
- review 取得は `findWithReviewerName` で 1 クエリ (JOIN fetch)
- 現状 /integrity-report 8-15s のうち review join は < 100ms 想定
- review テーブル想定規模: 数百 〜 数千行。index `(shop_no, transaction_month, entry_type)` で期間 + カテゴリ絞り込み O(log n)

---

## 10. テスト計画

### Unit
- `upsert`:
  - IGNORE 新規 → review insert、verified_amount 無変更
  - MF_APPLY (selfOnly) → 全税率行 verified_amount=0、previous 保存
  - MF_APPLY (amountMismatch) → 税率別按分 verified_amount 設定
  - mfOnly + MF_APPLY → 400
  - IGNORE → MF_APPLY 切替 → verified_amount 上書き
  - MF_APPLY → IGNORE 切替 → ロールバック
- `delete`: MF_APPLY 行を削除 → ロールバック

### Integration
- POST 201 + DB insert 確認
- DELETE 204 + 行消失 + verified_amount 復元

### E2E
- 整合性レポートで [確認済み] ボタン → 非表示化
- 金額変動シミュレート (summary 手動更新) → 再表示確認
- [MF で確定] → 該当 supplier 月が次回レポートで消える

---

## 11. 実装順序

1. Migration V027 + Entity + PK + Repository (40 min)
2. ConsistencyReviewService (upsert / delete / rollback / 按分ロジック) (1.5 h)
3. Controller (POST / DELETE) (20 min)
4. 整合性レポートサービスに review 取得 + snapshotStale 判定組込み (1 h)
5. Frontend types + 2 ボタン + confirm + 表示制御 (1.5 h)
6. smoke (curl + UI 動作) (30 min)
7. commit (10 min)

**合計工数**: 約 5.5 時間 (レビュー指摘の按分/ロールバック/join fetch を反映して v1 の 4h から増加)

---

## 12. レビュー反映サマリ

| 指摘 | 対応 |
|---|---|
| B1 mfOnly supplier_no NULL | entry_key VARCHAR に統一、supplier_no/sub_account_name どちらかを格納 |
| B2 税率別按分 | amountMismatch で change 比按分、selfOnly で全行 0、mfOnly は MF_APPLY 不可 |
| B3 snapshot 定義 | §7 表で各 entryType の self/mf snapshot を明記、example JSON も合わせる |
| C1 DELETE ロールバック | previous_verified_amounts JSONB 列、DELETE/IGNORE 切替時に復元 |
| C2 upsert 副作用 | §8 フロー明示 |
| C3 ±¥0 閾値 | ±¥100 (MATCH_TOLERANCE) に変更 |
| C4 パフォーマンス | `(shop_no, transaction_month, entry_type)` index 追加 |
| M1 SELF_APPLY 廃止 | IGNORE に統合、note に「MF修正済」記載で代替 |
| M2 shop 越境 | assertShopAccess を DELETE でも呼ぶ、Repository query に shopNo 必須 |
| M3 N+1 | findWithReviewerName で JOIN fetch |
| M4 mfOnly MF_APPLY | UI 無効化 + Backend 400 |
| m1 命名 | reviewStatus は過去形 (IGNORED/MF_APPLIED)、action_type は動詞 |
| m4 note サイズ | `@Size(max=500)` |
| m6 タイムゾーン | TIMESTAMPTZ |
