# 連携ファイル設定画面 機能仕様書

## 1. 概要

ショップごとのSMILE/B-CART連携ファイルパスを管理する画面。`m_shop_linked_file` テーブルの閲覧・編集を行う。

## 2. データモデル

### m_shop_linked_file
| カラム | 型 | 説明 |
|--------|-----|------|
| shop_no | Integer (PK) | ショップ番号 |
| smile_order_input_file_name | String | SMILE注文入力ファイル |
| smile_purchase_file_name | String | SMILE仕入ファイル |
| smile_order_output_file_name | String | SMILE注文出力ファイル |
| smile_partner_output_file_name | String | SMILE得意先出力ファイル |
| smile_destination_output_file_name | String | SMILE納品先出力ファイル |
| smile_goods_import_file_name | String | SMILE商品マスタCSVファイル |
| b_cart_logistics_import_file_name | String | B-CART出荷取込ファイル |
| invoice_file_path | String | 請求ファイルパス |

## 3. API設計

### 一覧取得
```
GET /api/v1/masters/shop-linked-files
```
レスポンス: `List<ShopLinkedFileResponse>`

### 更新
```
PUT /api/v1/masters/shop-linked-files/{shopNo}
```
リクエスト: `ShopLinkedFileUpdateRequest`（全ファイルパスフィールド）

## 4. 画面仕様

**パス**: `/masters/linked-files`

- ショップごとにカード形式で表示
- 各カードにショップ名 + ファイルパス一覧
- 各フィールドはインライン編集可能（テキスト入力）
- 「保存」ボタンで PUT API 呼び出し

## 5. 実装ファイル

### バックエンド
| ファイル | 変更 |
|---------|------|
| `ShopLinkedFileResponse.java` | **新規** DTO |
| `ShopLinkedFileUpdateRequest.java` | **新規** リクエストDTO |
| `MasterController.java` | GET/PUT エンドポイント追加 |
| `MShopLinkedFileService.java` | update メソッド追加 |

### フロントエンド
| ファイル | 変更 |
|---------|------|
| `app/(authenticated)/masters/linked-files/page.tsx` | **新規** ルーティング |
| `components/pages/master/linked-files.tsx` | **新規** ページコンポーネント |
| `Sidebar.tsx` | メニュー追加 |
