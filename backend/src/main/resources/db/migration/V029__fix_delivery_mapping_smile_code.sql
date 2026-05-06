-- V029: x_delivery_mapping の smile_delivery_code を b_cart_destination_code に揃える
--
-- 背景:
--   旧 deliveryCodeMapper の step 5 が連番採番 (000003 等) してしまい、
--   B-CART destination_code (運用者手動採番) と SMILE 納品先コードが乖離していた。
--   業務ルール: smile_delivery_code = b_cart_destination_code を 1:1 で同値とする。
--
-- 流れ:
--   1) 衝突行 (UNIQUE 制約に抵触する b_cart_destination_code) が 0 件であることを assert
--      → 1 件でも残ると修正後コードが INSERT 時に DataIntegrityViolation で停止する
--      → migration 適用前に下記 SELECT で 0 件確認 + 手動是正を完了させる必要あり
--   2) 是正対象行を一括 UPDATE (smile_csv_outputted=false で SMILE マスタへ再連携)

-- Step 1: 衝突行が残っていれば migration 失敗 (RAISE EXCEPTION)
DO $$
DECLARE
    conflict_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO conflict_count
      FROM x_delivery_mapping a
     WHERE a.b_cart_destination_code IS NOT NULL
       AND a.b_cart_destination_code <> ''
       AND a.smile_delivery_code <> a.b_cart_destination_code
       AND EXISTS (
           SELECT 1
             FROM x_delivery_mapping b
            WHERE b.smile_delivery_code = a.b_cart_destination_code
              AND b.x_delivery_mapping_id <> a.x_delivery_mapping_id
       );
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'V029: smile_delivery_code 衝突行が % 件残っています。手動是正してから再実行してください。', conflict_count;
    END IF;
END $$;

-- Step 2: 是正対象を一括更新
UPDATE x_delivery_mapping
   SET smile_delivery_code = b_cart_destination_code,
       smile_csv_outputted  = false
 WHERE b_cart_destination_code IS NOT NULL
   AND b_cart_destination_code <> ''
   AND smile_delivery_code <> b_cart_destination_code;

-- 参考: 衝突行確認 SQL (本番適用前に実行して 0 件を確認すること)
-- SELECT a.x_delivery_mapping_id AS need_fix_id,
--        a.b_cart_destination_code,
--        a.smile_delivery_code AS current_smile_code,
--        a.delivery_name        AS need_fix_name,
--        b.x_delivery_mapping_id AS conflict_with_id,
--        b.delivery_name        AS conflict_with_name
--   FROM x_delivery_mapping a
--   JOIN x_delivery_mapping b
--     ON b.smile_delivery_code = a.b_cart_destination_code
--    AND b.x_delivery_mapping_id <> a.x_delivery_mapping_id
--  WHERE a.b_cart_destination_code IS NOT NULL
--    AND a.b_cart_destination_code <> ''
--    AND a.smile_delivery_code <> a.b_cart_destination_code;
