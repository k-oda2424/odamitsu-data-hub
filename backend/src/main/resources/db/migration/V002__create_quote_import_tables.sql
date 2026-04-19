-- AI見積取込ヘッダー
CREATE TABLE IF NOT EXISTS t_quote_import_header (
    quote_import_id SERIAL PRIMARY KEY,
    shop_no INTEGER NOT NULL,
    supplier_name VARCHAR(200),
    supplier_code VARCHAR(50),
    supplier_no INTEGER,
    file_name VARCHAR(500),
    quote_date DATE,
    effective_date DATE,
    change_reason VARCHAR(10),
    price_type VARCHAR(20),
    total_count INTEGER NOT NULL DEFAULT 0,
    del_flg VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no INTEGER
);

-- AI見積取込ワーク明細（未処理のみ。処理完了でDELETE）
CREATE TABLE IF NOT EXISTS w_quote_import_detail (
    quote_import_detail_id SERIAL PRIMARY KEY,
    quote_import_id INTEGER NOT NULL REFERENCES t_quote_import_header(quote_import_id),
    row_no INTEGER,
    jan_code VARCHAR(20),
    quote_goods_name VARCHAR(500) NOT NULL,
    quote_goods_code VARCHAR(100),
    specification VARCHAR(200),
    quantity_per_case INTEGER,
    old_price DECIMAL(12,2),
    new_price DECIMAL(12,2),
    old_box_price DECIMAL(12,2),
    new_box_price DECIMAL(12,2),
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_w_quote_import_detail_header ON w_quote_import_detail(quote_import_id);
