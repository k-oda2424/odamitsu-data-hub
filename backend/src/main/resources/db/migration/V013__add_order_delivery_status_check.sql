-- 注文・出荷・返品系テーブルのステータス列にCHECK制約を追加
-- コード変更不要（既存enum値と整合）。 b_cart_* は API仕様ミラーのため対象外。
--
-- 許容値の根拠:
--   OrderStatus.java        : 00, 10, 20, 90, 99
--   OrderDetailStatus.java  : 00, 01, 10, 20, 90, 99
--   DeliveryStatus.java     : 00, 10, 20, 90, 99
--   DeliveryDetailStatus.java: 00, 10, 20, 90, 99
--   TaxType.java            : 0, 1, 2
--   OrderRoute.java         : web, tel, fax, mail, b_cart, other
--   ReturnStatus            : 定数クラス未定義。setReturnStatus 呼び出しも未検出 → 今回はスキップ（要確認）

-- ============================================================
-- 事前チェック: 許容値以外が入っていないか確認
-- ============================================================
-- ALTER TABLE が失敗する前に、下記 SELECT をすべて 0件 であることを確認すること。
-- NULL は全てのCHECKをパスする（PG三値論理）ため除外指定は不要。
--
-- SELECT order_status, COUNT(*) FROM t_order
--   WHERE order_status IS NOT NULL AND order_status NOT IN ('00','10','20','90','99')
--   GROUP BY order_status;
--
-- SELECT order_route, COUNT(*) FROM t_order
--   WHERE order_route IS NOT NULL AND order_route NOT IN ('web','tel','fax','mail','b_cart','other')
--   GROUP BY order_route;
--
-- SELECT order_detail_status, COUNT(*) FROM t_order_detail
--   WHERE order_detail_status IS NOT NULL AND order_detail_status NOT IN ('00','01','10','20','90','99')
--   GROUP BY order_detail_status;
--
-- SELECT tax_type, COUNT(*) FROM t_order_detail
--   WHERE tax_type IS NOT NULL AND tax_type NOT IN ('0','1','2')
--   GROUP BY tax_type;
--
-- SELECT delivery_status, COUNT(*) FROM t_delivery
--   WHERE delivery_status IS NOT NULL AND delivery_status NOT IN ('00','10','20','90','99')
--   GROUP BY delivery_status;
--
-- SELECT delivery_detail_status, COUNT(*) FROM t_delivery_detail
--   WHERE delivery_detail_status IS NOT NULL AND delivery_detail_status NOT IN ('00','10','20','90','99')
--   GROUP BY delivery_detail_status;
--
-- SELECT tax_type, COUNT(*) FROM t_return_detail
--   WHERE tax_type IS NOT NULL AND tax_type NOT IN ('0','1','2')
--   GROUP BY tax_type;
--
-- SELECT del_flg, COUNT(*) FROM t_order WHERE del_flg IS NOT NULL AND del_flg NOT IN ('0','1') GROUP BY del_flg;
-- （t_order_detail / t_delivery / t_delivery_detail / t_return / t_return_detail / m_delivery_destination も同様に）

-- ============================================================
-- 制約追加
-- 注: ALTER TABLE ... ADD CONSTRAINT は IF NOT EXISTS 非対応のため、
--     DO ブロックで既存チェックしてから追加する。
-- ============================================================

BEGIN;

-- ---- t_order ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_order_order_status') THEN
    ALTER TABLE t_order ADD CONSTRAINT chk_t_order_order_status
      CHECK (order_status IS NULL OR order_status IN ('00','10','20','90','99'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_order_order_route') THEN
    ALTER TABLE t_order ADD CONSTRAINT chk_t_order_order_route
      CHECK (order_route IS NULL OR order_route IN ('web','tel','fax','mail','b_cart','other'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_order_del_flg') THEN
    ALTER TABLE t_order ADD CONSTRAINT chk_t_order_del_flg
      CHECK (del_flg IS NULL OR del_flg IN ('0','1'));
  END IF;
END $$;

-- ---- t_order_detail ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_order_detail_status') THEN
    ALTER TABLE t_order_detail ADD CONSTRAINT chk_t_order_detail_status
      CHECK (order_detail_status IS NULL OR order_detail_status IN ('00','01','10','20','90','99'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_order_detail_tax_type') THEN
    ALTER TABLE t_order_detail ADD CONSTRAINT chk_t_order_detail_tax_type
      CHECK (tax_type IS NULL OR tax_type IN ('0','1','2'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_order_detail_del_flg') THEN
    ALTER TABLE t_order_detail ADD CONSTRAINT chk_t_order_detail_del_flg
      CHECK (del_flg IS NULL OR del_flg IN ('0','1'));
  END IF;
END $$;

-- ---- t_delivery ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_delivery_status') THEN
    ALTER TABLE t_delivery ADD CONSTRAINT chk_t_delivery_status
      CHECK (delivery_status IS NULL OR delivery_status IN ('00','10','20','90','99'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_delivery_del_flg') THEN
    ALTER TABLE t_delivery ADD CONSTRAINT chk_t_delivery_del_flg
      CHECK (del_flg IS NULL OR del_flg IN ('0','1'));
  END IF;
END $$;

-- ---- t_delivery_detail ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_delivery_detail_status') THEN
    ALTER TABLE t_delivery_detail ADD CONSTRAINT chk_t_delivery_detail_status
      CHECK (delivery_detail_status IS NULL OR delivery_detail_status IN ('00','10','20','90','99'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_delivery_detail_del_flg') THEN
    ALTER TABLE t_delivery_detail ADD CONSTRAINT chk_t_delivery_detail_del_flg
      CHECK (del_flg IS NULL OR del_flg IN ('0','1'));
  END IF;
END $$;

-- ---- t_return ----
-- return_status は許容値が未特定のためスキップ（別途調査してから追加）
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_return_del_flg') THEN
    ALTER TABLE t_return ADD CONSTRAINT chk_t_return_del_flg
      CHECK (del_flg IS NULL OR del_flg IN ('0','1'));
  END IF;
END $$;

-- ---- t_return_detail ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_return_detail_tax_type') THEN
    ALTER TABLE t_return_detail ADD CONSTRAINT chk_t_return_detail_tax_type
      CHECK (tax_type IS NULL OR tax_type IN ('0','1','2'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_t_return_detail_del_flg') THEN
    ALTER TABLE t_return_detail ADD CONSTRAINT chk_t_return_detail_del_flg
      CHECK (del_flg IS NULL OR del_flg IN ('0','1'));
  END IF;
END $$;

-- ---- m_delivery_destination ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_m_delivery_destination_del_flg') THEN
    ALTER TABLE m_delivery_destination ADD CONSTRAINT chk_m_delivery_destination_del_flg
      CHECK (del_flg IS NULL OR del_flg IN ('0','1'));
  END IF;
END $$;

COMMIT;

-- 確認:
-- SELECT conname, conrelid::regclass, pg_get_constraintdef(oid)
--   FROM pg_constraint WHERE conname LIKE 'chk_t_%' OR conname LIKE 'chk_m_delivery%' ORDER BY conname;
