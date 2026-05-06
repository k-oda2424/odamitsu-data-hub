# Runbook: V031 baseline 引き上げ手順 (請求機能 DDL 正規化)

作成日: 2026-05-04
対象: SF-01 (Cluster A 請求機能 SAFE-FIX) — `V031__create_t_invoice_and_m_partner_group.sql`
影響対象: prod 環境 (既存 DB に手動 DDL / Hibernate ddl-auto 由来の `t_invoice` / `m_partner_group` / `m_partner_group_member` が存在)

---

## 概要

V031 は以下 3 テーブルを Flyway 管理下に正規化する:

- `t_invoice`           — `shop_no NOT NULL` + `UNIQUE(partner_code, closing_date, shop_no)` + `CHECK closing_date format`
- `m_partner_group`     — `del_flg` を追加 (SF-20 論理削除化の前提)
- `m_partner_group_member` — `UNIQUE(partner_group_id, partner_code)` + FK + index

すべての DDL は **冪等** (CREATE TABLE IF NOT EXISTS / ALTER ... IF NOT EXISTS / DO ブロック内で pg_constraint 確認) のため、
**baseline 引き上げなしで素通し可能** だが、prod での明示的な手順を以下に記す。

---

## prod 適用前チェック

### 1. スキーマ現状確認

```sql
\d t_invoice
\d m_partner_group
\d m_partner_group_member
```

### 2. UNIQUE 制約候補の重複確認 (0 件であること)

```sql
-- t_invoice: (partner_code, closing_date, shop_no) 重複
SELECT partner_code, closing_date, shop_no, COUNT(*)
  FROM t_invoice WHERE shop_no IS NOT NULL
 GROUP BY 1,2,3 HAVING COUNT(*) > 1;

-- m_partner_group_member: (partner_group_id, partner_code) 重複
SELECT partner_group_id, partner_code, COUNT(*)
  FROM m_partner_group_member
 GROUP BY 1,2 HAVING COUNT(*) > 1;
```

重複行があれば **手動 dedup** を行ってから V031 を適用すること。
m_partner_group_member の重複は V031 内 DO ブロック内 RAISE EXCEPTION で fail-fast する。

### 3. closing_date フォーマット違反の確認 (0 件であること)

```sql
SELECT invoice_id, closing_date FROM t_invoice
 WHERE closing_date !~ '^\d{4}/\d{2}/(末|\d{2})$';
```

違反行があれば手動補正 (例: `2025/11/30` → `2025/11/末` or `2025/11/30` 維持) を行ってから適用すること。

### 4. shop_no IS NULL 行の確認

V031 内の以下 SQL で第 1 事業部 (shop_no=1) に強制補正される:

```sql
UPDATE t_invoice SET shop_no = 1 WHERE shop_no IS NULL;
```

旧 stock-app 由来で shop_no=NULL 行が大量にある場合は、**事前に業務部門と shop_no 配布ルールを確認** すること。
(本来 shop_no=2 (松山) として扱われるべき行が誤って shop_no=1 になる可能性がある)

### 5. NULL 同士の重複 dedup 手順 (M-N1: 必須事前チェック)

V031 は NULL→1 補正前に下記の DO ブロックで `(partner_code, closing_date)` 重複を **fail-fast 検出** する。
重複が 1 件でもあれば `RAISE EXCEPTION` で migration が中断され、起動不能になる。

#### 事前確認 SQL (prod 適用前に必ず実行)

```sql
-- shop_no IS NULL 行に (partner_code, closing_date) 重複があるかを確認 (0 件であること)
SELECT partner_code, closing_date, COUNT(*) AS cnt
  FROM t_invoice
 WHERE shop_no IS NULL
 GROUP BY partner_code, closing_date
HAVING COUNT(*) > 1
 ORDER BY cnt DESC, partner_code, closing_date;
```

#### 重複が見つかった場合の対処手順

1. **重複行を内容ベースで詳細確認** (どちらを残すか業務判断)
   ```sql
   SELECT invoice_id, partner_code, partner_name, closing_date, shop_no,
          current_billing_amount, payment_date
     FROM t_invoice
    WHERE shop_no IS NULL
      AND (partner_code, closing_date) IN (
          SELECT partner_code, closing_date
            FROM t_invoice
           WHERE shop_no IS NULL
           GROUP BY partner_code, closing_date
          HAVING COUNT(*) > 1
      )
    ORDER BY partner_code, closing_date, invoice_id;
   ```

2. **片方を物理削除** (旧 stock-app 時代は `del_flg` カラム未導入のため論理削除不可、物理削除のみ可)
   - 推奨: `invoice_id` の小さい方 (= 古い insert 順) を残す / 大きい方を削除
   - もしくは業務部門が指定する基準で削除
   ```sql
   BEGIN;
   -- 例: 同一 (partner_code, closing_date) のうち最大 invoice_id を削除
   DELETE FROM t_invoice
    WHERE invoice_id IN (
        SELECT MAX(invoice_id)
          FROM t_invoice
         WHERE shop_no IS NULL
         GROUP BY partner_code, closing_date
        HAVING COUNT(*) > 1
    );
   -- 削除件数が想定通りか確認した上で COMMIT
   -- COMMIT;  -- または ROLLBACK;
   ```

3. **再度 step 1 の SELECT を実行して 0 件になったことを確認**

4. **shop_no を業務ルールに従って事前補正** (V031 の自動 NULL→1 補正に依存しない場合)
   ```sql
   -- 例: 松山支社の partner_code リストを事前に shop_no=2 に補正
   UPDATE t_invoice SET shop_no = 2
    WHERE shop_no IS NULL
      AND partner_code IN ('XXXX', 'YYYY', ...);
   ```

---

## 適用手順

### 案 A: baseline 引き上げなし (推奨)

V031 は冪等のため、`flyway.baseline-version` を維持したまま適用しても問題ない。

```bash
cd backend
./gradlew flywayMigrate -Pprofile=prod
```

### 案 B: baseline 引き上げあり

`backend/src/main/resources/config/application-prod.yml` を編集:

```yaml
spring:
  flyway:
    baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:true}
    baseline-version: ${FLYWAY_BASELINE_VERSION:30}  # ← 18 から 30 に引き上げ
```

または環境変数で:

```bash
export FLYWAY_BASELINE_VERSION=30
./gradlew flywayMigrate -Pprofile=prod
```

baseline 引き上げ案を選ぶ場合、V019〜V030 の適用がスキップされるため、
`flyway_schema_history` テーブルに既に V019〜V030 が記録されていることを必ず事前確認:

```sql
SELECT version, description, success FROM flyway_schema_history
 WHERE version::numeric BETWEEN 19 AND 31
 ORDER BY installed_rank;
```

---

## 適用後検証

### 1. 制約が追加されたか確認

```sql
\d t_invoice                    -- uk_t_invoice_partner_closing_shop / chk_t_invoice_closing_date_format が見えること
\d m_partner_group              -- del_flg カラムが見えること
\d m_partner_group_member       -- uk_m_partner_group_member / fk_m_partner_group_member_group_id が見えること
```

### 2. flyway_schema_history に V031 が記録されたか確認

```sql
SELECT version, description, installed_on, success FROM flyway_schema_history
 WHERE version = '031';
```

### 3. JVM 再起動後、Hibernate validate が通ること

`application-prod.yml` の `spring.jpa.hibernate.ddl-auto: validate` のもと、
backend 起動時に Entity マッピングと DB schema の整合性が検証される。
不整合があれば起動時に例外。

```bash
./gradlew bootRun --args='--spring.profiles.active=web,prod'
```

---

## ロールバック

V031 で追加した制約のみを drop する場合:

```sql
ALTER TABLE t_invoice DROP CONSTRAINT IF EXISTS uk_t_invoice_partner_closing_shop;
ALTER TABLE t_invoice DROP CONSTRAINT IF EXISTS chk_t_invoice_closing_date_format;
ALTER TABLE t_invoice ALTER COLUMN shop_no DROP NOT NULL;

ALTER TABLE m_partner_group_member DROP CONSTRAINT IF EXISTS uk_m_partner_group_member;
ALTER TABLE m_partner_group_member DROP CONSTRAINT IF EXISTS fk_m_partner_group_member_group_id;
DROP INDEX IF EXISTS idx_m_partner_group_member_group_id;

ALTER TABLE m_partner_group DROP COLUMN IF EXISTS del_flg;

DELETE FROM flyway_schema_history WHERE version = '031';
```

(V031 自体は CREATE TABLE IF NOT EXISTS のため、テーブルそのものを drop する手順は記載しない)

---

## 関連ドキュメント

- `claudedocs/triage-invoice-management.md` SF-01 / SF-04 / SF-20
- `backend/src/main/resources/db/migration/V031__create_t_invoice_and_m_partner_group.sql`
- `backend/src/main/resources/config/application-prod.yml` (`spring.flyway.baseline-version`)
