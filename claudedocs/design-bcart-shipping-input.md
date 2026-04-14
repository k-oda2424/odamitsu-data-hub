# 設計書: B-Cart出荷情報入力画面（新システム版）

## 1. 概要

旧 stock-app の「B-Cart出荷情報入力」(`/bcart/shippingInputForm`) を新システムへ移植。

- **Backend**: 既存 `BCartController` と `BCartShippingResponse` を刷新。新 Service を追加せず Controller+既存 Service で実装。粒度を **BCartLogistics 単位**に変更。
- **Frontend**: 既存 `/bcart/shipping` ページを刷新。検索・一括更新・送り状番号入力・出荷日入力・メモ/連絡事項編集を実装。
- **データ連動**: BCartLogistics 全出荷 SHIPPED → BCartOrder.status=完了、不揃いの場合は `カスタム1`。

## 2. アーキテクチャ

```
[Next.js: /bcart/shipping]
  ├─ SearchForm (partnerCode, status)
  ├─ Table (行ごと編集、チェックボックス一括選択)
  ├─ [個別更新] → PUT /api/v1/bcart/shipping (全行送信)
  └─ [一括ステータス更新] → PUT /api/v1/bcart/shipping/bulk-status

[Spring Boot: BCartController]
  ├─ GET  /api/v1/bcart/shipping
  ├─ PUT  /api/v1/bcart/shipping           (全行一括保存)
  └─ PUT  /api/v1/bcart/shipping/bulk-status (選択行のステータスのみ一括)
     ↓
  [BCartShippingInputService (新規)]
     - BCartLogisticsService.findByStatus / findByStatusIn / findByIdIn / save
     - TSmileOrderImportFileService.findBybCartLogisticsIdIn
     - WSalesGoodsService.getByShopNoAndGoodsCode
     - BCartOrderService.findByIdIn / save (ステータス連動)
```

## 3. バックエンド設計

### 3.1 新規 enum `BCartOrderStatus`

`backend/src/main/java/jp/co/oda32/constant/BCartOrderStatus.java`

```java
@Getter
public enum BCartOrderStatus {
    NEW_ORDER("新規注文"),
    PROCESSING("カスタム1"),      // 処理中
    CANCELED("カスタム2"),        // キャンセル
    COMPLETED("完了");

    private final String status;
    BCartOrderStatus(String status) { this.status = status; }
}
```

### 3.2 DTO

#### `BCartShippingInputResponse`
`backend/src/main/java/jp/co/oda32/dto/bcart/BCartShippingInputResponse.java`

```java
@Data
@Builder
public class BCartShippingInputResponse {
    private Long bCartLogisticsId;
    private String partnerCode;              // SMILE 得意先コード
    private String partnerName;              // SMILE 得意先名
    private String deliveryCompName;         // 届け先名
    private String deliveryCode;             // 送り状番号（入力対象）
    private String shipmentDate;             // 出荷日 YYYY-MM-DD（入力対象）
    private String memo;                     // 発送メモ（入力対象）
    private String adminMessage;             // 得意先への連絡事項（入力対象）
    private String shipmentStatus;           // "未発送" 等
    private List<String> goodsInfo;          // "商品コード:商品名:数量" の配列
    private List<Integer> slipNoList;        // SMILE 伝票番号（重複除去）
    private boolean bCartCsvExported;        // 非活性判定用
}
```

#### `BCartShippingUpdateRequest`
`backend/src/main/java/jp/co/oda32/dto/bcart/BCartShippingUpdateRequest.java`

```java
@Data
public class BCartShippingUpdateRequest {
    @NotNull private Long bCartLogisticsId;
    @Size(max = 255) private String deliveryCode;
    @Size(max = 10)  private String shipmentDate;  // YYYY-MM-DD or empty
    private String memo;
    private String adminMessage;
    @NotNull private String shipmentStatus;        // "未発送" 等
}
```

#### `BCartShippingBulkStatusRequest`
```java
@Data
public class BCartShippingBulkStatusRequest {
    @NotEmpty private List<Long> bCartLogisticsIds;
    @NotNull  private String shipmentStatus;
}
```

### 3.2.5 `BCartShippingUpdateRequest` のステータス enum 化 (Mj-03 対応)

`shipmentStatus` は `String` ではなく `BcartShipmentStatus` 直接受け取り (Spring の enum バインドを利用):
```java
@NotNull private BcartShipmentStatus shipmentStatus;
```
フロントは enum 名 (`NOT_SHIPPED` 等) でなく displayName (`"未発送"` 等) を送信するため、`BcartShipmentStatus` に以下を追加：
- `@JsonCreator` で displayName から enum へ変換
- `@JsonValue` で displayName を返却

### 3.3 Service `BCartShippingInputService`
`backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartShippingInputService.java`

責務:
- 一覧取得ロジック（旧 `convertLogisticsToInputForm` を移植）
- 一括保存ロジック（旧 `update` + `updateBCartOrderStatus` 移植）
- 選択行一括ステータス更新（旧 `bulkUpdate` 移植）

主要メソッド:
```java
@Transactional(readOnly = true)
public List<BCartShippingInputResponse> search(String status, String partnerCode);

@Transactional
public void saveAll(List<BCartShippingUpdateRequest> requests);

@Transactional
public void bulkUpdateStatus(List<Long> logisticsIds, String status);
```

内部ヘルパ:
- `buildResponseList(List<BCartLogistics>)` — SMILE 突合 + ProductCode 変換
- `resolveSmileProductCode(String productName, String productNo)` — 旧 `getSmileProductCode` 移植
- `syncBCartOrderStatus(List<BCartLogistics> updated)` — 旧 `updateBCartOrderStatus` 移植

変換キー（内部）:
```java
record BCartLogisticsKey(Long logisticsId, String productCode, BigDecimal setQuantity, BigDecimal quantity) {
    // setQuantity/quantity は compareTo(0) で比較されるため stripTrailingZeros 必須
}
```

### 3.4 Controller 刷新 `BCartController`
既存メソッドを削除し、以下を実装:

```java
@GetMapping("/shipping")
public ResponseEntity<List<BCartShippingInputResponse>> list(
    @RequestParam(required = false) String status,
    @RequestParam(required = false) String partnerCode);

@PutMapping("/shipping")
public ResponseEntity<Void> saveAll(@RequestBody @Valid List<BCartShippingUpdateRequest> requests);

@PutMapping("/shipping/bulk-status")
public ResponseEntity<Void> bulkUpdateStatus(@RequestBody @Valid BCartShippingBulkStatusRequest request);
```

既存の `GET /api/v1/bcart/shipping` と `PUT /api/v1/bcart/shipping/{orderId}/status` は破壊的変更（フロントと同時に修正するため問題なし）。

### 3.5 Repository 追加メソッド
`BCartLogisticsRepository` に 得意先コード絞り込み用の追加クエリ:

```java
@Query("""
    SELECT DISTINCT bl FROM BCartLogistics bl
    JOIN bl.bCartOrderProductList bop
    JOIN TSmileOrderImportFile tsof ON tsof.bCartLogisticsId = bl.id
    WHERE bl.status IN (:statuses)
      AND (:partnerCode IS NULL OR tsof.customerCode LIKE CONCAT('%', :partnerCode, '%'))
    """)
List<BCartLogistics> findByStatusesAndPartnerCode(@Param("statuses") List<String> statuses,
                                                   @Param("partnerCode") String partnerCode);
```

※ 既存 `findByStatusIn` / `findByStatusNative` と整合を取りつつ、partnerCode 指定時のみ追加フィルタ。
→ 簡略化のため、実装は **Service 層で取得後に Stream で partnerCode フィルタ**する方針（Repository 変更最小）。

## 4. フロントエンド設計

### 4.1 ルート
- `app/(authenticated)/bcart/shipping/page.tsx` — 既存のまま
- `components/pages/bcart/shipping.tsx` — **刷新**

### 4.2 画面構造

```
[PageHeader: B-Cart出荷情報入力]
[SearchForm]
  - 得意先コード (Input, 任意)
  - 出荷ステータス (SearchableSelect: 未発送/発送指示/発送済/対象外/全て)
  - [検索] ボタン
[一括操作]
  - 選択行に一括適用ステータス (Select)
  - [選択行を一括更新] ボタン → confirm dialog
[Table]
  - [☐全選択] | 得意先 | Logistics/SMILE伝票 | 届け先 | 商品情報 | 送り状番号(input) | 出荷日(Input date) | ステータス(Select) | メモ(Textarea) | 連絡事項(Textarea)
[保存]
  - [入力内容を保存] ボタン → PUT /bcart/shipping
```

### 4.3 型定義 `frontend/types/bcart-shipping.ts`

```ts
export const BCART_SHIPMENT_STATUSES = ['未発送', '発送指示', '発送済', '対象外'] as const
export type BCartShipmentStatus = typeof BCART_SHIPMENT_STATUSES[number]

export interface BCartShippingInputResponse {
  bCartLogisticsId: number
  partnerCode: string
  partnerName: string
  deliveryCompName: string
  deliveryCode: string
  shipmentDate: string
  memo: string
  adminMessage: string
  shipmentStatus: BCartShipmentStatus
  goodsInfo: string[]
  slipNoList: number[]
  bCartCsvExported: boolean
}

export interface BCartShippingUpdateRequest {
  bCartLogisticsId: number
  deliveryCode: string
  shipmentDate: string
  memo: string
  adminMessage: string
  shipmentStatus: BCartShipmentStatus
}
```

### 4.4 状態管理
- 検索条件: `useState<{status:BCartShipmentStatus|'ALL'|null, partnerCode:string}>` — 初期 `{status:null, partnerCode:''}`（初回検索なし）
- 一覧データ: `useQuery` で取得 → ローカル編集用に `useState` へコピー（旧実装と同様、画面上の編集を一括保存するため）
- 選択行: `useState<Set<number>>` （logisticsId の集合）
- 編集中データ: `Map<number, BCartShippingInputResponse>` で行ごと保持
- ステータス enum マッピング: `statusMap` で `未発送→destructive` 等

### 4.5 バリデーション
- 送り状番号: 文字列 max 255
- 出荷日: 正規表現 `YYYY-MM-DD` または空
- B-CART 連携済みの発送済み行は全フィールド `disabled` (保存時はバックエンドで再チェック)

### 4.6 サイドバー
既存の `B-CART出荷` → `B-Cart出荷情報入力` へ表示名変更。href は既存の `/bcart/shipping` を維持。

## 5. ビジネスロジック詳細

### 5.1 商品コード解決 `resolveSmileProductCode`
旧 `getSmileProductCode` を完全移植。

```
商品名が "送料" → SHIPPING_FEE_PRODUCT_CODE (00000015)
productNo が "case_XXX" → "XXX" を使用
productNo に "_" を含む → split("_").last を使用
WSalesGoods で shopNo=B_CART_ORDER(=1) + goodsCode 完全一致で検索
  見つからない → FIXED_PRODUCT_CODE (99999999) 手入力商品扱い
  見つかる → goodsCode を返す
```

### 5.2 SMILE 突合 `buildResponseList`
（旧 `convertLogisticsToInputForm` L83-184 相当）

1. logisticsIds → `TSmileOrderImportFile` 一括取得
2. フィルタ: `productCode != SHIPPING_FEE_PRODUCT_CODE("00000015")` かつ `(int)(Math.log10(processingSerialNumber) + 1) < 8` (SMILE 連携済み判定。旧 L94-97 と厳密一致)
3. **前処理 `convertSetQuantity`** (旧 L77-81): `setQuantity == null || compareTo(ZERO)==0` → `BigDecimal.ONE` に補正。これを行わないとキー不一致で全件 warn。
4. グルーピング: キー `BCartLogisticsKey(logisticsId, resolveSmileProductCode(productName, productCode), setQuantity, quantity)` → 重複は warn ログ、先頭採用
5. 各 BCartLogistics について:
   - `shipmentDate = logistics.shipmentDate ?? logistics.arrivalDate`
   - `memo = (dueDate ?? "") + (logistics.memo ?? "")` (配送希望日を先頭に付与)
   - `adminMessage = bCartOrderProductList.stream().map(BCartOrderProduct::getBCartOrder).map(BCartOrder::getAdminMessage).distinct().findFirst().orElse(null)` (旧 L154)
   - `bCartCsvExported = logistics.isBCartCsvExported()` （画面側非活性判定用、Cr-03 対応）
   - 各 `BCartOrderProduct` を照合: 照合側キー = `BCartLogisticsKey(logisticsId, resolveSmileProductCode(productName, productNo), setQuantity ?? ONE, orderProCount.multiply(setQuantity ?? ONE))` （**`quantity` は `orderProCount × setQuantity`**、Cr-01 対応）
   - 照合成功: `goodsInfo.add(tsof.productCode + "：" + orderProduct.productName + ":" + tsof.quantity)` (セパレータは旧 L174 と一致: 商品コード後は全角「：」、商品名後は半角「:」)
   - `partnerCode/Name = tsof.customerCode/CompName`（先勝ち）
   - `slipNoList.add(tsof.slipNumber)` → `distinct()` で重複除去
   - 突合失敗: warn ログ + その orderProduct をスキップ（他の商品行は引き続き処理、旧 L168 と同じ）

### 5.3 一括保存 `saveAll`
1. request から logisticsIds を抽出
2. DB から `findByIdIn` で取得
3. **B-CART CSV 出力済みの発送済み行をフィルタ除外**（旧と同じ: `!(SHIPPED && bCartCsvExported)`）
4. 各行の deliveryCode / shipmentDate / status / memo を反映
5. 紐づく `BCartOrder.adminMessage` を reqeust のものへ更新（重複分は 1 回）
6. `BCartLogistics.save(list)` / `BCartOrder.save(list)`
7. `syncBCartOrderStatus` を呼び出し、BCartOrder のステータスを `完了` or `カスタム1` に更新

### 5.4 選択行一括ステータス更新 `bulkUpdateStatus`
1. logisticsIds で `findByIdIn`
2. B-CART 出力済み発送済み行はフィルタ除外
3. 全行のステータスを一括書換
4. save + `syncBCartOrderStatus`

### 5.5 syncBCartOrderStatus
旧 `updateBCartOrderStatus` の **peek 副作用パターンを修正**して移植 (Cr-04 対応)。

旧 L298-304 の `peek(logistics -> logistics.setStatus(...))` は `allMatch` の短絡評価により前半ヒット時に後続が更新されないバグがある。新実装では以下のように 2 段階で実施:

```java
// 1. 更新された logistics のステータスを Map に保持
Map<Long, String> updatedStatusMap = updatedLogistics.stream()
    .collect(Collectors.toMap(BCartLogistics::getId, BCartLogistics::getStatus));

// 2. 関連 BCartOrder を取得し、各 order の全 logistics を再取得
List<BCartOrder> orders = bCartOrderService.findByIdIn(affectedOrderIds);
for (BCartOrder order : orders) {
    List<BCartLogistics> allLogistics = order.getOrderProductList().stream()
        .map(BCartOrderProduct::getBCartLogistics)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    // 3. 更新対象の最新ステータスを反映（副作用を先に全件完了させる）
    for (BCartLogistics l : allLogistics) {
        String newStatus = updatedStatusMap.get(l.getId());
        if (newStatus != null) l.setStatus(newStatus);
    }

    // 4. 判定（allMatch はこの時点で全件更新済みのため安全）
    boolean allShipped = allLogistics.stream()
        .allMatch(l -> BcartShipmentStatus.SHIPPED.getDisplayName().equals(l.getStatus()));
    order.setStatus(allShipped
        ? BCartOrderStatus.COMPLETED.getStatus()
        : BCartOrderStatus.PROCESSING.getStatus());
}
bCartOrderService.save(orders);
```

### 5.6 トランザクション境界 (Mj-01 対応)
- `saveAll` / `bulkUpdateStatus` / `search` はすべて `BCartShippingInputService` 内で `@Transactional` / `@Transactional(readOnly=true)` を宣言。
- `syncBCartOrderStatus` は private メソッドとして同一トランザクション内で完結（別 `@Transactional` にしない）。
- N+1 懸念: `BCartOrder.orderProductList` が LAZY のため、`syncBCartOrderStatus` 内では `findByIdIn` の戻り値に対して `orderProductList` をループで触るだけだが、EntityGraph 追加は **Phase 4 で計測後に判断**（先行最適化しない）。

### 5.7 パフォーマンス方針 (Mj-02 対応)
- partnerCode 部分一致は Service 層の Stream フィルタで実装（現状 B-Cart logistics 件数は未発送+発送指示で数十〜数百件程度）。
- 件数が 1,000 件を超えるようになった場合、`findByStatusInAndPartnerCode` を Repository に追加する。

### 5.8 dirty チェック方針 (Mj-04 対応)
- フロント側: `isDirty` フラグを行ごとに持ち、変更があった行のみ PUT に含める。
- バックエンド: 受信した request のみ DB 更新。未送信行には触らない（旧実装は全行送信で汚染していたが、新実装では送信量が減り audit フィールドの汚染を防ぐ）。

### 5.9 旧エンドポイント削除前チェック (Mj-05 対応)
削除前に以下の grep を実行し、外部依存がないことを確認:
```bash
grep -r "/bcart/shipping/.*status" frontend/ --include='*.ts' --include='*.tsx'
grep -r "PUT.*shipping" backend/ --include='*.java'
```
現状フロントの `components/pages/bcart/shipping.tsx` 以外に依存なし。

## 6. DB 変更
なし（既存テーブルをそのまま利用）。

## 7. エラーハンドリング
- 404 相当: 入力された logisticsId が DB にない → 無視（旧実装踏襲、リストフィルタ）
- バリデーションエラー: `@Valid` 失敗 → 400 + MethodArgumentNotValidException 標準処理
- SMILE 未連携: warn ログのみ、一覧には他の行を返す

## 8. テスト観点
- 初期表示: 未発送+発送指示のみ表示される
- 検索: ステータス指定で絞り込まれる / 得意先コード部分一致
- 個別更新: 送り状番号/出荷日/メモ/連絡事項が保存される
- 一括ステータス更新: 選択行のみ更新される
- B-CART 連携済みの発送済み行: 更新対象外（フィールド非活性 + バックエンドでフィルタ）
- BCartOrder ステータス連動: 全出荷 SHIPPED → 完了、不揃い → カスタム1
- SMILE 未連携の出荷: goodsInfo が空で表示される / warn ログが出る

## 9. 影響範囲
- 既存 `BCartController.listShipping/updateShippingStatus` は破壊的変更（フロントと同時更新）
- `BCartShippingResponse` DTO は不要化 → 削除
- `frontend/components/pages/bcart/shipping.tsx` 大幅刷新
- サイドバー表示名変更
- 既存 E2E `frontend/e2e/dashboard.spec.ts` への影響なし（`/bcart/shipping` モックに依存）

## 10. 実装差分（ファイル単位）

### New
- `backend/src/main/java/jp/co/oda32/constant/BCartOrderStatus.java`
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartShippingInputResponse.java`
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartShippingUpdateRequest.java`
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartShippingBulkStatusRequest.java`
- `backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartShippingInputService.java`
- `frontend/types/bcart-shipping.ts`

### Modified
- `backend/src/main/java/jp/co/oda32/api/bcart/BCartController.java` — 刷新
- `frontend/components/pages/bcart/shipping.tsx` — 刷新
- `frontend/components/layout/Sidebar.tsx` — `B-CART出荷` → `B-Cart出荷情報入力`

### Deleted
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartShippingResponse.java`
