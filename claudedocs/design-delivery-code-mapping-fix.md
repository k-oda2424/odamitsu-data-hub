# B-CART → SMILE 納品先コード 1:1 同値化修正 設計書

## 1. 背景・問題

### 1.1 報告事象
- 受注番号 17770157456 (B-CART会員 10043 / 新栄不動産ビジネス㈱) の B-CART 受注情報 CSV 出力バッチで、配送先「18」(胡町ビルディング) に対する納品先コードが「000003」と出力される。
- 業務上は B-CART で運用者が手動採番した destination_code「18」がそのまま SMILE 側にも登録されており、SMILE 受注 CSV にも「18」が出力されるべき。

### 1.2 業務ルール (確認済み)
- B-CART の `destination_code` は登録当初空白。運用者が新規配送先登録時に手動採番する。
- 採番した destination_code を SMILE 納品先マスタにも CSV 連携 (`SmileDestinationFileOutPutTasklet`) で同コードで登録する。
- 受注 CSV (`SmileOrderFileOutPutTasklet`) も同じ納品先コードを参照して整合する。
- 結論: **B-CART `destination_code` = SMILE 納品先コード = `x_delivery_mapping.smile_delivery_code` を 1:1 同値化する**。

### 1.3 バグの正体
`BCartOrderConvertSmileOrderFileTasklet.deliveryCodeMapper` (line 525-593):
- step 1 のフィルタは `smile_delivery_code == destinationCode` (両者一致前提) で仕様上は正しい。
- step 5 で `DeliveryMappingService#allocateSmileDeliveryCodeAndSave` を呼び、`MAX(smile_delivery_code)+1` の連番採番を行ってしまう。
- 結果、`smile_delivery_code` が destination_code とずれる ("18" → "000003") ため、step 1 が永遠にマッチしなくなる。
- step 2 (`delivery_name` フォールバック) は新仕様では不要・誤マッチ要因。

旧 stock-app にも同じバグが入っており、初期コミット以来未修正。

## 2. 修正方針

### 2.1 コード修正
| ファイル | 修正内容 |
|----------|----------|
| `BCartOrderConvertSmileOrderFileTasklet.deliveryCodeMapper` | step 5 の連番採番を削除し、`smile_delivery_code = bCartLogistics.destinationCode` で保存。step 2 の delivery_name フォールバックを撤去。`destination_code` が **null / 空文字 / 空白のみ** のときは `String#isBlank()` で判定し `IllegalStateException` を投げる (`StringUtil.isEmpty` は空白を素通りさせるため不採用)。 |
| `DeliveryMappingService#allocateSmileDeliveryCodeAndSave` | 削除 (他参照なしを Grep で確認済み) |
| `DeliveryMappingService#findMaxSmileDeliveryCodeNumber` | 削除 (allocate からのみ参照) |
| `DeliveryMappingRepository#findMaxSmileDeliveryCodeNumber` | 削除 |
| `SmileDestinationFileOutPutTasklet.exportToCsv` (回帰防止) | `IOException` 握り潰し (line 94 `e.printStackTrace()`) を再スロー化。V029 migration で `smile_csv_outputted=false` リセットされた行が部分書き込み後に CSV 出力ステータスをロストするのを防ぐ。 |

### 2.2 データ移行 (V029 migration)
過去に連番採番されてしまった既存行を是正する。**衝突行が残った状態で本番デプロイすると修正後コードが UNIQUE 制約違反でバッチ停止する** ため、migration は **衝突行ゼロを assert して失敗させる二段構え** にする。

```sql
-- Step 1: 衝突行ゼロを assert (1件でも残っていれば migration 失敗 → 手動是正してから再実行)
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

-- Step 2: 是正対象行を一括更新
UPDATE x_delivery_mapping
   SET smile_delivery_code = b_cart_destination_code,
       smile_csv_outputted  = false
 WHERE b_cart_destination_code IS NOT NULL
   AND b_cart_destination_code <> ''
   AND smile_delivery_code <> b_cart_destination_code;
```

- `b_cart_destination_code` が NULL/空の行は対象外 (旧データでマスタ未整備)。
- 衝突行が 1 件でも残っていれば migration 失敗。dev/prod 適用前に下記確認 SQL で 0 件を確認してから実行する:

```sql
-- 衝突行確認 (本番適用前に実行)
SELECT a.x_delivery_mapping_id AS need_fix_id, a.b_cart_destination_code,
       a.smile_delivery_code AS current_smile_code,
       b.x_delivery_mapping_id AS conflict_with_id, b.delivery_name AS conflict_name
  FROM x_delivery_mapping a
  JOIN x_delivery_mapping b
    ON b.smile_delivery_code = a.b_cart_destination_code
   AND b.x_delivery_mapping_id <> a.x_delivery_mapping_id
 WHERE a.b_cart_destination_code IS NOT NULL
   AND a.b_cart_destination_code <> ''
   AND a.smile_delivery_code <> a.b_cart_destination_code;
```

- 是正対象行は `smile_csv_outputted=false` に戻し、次回 `SmileDestinationFileOutPutTasklet` で SMILE マスタへ再連携する。

### 2.3 影響範囲
- 影響対象バッチ: `bCartOrderConvertSmileOrderFileTasklet`, `smileDestinationFileOutPutTasklet` (経由で SMILE マスタ CSV)
- 影響対象テーブル: `x_delivery_mapping`
- 既出力済み伝票 (`t_smile_order_import_file.csv_exported=true`) は **触らない**。SMILE 側の旧連番納品先コードは運用者が SMILE 上で手動削除する。
- フロントエンドへの影響なし。

### 2.4 リスクと対策
| リスク | 対策 |
|--------|------|
| migration 実行後、step 1 で見つからずに step 5 で重複作成される | migration が UNIQUE 制約衝突回避済み + コード側でも step 1 (smile_delivery_code) ルックアップ → 既存マッピングを再利用 |
| `destination_code` 未設定の出荷データが流れてエラー停止 | 業務ルール上は必須。エラーメッセージで運用者に B-CART 側の登録漏れを通知 |
| migration 除外対象 (UNIQUE 衝突) の手動是正 | 衝突発生時は migration ログに残らないため、別途確認 SQL を運用手順に追加 (運用ドキュメント側で別途対応) |

## 3. テスト計画概要 (詳細は後述)

### 3.1 単体検証
- `./gradlew compileJava` (型/構文)
- `./gradlew test` (既存ユニットテスト全 PASS 維持)

### 3.2 ロジック検証 (手動 or 単体テスト追加)
- `destinationCode="18"` の新規 → `smile_delivery_code="18"` で保存される
- `destinationCode="18"` の既存マッピング (smile_delivery_code="18") + データ更新 → 業務フィールド差分時のみ save 発生
- `destinationCode=null` → `IllegalStateException`
- `destinationCode=""` (空文字) → `IllegalStateException` (StringUtil.isEmpty 判定)
- `destinationCode="   "` (空白のみ) → `IllegalStateException`
- 既存マッピングと新規マッピングの混在 → step 1 で正しく切り分け
- migration 是正済みデータ (旧 smile_delivery_code="000003" → "18" 変換後) → step 1 でヒットし新規行を作らない

### 3.3 migration 検証
- 開発DB (oda32-postgres17) で適用前後の `x_delivery_mapping` カウント確認
- B-CART 会員 10043 の destination_code="18" 行が `smile_delivery_code="18"` に修正されること
- 衝突行 (もしあれば) が migration ログから除外されていること

### 3.4 E2E 影響
- 既存 `e2e/bcart-shipping-input.spec.ts` 等の Playwright テストに影響なし (バッチ修正のため)。

## 4. ロールアウト
1. branch `refactor/code-review-fixes` (現行) で修正コミット
2. `./gradlew compileJava` で疎通確認
3. dev 環境で migration 適用 → サンプル B-CART 配送データで `bCartOrderConvertSmileOrderFileJob` 実行 → 出力 CSV を目視確認
4. SMILE 側に再連携用 CSV (`SmileDestinationFileOutPutTasklet`) を流して納品先マスタ更新
5. 本番展開時は SMILE 側の旧連番マスタクリーニングを運用者と段取り
