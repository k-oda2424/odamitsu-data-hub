# 発注入力 機能仕様書

## 1. 概要

仕入先への発注（仕入発注）を入力・管理する機能。入力→確認→登録の多段階フローと、発注一覧でのステータス管理（発注済→納期回答→入荷済→仕入入力済）を提供する。

### 対象テーブル
- `t_send_order` — 発注ヘッダー
- `t_send_order_detail` — 発注明細

### 旧システム対応
| 旧画面 | 旧URL | 新URL（案） |
|--------|--------|-------------|
| 発注入力 | `/sendOrderInput` | `/send-orders/create` |
| 発注確認 | `/sendOrderConfirm` | SPA内ステップ（画面遷移なし） |
| 発注完了 | `/sendOrderCreate` | SPA内ステップ（画面遷移なし） |
| 発注一覧 | `/sendOrderList` | `/send-orders` |

---

## 2. データモデル

### 2.1 テーブル: `t_send_order`（ヘッダー）

| カラム | 型 | PK | 説明 |
|--------|-----|-----|------|
| `send_order_no` | Integer | PK | 発注番号（自動採番） |
| `send_order_date_time` | LocalDateTime | | 発注日時 |
| `desired_delivery_date` | LocalDate | | 希望納期 |
| `shop_no` | Integer | | 店舗番号 |
| `company_no` | Integer | | 会社番号 |
| `supplier_no` | Integer | | 仕入先番号（FK → m_supplier） |
| `send_order_status` | String | | 発注ステータス |
| `warehouse_no` | Integer | | 入荷倉庫番号（FK → m_warehouse） |
| `del_flg` | String | | 削除フラグ |

### 2.2 テーブル: `t_send_order_detail`（明細）

| カラム | 型 | PK | 説明 |
|--------|-----|-----|------|
| `send_order_no` | Integer | PK1 | 発注番号（FK → t_send_order） |
| `send_order_detail_no` | Integer | PK2 | 明細番号（1, 2, 3...） |
| `shop_no` | Integer | | 店舗番号 |
| `company_no` | Integer | | 会社番号 |
| `warehouse_no` | Integer | | 入荷倉庫番号 |
| `goods_no` | Integer | | 商品番号 |
| `goods_code` | String | | 商品コード |
| `goods_name` | String | | 商品名 |
| `goods_price` | BigDecimal | | 仕入単価 |
| `send_order_num` | Integer | | 発注数量（個数） |
| `send_order_case_num` | BigDecimal | | ケース数 |
| `arrive_plan_date` | LocalDate | | 入荷予定日 |
| `arrived_date` | LocalDate | | 入荷日 |
| `arrived_num` | BigDecimal | | 入荷数量 |
| `difference_num` | BigDecimal | | 差異数量 |
| `send_order_detail_status` | String | | 明細ステータス |
| `contain_num` | Integer | | 入数（ケースあたり個数） |
| `del_flg` | String | | 削除フラグ |

### 2.3 ステータス定義

| コード | 名称 | 説明 |
|--------|------|------|
| `00` | 発注済 | 初期状態 |
| `10` | 納期回答 | 仕入先が入荷予定日を回答 |
| `20` | 入荷済 | 商品が倉庫に到着 |
| `30` | 仕入入力済 | 仕入伝票が作成された（最終） |
| `99` | キャンセル | キャンセル |

**遷移ルール**: 前方のみ（00→10→20→30）。後退不可。

---

## 3. 画面仕様

### 3.1 発注入力画面

**パス**: `/send-orders/create`

SPA内で「入力→確認→完了」の3ステップを管理する。

#### ステップ1: 入力フォーム

**ヘッダー部**

| 項目 | 入力方式 | 必須 | 備考 |
|------|----------|------|------|
| ショップ | セレクト | ○ | admin(shopNo=0)のみ表示。非adminは固定 |
| 倉庫 | セレクト | ○ | ショップ選択後に動的取得 |
| 仕入先 | SearchableSelect | ○ | ショップに紐づく仕入先一覧 |
| 発注日時 | datetime-local | ○ | デフォルト: 現在日時 |
| 希望納期 | date | - | |

**明細部（動的行追加テーブル）**

| 項目 | 入力方式 | 必須 | 備考 |
|------|----------|------|------|
| 商品コード | テキスト | ○ | blur時にAJAXで商品情報取得 |
| 商品名 | 表示のみ | | 商品コードから自動取得 |
| 仕入単価 | 数値入力 | ○ | 商品コードから自動取得、編集可 |
| 発注数量 | 数値入力 | ○ | Min(1) |
| 入数 | 表示のみ | | 商品マスタの caseContainNum |
| ケース数 | 表示のみ | | 発注数量 ÷ 入数（自動計算） |
| 小計 | 表示のみ | | 仕入単価 × 発注数量（自動計算） |

**操作ボタン**
- 「行追加」— 空の明細行を追加
- 「確認画面へ」— バリデーション後にステップ2へ

#### ステップ2: 確認画面

入力内容を読み取り専用で表示。

**操作ボタン**
- 「発注登録」— API呼び出し → ステップ3へ
- 「戻る」— ステップ1に戻る（入力内容保持）

#### ステップ3: 完了画面

登録された発注番号・発注内容のサマリを表示。

**操作ボタン**
- 「発注一覧へ」— 一覧画面に遷移
- 「続けて発注」— ステップ1に戻る（フォームリセット）

### 3.2 発注一覧画面

**パス**: `/send-orders`

#### 検索フォーム

| 項目 | 入力方式 | 必須 | 検索条件 |
|------|----------|------|----------|
| ショップ | セレクト | ○ | 完全一致 |
| 倉庫 | セレクト | - | 完全一致 |
| 仕入先 | SearchableSelect | - | 完全一致 |
| ステータス | セレクト | - | 完全一致 |
| 発注日 | date range | - | BETWEEN |

**初期表示**: 検索フォームのみ（テーブル非表示）

#### 検索結果テーブル

| # | カラム | 説明 |
|---|--------|------|
| 1 | 発注番号 | `sendOrderNo`-`sendOrderDetailNo` |
| 2 | 発注日時 | `sendOrderDateTime` |
| 3 | 仕入先 | `supplierName` |
| 4 | 商品コード | `goodsCode` |
| 5 | 商品名 | `goodsName` |
| 6 | 仕入単価 | `goodsPrice` |
| 7 | 発注数量 | `sendOrderNum` |
| 8 | 入荷予定日 | `arrivePlanDate`（編集可） |
| 9 | 入荷日 | `arrivedDate`（編集可） |
| 10 | 入荷数量 | `arrivedNum`（編集可） |
| 11 | ステータス | Badge + セレクト（ステータス更新用） |

**インライン更新**: 行ごとにステータス・入荷情報を更新可能

**ステータス更新時のバリデーション**:
- `10`（納期回答）: `arrivePlanDate` 必須
- `20`（入荷済）: `arrivedNum > 0` 必須
- `30`（仕入入力済）: `arrivedDate` 必須
- 後退不可（現ステータス以下への変更は拒否）

---

## 4. API 設計

### 4.1 商品情報取得（商品コードから）

```
GET /api/v1/sales-goods/by-code?goodsCode=ABC&shopNo=1
```

**レスポンス**:
```json
{
  "goodsNo": 100,
  "goodsCode": "ABC",
  "goodsName": "商品A",
  "purchasePrice": 500.00,
  "supplierNo": 1,
  "caseContainNum": 24
}
```

### 4.2 発注登録

```
POST /api/v1/send-orders
```

**リクエスト**:
```json
{
  "shopNo": 1,
  "warehouseNo": 1,
  "supplierNo": 5,
  "sendOrderDateTime": "2026-04-01T10:00:00",
  "desiredDeliveryDate": "2026-04-10",
  "details": [
    {
      "goodsNo": 100,
      "goodsCode": "ABC",
      "goodsName": "商品A",
      "goodsPrice": 500.00,
      "sendOrderNum": 24,
      "containNum": 24
    }
  ]
}
```

**レスポンス**: `SendOrderResponse`（登録された発注データ）

### 4.3 発注一覧検索

```
GET /api/v1/send-orders/details?shopNo=1&supplierNo=5&sendOrderDetailStatus=00&sendOrderDateTimeFrom=...&sendOrderDateTimeTo=...
```

**クエリパラメータ**:
| パラメータ | 型 | 説明 |
|-----------|-----|------|
| shopNo | Integer | 店舗番号（必須） |
| warehouseNo | Integer | 倉庫番号 |
| supplierNo | Integer | 仕入先番号 |
| sendOrderDetailStatus | String | ステータス |
| sendOrderDateTimeFrom | LocalDateTime | 発注日FROM |
| sendOrderDateTimeTo | LocalDateTime | 発注日TO |

**レスポンス**: `List<SendOrderDetailResponse>`

### 4.4 発注明細ステータス更新

```
PUT /api/v1/send-orders/{sendOrderNo}/details/{sendOrderDetailNo}/status
```

**リクエスト**:
```json
{
  "sendOrderDetailStatus": "10",
  "arrivePlanDate": "2026-04-15",
  "arrivedDate": null,
  "arrivedNum": null
}
```

**バリデーション**:
- ステータスは前方遷移のみ許可
- 各ステータスに応じた必須項目チェック

---

## 5. バックエンド実装方針

### 5.1 既存資産

以下は新システムに移行済み（変更不要）:
- Entity: `TSendOrder`, `TSendOrderDetail`, `TSendOrderDetailPK`
- Repository: `TSendOrderRepository`, `TSendOrderDetailRepository`
- Service: `TSendOrderService`, `TSendOrderDetailService`
- Specification: `TSendOrderDetailSpecification`
- Constant: `SendOrderDetailStatus`

### 5.2 新規・修正ファイル

```
backend/src/main/java/jp/co/oda32/
├── api/purchase/
│   └── SendOrderController.java        # 既存を大幅拡張（CRUD + ステータス更新）
└── dto/purchase/
    ├── SendOrderResponse.java          # 既存を拡張（明細リスト含む）
    ├── SendOrderDetailResponse.java    # 新規（一覧用）
    ├── SendOrderCreateRequest.java     # 新規（登録リクエスト）
    └── SendOrderDetailStatusUpdateRequest.java  # 新規（ステータス更新）
```

### 5.3 登録ロジック

```
1. TSendOrder を INSERT（sendOrderStatus='00'）
2. 明細ごとに TSendOrderDetail を INSERT
   - sendOrderDetailNo は 1 から連番
   - sendOrderDetailStatus='00'
   - sendOrderCaseNum = sendOrderNum ÷ containNum（containNum > 0 の場合）
3. 発注番号を含むレスポンスを返す
```

### 5.4 ステータス更新ロジック

```
1. 現在のステータスを取得
2. 後退チェック（現ステータス >= 更新ステータス → 拒否）
3. ステータス別バリデーション
4. TSendOrderDetail を UPDATE
5. ※ 入荷済(20)時の在庫更新（StockManager）は t_stock 不使用方針のため省略
```

---

## 6. フロントエンド実装方針

### 6.1 ファイル構成

```
frontend/
├── app/(authenticated)/send-orders/
│   ├── page.tsx                       # 一覧ページ
│   └── create/
│       └── page.tsx                   # 入力ページ
├── components/pages/send-order/
│   ├── index.tsx                      # 一覧ページコンポーネント
│   └── create.tsx                     # 入力（3ステップ）コンポーネント
└── types/
    └── send-order.ts                  # 型定義
```

### 6.2 入力画面の状態管理

```
step: 'input' | 'confirm' | 'complete'

step='input':
  - React Hook Form でヘッダー + 明細を管理
  - useFieldArray で明細行の動的追加/削除
  - 商品コードblur → API呼出 → 自動入力
  - 「確認画面へ」→ バリデーション → step='confirm'

step='confirm':
  - フォームデータを読み取り専用で表示
  - 「発注登録」→ POST API → step='complete'
  - 「戻る」→ step='input'

step='complete':
  - 登録結果表示
  - 「続けて発注」→ フォームリセット + step='input'
```

### 6.3 一覧画面のインライン更新

```
1. 検索 → DataTable 表示
2. 各行にステータスセレクト + 入荷情報入力フィールド
3. 「更新」ボタン → PUT API → 成功トースト + データ再取得
```

---

## 7. 旧システムとの差分

| 項目 | 旧（stock-app） | 新（oda-data-hub） |
|------|----------------|-------------------|
| 画面遷移 | 4画面（入力/確認/完了/一覧） | 2ページ（入力=3ステップSPA、一覧） |
| 商品検索 | ポップアップウィンドウ (window.open) | 商品コードblur自動取得 + 商品検索ポップオーバー |
| 仕入先選択 | テキスト入力 + 検索ボタン | SearchableSelect |
| 倉庫選択 | AJAX で動的取得 | useQuery 依存クエリ |
| ステータス更新 | DataTable + jQuery AJAX | DataTable インラインセレクト + useMutation |
| 入荷時の在庫更新 | StockManager.move()（t_stock） | **省略**（t_stock 不使用方針） |
| 発注→仕入紐付け | PurchaseLinkSendOrderTasklet バッチ | 既存バッチ維持（変更なし） |

## 8. 実装決定事項

| # | 項目 | 決定 | 実装状況 |
|---|------|------|----------|
| 1 | 商品検索方式 | SearchableSelect（Select2相当）で仕入先の商品一覧から選択。ポップアップ廃止 | 完了 |
| 2 | 一覧のインライン更新 | 行ごとに個別更新（旧システム踏襲） | 完了（API実装済み、UI未実装） |
| 3 | 入荷時の在庫更新 | 省略（t_stock不使用方針） | 完了 |
| 4 | カスケード連動 | ショップ→仕入先→商品の3段階連動 | 完了 |

## 9. 作成ファイル一覧

### バックエンド
- `api/purchase/SendOrderController.java` — REST API（一覧検索/詳細取得/登録/ステータス更新）
- `dto/purchase/SendOrderResponse.java` — ヘッダーレスポンスDTO（拡張）
- `dto/purchase/SendOrderDetailResponse.java` — 明細レスポンスDTO
- `dto/purchase/SendOrderCreateRequest.java` — 登録リクエストDTO
- `dto/purchase/SendOrderDetailStatusUpdateRequest.java` — ステータス更新リクエストDTO

### フロントエンド
- `types/send-order.ts` — 型定義・ステータス定数
- `components/pages/send-order/index.tsx` — 一覧ページコンポーネント
- `components/pages/send-order/create.tsx` — 入力ページ（3ステップSPA）
- `app/(authenticated)/send-orders/page.tsx` — 一覧ルート
- `app/(authenticated)/send-orders/create/page.tsx` — 入力ルート
- `hooks/use-master-data.ts` — useWarehouses/useSalesGoodsBySupplier フック追加
- `e2e/helpers/mock-api.ts` — モックデータ追加
- `e2e/send-order.spec.ts` — E2Eテスト（13件）

### バグ修正
- `domain/model/purchase/TSendOrder.java` — OneToMany JoinColumn `purchase_no` → `send_order_no` 修正
