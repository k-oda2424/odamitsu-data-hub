# DOC参照サマリー - B-Cart出荷情報入力画面 移植

## 参照ドキュメント
- `docs/detailed-design/DD_08_BCART連携.md` §2 出荷情報入力画面処理設計, §11 画面構造
- `docs/00_system_overview.md` §4.2.3 Web 画面連携
- `docs/08_bcart_integration.md`
- 旧システム実装: `C:/project/stock-app/src/main/java/jp/co/oda32/app/bcart/BCartShippingInputController.java` (他 Form/Search/HTML/JS)
- 新システム既存: `backend/.../api/bcart/BCartController.java`, `frontend/components/pages/bcart/shipping.tsx`

## 設計意図（旧システム）
- **粒度**: `BCartLogistics` 単位（出荷単位）で編集。1受注に複数出荷がぶら下がる。
- **SMILE 突合**: `TSmileOrderImportFile` と `(logisticsId, productCode, setQuantity, quantity)` をキーに突合（照合側 quantity は `orderProCount × setQuantity`）。`processing_serial_number` が 8 桁未満のレコードのみ SMILE 連携済みとしてフィルタ。
- **売上明細取込との連動**: SMILE へ売上明細取込を行ってから表示される。突合できない場合は warn ログのみ。
- **出荷完了連動**: `BCartLogistics` が全て SHIPPED になったら `BCartOrder.status=完了`, そうでなければ `カスタム1` (処理中)。
- **B-CART 出力済みの発送済み行は変更不可**: `isBCartCsvExported=true && status=SHIPPED` は update 対象外。

## データモデル（新システムに既存）
- `b_cart_logistics` (BCartLogistics.java): delivery_code, shipment_date, due_date, memo, status, is_updated, b_cart_csv_exported
- `b_cart_order` (BCartOrder.java): admin_message, status
- `b_cart_order_product` (BCartOrderProduct.java): productNo, productName, setQuantity, orderProCount
- `t_smile_order_import_file`: slip_number, product_code, customer_code/name, processing_serial_number, b_cart_logistics_id

## API 仕様（新規設計）
- `GET /api/v1/bcart/shipping` — 既存：刷新する
  - Query: `status` (NOT_SHIPPED/SHIPPING_INSTRUCTED/SHIPPED/EXCLUDED, 省略時=未発送+発送指示), `partnerCode` (任意、部分一致)
  - Response: `BCartShippingInputResponse[]` (logisticsId 粒度)
- `PUT /api/v1/bcart/shipping` — 新規（一覧一括更新）: `List<BCartShippingUpdateRequest>` → 保存 + BCartOrder ステータス連動
- `PUT /api/v1/bcart/shipping/bulk-status` — 新規（選択行一括ステータス更新）: `{logisticsIds:Long[], status:BcartShipmentStatus}`
- 既存 `PUT /api/v1/bcart/shipping/{orderId}/status` は **破壊的変更**。現在フロントしか使っていないため削除してよい。

## UI 挙動（移植ポイント）
- 初期表示: 未発送+発送指示のみ表示
- 検索: 得意先コード（部分一致 or 完全一致）+ 出荷ステータス
- テーブル列: チェックボックス / 得意先コード・名 / LogisticsID・SMILE 伝票番号 / 届け先 / 商品コード:商品名:数量 / 送り状番号入力・出荷日入力 / 出荷ステータス / メモ入力・得意先連絡事項入力
- 一括更新ボタン（チェック行にステータスを一斉適用）
- 個別更新ボタン（現行の編集内容を全行保存）
- B-CART 出力済みの発送済み行はフィールド非活性化

## 権限
- 既存 `@PreAuthorize("isAuthenticated()")` を踏襲

## ドキュメントギャップ（Phase 7 で更新）
- `docs/detailed-design/DD_08_BCART連携.md` §2 は旧システム（Thymeleaf+Controller）ベース → 新システムの REST API + Next.js 実装へ書き換え
- `docs/00_system_overview.md` §4.2.3 Web 画面連携 → BCartController 記述に更新
- 新規追加推奨: `docs/screens/bcart-shipping-input/README.md`（スクリーン固有設計）
