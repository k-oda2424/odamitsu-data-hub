-- 突合履歴・取消機能のためのカラム追加
ALTER TABLE w_quote_import_detail
  ADD COLUMN IF NOT EXISTS status VARCHAR(10) DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS matched_goods_code VARCHAR(100),
  ADD COLUMN IF NOT EXISTS matched_goods_no INTEGER,
  ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP;
