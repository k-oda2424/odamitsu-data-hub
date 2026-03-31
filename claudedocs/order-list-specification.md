# 受注一覧画面 移行設計書

## 1. 概要

旧システム（stock-app）の受注一覧画面を新システム（odamitsu-data-hub）に移行する。
旧システムは**注文明細（TOrderDetail）単位**で一覧表示しており、商品コード・商品名・数量・単価など明細レベルの情報を表示する。新システムでも同様に明細単位の一覧を実装する。

### 現状（新システム）の課題
- 受注ヘッダー（TOrder）レベルの4カラムのみ表示
- 検索パラメータが shopNo / companyNo の2つだけ
- 初期表示で全件取得（パフォーマンス問題）
- ステータスフィルタ・日付範囲検索なし

## 2. 旧システム仕様（移行元）

### 2.1 検索条件

| 項目 | フィールド名 | 型 | 必須 | 備考 |
|------|-------------|-----|------|------|
| ショップ | shopNo | Integer | Yes | ドロップダウン選択 |
| 伝票番号 | slipNo | String | No | 完全一致 |
| 得意先コード | partnerCode | String | No | 得意先検索ポップアップ連動 |
| 得意先名 | partnerName | String | No | 表示のみ（partnerNoで検索） |
| 商品名 | goodsName | String | No | 部分一致（LIKE %text%） |
| 商品コード | goodsCode | String | No | 前方一致（LIKE text%） |
| 注文ステータス | orderDetailStatus | String | No | ドロップダウン（OrderDetailStatus enum） |
| 注文日時FROM | orderDateTimeFrom | LocalDateTime | No | デフォルト: 1ヶ月前 |
| 注文日時TO | orderDateTimeTo | LocalDateTime | No | - |
| 伝票日付FROM | slipDateFrom | LocalDate | No | - |
| 伝票日付TO | slipDateTo | LocalDate | No | - |

### 2.2 一覧表示カラム

| # | カラム名 | データソース | 備考 |
|---|---------|-------------|------|
| 1 | 注文番号 | orderNo-orderDetailNo | 複合表示 |
| 2 | 伝票日付 | tDelivery.slipDate | 納品伝票日付 |
| 3 | 得意先 | tOrder.companyName | 得意先名 |
| 4 | 伝票番号 | tDelivery.slipNo | 納品伝票番号 |
| 5 | 商品コード | goodsCode | - |
| 6 | 商品名 | goodsName | - |
| 7 | 単価 | goodsPrice | 通貨フォーマット |
| 8 | 数量 | orderNum | 数値入力可能 |
| 9 | 入数 | unitContainNum | ケースあたり数量 |
| 10 | ケース換算 | unitNum | ケース数 |
| 11 | 小計 | goodsPrice × (orderNum - cancelNum - returnNum) | 計算値 |
| 12 | 受注日時 | tOrder.orderDateTime | 日時フォーマット |

### 2.3 ステータス定義（OrderDetailStatus）

| コード | 値 | 表示名 |
|--------|-----|--------|
| 00 | RECEIPT | 注文受付 |
| 01 | BACK_ORDERED | 入荷待ち |
| 10 | ALLOCATION | 在庫引当 |
| 20 | DELIVERED | 納品済 |
| 90 | CANCEL | キャンセル |
| 99 | RETURN | 返品 |

### 2.4 デフォルト動作
- 注文日時FROM: 画面オープン時に1ヶ月前を自動設定
- ソート: 受注日時の降順（新しい順）
- 店舗未選択時: 検索不可（コントロール無効化）

## 3. 新システム実装設計

### 3.1 バックエンド

#### 3.1.1 OrderDetailResponse DTO（新規）

```java
@Data
@Builder
public class OrderDetailResponse {
    private Integer orderNo;
    private Integer orderDetailNo;
    private Integer shopNo;
    private String companyName;      // tOrder.companyName
    private String partnerCode;      // tOrder.partnerCode
    private Integer partnerNo;       // tOrder.partnerNo
    private String orderDetailStatus;
    private LocalDateTime orderDateTime; // tOrder.orderDateTime
    private String goodsCode;
    private String goodsName;
    private BigDecimal goodsPrice;
    private BigDecimal orderNum;
    private BigDecimal cancelNum;
    private BigDecimal returnNum;
    private Integer unitContainNum;
    private Integer unitNum;
    private BigDecimal subtotal;     // 計算: goodsPrice × (orderNum - cancelNum - returnNum)
    private String slipNo;           // tDelivery.slipNo
    private LocalDate slipDate;      // tDelivery.slipDate
    private Integer deliveryNo;

    public static OrderDetailResponse from(TOrderDetail od) {
        BigDecimal effectiveQty = od.getOrderNum()
            .subtract(od.getCancelNum())
            .subtract(od.getReturnNum());
        BigDecimal subtotal = od.getGoodsPrice() != null
            ? od.getGoodsPrice().multiply(effectiveQty)
            : BigDecimal.ZERO;

        return OrderDetailResponse.builder()
            .orderNo(od.getOrderNo())
            .orderDetailNo(od.getOrderDetailNo())
            .shopNo(od.getShopNo())
            .companyName(od.getTOrder() != null ? od.getTOrder().getCompanyName() : null)
            .partnerCode(od.getTOrder() != null ? od.getTOrder().getPartnerCode() : null)
            .partnerNo(od.getTOrder() != null ? od.getTOrder().getPartnerNo() : null)
            .orderDetailStatus(od.getOrderDetailStatus())
            .orderDateTime(od.getTOrder() != null ? od.getTOrder().getOrderDateTime() : null)
            .goodsCode(od.getGoodsCode())
            .goodsName(od.getGoodsName())
            .goodsPrice(od.getGoodsPrice())
            .orderNum(od.getOrderNum())
            .cancelNum(od.getCancelNum())
            .returnNum(od.getReturnNum())
            .unitContainNum(od.getUnitContainNum())
            .unitNum(od.getUnitNum())
            .subtotal(subtotal)
            .slipNo(od.getTDelivery() != null ? od.getTDelivery().getSlipNo() : null)
            .slipDate(od.getTDelivery() != null ? od.getTDelivery().getSlipDate() : null)
            .deliveryNo(od.getDeliveryNo())
            .build();
    }
}
```

#### 3.1.2 OrderController 拡張

```
GET /api/v1/orders/details
  @RequestParam Integer shopNo          ← 必須
  @RequestParam(required=false) Integer companyNo
  @RequestParam(required=false) String slipNo
  @RequestParam(required=false) String goodsName
  @RequestParam(required=false) String goodsCode
  @RequestParam(required=false) String orderDetailStatus
  @RequestParam(required=false) @DateTimeFormat LocalDateTime orderDateTimeFrom
  @RequestParam(required=false) @DateTimeFormat LocalDateTime orderDateTimeTo
  @RequestParam(required=false) @DateTimeFormat LocalDate slipDateFrom
  @RequestParam(required=false) @DateTimeFormat LocalDate slipDateTo
  → List<OrderDetailResponse>（受注日時降順でソート）
```

既存の `GET /api/v1/orders` と `GET /api/v1/orders/{orderNo}` は変更しない。

#### 3.1.3 既存リソース（変更不要）
- `TOrderDetailService.find()` — 全検索パラメータに対応済み
- `TOrderDetailSpecification` — 全条件のSpecificationメソッドあり
- `OrderDetailStatus` enum — 新システムに移行済み
- `OrderStatus` enum — 新システムに移行済み

### 3.2 フロントエンド

#### 3.2.1 型定義（types/order.ts）

```typescript
export interface OrderDetailResponse {
  orderNo: number
  orderDetailNo: number
  shopNo: number
  companyName: string | null
  partnerCode: string | null
  partnerNo: number | null
  orderDetailStatus: string | null
  orderDateTime: string | null
  goodsCode: string | null
  goodsName: string | null
  goodsPrice: number | null
  orderNum: number | null
  cancelNum: number | null
  returnNum: number | null
  unitContainNum: number | null
  unitNum: number | null
  subtotal: number | null
  slipNo: string | null
  slipDate: string | null
  deliveryNo: number | null
}

export const ORDER_DETAIL_STATUS_OPTIONS = [
  { value: '00', label: '注文受付' },
  { value: '01', label: '入荷待ち' },
  { value: '10', label: '在庫引当' },
  { value: '20', label: '納品済' },
  { value: '90', label: 'キャンセル' },
  { value: '99', label: '返品' },
] as const
```

#### 3.2.2 ページコンポーネント（components/pages/order/index.tsx）

**共通パターン適用:**
- 初期表示で検索しない（`searchParams: null`）
- admin（shopNo=0）には店舗セレクト表示
- ローディングはテーブル部分のみ（検索フォームは常に表示）
- マスタデータは `use-master-data.ts` の共通フック使用

**検索フォーム:**

| 項目 | UIコンポーネント | 備考 |
|------|-----------------|------|
| 店舗 | Select（admin時のみ） | useShops() |
| 伝票番号 | Input | テキスト入力 |
| 得意先 | SearchableSelect | usePartners() |
| 商品名 | Input | テキスト入力 |
| 商品コード | Input | テキスト入力 |
| 注文ステータス | Select | ORDER_DETAIL_STATUS_OPTIONS |
| 注文日時 | Input type="datetime-local" × 2 | FROM / TO |
| 伝票日付 | Input type="date" × 2 | FROM / TO |

**テーブルカラム:**

| # | ヘッダー | キー | ソート | 表示 |
|---|---------|------|--------|------|
| 1 | 注文番号 | orderNo | Yes | `{orderNo}-{orderDetailNo}` |
| 2 | 受注日時 | orderDateTime | Yes | 日時フォーマット |
| 3 | 伝票日付 | slipDate | Yes | 日付フォーマット |
| 4 | 得意先 | companyName | Yes | テキスト |
| 5 | 伝票番号 | slipNo | No | テキスト |
| 6 | 商品コード | goodsCode | No | テキスト |
| 7 | 商品名 | goodsName | Yes | テキスト |
| 8 | 単価 | goodsPrice | No | 通貨フォーマット |
| 9 | 数量 | orderNum | No | 数値 |
| 10 | 小計 | subtotal | No | 通貨フォーマット |
| 11 | ステータス | orderDetailStatus | No | Badge |

**ステータスBadgeカラーマッピング:**

| ステータス | Badge variant |
|-----------|---------------|
| 注文受付（00） | default |
| 入荷待ち（01） | outline |
| 在庫引当（10） | secondary |
| 納品済（20） | secondary |
| キャンセル（90） | destructive |
| 返品（99） | destructive |

#### 3.2.3 ルーティング

既存の `/orders` ページを改修。新規ルートの追加は不要。

### 3.3 旧システムからの変更点

| 項目 | 旧システム | 新システム | 理由 |
|------|-----------|-----------|------|
| 得意先検索 | ポップアップモーダル | SearchableSelect | UX統一 |
| 日付入力 | jQuery DateTimePicker | HTML5 datetime-local / date | ライブラリ依存排除 |
| 店舗選択 | 全ユーザーにドロップダウン | admin時のみ表示 | 一般ユーザーは自店舗固定 |
| 行内更新ボタン | あり | Phase 1では除外 | 詳細画面で対応予定 |
| 数量編集 | テーブル内で直接編集 | Phase 1では読取専用 | 詳細画面で対応予定 |
| デフォルト日付 | 1ヶ月前自動設定 | 1ヶ月前自動設定 | 踏襲 |
| 初期検索 | 店舗選択後に検索可能 | 検索ボタン押下後 | パフォーマンス対策 |

## 4. 実装手順

### Phase 1: バックエンド API

| # | タスク | ファイル |
|---|--------|---------|
| 1 | OrderDetailResponse DTO 新規作成 | `dto/order/OrderDetailResponse.java` |
| 2 | OrderController に `GET /orders/details` 追加 | `api/order/OrderController.java` |

### Phase 2: フロントエンド

| # | タスク | ファイル |
|---|--------|---------|
| 3 | 型定義作成 | `types/order.ts` |
| 4 | 受注一覧ページ全面書き換え | `components/pages/order/index.tsx` |

### Phase 3: テスト

| # | タスク | ファイル |
|---|--------|---------|
| 5 | APIモックデータ追加 | `e2e/helpers/mock-api.ts` |
| 6 | E2Eテスト作成 | `e2e/order-list.spec.ts` |

## 5. テスト観点

| カテゴリ | テスト内容 |
|---------|----------|
| 初期表示 | 検索フォーム表示、テーブル非表示、案内メッセージ表示 |
| 検索 | 各条件での検索、リセット、空結果表示 |
| テーブル | カラム表示、ソート、ステータスBadge |
| 日付デフォルト | 注文日時FROMに1ヶ月前が設定されている |
| admin | 店舗セレクト表示、店舗選択で検索可能 |

## 6. 影響範囲

- `OrderController.java` — エンドポイント追加（既存変更なし）
- `components/pages/order/index.tsx` — 全面書き換え
- `types/order.ts` — 新規
- `e2e/helpers/mock-api.ts` — モックデータ追加
- サイドバー — 変更なし（既存の「受注一覧」リンクがそのまま使える）
