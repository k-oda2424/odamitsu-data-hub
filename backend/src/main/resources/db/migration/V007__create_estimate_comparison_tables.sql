-- 比較見積ヘッダ
CREATE TABLE IF NOT EXISTS t_estimate_comparison (
    comparison_no SERIAL PRIMARY KEY,
    shop_no INTEGER NOT NULL,
    partner_no INTEGER,
    destination_no INTEGER,
    comparison_date DATE NOT NULL,
    comparison_status VARCHAR(2) NOT NULL DEFAULT '00',
    source_estimate_no INTEGER,
    title VARCHAR(200),
    note TEXT,
    company_no INTEGER NOT NULL,
    del_flg VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no INTEGER
);

-- 基準品グループ
CREATE TABLE IF NOT EXISTS t_comparison_group (
    comparison_no INTEGER NOT NULL REFERENCES t_estimate_comparison(comparison_no),
    group_no INTEGER NOT NULL,
    base_goods_no INTEGER,
    base_goods_code VARCHAR(50),
    base_goods_name VARCHAR(200) NOT NULL,
    base_specification VARCHAR(200),
    base_purchase_price DECIMAL(12,2),
    base_goods_price DECIMAL(12,2),
    base_contain_num DECIMAL(12,2),
    display_order INTEGER NOT NULL,
    group_note TEXT,
    shop_no INTEGER NOT NULL,
    company_no INTEGER NOT NULL,
    del_flg VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no INTEGER,
    PRIMARY KEY (comparison_no, group_no)
);

-- 代替提案明細
CREATE TABLE IF NOT EXISTS t_comparison_detail (
    comparison_no INTEGER NOT NULL,
    group_no INTEGER NOT NULL,
    detail_no INTEGER NOT NULL,
    goods_no INTEGER,
    goods_code VARCHAR(50),
    goods_name VARCHAR(200) NOT NULL,
    specification VARCHAR(200),
    purchase_price DECIMAL(12,2),
    proposed_price DECIMAL(12,2),
    contain_num DECIMAL(12,2),
    profit_rate DECIMAL(5,2),
    detail_note TEXT,
    display_order INTEGER NOT NULL,
    supplier_no INTEGER,
    shop_no INTEGER NOT NULL,
    company_no INTEGER NOT NULL,
    del_flg VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no INTEGER,
    PRIMARY KEY (comparison_no, group_no, detail_no),
    FOREIGN KEY (comparison_no, group_no) REFERENCES t_comparison_group(comparison_no, group_no)
);

CREATE INDEX IF NOT EXISTS idx_comparison_detail_comparison_group ON t_comparison_detail(comparison_no, group_no);
CREATE INDEX IF NOT EXISTS idx_estimate_comparison_shop ON t_estimate_comparison(shop_no);
CREATE INDEX IF NOT EXISTS idx_estimate_comparison_status ON t_estimate_comparison(comparison_status);
