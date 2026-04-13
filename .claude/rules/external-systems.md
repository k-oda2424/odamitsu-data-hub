# 外部システム連携

## SMILE（基幹システム）
- 支払情報: CSVファイル経由でw_smile_payment→t_smile_paymentへUPSERT
- 受注データ: w_smile_order_output_file経由
- 商品マスタ: goods_import.csv（Unicode TSV）→ GoodsFileImportバッチ

## B-CART（ECシステム）
- REST API経由で商品・注文・在庫データの双方向同期
- 出荷ステータス: 未発送、発送指示、発送済、対象外（EXCLUDED）

## マネーフォワード（会計）
- CSV出力による仕訳データ連携（買掛金・売掛金）
