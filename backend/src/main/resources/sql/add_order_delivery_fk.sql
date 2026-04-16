-- 注文・出荷・返品系テーブルの外部キー制約追加
-- コード変更不要。ただし孤児レコード存在時はADDに失敗する → 事前に下記「孤児チェック」で0件を確認すること。
-- B-Cart系 (b_cart_*) は import タイミングで FK 違反を起こすリスクがあるため除外。

-- ============================================================
-- STEP 1: 孤児チェック（すべて 0 件であることを確認してから STEP 2 へ）
-- ============================================================
-- 親が存在しない t_order_detail
-- SELECT COUNT(*) FROM t_order_detail od
--   LEFT JOIN t_order o ON o.order_no = od.order_no
--  WHERE o.order_no IS NULL;
--
-- 親が存在しない t_delivery_detail
-- SELECT COUNT(*) FROM t_delivery_detail dd
--   LEFT JOIN t_delivery d ON d.delivery_no = dd.delivery_no
--  WHERE d.delivery_no IS NULL;
--
-- t_delivery_detail.(order_no, order_detail_no) の孤児（NULLは除外）
-- SELECT COUNT(*) FROM t_delivery_detail dd
--   LEFT JOIN t_order_detail od
--     ON od.order_no = dd.order_no AND od.order_detail_no = dd.order_detail_no
--  WHERE dd.order_no IS NOT NULL AND od.order_no IS NULL;
--
-- t_return.order_no の孤児
-- SELECT COUNT(*) FROM t_return r
--   LEFT JOIN t_order o ON o.order_no = r.order_no
--  WHERE r.order_no IS NOT NULL AND o.order_no IS NULL;
--
-- t_return_detail.return_no の孤児
-- SELECT COUNT(*) FROM t_return_detail rd
--   LEFT JOIN t_return r ON r.return_no = rd.return_no
--  WHERE r.return_no IS NULL;
--
-- t_return_detail.(order_no, order_detail_no) の孤児
-- SELECT COUNT(*) FROM t_return_detail rd
--   LEFT JOIN t_order_detail od
--     ON od.order_no = rd.order_no AND od.order_detail_no = rd.order_detail_no
--  WHERE rd.order_no IS NOT NULL AND od.order_no IS NULL;
--
-- t_return_detail.(delivery_no, delivery_detail_no) の孤児
-- SELECT COUNT(*) FROM t_return_detail rd
--   LEFT JOIN t_delivery_detail dd
--     ON dd.delivery_no = rd.delivery_no AND dd.delivery_detail_no = rd.delivery_detail_no
--  WHERE rd.delivery_no IS NOT NULL AND dd.delivery_no IS NULL;
--
-- t_order_detail.(delivery_no, delivery_detail_no) の孤児 (逆参照)
-- SELECT COUNT(*) FROM t_order_detail od
--   LEFT JOIN t_delivery_detail dd
--     ON dd.delivery_no = od.delivery_no AND dd.delivery_detail_no = od.delivery_detail_no
--  WHERE od.delivery_no IS NOT NULL AND dd.delivery_no IS NULL;

-- ============================================================
-- STEP 2: FK制約追加
-- 注: 複合FKの参照先カラムはUNIQUE制約が必要（PKなら自動的にUNIQUE）
-- ON DELETE/UPDATE はアプリ論理削除(del_flg)との整合のため RESTRICT (= 物理削除を禁止) を採用
-- ============================================================

BEGIN;

-- ---- t_order_detail → t_order ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_t_order_detail_order') THEN
    ALTER TABLE t_order_detail ADD CONSTRAINT fk_t_order_detail_order
      FOREIGN KEY (order_no) REFERENCES t_order(order_no)
      ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
END $$;

-- ---- t_delivery_detail → t_delivery ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_t_delivery_detail_delivery') THEN
    ALTER TABLE t_delivery_detail ADD CONSTRAINT fk_t_delivery_detail_delivery
      FOREIGN KEY (delivery_no) REFERENCES t_delivery(delivery_no)
      ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
END $$;

-- ---- t_delivery_detail → t_order_detail (複合FK、NULL許可) ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_t_delivery_detail_order_detail') THEN
    ALTER TABLE t_delivery_detail ADD CONSTRAINT fk_t_delivery_detail_order_detail
      FOREIGN KEY (order_no, order_detail_no) REFERENCES t_order_detail(order_no, order_detail_no)
      ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
END $$;

-- ---- t_return_detail → t_return ----
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_t_return_detail_return') THEN
    ALTER TABLE t_return_detail ADD CONSTRAINT fk_t_return_detail_return
      FOREIGN KEY (return_no) REFERENCES t_return(return_no)
      ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
END $$;

-- ============================================================
-- 【保留】歴史データの孤児により追加不可のFK (2026-04-16時点)
-- ============================================================
-- 2013〜2024年の並行運用期間に生じた孤児レコードが存在するため、以下3つは保留。
-- 孤児の整理（論理削除 or 補填）が完了してから追加予定。
--   - t_return → t_order              : 孤児 497件 (t_return.del_flg='0' のみ)
--   - t_return_detail → t_order_detail : 孤児 564件
--   - t_return_detail → t_delivery_detail : 孤児 564件
--
-- -- ---- t_return → t_order ----
-- ALTER TABLE t_return ADD CONSTRAINT fk_t_return_order
--   FOREIGN KEY (order_no) REFERENCES t_order(order_no)
--   ON DELETE RESTRICT ON UPDATE RESTRICT;
--
-- -- ---- t_return_detail → t_order_detail ----
-- ALTER TABLE t_return_detail ADD CONSTRAINT fk_t_return_detail_order_detail
--   FOREIGN KEY (order_no, order_detail_no) REFERENCES t_order_detail(order_no, order_detail_no)
--   ON DELETE RESTRICT ON UPDATE RESTRICT;
--
-- -- ---- t_return_detail → t_delivery_detail ----
-- ALTER TABLE t_return_detail ADD CONSTRAINT fk_t_return_detail_delivery_detail
--   FOREIGN KEY (delivery_no, delivery_detail_no) REFERENCES t_delivery_detail(delivery_no, delivery_detail_no)
--   ON DELETE RESTRICT ON UPDATE RESTRICT;

-- NOTE: t_order_detail.(delivery_no, delivery_detail_no) → t_delivery_detail への FK は
--       挿入順序（出荷→注文明細の更新）に依存して違反する可能性が高いため、追加を保留。
--       DEFERRABLE INITIALLY DEFERRED で追加することも可能だが、運用影響を見てから判断。

COMMIT;

-- 確認:
-- SELECT conname, conrelid::regclass, pg_get_constraintdef(oid)
--   FROM pg_constraint
--  WHERE conname LIKE 'fk_t_order%' OR conname LIKE 'fk_t_delivery%' OR conname LIKE 'fk_t_return%'
--  ORDER BY conname;
