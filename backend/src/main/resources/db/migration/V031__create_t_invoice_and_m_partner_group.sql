-- V031: t_invoice / m_partner_group / m_partner_group_member の DDL を Flyway 管理下に正規化
--
-- 背景:
--   旧 stock-app 時代に手動 SQL で生成された t_invoice と、Hibernate ddl-auto により
--   暗黙生成されていた m_partner_group / m_partner_group_member (`@ElementCollection`) が
--   Flyway 管理外にあり、新規環境構築時に DDL が再現できない状態だった。
--   triage-invoice-management.md SF-01 に基づき、欠落 DDL を Flyway で正規化する。
--
-- 主な追加制約:
--   - t_invoice: shop_no NOT NULL + UNIQUE(partner_code, closing_date, shop_no)
--                 + CHECK (closing_date format) (設計 M-7 短期対応)
--   - m_partner_group: del_flg を追加 (SF-20 論理削除化の前提)
--   - m_partner_group_member: UNIQUE(partner_group_id, partner_code)
--                 (SF-04 Service 側 dedup と一致させる)
--
-- ★ prod baseline 引き上げ運用手順 (重要):
--   既存 prod 環境では V001〜V030 まで適用済 + 上記テーブルが手動 DDL/Hibernate 由来で
--   既に存在するため、V031 を素直に流すと「テーブルが既に存在」エラーになる可能性がある。
--   そこで全 DDL は CREATE TABLE IF NOT EXISTS / ALTER ... IF NOT EXISTS / ADD CONSTRAINT
--   IF NOT EXISTS で冪等化済 (PostgreSQL 9.6+/17 共に対応)。
--   既存 prod に対しては以下の手順で適用:
--     1) 適用前に下記 SELECT で現状スキーマを確認:
--          \d t_invoice
--          \d m_partner_group
--          \d m_partner_group_member
--     2) UNIQUE 制約衝突がないことを確認 (DO ブロック内 RAISE EXCEPTION で fail-fast):
--          - SELECT partner_code, closing_date, shop_no, COUNT(*)
--              FROM t_invoice WHERE shop_no IS NOT NULL
--             GROUP BY 1,2,3 HAVING COUNT(*) > 1;  -- 0 件であること
--          - SELECT partner_group_id, partner_code, COUNT(*)
--              FROM m_partner_group_member
--             GROUP BY 1,2 HAVING COUNT(*) > 1;     -- 0 件であること
--     3) baseline 引き上げが必要な場合のみ、application-prod.yml の
--          flyway.baseline-version を 18 → 30 (V031 直前) に更新
--        (V031 は冪等なので、baseline 引き上げなしで素通しでも問題なく動作する)
--     4) `./gradlew flywayMigrate -Pprofile=prod` で適用
--     5) ログに ALTER TABLE / ADD CONSTRAINT が出ていれば成功

-- ============================================================
-- Step 1: t_invoice (請求実績テーブル)
-- ============================================================
CREATE TABLE IF NOT EXISTS t_invoice (
    invoice_id              SERIAL          PRIMARY KEY,
    partner_code            VARCHAR(20)     NOT NULL,
    partner_name            VARCHAR(255)    NOT NULL,
    closing_date            VARCHAR(10)     NOT NULL,
    previous_balance        NUMERIC(15, 0),
    total_payment           NUMERIC(15, 0),
    carry_over_balance      NUMERIC(15, 0),
    net_sales               NUMERIC(15, 0),
    tax_price               NUMERIC(15, 0),
    net_sales_including_tax NUMERIC(15, 0),
    current_billing_amount  NUMERIC(15, 0),
    shop_no                 INTEGER,
    payment_date            DATE
);

-- shop_no を NOT NULL 化 (既存データ補正後 — 必要に応じて prod では UPDATE で 1 埋め後実行)
-- 既存環境で NULL データが残っている可能性があるため、まず NULL を 1 (第1事業部) に補正
--
-- ★ M-N1 (round 2 fix): NULL→1 補正前に (partner_code, closing_date) 重複を fail-fast チェック
-- shop_no IS NULL 行同士の (partner_code, closing_date) 重複があると、
-- UPDATE 後に shop_no=1 で衝突して下流の UNIQUE 制約追加が失敗する。
-- 起動不能を避けるため、ここで RAISE EXCEPTION して runbook の事前 dedup を促す。
DO $$
DECLARE
    duplicate_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT partner_code, closing_date, COUNT(*) AS cnt
          FROM t_invoice
         WHERE shop_no IS NULL
         GROUP BY partner_code, closing_date
        HAVING COUNT(*) > 1
    ) dup;
    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'V031: shop_no IS NULL 行に (partner_code, closing_date) の重複が % 件あります。NULL→1 補正後に UNIQUE 制約衝突が発生するため migration を中止します。runbook-v031-baseline.md の "NULL 同士の重複 dedup 手順" に従い、手動 dedup (旧行に del_flg / 物理削除) 後に再実行してください。',
                        duplicate_count;
    END IF;
END $$;

UPDATE t_invoice SET shop_no = 1 WHERE shop_no IS NULL;
ALTER TABLE t_invoice ALTER COLUMN shop_no SET NOT NULL;

-- UNIQUE(partner_code, closing_date, shop_no) — 重複検出 + Excel UPSERT キー
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'uk_t_invoice_partner_closing_shop'
    ) THEN
        ALTER TABLE t_invoice
            ADD CONSTRAINT uk_t_invoice_partner_closing_shop
            UNIQUE (partner_code, closing_date, shop_no);
    END IF;
END $$;

-- CHECK (closing_date が "YYYY/MM/末" or "YYYY/MM/DD" 形式) — DD-13 短期対応
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'chk_t_invoice_closing_date_format'
    ) THEN
        ALTER TABLE t_invoice
            ADD CONSTRAINT chk_t_invoice_closing_date_format
            CHECK (closing_date ~ '^\d{4}/\d{2}/(末|\d{2})$');
    END IF;
END $$;

COMMENT ON TABLE t_invoice IS 'SMILE請求実績 (Excel取込先)';
COMMENT ON COLUMN t_invoice.closing_date IS '締め日 ("YYYY/MM/末" or "YYYY/MM/DD" 形式、DD-13 中期で LocalDate 化予定)';
COMMENT ON COLUMN t_invoice.payment_date IS '入金日 (NULL = 未入金 or クリア済)';

-- ============================================================
-- Step 2: m_partner_group (パートナーグループ)
-- ============================================================
CREATE TABLE IF NOT EXISTS m_partner_group (
    partner_group_id        SERIAL          PRIMARY KEY,
    group_name              VARCHAR(255)    NOT NULL,
    shop_no                 INTEGER         NOT NULL,
    del_flg                 CHAR(1)         NOT NULL DEFAULT '0'
);

-- 既存環境で del_flg カラムが無ければ追加 (SF-20 論理削除化の前提)
ALTER TABLE m_partner_group ADD COLUMN IF NOT EXISTS del_flg CHAR(1) NOT NULL DEFAULT '0';

COMMENT ON TABLE m_partner_group IS 'パートナーグループ (得意先一括処理用)';
COMMENT ON COLUMN m_partner_group.del_flg IS '論理削除フラグ (0=有効, 1=削除)';

-- ============================================================
-- Step 3: m_partner_group_member (グループ所属パートナー)
-- ============================================================
CREATE TABLE IF NOT EXISTS m_partner_group_member (
    partner_group_id        INTEGER         NOT NULL,
    partner_code            VARCHAR(20)     NOT NULL
);

-- FK to m_partner_group
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_m_partner_group_member_group_id'
    ) THEN
        ALTER TABLE m_partner_group_member
            ADD CONSTRAINT fk_m_partner_group_member_group_id
            FOREIGN KEY (partner_group_id)
            REFERENCES m_partner_group(partner_group_id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- UNIQUE(partner_group_id, partner_code) — SF-04 Service 側 dedup と一致
DO $$
DECLARE
    dup_count INTEGER;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'uk_m_partner_group_member'
    ) THEN
        -- 既存重複を確認 (1 件でもあれば fail-fast)
        SELECT COUNT(*) INTO dup_count FROM (
            SELECT partner_group_id, partner_code, COUNT(*)
              FROM m_partner_group_member
             GROUP BY partner_group_id, partner_code
            HAVING COUNT(*) > 1
        ) t;
        IF dup_count > 0 THEN
            RAISE EXCEPTION 'V031: m_partner_group_member に既存重複が % 件あります。手動 dedup 後に再実行してください。', dup_count;
        END IF;
        ALTER TABLE m_partner_group_member
            ADD CONSTRAINT uk_m_partner_group_member
            UNIQUE (partner_group_id, partner_code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_m_partner_group_member_group_id
    ON m_partner_group_member(partner_group_id);

COMMENT ON TABLE m_partner_group_member IS 'パートナーグループ所属メンバー (m_partner_group の子)';
