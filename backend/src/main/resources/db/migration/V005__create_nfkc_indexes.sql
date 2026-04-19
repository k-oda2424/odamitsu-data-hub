-- NFKC正規化関数を使った検索用インデックス
-- nfkc() は IMMUTABLE 宣言済みのため関数インデックス作成可能

-- 商品マスタ
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_goods_nfkc_goods_name ON m_goods (nfkc(goods_name));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_goods_nfkc_keyword ON m_goods (nfkc(keyword));

-- 販売商品（ワーク）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_w_sales_goods_nfkc_goods_name ON w_sales_goods (nfkc(goods_name));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_w_sales_goods_nfkc_goods_code ON w_sales_goods (nfkc(goods_code));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_w_sales_goods_nfkc_keyword ON w_sales_goods (nfkc(keyword));

-- 販売商品（マスタ）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_sales_goods_nfkc_goods_name ON m_sales_goods (nfkc(goods_name));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_sales_goods_nfkc_goods_code ON m_sales_goods (nfkc(goods_code));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_sales_goods_nfkc_keyword ON m_sales_goods (nfkc(keyword));

-- 得意先商品
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_partner_goods_nfkc_goods_name ON m_partner_goods (nfkc(goods_name));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_partner_goods_nfkc_goods_code ON m_partner_goods (nfkc(goods_code));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_partner_goods_nfkc_keyword ON m_partner_goods (nfkc(keyword));

-- 注文明細
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_order_detail_nfkc_goods_code ON t_order_detail (nfkc(goods_code));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_order_detail_nfkc_goods_name ON t_order_detail (nfkc(goods_name));

-- 仕入価格変更予定
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_purchase_price_change_plan_nfkc_goods_code ON m_purchase_price_change_plan (nfkc(goods_code));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_purchase_price_change_plan_nfkc_jan_code ON m_purchase_price_change_plan (nfkc(jan_code));

-- 請求
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_invoice_nfkc_partner_name ON t_invoice (nfkc(partner_name));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_invoice_nfkc_partner_code ON t_invoice (nfkc(partner_code));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_invoice_nfkc_closing_date ON t_invoice (nfkc(closing_date));

-- メーカー
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_maker_nfkc_maker_name ON m_maker (nfkc(maker_name));

-- 仕入先
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_supplier_nfkc_supplier_name ON m_supplier (nfkc(supplier_name));

-- 得意先
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_partner_nfkc_partner_name ON m_partner (nfkc(partner_name));

-- 倉庫
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_warehouse_nfkc_warehouse_name ON m_warehouse (nfkc(warehouse_name));

-- 会社
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_m_company_nfkc_company_name ON m_company (nfkc(company_name));

-- 仕入明細
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_t_purchase_detail_nfkc_goods_code ON t_purchase_detail (nfkc(goods_code));
