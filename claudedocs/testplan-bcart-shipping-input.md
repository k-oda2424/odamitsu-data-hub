# テスト計画書: B-Cart出荷情報入力画面（新システム移植）

- **作成日**: 2026-04-14
- **対象設計書**: `claudedocs/design-bcart-shipping-input.md`
- **DOC参照**: `claudedocs/doc-review-bcart-shipping-input.md`
- **旧実装**: `C:\project\stock-app\src\main\java\jp\co\oda32\app\bcart\BCartShippingInputController.java`
- **対象フェーズ**: Phase 5（テスト計画）— 本計画はドキュメントのみ、コードは Phase 6 で作成

---

## 1. テスト対象

| # | 対象 | ファイル |
|---|---|---|
| 1 | `BCartShippingInputService.search()` | `backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartShippingInputService.java` |
| 2 | `BCartShippingInputService.saveAll()` | 同上 |
| 3 | `BCartShippingInputService.bulkUpdateStatus()` | 同上 |
| 4 | `BCartShippingInputService.syncBCartOrderStatus()` (private) | 同上 |
| 5 | `BCartShippingInputService.resolveSmileProductCode()` (private) | 同上 |
| 6 | `BCartShippingInputService.buildResponseList()` (private) | 同上 |
| 7 | Controller `GET /api/v1/bcart/shipping` | `backend/src/main/java/jp/co/oda32/api/bcart/BCartController.java` |
| 8 | Controller `PUT /api/v1/bcart/shipping` | 同上 |
| 9 | Controller `PUT /api/v1/bcart/shipping/bulk-status` | 同上 |
| 10 | フロントエンド画面 `/bcart/shipping` | `frontend/components/pages/bcart/shipping.tsx` |
| 11 | 型定義 `bcart-shipping.ts` | `frontend/types/bcart-shipping.ts` |
| 12 | サイドバーメニュー表示名 | `frontend/components/layout/Sidebar.tsx` |

---

## 2. テスト観点（レビュー指摘対応）

### 2.1 Bk (Blocker) レビュー由来の観点
| ID | 観点 | 対応レビュー項目 |
|----|------|----------------|
| V-Bk-01 | `processingSerialNumber` の桁数判定（`log10+1 < 8` = 7桁以下）が旧実装と厳密一致しているか | 旧 L94-97 |
| V-Bk-02 | `processingSerialNumber == 0` の場合に `log10` で NaN/例外を起こさないこと | 潜在バグ、新実装で防御必要 |
| V-Bk-03 | B-CART 出力済み（`bCartCsvExported=true`）かつ `SHIPPED` 行が保存ロジックでフィルタされること（`saveAll` / `bulkUpdateStatus` 両方） | 設計 §5.3 / §5.4 |
| V-Bk-04 | B-CART 出力済みかつ `SHIPPED` 行はフロント側でも非活性化されること（`bCartCsvExported` フラグ） | 設計 §4.5, Cr-03 |

### 2.2 Cr (Critical) レビュー由来の観点
| ID | 観点 | 対応レビュー項目 |
|----|------|----------------|
| V-Cr-01 | `BCartOrderProduct` 側の照合 quantity = `orderProCount × setQuantity` （旧 L164 と一致） | Cr-01 |
| V-Cr-02 | `setQuantity == null` または `ZERO` のときは `BigDecimal.ONE` に補正（`convertSetQuantity`） | 旧 L77-81 |
| V-Cr-03 | `BCartLogisticsKey.equals` は `compareTo` 比較、`hashCode` は `stripTrailingZeros` （BigDecimal の `1.0` vs `1` 問題） | 旧 L388-402 |
| V-Cr-04 | `syncBCartOrderStatus` で `peek` 副作用バグが修正されている（2 段階構成） — 複数 logistics が同一 order に紐づいても全件反映後に allMatch 判定 | Cr-04, 設計 §5.5 |
| V-Cr-05 | 全 logistics が SHIPPED → `BCartOrder.status=完了` (`BCartOrderStatus.COMPLETED`) | 設計 §5.5 |
| V-Cr-06 | 不揃いなら `カスタム1` (`BCartOrderStatus.PROCESSING`) | 設計 §5.5 |

### 2.3 Mj (Major) レビュー由来の観点
| ID | 観点 | 対応レビュー項目 |
|----|------|----------------|
| V-Mj-01 | `saveAll` / `bulkUpdateStatus` が単一トランザクションで完結（`@Transactional` 境界） | Mj-01 |
| V-Mj-02 | partnerCode 部分一致フィルタが Service 層 Stream で機能する | Mj-02 |
| V-Mj-03 | `shipmentStatus` の enum バインド（`@JsonCreator` による displayName 受け入れ） | Mj-03 |
| V-Mj-04 | dirty 行のみの送信でも正しく保存される（未送信行は DB 非汚染） | Mj-04 |
| V-Mj-05 | 旧エンドポイント `/shipping/{orderId}/status` 削除、フロント参照ゼロ（削除前に `grep -r "/bcart/shipping/.*status" frontend/` および `grep -r "PUT.*shipping" backend/` 実施） | Mj-05 |

### 2.4 機能観点（設計 §8 ベース）
| ID | 観点 |
|----|------|
| V-F-01 | 初期表示で status=未発送+発送指示のみ取得される |
| V-F-02 | 検索：ステータス指定で絞り込まれる |
| V-F-03 | 検索：partnerCode 部分一致で絞り込まれる |
| V-F-04 | 個別更新：deliveryCode / shipmentDate / memo / adminMessage / shipmentStatus がすべて保存される |
| V-F-05 | 一括ステータス更新：選択行のみ更新される（非選択行は変化なし） |
| V-F-06 | SMILE 未連携の logistics：`goodsInfo=[]`、warn ログ、行自体は返却される |
| V-F-07 | 商品名 "送料" → SHIPPING_FEE_PRODUCT_CODE `00000015` 返却（フィルタ除外） |
| V-F-08 | productNo `case_XXX` → `XXX` へ変換 |
| V-F-09 | productNo `AAA_BBB_CCC` → 末尾 `CCC` を使用 |
| V-F-10 | WSalesGoods 見つからない → `FIXED_PRODUCT_CODE` (99999999) |
| V-F-11 | `memo = dueDate + logistics.memo`（配送希望日先頭） |
| V-F-12 | `shipmentDate = logistics.shipmentDate ?? logistics.arrivalDate` |
| V-F-13 | `adminMessage` は `BCartOrder` から `distinct().findFirst()` |
| V-F-14 | `slipNoList` は `distinct` で重複除去 |
| V-F-15 | `goodsInfo` セパレータ：商品コード後「：」（全角）、商品名後「:」（半角） |
| V-F-16 | `bCartCsvExported=true && SHIPPED` 行はレスポンスにも含まれるが、フラグ true で返る（一覧表示はする） |

---

## 3. バックエンド単体テスト（JUnit 5 + Mockito）

- **テストクラス**: `backend/src/test/java/jp/co/oda32/domain/service/bcart/BCartShippingInputServiceTest.java`
- **モック対象**: `BCartLogisticsService`, `BCartOrderService`, `TSmileOrderImportFileService`, `WSalesGoodsService`
- **フレームワーク**: `@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks`

### 3.1 `resolveSmileProductCode` テスト

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| UT-RC-01 | 正常系 | `productName="送料"`, `productNo="anything"` | `resolveSmileProductCode(name, no)` 呼出 | 戻り値 = `"00000015"` (`SHIPPING_FEE_PRODUCT_CODE`) |
| UT-RC-02 | 正常系 | `productNo="case_ABC123"`, WSalesGoods 存在（shopNo=1, goodsCode="ABC123"） | 呼出 | 戻り値 = `"ABC123"` |
| UT-RC-03 | 正常系 | `productNo="case_ABC123"`, WSalesGoods 不在 | 呼出 | 戻り値 = `"99999999"` (`FIXED_PRODUCT_CODE`) |
| UT-RC-04 | 正常系 | `productNo="AAA_BBB_CCC"`, WSalesGoods 存在（goodsCode="CCC"） | 呼出 | 戻り値 = `"CCC"` |
| UT-RC-05 | 正常系 | `productNo="AAA_BBB"`, WSalesGoods 不在（goodsCode="BBB"） | 呼出 | 戻り値 = `"99999999"` |
| UT-RC-06 | 正常系 | `productNo="PLAIN123"`（`_` なし、`case_` なし）, WSalesGoods 存在 | 呼出 | 戻り値 = `"PLAIN123"` |
| UT-RC-07 | 境界値 | `productNo="PLAIN123"`, WSalesGoods 不在 | 呼出 | 戻り値 = `"99999999"` |
| UT-RC-08 | 境界値 | `productNo="case_"`（空サフィックス） | 呼出 | 空文字で WSalesGoods 検索 → 不在なら `"99999999"` |
| UT-RC-09 | 境界値 | `productName=null`, `productNo="X"` | 呼出 | NPE を起こさず通常ルートへ |
| UT-RC-10 | 正常系 (先勝ち) | `productName="送料"`, `productNo="case_ABC"` | 呼出 | 戻り値 = `"00000015"`（商品名判定が先、case_ 処理に進まない） |

### 3.2 `search` テスト

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| UT-SR-01 | 正常系 | status=null, partnerCode=null。未発送 1 件・発送指示 1 件・発送済 1 件が DB 存在 | `search(null, null)` | 戻りサイズ=2（未発送+発送指示）、発送済は含まれない |
| UT-SR-02 | 正常系 | status="未発送", partnerCode=null | `search("未発送", null)` | 戻り全件が `shipmentStatus="未発送"` |
| UT-SR-03 | 正常系 | status="対象外", partnerCode=null | `search("対象外", null)` | 対象外のみ返却 |
| UT-SR-04 | 正常系 | partnerCode="12345"（部分一致）、customerCode="ABC-12345-Z" 含む 1 件、他 1 件 | `search(null, "12345")` | 戻りサイズ=1、partnerCode 含む行のみ |
| UT-SR-05 | 境界値 | partnerCode="" | `search(null, "")` | フィルタ無効扱いで全件返却（空文字=絞らない） |
| UT-SR-06 | 正常系 (V-Bk-01) | `processingSerialNumber=9999999`（7 桁）と `10000000`（8 桁）が混在 | `search(null, null)` | 7 桁のみ SMILE 連携済みとして採用 |
| UT-SR-07 | 境界値 (V-Bk-02) | `processingSerialNumber=0` | `search(null, null)` | 例外なく処理され、`log10(0)` の結果は連携対象外と判定（NaN/負無限 → 比較 false） |
| UT-SR-08 | 正常系 (V-F-06) | SMILE 未連携の logistics 1 件 | `search(null, null)` | 戻りに含まれ `goodsInfo=[]`, `slipNoList=[]`, warn ログ |
| UT-SR-09 | 正常系 (V-F-15) | 正常データ | `search(null, null)` | `goodsInfo` 要素 = `"00001：商品名:3"`（全角「：」＋半角「:」） |
| UT-SR-10 | 正常系 (V-F-11) | `dueDate="2026-04-20"`, `memo="追加メモ"` | 呼出 | response.memo = `"2026-04-20追加メモ"` |
| UT-SR-11 | 正常系 (V-F-12) | `shipmentDate=null`, `arrivalDate="2026-04-15"` | 呼出 | response.shipmentDate = `"2026-04-15"` |
| UT-SR-12 | 正常系 (V-F-14) | 同一 logistics に同一 slipNumber を持つ TSmileOrderImportFile が 2 件 | 呼出 | `slipNoList.size()=1` |
| UT-SR-13 | 正常系 (V-Cr-01) | BCartOrderProduct: `orderProCount=3, setQuantity=2` → quantity 照合キー=6。TSmileOrderImportFile.quantity=6 | 呼出 | 突合成功、goodsInfo に追加される |
| UT-SR-14 | 正常系 (V-Cr-02) | TSmileOrderImportFile.setQuantity=ZERO (BigDecimal.ZERO) | 呼出 | 内部で ONE に補正、キー一致 |
| UT-SR-15 | 正常系 (V-Cr-03) | setQuantity=1.00 vs 1（trailing zero） | 呼出 | hash/equals で同一キー扱い、突合成功 |
| UT-SR-16 | 正常系 (V-F-13) | 同じ logistics に紐づく BCartOrder 2 件（adminMessage="A" と "B"） | 呼出 | `adminMessage="A"` （distinct().findFirst() 先勝ち） |
| UT-SR-17 | 正常系 (V-F-07) | TSmileOrderImportFile.productCode="00000015" 送料行が混在 | 呼出 | 送料行はフィルタ除外され、突合キーに入らない |
| UT-SR-18 | 正常系 (V-F-16) | logistics.bCartCsvExported=true, status="発送済" | 呼出 | `bCartCsvExported=true` として返る |
| UT-SR-19 | 境界値 (V-Bk-01) | `processingSerialNumber=1`（1桁） | 呼出 | SMILE 連携済みとして採用される |
| UT-SR-20 | 境界値 (V-Bk-02) | `processingSerialNumber=-1` または `null` | 呼出 | 例外を起こさず連携対象外扱い（防御コード） |
| UT-SR-21 | 正常系 (V-Cr-03) | TSmileOrderImportFile.quantity=`6.00`, BCartOrderProduct 照合 quantity=`6` | 呼出 | hash/equals で同一、突合成功 |
| UT-SR-22 | 境界値 (V-F-15) | 商品名が `"特価：商品X:レア"`（区切り文字混在） | 呼出 | `goodsInfo` = `"00001：特価：商品X:レア:3"`（エスケープなし、そのまま連結） |

### 3.3 `saveAll` テスト

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| UT-SA-01 | 正常系 (V-F-04) | 2 件の UpdateRequest、DB に対応 logistics あり | `saveAll(requests)` | `BCartLogisticsService.save` が 2 件で呼ばれ、各フィールド反映 |
| UT-SA-02 | 正常系 (V-Bk-03) | 3 件のうち 1 件は B-CART 出力済み＋SHIPPED | 呼出 | save は 2 件のみ、該当 1 件は除外 |
| UT-SA-03 | 正常系 (V-Cr-05) | logistics 2 件、どちらも status="発送済" に更新、同一 BCartOrder 紐づき、他の logistics も既発送済 | 呼出 | BCartOrder.status="完了" |
| UT-SA-04 | 正常系 (V-Cr-06) | 2 件中 1 件のみ SHIPPED、他 1 件は NOT_SHIPPED | 呼出 | BCartOrder.status="カスタム1" |
| UT-SA-05 | 正常系 (V-Cr-04) | 同じ BCartOrder に紐づく 3 件の logistics を全て SHIPPED に更新。**旧バグ再現データ**（最初の allMatch が true で終わる） | 呼出 | 全件反映後判定のため COMPLETED。peek 副作用に依存しない |
| UT-SA-06 | 正常系 (V-F-04) | adminMessage="連絡事項X" をリクエスト | 呼出 | 関連する BCartOrder.adminMessage="連絡事項X" |
| UT-SA-07 | 正常系 (V-Mj-04) | 10 件の logistics のうち 3 件のみ request 送信 | 呼出 | 送信した 3 件のみ save 対象、未送信 7 件は DB 不変 |
| UT-SA-08 | 異常系 | request.logisticsId が DB に存在しない | 呼出 | 該当ID は無視（旧実装踏襲、例外なし） |
| UT-SA-09 | 境界値 | request リストが空 | `saveAll(List.of())` | save は呼ばれない、例外なし |
| UT-SA-10 | 境界値 | shipmentDate="" | 呼出 | DB に空文字として反映（null 化しない） |
| UT-SA-11 | 異常系 (V-Mj-01) | 途中で例外発生 | 呼出 | トランザクションロールバック検証（`@Transactional` 境界） |
| UT-SA-12 | 異常系 | 同じ logisticsId の request が 2 件含まれる | 呼出 | 後勝ち or 先勝ちで上書き、例外なし。仕様として明示（設計では後勝ち想定） |

### 3.4 `bulkUpdateStatus` テスト

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| UT-BU-01 | 正常系 (V-F-05) | logisticsIds=[1,2,3], status="発送指示" | `bulkUpdateStatus([1,2,3], "発送指示")` | 3 件すべて status="発送指示" へ更新 |
| UT-BU-02 | 正常系 (V-Bk-03) | 3 件中 1 件が B-CART 出力済み＋SHIPPED | 呼出 | 2 件のみ更新、該当 1 件は除外 |
| UT-BU-03 | 正常系 (V-Cr-05) | status="発送済" に 2 件更新、同一 BCartOrder で他の logistics も既発送済 | 呼出 | BCartOrder.status="完了" |
| UT-BU-04 | 異常系 | logisticsIds=[999]（DB 不在） | 呼出 | save 呼出なし、例外なし |
| UT-BU-05 | 境界値 | logisticsIds が空 | 呼出 | save 呼出なし、例外なし |
| UT-BU-06 | 異常系 | status=null | 呼出 | Controller 側 `@NotNull` で 400 扱い、Service 単体テストでは IllegalArgumentException 想定 |

### 3.5 `syncBCartOrderStatus` テスト（private 経由で saveAll 経由テスト）

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| UT-SY-01 | 正常系 (V-Cr-04) | BCartOrder に logistics 3 件、全て SHIPPED | saveAll 経由で呼出 | order.status=COMPLETED |
| UT-SY-02 | 正常系 (V-Cr-06) | 3 件中 2 件 SHIPPED、1 件 NOT_SHIPPED | 呼出 | order.status="カスタム1" |
| UT-SY-03 | 正常系 | 1 BCartOrder に logistics null（まだ未紐付）を含む | 呼出 | `Objects::nonNull` で除外、例外なし |
| UT-SY-04 | 正常系 | updatedLogistics に含まれない既存 logistics は status 不変 | 呼出 | updatedStatusMap に無いIDは setStatus 呼ばれない |

---

## 4. バックエンド統合テスト（`@SpringBootTest` + `MockMvc`）

- **テストクラス**: `backend/src/test/java/jp/co/oda32/api/bcart/BCartControllerIntegrationTest.java`
- **セットアップ**: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`（ロールバック） + `@ActiveProfiles("test")`
- **テストデータ**: `@Sql` で DB 投入、または Repository 経由

### 4.1 `GET /api/v1/bcart/shipping`

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| IT-GET-01 | 正常系 | 認証済み、未発送/発送指示/発送済/対象外 各 1 件 | GET `/api/v1/bcart/shipping` | 200、size=2、各行が未発送または発送指示 |
| IT-GET-02 | 正常系 | 同上 | GET `/api/v1/bcart/shipping?status=発送済` | 200、size=1、status="発送済" |
| IT-GET-03 | 正常系 (V-Mj-02) | customerCode="ABC12345" と "XYZ99999" | GET `?partnerCode=ABC` | 200、size=1、customerCode に "ABC" 含む |
| IT-GET-04 | 正常系 | ステータス＋得意先コード両指定 | GET `?status=未発送&partnerCode=ABC` | 200、両条件 AND |
| IT-GET-05 | 異常系 | 未認証 | GET | 401/403 |
| IT-GET-06 | 正常系 | SMILE 未連携の logistics のみ | GET | 200、行は返るが goodsInfo=[] |
| IT-GET-07 | 正常系 (V-F-16) | bCartCsvExported=true＋発送済 | GET `?status=発送済` | 200、`bCartCsvExported:true` を返す |
| IT-GET-08 | 権限 | 非 admin ユーザー（shopNo=1）で認証 | GET | 200、現状仕様では shopNo フィルタなしで同等結果（旧踏襲）。shopNo 制限を追加する場合は別途 |
| IT-GET-09 | 境界値 | partnerCode="abc"（小文字）vs DB に "ABC12345" | GET `?partnerCode=abc` | DB 照合方式に準拠（PostgreSQL LIKE はデフォルト case-sensitive のため 0 件を期待。ILIKE 採用する場合は 1 件） |

### 4.2 `PUT /api/v1/bcart/shipping`

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| IT-PUT-01 | 正常系 | 対応 logistics 1 件 | PUT body=`[{bCartLogisticsId:1, deliveryCode:"D123", shipmentDate:"2026-04-20", memo:"m", adminMessage:"a", shipmentStatus:"未発送"}]` | 200、DB 更新反映 |
| IT-PUT-02 | 正常系 (V-Mj-03) | shipmentStatus="発送指示" (displayName) | PUT | 200、enum バインド成功 |
| IT-PUT-03 | 異常系 | shipmentStatus="INVALID" | PUT | 400 (BadRequest) |
| IT-PUT-04 | 異常系 | bCartLogisticsId=null | PUT | 400 (`@NotNull`) |
| IT-PUT-05 | 異常系 | deliveryCode が 256 文字 | PUT | 400 (`@Size`) |
| IT-PUT-06 | 正常系 (V-Bk-03) | 対象 logistics が B-CART 出力済み+SHIPPED | PUT | 200、但し DB 反映なし（フィルタ除外） |
| IT-PUT-07 | 正常系 (V-Cr-05) | 全 logistics を SHIPPED に更新 | PUT | BCartOrder.status="完了" |
| IT-PUT-08 | 異常系 | 空リスト | PUT body=`[]` | 200 or 400（設計選択: 無視で 200） |
| IT-PUT-09 | 異常系 | 未認証 | PUT | 401/403 |

### 4.3 `PUT /api/v1/bcart/shipping/bulk-status`

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| IT-BLK-01 | 正常系 | logistics 3 件 | PUT body=`{bCartLogisticsIds:[1,2,3], shipmentStatus:"発送指示"}` | 200、DB 反映 |
| IT-BLK-02 | 異常系 | bCartLogisticsIds=[] | PUT | 400 (`@NotEmpty`) |
| IT-BLK-03 | 異常系 | shipmentStatus=null | PUT | 400 (`@NotNull`) |
| IT-BLK-04 | 正常系 (V-Bk-03) | 1 件が B-CART 出力済み+SHIPPED | PUT | 200、該当行は変化なし |
| IT-BLK-05 | 正常系 (V-Cr-05) | 全件 SHIPPED に | PUT | BCartOrder 完了 |
| IT-BLK-06 | 異常系 | 未認証 | PUT | 401/403 |
| IT-BLK-07 | 正常系 (V-Mj-05) | 旧エンドポイント `/shipping/{id}/status` | PUT | 404（削除確認） |

---

## 5. E2E テスト（Playwright）

- **ファイル**: `frontend/e2e/bcart-shipping-input.spec.ts`
- **ヘルパー**: 既存の `frontend/e2e/helpers/mock-api.ts` と `frontend/e2e/helpers/auth.ts` を踏襲
- **モックデータ**: `MOCK_BCART_SHIPPING_LIST` を `mock-api.ts` に追加（または spec ファイル内定義）
- **パターン**: `await mockAllApis(page)` → `page.route()` で当該画面専用 API を上書き → `loginAndGoto(page, '/bcart/shipping')`

### 5.1 モックデータ想定

```ts
const MOCK_BCART_SHIPPING_LIST = [
  {
    bCartLogisticsId: 1,
    partnerCode: '00123',
    partnerName: '得意先A',
    deliveryCompName: '届け先A',
    deliveryCode: '',
    shipmentDate: '2026-04-20',
    memo: '配送希望2026-04-22',
    adminMessage: '',
    shipmentStatus: '未発送',
    goodsInfo: ['00001：商品A:3', '00002：商品B:5'],
    slipNoList: [1001],
    bCartCsvExported: false,
  },
  {
    bCartLogisticsId: 2,
    partnerCode: '00456',
    partnerName: '得意先B',
    deliveryCompName: '届け先B',
    deliveryCode: 'D-EXISTING',
    shipmentDate: '2026-04-18',
    memo: '',
    adminMessage: '既存連絡事項',
    shipmentStatus: '発送済',
    goodsInfo: ['00003：商品C:1'],
    slipNoList: [1002],
    bCartCsvExported: true, // 非活性行
  },
]
```

### 5.2 E2E テストケース

| ケースID | 分類 | 前提 | 手順 | 期待結果 |
|---|---|---|---|---|
| E2E-01 | 初期表示 | mockAllApis + `/bcart/shipping` GET 未発送+発送指示 2 件 | `/bcart/shipping` 遷移 | ページタイトル「B-Cart出荷情報入力」、テーブルに 2 行表示 |
| E2E-02 | 初期表示 (V-F-01) | 初期 API 呼出 | 画面遷移 | GET パラメータに `status` 未指定（または空） |
| E2E-03 | 表示内容 (V-F-15) | logistics 1 に goodsInfo=`["00001：商品A:3"]` | 画面表示 | セル内に「00001：商品A:3」がそのまま表示 |
| E2E-04 | 表示内容 (V-F-11) | memo="配送希望2026-04-22" | 画面表示 | memo テキストエリアに「配送希望2026-04-22」 |
| E2E-05 | 検索 (V-F-02) | status プルダウン | 「発送済」を選択 → 検索ボタン | GET `?status=発送済` が呼ばれる |
| E2E-06 | 検索 (V-F-03) | partnerCode 入力 | "00123" を入力 → 検索 | GET `?partnerCode=00123` が呼ばれる |
| E2E-07 | 検索 | 全条件指定 | status=未発送＋partnerCode=ABC | GET `?status=未発送&partnerCode=ABC` |
| E2E-08 | 保存 (V-F-04) | 行1の deliveryCode 入力 | "D-NEW" を入力 → 入力内容を保存ボタン | PUT `/bcart/shipping` body 内 deliveryCode="D-NEW" |
| E2E-09 | 保存 (V-Mj-04) | 行1 のみ編集 | 保存ボタン | PUT body 内に行1のみ含まれる（行2は未送信） |
| E2E-10 | 保存 | 全項目変更 | deliveryCode, shipmentDate, memo, adminMessage, shipmentStatus 変更 → 保存 | PUT body に全項目反映 |
| E2E-11 | 保存 | 成功レスポンス | 保存 | 成功トースト「保存しました」 |
| E2E-12 | 保存 | 500 エラー | 保存 | エラートースト「保存に失敗しました」 |
| E2E-13 | 一括ステータス更新 (V-F-05) | 行1/行2 チェック＋ステータス選択 | 一括更新ボタン → 確認 OK | PUT `/bcart/shipping/bulk-status` body=`{bCartLogisticsIds:[1,2], shipmentStatus:"発送指示"}` |
| E2E-14 | 一括ステータス更新 | 確認キャンセル | 一括更新ボタン → キャンセル | API 呼出なし |
| E2E-15 | 一括ステータス更新 | 未選択 | 一括更新ボタン | エラートースト「行を選択してください」 |
| E2E-16 | 全選択 | ヘッダーチェック | 全選択チェックボックス ON | 全行チェック状態 |
| E2E-17 | 非活性 (V-Bk-04) | 行2 `bCartCsvExported=true` かつ status=発送済 | 画面表示 | 行2の deliveryCode / shipmentDate / memo / adminMessage / shipmentStatus 全て disabled |
| E2E-18 | 非活性 | 行2 のチェックボックス | 画面表示 | disabled（一括対象外） |
| E2E-19 | 非活性 | 行1（未発送、非 exported） | 画面表示 | 編集フィールド活性 |
| E2E-20 | ステータス連動 (V-Cr-05) | 全行 SHIPPED 選択 → 保存 | 保存 | 後続の再取得 GET が走り、BCartOrder が完了扱いであること（レスポンスで確認する場合） |
| E2E-21 | バリデーション | deliveryCode 256 文字入力 | 保存 | エラートースト表示（400） |
| E2E-22 | バリデーション | shipmentDate="2026/04/20"（書式違反） | 保存 | バリデーションエラー表示 |
| E2E-23 | サイドバー | サイドバー表示 | 画面表示 | 「B-Cart出荷情報入力」メニュー存在 |
| E2E-24 | サイドバー | クリック | サイドバーメニュークリック | `/bcart/shipping` 遷移 |
| E2E-25 | 認証 | ログアウト状態 | `/bcart/shipping` 直接遷移 | `/login` へリダイレクト |
| E2E-26 | ローディング | 取得中 | 検索ボタン押下 | テーブル部分にローディング表示、検索フォームは常時表示 |
| E2E-27 | SMILE 未連携 (V-F-06) | goodsInfo=[] のデータ | 画面表示 | 行は表示されるが商品情報列が空 |
| E2E-28 | slip 複数 | slipNoList=[1001,1002] | 画面表示 | 「1001, 1002」など複数表示 |
| E2E-29 | partnerCode 部分一致表示 | 検索結果 | 表示 | partnerCode/partnerName が表示される |
| E2E-30 | dirty 管理 (V-Mj-04) | 編集→元の値に戻す | 保存 | 該当行は PUT body に含まれない（isDirty=false） |
| E2E-31 | 検索保持 | 検索条件=発送済で検索後、保存 | 保存後 | 再取得も `?status=発送済` で実施 |
| E2E-32 | 保存後再取得 | 保存成功 | 保存後 | GET `/bcart/shipping` が再度呼ばれる（TanStack Query invalidate） |
| E2E-33 | 一括後再取得 | bulk-status 成功 | 一括更新後 | GET `/bcart/shipping` が再度呼ばれる |
| E2E-34 | 空配列表示 | GET が `[]` を返す | 画面表示 | 「該当データがありません」相当の表示、テーブルは空 |

### 5.3 モック API セットアップ例（参考）

```ts
// spec ファイル先頭で mockAllApis(page) の後に追加:
await page.route(
  (url) => url.pathname === '/api/v1/bcart/shipping' && url.pathname.split('/').length === 5,
  async (route) => {
    const req = route.request()
    if (req.method() === 'GET') {
      const urlObj = new URL(req.url())
      const status = urlObj.searchParams.get('status')
      const partnerCode = urlObj.searchParams.get('partnerCode')
      let data = MOCK_BCART_SHIPPING_LIST
      if (status) data = data.filter(d => d.shipmentStatus === status)
      if (partnerCode) data = data.filter(d => d.partnerCode.includes(partnerCode))
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
    } else if (req.method() === 'PUT') {
      await route.fulfill({ status: 200 })
    }
  }
)

await page.route('**/api/v1/bcart/shipping/bulk-status', async (route) => {
  await route.fulfill({ status: 200 })
})
```

---

## 6. テスト環境

| 項目 | 設定 |
|---|---|
| バックエンド | Java 21, Spring Boot 3.3.x, JUnit 5, Mockito, H2（`test` プロファイル）|
| フロントエンド | Playwright（Chromium headless）、Next.js 16 dev サーバー（3000）|
| API | モックのためバックエンド不要（E2E）|
| コマンド | `cd backend && ./gradlew test`, `cd frontend && npx playwright test bcart-shipping-input.spec.ts` |

---

## 7. カバレッジ目標

| 対象 | カバレッジ目標 |
|---|---|
| `BCartShippingInputService` | 90%（分岐、特に桁数判定・setQuantity補正・フィルタ・状態連動）|
| Controller | 80%（3 エンドポイント × 正常/異常）|
| フロント画面 | E2E 網羅（UI 操作）|

---

## 8. 実装優先順位

1. **Phase 6-a（Backend 単体）**: UT-RC-* → UT-SR-* → UT-SA-* → UT-BU-* → UT-SY-*
2. **Phase 6-b（Backend 統合）**: IT-GET-* → IT-PUT-* → IT-BLK-*
3. **Phase 6-c（E2E 基本）**: E2E-01 ~ E2E-16
4. **Phase 6-d（E2E 非活性・権限・異常系）**: E2E-17 ~ E2E-32

---

## 9. リスクと対処

| リスク | 対処 |
|---|---|
| `processingSerialNumber=0` で `log10` NaN | UT-SR-07 で検証、実装側でも `if (psn > 0)` ガード |
| BigDecimal の `1` vs `1.0` キー不一致 | UT-SR-15 で検証、設計 §3.3 の `stripTrailingZeros` |
| 旧バグ `peek` 副作用の再現 | UT-SA-05 で検証、新設計 §5.5 の 2 段階構造 |
| B-CART 出力済み行の誤更新 | UT-SA-02, UT-BU-02, IT-PUT-06, IT-BLK-04, E2E-17 の多重防御 |
| enum displayName バインド失敗 | IT-PUT-02/03 で検証 |
| dirty チェック未実装で全行送信 | E2E-09, E2E-30 で検証 |

---

## 10. 参考

- 既存 E2E：`frontend/e2e/estimate-comparison.spec.ts`, `frontend/e2e/dashboard.spec.ts`
- 既存ヘルパー：`frontend/e2e/helpers/mock-api.ts`, `frontend/e2e/helpers/auth.ts`
- 旧実装：`C:\project\stock-app\src\main\java\jp\co\oda32\app\bcart\BCartShippingInputController.java`
- 設計書：`claudedocs/design-bcart-shipping-input.md`
- DOC参照：`claudedocs/doc-review-bcart-shipping-input.md`
