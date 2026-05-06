# テスト計画: B-CART → SMILE 納品先コード 1:1 同値化修正

**対象ブランチ**: `refactor/code-review-fixes`
**設計書**: `claudedocs/design-delivery-code-mapping-fix.md`
**作成日**: 2026-04-27

## 0. 修正対象ファイル一覧

| # | パス | 修正内容 |
|---|------|---------|
| 1 | `backend/src/main/java/jp/co/oda32/batch/bcart/BCartOrderConvertSmileOrderFileTasklet.java` | `deliveryCodeMapper` 書き換え（連番採番削除、destinationCode 同値化、空白チェック追加、step2 delivery_name フォールバック削除） |
| 2 | `backend/src/main/java/jp/co/oda32/domain/service/bcart/DeliveryMappingService.java` | `allocateSmileDeliveryCodeAndSave` / `findMaxSmileDeliveryCodeNumber` メソッド削除 |
| 3 | `backend/src/main/java/jp/co/oda32/domain/repository/bcart/DeliveryMappingRepository.java` | `findMaxSmileDeliveryCodeNumber` クエリ削除 |
| 4 | `backend/src/main/java/jp/co/oda32/batch/bcart/SmileDestinationFileOutPutTasklet.java` | `IOException` 握り潰しを再スロー化 |
| 5 | `backend/src/main/resources/db/migration/V029__fix_delivery_mapping_smile_code.sql` | 衝突 assert + 一括是正 UPDATE（新規） |

---

## 1. ビルド・コンパイル検証

| ID | 観点 | 手順 | 期待結果 | 優先度 |
|----|------|------|----------|--------|
| TC-BUILD-001 | 型/構文整合 | `cd backend && ./gradlew compileJava` | BUILD SUCCESSFUL。`allocateSmileDeliveryCodeAndSave` / `findMaxSmileDeliveryCodeNumber` への参照が他ファイルから消えていることを Grep で再確認 | Critical |
| TC-BUILD-002 | 既存ユニットテスト維持 | `cd backend && ./gradlew test` | 全 PASS。既存テストの retrogression なし | Critical |
| TC-BUILD-003 | 削除メソッド残参照確認 | `Grep "allocateSmileDeliveryCodeAndSave\|findMaxSmileDeliveryCodeNumber"` を repo 全域に対して実行 | hit はテスト計画書/設計書のみ。production code に残っていない | High |
| TC-BUILD-004 | フロントエンド型整合 | `cd frontend && npx tsc --noEmit` | Error 0。バックエンド削除の影響なし（API 追加なし） | Medium |

---

## 2. 単体テスト (JUnit 新規追加)

新規テストクラス `BCartOrderConvertSmileOrderFileTaskletDeliveryCodeMapperTest`（または既存と同パッケージ配下） に以下を追加する。`deliveryCodeMapper` は private のため、`ReflectionTestUtils` で呼び出すか、メソッドを package-private に降格して直接呼び出す。
`DeliveryMappingService` は `@MockBean`。

| ID | 観点 | 入力 | 期待結果 | 優先度 |
|----|------|------|----------|--------|
| TC-UT-001 | step1 ヒット（既存マッピング再利用） | `bCartLogistics.destinationCode="18"`、既存 `DeliveryMapping(smileDeliveryCode="18", deliveryName="胡町ビルディング", zip/住所等同値)` | 戻り値 `"18"`、`save()` が**呼ばれない** | Critical |
| TC-UT-002 | step1 ヒット + 業務データ差分 | `bCartLogistics.destinationCode="18"`、既存 mapping は smileDeliveryCode="18" だが zip/address が変更されている | 戻り値 `"18"`、`save()` が呼ばれ、`smileDeliveryCode` は `"18"` のまま、`smileCsvOutputted=false` | Critical |
| TC-UT-003 | 新規登録（destinationCode をそのまま採用） | `bCartLogistics.destinationCode="42"`、既存 mapping なし | 戻り値 `"42"`、`save()` 呼ばれ `smileDeliveryCode="42"` で保存 | Critical |
| TC-UT-004 | destinationCode = null | `bCartLogistics.destinationCode=null` | `IllegalStateException` がスローされる、メッセージに "destinationCode" / "B-CART 配送先" を示す文言を含む | Critical |
| TC-UT-005 | destinationCode = 空文字 | `bCartLogistics.destinationCode=""` | `IllegalStateException`（`String#isBlank()` 判定経由） | Critical |
| TC-UT-006 | destinationCode = 空白のみ | `bCartLogistics.destinationCode="   "` | `IllegalStateException`（`String#isBlank()` で空白も検出） | Critical |
| TC-UT-007 | step2 (delivery_name フォールバック) が呼ばれない | 既存 mapping は同 deliveryName だが smileDeliveryCode が異なる ("000003")、`destinationCode="18"` | 戻り値 `"18"`、新規 mapping が `smileDeliveryCode="18"` で保存される（旧 mapping は再利用しない） | High |
| TC-UT-008 | 偶然一致パターン（旧 "000001"） | 旧 `smileDeliveryCode="000001"` の mapping が同顧客に存在、`destinationCode="000001"` | step1 ヒット、戻り値 `"000001"`、save 呼ばれない | Medium |
| TC-UT-009 | migration 是正済みデータでの step1 ヒット | 旧 `(smileDeliveryCode="000003", bCartDestinationCode="18")` を migration で `smileDeliveryCode="18"` に修正済の状態を再現、`destinationCode="18"` | 戻り値 `"18"`、save 呼ばれない（業務データ同値） | High |
| TC-UT-010 | 同一顧客で複数 destinationCode | 既存 `(smileDeliveryCode="18")`、`destinationCode="19"` の新規入力 | 戻り値 `"19"`、新規 save、既存 "18" は無傷 | Medium |

### 2.1 既存ロジックの回帰観点

| ID | 観点 | 期待結果 | 優先度 |
|----|------|----------|--------|
| TC-UT-101 | `convertSmileOrderImportFile` 全体パス通過 | 修正後 `setLogisticsProperties` 経由で `detail.deliveryCode` が `bCartLogistics.destinationCode` と一致 | High |
| TC-UT-102 | 軽減税率分岐（slipNumber 2→8 変換） | 既存仕様維持（修正範囲外であることを confirm） | Medium |

---

## 3. Migration (V029) 検証 (dev DB: oda32-postgres17, port 55544)

### 3.1 適用前確認 SQL

```sql
-- 衝突行確認（migration 失敗を回避するため事前チェック）
SELECT a.x_delivery_mapping_id AS need_fix_id,
       a.b_cart_destination_code,
       a.smile_delivery_code AS current_smile_code,
       b.x_delivery_mapping_id AS conflict_with_id,
       b.delivery_name AS conflict_name
  FROM x_delivery_mapping a
  JOIN x_delivery_mapping b
    ON b.smile_delivery_code = a.b_cart_destination_code
   AND b.x_delivery_mapping_id <> a.x_delivery_mapping_id
 WHERE a.b_cart_destination_code IS NOT NULL
   AND a.b_cart_destination_code <> ''
   AND a.smile_delivery_code <> a.b_cart_destination_code;

-- 是正対象件数の事前カウント
SELECT COUNT(*) AS will_be_updated
  FROM x_delivery_mapping
 WHERE b_cart_destination_code IS NOT NULL
   AND b_cart_destination_code <> ''
   AND smile_delivery_code <> b_cart_destination_code;

-- 全行スナップショット保存（ロールバック用）
CREATE TABLE IF NOT EXISTS _backup_x_delivery_mapping_v029 AS
  SELECT * FROM x_delivery_mapping;
```

### 3.2 Migration 検証ケース

| ID | 観点 | 手順 | 期待結果 | 優先度 |
|----|------|------|----------|--------|
| TC-MIG-001 | 衝突行 0 件で migration 成功 | 衝突確認 SQL で 0 件確認 → `./gradlew bootRun --args='--spring.profiles.active=web,dev'` で Flyway 自動適用 | V029 が `flyway_schema_history` に SUCCESS で記録される、`UPDATE` 件数がログ出力される | Critical |
| TC-MIG-002 | 是正後の値検証 | `SELECT * FROM x_delivery_mapping WHERE b_cart_destination_code IS NOT NULL AND b_cart_destination_code <> ''` | 全行で `smile_delivery_code = b_cart_destination_code`、`smile_csv_outputted=false` | Critical |
| TC-MIG-003 | NULL/空 destinationCode 行は触らない | 適用前後で `b_cart_destination_code IS NULL OR b_cart_destination_code = ''` の行のスナップショット比較 | `smile_delivery_code` / `smile_csv_outputted` が変化していない | High |
| TC-MIG-004 | 衝突行 ≥ 1 のとき意図的失敗 | テスト DB で意図的に衝突行を作成（例: `INSERT (b_cart_customer_id=999, smile_delivery_code='99', b_cart_destination_code='99')` と `INSERT (b_cart_customer_id=999, smile_delivery_code='000099', b_cart_destination_code='99')` を投入）→ migration 適用 | `RAISE EXCEPTION 'V029: smile_delivery_code 衝突行が N 件残っています。手動是正してから再実行してください。'` でロールバック、`flyway_schema_history` に FAILED 記録 | Critical |
| TC-MIG-005 | 報告事象データの是正 | B-CART 会員 10043 / destination_code="18" の行を SQL で確認 | 適用後 `smile_delivery_code='18'` (旧 `'000003'` から修正) | Critical |
| TC-MIG-006 | UNIQUE 制約 (`smile_delivery_code`) 維持 | `\d x_delivery_mapping` で制約確認 | 既存 UNIQUE 維持。migration 中に重複 INSERT が発生していないことを確認 | High |
| TC-MIG-007 | 再実行 idempotency | Flyway 経由ではなく psql で V029 の SQL 本体を直接再実行（`psql -f V029__fix_delivery_mapping_smile_code.sql`） | DO ブロック PASS（衝突 0 件 assert 通過）、UPDATE 0 行、エラーなし。`flyway_schema_history` は元の SUCCESS 記録から変化なし | Medium |

### 3.3 ロールバック手順（必要時のみ）

**前提**: `x_delivery_mapping` の FK 関係を必ず事前確認する (`\d+ x_delivery_mapping` で参照先テーブルが無いことを確認)。FK 親になっているテーブルがあれば TRUNCATE は CASCADE しないと失敗する/CASCADE すると子データを失う。本テーブルは現状 FK 親ではない想定だが、確認なしの TRUNCATE は禁止。

```sql
-- ① 親 FK 確認
\d+ x_delivery_mapping
-- "Referenced by:" セクションが空であることを確認

-- ② 安全な復旧 (DELETE + INSERT、TRUNCATE 不使用)
BEGIN;
DELETE FROM x_delivery_mapping;  -- TRUNCATE ではなく DELETE で安全に
INSERT INTO x_delivery_mapping SELECT * FROM _backup_x_delivery_mapping_v029;
-- Flyway 履歴のクリーンアップ (FAILED ステータスを除去)
DELETE FROM flyway_schema_history WHERE version = '29';
COMMIT;
```

---

## 4. バッチ統合検証 (dev 環境)

### 4.1 前提
- dev DB に V029 適用済み
- `bCartOrderConvertSmileOrderFileJob` を IntelliJ から実行可能（args: `--spring.profiles.active=batch,dev --spring.batch.job.name=bCartOrderConvertSmileOrderFileJob shopNo=1`）
- サンプルデータ: `b_cart_logistics` に `destination_code='18'`, `comp_name='新栄不動産ビジネス'` の出荷指示行が存在する状態（無ければテスト用に挿入）

### 4.2 統合テストケース

| ID | 観点 | 手順 | 期待結果 | 優先度 |
|----|------|------|----------|--------|
| TC-IT-001 | 報告事象再現 + 修正確認 | 受注番号 17770157456 相当のサンプル行（destination_code="18"）で `bCartOrderConvertSmileOrderFileJob` 実行 | `t_smile_order_import_file.delivery_code = '18'`（"000003" でない） | Critical |
| TC-IT-002 | x_delivery_mapping 新規 INSERT | 上記実行後 `SELECT * FROM x_delivery_mapping WHERE b_cart_customer_id=10043 AND b_cart_destination_code='18'` | `smile_delivery_code='18'`、`smile_csv_outputted=false`、業務フィールド (zip/住所/recipient_name1) が B-CART logistics と一致 | Critical |
| TC-IT-003 | 同一 destinationCode 2 回目実行 | TC-IT-001 と同じ条件でジョブ再実行 | 重複 INSERT が発生しない（`SELECT COUNT(*) FROM x_delivery_mapping WHERE ...` 件数不変）、業務データ差分なしなら save も発生しない（`modify_date_time` 不変で確認） | Critical |
| TC-IT-004 | 業務データ差分時 update | B-CART logistics の zip を変更してジョブ再実行 | 同一行が UPDATE され `smile_csv_outputted=false`、`smile_delivery_code='18'` のまま | High |
| TC-IT-005 | destination_code 未設定エラー | テスト用 logistics に `destination_code=NULL` を入れて配送先が顧客と異なる行を作成 → ジョブ実行 | `IllegalStateException` でステップ FAIL、ログに B-CART 側の登録漏れを示すメッセージ | High |
| TC-IT-006 | SMILE 納品先 CSV 出力連携 | 続けて `smileDestinationFileOutPutJob` を実行 | 出力 CSV の「納品先コード」列が "18"、対象行の `smile_csv_outputted=true` に更新 | Critical |
| TC-IT-007 | SmileDestinationFileOutPutTasklet IOException 再スロー (単体テスト推奨) | JUnit + `@SpringBootTest` で `MShopLinkedFile.smileDestinationOutputFileName` に存在しないドライブ (例 `Z:\nonexistent\out.csv`) を設定し tasklet を直接実行。あるいは Mockito で `FileOutputStream` 生成箇所を spy する | `RuntimeException`（cause=IOException）で step FAIL、`saveAll` が呼ばれず `smile_csv_outputted` が `true` に書き換わっていない（部分書き込みステータスロスト防止） | High |
| TC-IT-008 | SMILE 受注 CSV 出力 | `smileOrderFileOutPutJob` を実行 | 出力 CSV の納品先コード列が "18" | High |

### 4.3 追加: 偶然一致パターンの回帰確認

| ID | 観点 | 手順 | 期待結果 | 優先度 |
|----|------|------|----------|--------|
| TC-IT-101 | 旧 "000001" 等の数字パディング mapping が壊れない | 既存 `smile_delivery_code='000001'` + `b_cart_destination_code='000001'` の行（migration 是正対象外）でジョブ実行 | step1 ヒット、戻り値 "000001"、新規行は作られない | Medium |

---

## 5. 回帰テスト (Playwright E2E)

| ID | 観点 | 手順 | 期待結果 | 優先度 |
|----|------|------|----------|--------|
| TC-E2E-001 | 既存 B-CART 出荷情報入力画面 | `cd frontend && npx playwright test e2e/bcart-shipping-input.spec.ts` | 全 8 テスト PASS（バッチ修正のため UI 影響なしを confirm） | High |
| TC-E2E-002 | 関連サイドバー回帰 | `npx playwright test e2e/sidebar.spec.ts`（存在すれば） | PASS | Medium |
| TC-E2E-003 | 全体 E2E スイート | `npx playwright test` | green、retrogression なし | Medium |

---

## 6. 影響範囲外確認 (Grep)

| ID | 観点 | コマンド | 期待結果 | 優先度 |
|----|------|---------|----------|--------|
| TC-SCOPE-001 | フロントエンドへの影響なし | `Grep "delivery_mapping\|smileDeliveryCode\|bCartDestinationCode"` を `frontend/` 配下に対して実行 | hit 0（または UI 表示無関係の hit のみ） | High |
| TC-SCOPE-002 | 他バッチへの影響なし | `Grep "DeliveryMappingService\|DeliveryMappingRepository\|x_delivery_mapping"` で `backend/` を検索 | hit が `BCartOrderConvertSmileOrderFileTasklet` / `SmileDestinationFileOutPutTasklet` / `DeliveryMapping*` の自身のみ | High |
| TC-SCOPE-003 | API 経由参照なし | `Grep "delivery_mapping\|deliveryMapping"` で `backend/src/main/java/jp/co/oda32/api/` 配下を検索 | hit 0（REST API が `DeliveryMapping` を返していない） | Medium |
| TC-SCOPE-004 | テスト/設計書以外の参照クリア | `Grep "allocateSmileDeliveryCodeAndSave\|findMaxSmileDeliveryCodeNumber"` を repo 全域 | hit がテスト計画書/設計書/古いコメントのみ。プロダクションコード 0 | High |

---

## 7. 実行順序とゲート

```
1. TC-BUILD-001 ~ 004           ← compile/tsc が通らない時点で停止
       ↓
2. TC-UT-001 ~ 010 / 101 ~ 102  ← 単体テスト全 PASS
       ↓
3. TC-MIG-001 ~ 007             ← dev DB で migration 検証
       ↓
4. TC-IT-001 ~ 008 / 101        ← バッチ統合検証（実 B-CART データで）
       ↓
5. TC-E2E-001 ~ 003             ← 既存 E2E retrogression check
       ↓
6. TC-SCOPE-001 ~ 004           ← Grep で影響範囲外確認
```

各段階で FAIL があれば次に進まず原因調査。Critical 項目は 1 件でも FAIL したらマージ不可。

---

## 8. 受け入れ基準 (Definition of Done)

- Critical 項目 全 PASS（TC-BUILD-001/002, TC-UT-001~007, TC-MIG-001/002/004/005, TC-IT-001/002/003/006）
- High 項目 95% 以上 PASS
- Medium 項目 80% 以上 PASS
- 報告事象（B-CART 会員 10043 / destination_code="18" → SMILE 受注 CSV 納品先コード "18"）が実機で再現確認
- migration 適用後、x_delivery_mapping に `smile_delivery_code <> b_cart_destination_code AND b_cart_destination_code IS NOT NULL AND b_cart_destination_code <> ''` の行が 0 件
- フロントエンド E2E に retrogression なし

---

## 9. 残課題 / Out of Scope

- SMILE 側マスタの旧連番納品先コード（"000003" 等）の物理クリーニングは運用者作業。本テストでは `smile_csv_outputted=false` リセット → 再連携 CSV 出力までを検証。
- 本番適用は別ロールアウト計画 (設計書 §4) で実施。本テストは dev のみ対象。
- `DeliveryMappingService.save` 自体のテストは既存テストカバレッジに依存。
