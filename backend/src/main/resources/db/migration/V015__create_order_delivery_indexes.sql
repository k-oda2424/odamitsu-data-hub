-- 注文・出荷・返品系テーブルの補助インデックス追加
-- 背景: PostgreSQLはFKカラムを自動インデックスしない。Entityの@JoinColumnはFK制約/インデックスを生成しないため、
--      Repositoryの既存クエリ (getBy*, findBy*) を元に必要なインデックスを明示追加する。
-- 注意: CREATE INDEX CONCURRENTLY はトランザクション内で実行できないため、psqlで1文ずつ流すこと。
--      全て IF NOT EXISTS 付きなので、既に存在する場合はスキップされる（再実行安全）。

-- ============================================================
-- t_order
-- ============================================================
-- TOrderRepository#getByShopNoAndProcessingSerialNumber (SMILE受注連携)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_order_shop_no_processing_serial
  ON t_order (shop_no, processing_serial_number)
  WHERE processing_serial_number IS NOT NULL;

-- B-Cart受注との紐付け (nullが大半のため部分インデックス)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_order_b_cart_order_id
  ON t_order (b_cart_order_id)
  WHERE b_cart_order_id IS NOT NULL;

-- ============================================================
-- t_order_detail
-- ============================================================
-- TOrderDetailRepository#findByDeliveryNo / findByDeliveryNos (出荷→注文明細の逆引き)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_order_detail_delivery_no
  ON t_order_detail (delivery_no)
  WHERE delivery_no IS NOT NULL;

-- ============================================================
-- t_delivery
-- ============================================================
-- TDeliveryRepository#getByShopNoAndProcessingSerialNumber (SMILE出荷連携)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_delivery_shop_no_processing_serial
  ON t_delivery (shop_no, processing_serial_number)
  WHERE processing_serial_number IS NOT NULL;

-- TDeliveryRepository#getByShopNoAndPartnerCodeAndSlipNo
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_delivery_shop_no_partner_code_slip_no
  ON t_delivery (shop_no, partner_code, slip_no);

-- ============================================================
-- t_delivery_detail
-- ============================================================
-- 注文明細⇔出荷明細の複合FK (TDeliveryDetail.tOrderDetail の @JoinColumns)
-- TDeliveryDetailRepository#findByDeliveryNoAndOrderDetailNo にも効く
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_delivery_detail_order_no_order_detail_no
  ON t_delivery_detail (order_no, order_detail_no)
  WHERE order_no IS NOT NULL;

-- TDeliveryDetailRepository#getByShopNoAndSlipNoAndDeliveryDetailNo
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_delivery_detail_shop_no_slip_no
  ON t_delivery_detail (shop_no, slip_no);

-- ============================================================
-- t_return
-- ============================================================
-- 注文→返品の逆引き
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_return_order_no
  ON t_return (order_no);

-- TReturnRepository#getByShopNoAndReturnSlipNo
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_return_shop_no_return_slip_no
  ON t_return (shop_no, return_slip_no);

-- ============================================================
-- t_return_detail
-- ============================================================
-- TReturnDetailRepository#findByOrderNo
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_return_detail_order_no
  ON t_return_detail (order_no)
  WHERE order_no IS NOT NULL;

-- TReturnDetailRepository#findByDeliveryNoAndDeliveryDetailNo
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_return_detail_delivery_no_delivery_detail_no
  ON t_return_detail (delivery_no, delivery_detail_no)
  WHERE delivery_no IS NOT NULL;

-- ============================================================
-- b_cart_order_product
-- ============================================================
-- BCartOrderProductRepository#findByLogisticsIdIn (出荷→商品の逆引き、最重要)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_b_cart_order_product_logistics_id
  ON b_cart_order_product (logistics_id);

-- PKは(id, order_id)のため order_id 単独クエリでは使えない。@ManyToOne経由のJOINも補助
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_b_cart_order_product_order_id
  ON b_cart_order_product (order_id);

-- ============================================================
-- 確認用クエリ (任意)
-- ============================================================
-- 作成されたインデックスの一覧:
-- SELECT schemaname, tablename, indexname, indexdef
--   FROM pg_indexes
--  WHERE indexname LIKE 'idx_t_order%'
--     OR indexname LIKE 'idx_t_delivery%'
--     OR indexname LIKE 'idx_t_return%'
--     OR indexname LIKE 'idx_b_cart_order_product%'
--  ORDER BY tablename, indexname;
