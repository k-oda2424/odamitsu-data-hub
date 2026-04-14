-- バッチ「得意先見積自動生成」で作成された見積の再生成ガード判定用
ALTER TABLE t_estimate
  ADD COLUMN IF NOT EXISTS auto_generated BOOLEAN DEFAULT FALSE;
