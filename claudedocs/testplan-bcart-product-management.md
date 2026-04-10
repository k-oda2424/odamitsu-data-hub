# テスト計画: B-CART商品説明管理（Phase 2）

## 対象スコープ

- **バックエンド API**: `BCartProductController` — 商品一覧/詳細/説明更新/変更履歴
- **バッチ**: `bCartProductDescriptionUpdate` — 商品説明のB-CART API反映
- **フロントエンド**: B-CART商品一覧画面、商品詳細画面（タブ: 商品情報/セット一覧/変更履歴）

## 前提条件

- Phase 1（カテゴリマスタ + 基盤）が完了済み
- `b_cart_products`, `b_cart_product_sets`, `b_cart_change_history`, `b_cart_categories` テーブルにデータが存在
- 認証済みユーザーでアクセス

---

## 1. バックエンド単体テスト（JUnit 5 + Mockito）

テストクラス: `BCartProductDescriptionServiceTest`, `BCartProductDescriptionUpdateTaskletTest`

### 1-1. 商品説明更新サービス

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| UT-01 | Service | 商品説明の正常更新 | description, catchCopy, prependText を指定して更新 | DB更新成功、`b_cart_reflected=false` が設定される | 高 |
| UT-02 | Service | 変更履歴の自動記録 | 商品説明を更新 | `b_cart_change_history` に `target_type='PRODUCT'`, `change_type='DESCRIPTION'` のレコードが作成される | 高 |
| UT-03 | Service | 変更前スナップショットの保存 | 商品説明を更新 | `before_snapshot` に更新前の全フィールドがJSON形式で保存される | 高 |
| UT-04 | Service | before/after値の記録 | catchCopy を "旧コピー" → "新コピー" に変更 | `before_value="旧コピー"`, `after_value="新コピー"` が記録される | 中 |
| UT-05 | Service | 存在しない商品IDの更新 | 存在しないproductIdで説明更新を実行 | 適切な例外またはnull応答が返る | 高 |
| UT-06 | Service | description 65535文字制限 | 65536文字の description で更新 | バリデーションエラーが発生 | 中 |
| UT-07 | Service | description ちょうど65535文字 | 65535文字の description で更新 | 正常に保存される | 中 |
| UT-08 | Service | 全フィールドnullで更新 | 全説明フィールドをnullで送信 | nullで更新される（空更新を許容するか、バリデーションエラーか、仕様に従う） | 中 |
| UT-09 | Service | HTML含む説明文の保存 | `<div><p>商品説明</p></div>` を description に設定 | HTMLタグがそのまま保存される（サニタイズしない） | 中 |
| UT-10 | Service | 変更者IDの記録 | loginUserNo=5 のユーザーで更新 | `changed_by=5` が履歴に記録される | 中 |

### 1-2. バッチ: bCartProductDescriptionUpdate タスクレット

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| UT-11 | Batch | 未反映レコードの取得 | `b_cart_reflected=false` かつ `target_type='PRODUCT'` の履歴が3件存在 | 3件の対象商品が取得される | 高 |
| UT-12 | Batch | B-CART API正常送信 | PATCH `/api/v1/products/{id}` を `application/x-www-form-urlencoded` 形式で送信 | ステータス200、Content-Typeが `application/x-www-form-urlencoded` | 高 |
| UT-13 | Batch | 日本語フィールドのURLエンコード | description に日本語テキストを含む商品を送信 | UTF-8でURLエンコードされて送信される | 高 |
| UT-14 | Batch | 反映成功後のフラグ更新 | API送信成功 | `b_cart_change_history.b_cart_reflected=true`, `b_cart_reflected_at` にタイムスタンプが設定される | 高 |
| UT-15 | Batch | API失敗時の未反映維持 | B-CART APIが500エラーを返す | `b_cart_reflected=false` のまま、次回バッチで再試行可能 | 高 |
| UT-16 | Batch | レート制限: 250件区切り | 300件の未反映レコード | 250件送信後に5分待機、残り50件を送信 | 中 |
| UT-17 | Batch | 未反映レコード0件 | 対象レコードなし | 正常終了（`RepeatStatus.FINISHED`）、API呼び出しなし | 中 |
| UT-18 | Batch | 同一商品の複数変更履歴 | 同じproductIdに対して3件の未反映履歴が存在 | 最新の商品データで1回だけAPIを送信、3件すべて `b_cart_reflected=true` に更新 | 高 |
| UT-19 | Batch | form-urlencoded形式のフィールド送信 | description, catch_copy, prepend_text 等を送信 | B-CART APIのフィールド名（snake_case）で送信される | 高 |

---

## 2. API テスト（MockMvc / @WebMvcTest）

テストクラス: `BCartProductControllerTest`

### 2-1. GET /api/v1/bcart/products（商品一覧）

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| API-01 | 一覧 | パラメータなしで全件取得 | GET `/api/v1/bcart/products` | 200 OK、全商品リストが返る（カテゴリ名結合済み） | 高 |
| API-02 | 一覧 | 商品名で検索 | GET `/api/v1/bcart/products?name=ペーパータオル` | 200 OK、商品名に「ペーパータオル」を含む商品のみ返る | 高 |
| API-03 | 一覧 | カテゴリIDで絞り込み | GET `/api/v1/bcart/products?categoryId=129` | 200 OK、categoryId=129 の商品のみ返る | 高 |
| API-04 | 一覧 | 表示フラグで絞り込み | GET `/api/v1/bcart/products?flag=表示` | 200 OK、flag="表示" の商品のみ返る | 中 |
| API-05 | 一覧 | 複合検索 | GET `/api/v1/bcart/products?name=洗剤&categoryId=130&flag=表示` | 200 OK、全条件を満たす商品のみ返る | 中 |
| API-06 | 一覧 | 検索結果0件 | GET `/api/v1/bcart/products?name=存在しない商品名` | 200 OK、空配列 | 中 |
| API-07 | 一覧 | 未認証アクセス | Authorization ヘッダーなしでリクエスト | 401 または 403 | 高 |
| API-08 | 一覧 | レスポンスにセット数が含まれる | 商品にセットが3件紐付く | レスポンスの `setCount` が 3 | 中 |
| API-09 | 一覧 | レスポンスにカテゴリ名が含まれる | categoryId=129 の商品 | `categoryName="紙製品"` が返る | 中 |

### 2-2. GET /api/v1/bcart/products/{productId}（商品詳細）

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| API-10 | 詳細 | 正常取得 | GET `/api/v1/bcart/products/123` | 200 OK、商品情報 + 全セット + カテゴリ情報が返る | 高 |
| API-11 | 詳細 | セット情報が含まれる | 商品に2件のセットが紐付く | `productSets` に2件のセット情報（単価、仕入価格等）が返る | 高 |
| API-12 | 詳細 | カテゴリ情報が含まれる | categoryId=140, subCategoryId="141,142" の商品 | `categoryName`, `subCategoryNames` が返る | 中 |
| API-13 | 詳細 | 存在しない商品ID | GET `/api/v1/bcart/products/999999` | 404 Not Found | 高 |
| API-14 | 詳細 | 説明フィールドの全項目取得 | description, catchCopy, prependText, appendText, middleText, rvPrependText, rvAppendText, rvMiddleText が設定済み | 全フィールドがレスポンスに含まれる | 中 |
| API-15 | 詳細 | META情報の取得 | metaTitle, metaKeywords, metaDescription が設定済み | 全META情報がレスポンスに含まれる | 低 |

### 2-3. PUT /api/v1/bcart/products/{productId}/description（説明更新）

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| API-16 | 更新 | 正常更新 | PUT `/api/v1/bcart/products/123/description` + JSON body | 200 OK、更新後の商品情報が返る | 高 |
| API-17 | 更新 | description のみ更新 | description だけ変更、他フィールドは未送信 | description のみ更新、他フィールドは変更なし | 高 |
| API-18 | 更新 | 全説明フィールド一括更新 | description, catchCopy, prependText, appendText, middleText, rvPrependText, rvAppendText, rvMiddleText を同時更新 | 全フィールドが更新される | 高 |
| API-19 | 更新 | 変更履歴が作成される | PUT で description を更新 | レスポンス後に GET `/api/v1/bcart/products/123/history` で変更履歴が確認できる | 高 |
| API-20 | 更新 | 存在しない商品ID | PUT `/api/v1/bcart/products/999999/description` | 404 Not Found | 高 |
| API-21 | 更新 | description 65536文字超過 | 65536文字の description を送信 | 400 Bad Request、バリデーションエラーメッセージ | 中 |
| API-22 | 更新 | 未認証アクセス | Authorization ヘッダーなし | 401 または 403 | 高 |
| API-23 | 更新 | b_cart_reflected が false に設定 | PUT で更新後、DB を確認 | `b_cart_products` の該当レコードで反映待ち状態になる | 高 |
| API-24 | 更新 | 空文字列で更新 | description を空文字列で送信 | 正常に保存される（空文字許可） | 中 |

### 2-4. GET /api/v1/bcart/products/{productId}/history（変更履歴）

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| API-25 | 履歴 | 正常取得 | GET `/api/v1/bcart/products/123/history` | 200 OK、変更履歴リスト（降順）が返る | 高 |
| API-26 | 履歴 | 履歴0件 | 変更履歴なしの商品 | 200 OK、空配列 | 中 |
| API-27 | 履歴 | 履歴の内容確認 | 説明更新後に履歴取得 | `changeType`, `fieldName`, `beforeValue`, `afterValue`, `changedBy`, `changedAt`, `bCartReflected` が含まれる | 高 |
| API-28 | 履歴 | 反映済み/未反映の区別 | 反映済み+未反映の履歴がある | `bCartReflected` フラグで区別できる | 中 |
| API-29 | 履歴 | 存在しない商品ID | GET `/api/v1/bcart/products/999999/history` | 200 OK、空配列（エラーではない） | 中 |

---

## 3. E2Eテスト（Playwright）

テストファイル: `e2e/bcart-products.spec.ts`

> E2Eテストは API をモックして実行する。`page.route()` で `/api/v1/bcart/products*` 等をインターセプトし、固定レスポンスを返す。

### 3-1. 商品一覧画面 `/bcart-products`

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| E2E-01 | 一覧表示 | 初期表示で検索しない | `/bcart-products` に遷移 | テーブルが空（「検索してください」表示）、検索フォームは表示 | 高 |
| E2E-02 | 一覧表示 | 検索実行で商品一覧表示 | 検索ボタンをクリック | テーブルに商品名、カテゴリ名、セット数、基本単価、最終同期日が表示される | 高 |
| E2E-03 | 検索 | 商品名で検索 | 商品名フィールドに「ペーパータオル」を入力して検索 | APIに `name=ペーパータオル` パラメータが送信される | 高 |
| E2E-04 | 検索 | カテゴリで絞り込み | カテゴリセレクトから「紙製品」を選択して検索 | APIに `categoryId=129` パラメータが送信される | 高 |
| E2E-05 | 検索 | 表示/非表示フラグで絞り込み | フラグセレクトから「表示」を選択して検索 | APIに `flag=表示` パラメータが送信される | 中 |
| E2E-06 | 検索 | 検索結果0件 | 該当なしの条件で検索 | 「該当するデータがありません」メッセージ表示 | 中 |
| E2E-07 | 遷移 | 商品行クリックで詳細へ | テーブルの商品行をクリック | `/bcart-products/{productId}` に遷移 | 高 |
| E2E-08 | 表示 | 販売商品紐付き状態の表示 | 紐付き済み/未紐付きの商品が混在 | Badge等で紐付き状態が視覚的に区別できる | 低 |

### 3-2. 商品詳細画面 `/bcart-products/{productId}` - 商品情報タブ

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| E2E-09 | 詳細表示 | 商品情報タブが初期表示 | 詳細画面に遷移 | 商品情報タブがアクティブ、商品名・キャッチコピー・カテゴリ・説明文が表示 | 高 |
| E2E-10 | 詳細表示 | 全説明フィールドの表示 | description, catchCopy, prependText, appendText, middleText, rvPrependText, rvAppendText, rvMiddleText が設定済み | 全フィールドがフォームに表示される | 高 |
| E2E-11 | 詳細表示 | META情報の表示 | metaTitle, metaKeywords, metaDescription が設定済み | SEO情報セクションに表示される | 中 |
| E2E-12 | 編集 | 説明文を編集して保存 | description を変更 → 「保存」ボタンクリック | PUT `/api/v1/bcart/products/{id}/description` が呼ばれ、成功トースト表示 | 高 |
| E2E-13 | 編集 | キャッチコピー編集 | catchCopy を変更して保存 | 正常に更新される | 中 |
| E2E-14 | 編集 | フリースペース（PC/レスポンシブ）編集 | prependText, rvPrependText を変更して保存 | 正常に更新される | 中 |
| E2E-15 | 編集 | 保存失敗時のエラー表示 | API が 500 を返す | エラートースト表示、フォームの入力値は維持 | 高 |
| E2E-16 | 編集 | 未変更時の保存ボタン | フォームを変更しない | 保存ボタンが非活性（または保存してもAPIは呼ばれない） | 低 |

### 3-3. 商品詳細画面 - セット一覧タブ

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| E2E-17 | セット | タブ切替でセット一覧表示 | 「価格・セット一覧」タブをクリック | セットごとの単価、仕入価格が一覧表示 | 高 |
| E2E-18 | セット | セットが0件の商品 | セット未登録の商品 | 「セットが登録されていません」メッセージ | 中 |
| E2E-19 | セット | 複数セットの表示 | 3件のセットが紐付く商品 | 3行のセット情報が表示される | 中 |

### 3-4. 商品詳細画面 - 変更履歴タブ

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| E2E-20 | 履歴 | タブ切替で履歴一覧表示 | 「変更履歴」タブをクリック | 変更日時、変更者、変更種別、変更フィールド、before/after が一覧表示 | 高 |
| E2E-21 | 履歴 | B-CART反映状態の表示 | 反映済み+未反映の履歴がある | 反映済み/未反映がBadge等で区別表示される | 中 |
| E2E-22 | 履歴 | 履歴が0件 | 変更履歴なしの商品 | 「変更履歴がありません」メッセージ | 中 |
| E2E-23 | 履歴 | 降順表示の確認 | 複数の変更履歴がある | 最新の変更が一番上に表示される | 中 |
| E2E-24 | 履歴 | 説明更新後に履歴が増加 | 商品情報タブで保存 → 履歴タブに切替 | 今回の変更が履歴一覧に追加されている | 高 |

---

## 4. エッジケース / 異常系

| ID | カテゴリ | テスト名 | 手順 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| EDGE-01 | 楽観的ロック | 同時編集の検出 | ユーザーAが編集中にユーザーBが同じ商品を更新 → ユーザーAが保存 | 409 Conflict エラー、再読み込みを促すメッセージ | 高 |
| EDGE-02 | B-CART同期 | 同期中のユーザー編集 | バッチ同期中にユーザーが同じ商品を編集 | ユーザーの編集が優先、`b_cart_reflected=false` で再反映対象になる | 中 |
| EDGE-03 | Batch | B-CART API ダウン時 | バッチ実行中にB-CART APIが応答しない | タイムアウト後、`b_cart_reflected=false` のまま残る。エラーログ出力 | 高 |
| EDGE-04 | Batch | 部分的なAPI失敗 | 10件中3件目でAPI失敗 | 1-2件目は `reflected=true`、3件目は `reflected=false`、4件目以降も処理を継続 | 高 |
| EDGE-05 | Data | HTMLインジェクション | `<script>alert('xss')</script>` を description に入力 | そのまま保存される（B-CART側の表示責任）。フロントエンドでは `dangerouslySetInnerHTML` を使用しない | 中 |
| EDGE-06 | Data | 超長文の説明 | 65535文字ギリギリの description を保存 | 正常に保存・表示される | 中 |
| EDGE-07 | Batch | 重複商品IDの処理 | 同一productIdに対して複数の未反映履歴 | 商品ごとに1回のAPI呼び出しに集約、全関連履歴を反映済みに更新 | 高 |

---

## 5. テスト優先度サマリ

| 優先度 | 件数 | 説明 |
|--------|------|------|
| 高 | 30 | MVP必須。説明の保存/履歴/バッチ反映の基本フロー |
| 中 | 24 | 主要な検索パターン、UI表示の正確性 |
| 低 | 4 | UXの最適化、細かい表示調整 |

## 6. テスト実行方針

### バックエンド単体テスト
```bash
cd backend && ./gradlew test --tests "jp.co.oda32.domain.service.bcart.BCartProduct*"
cd backend && ./gradlew test --tests "jp.co.oda32.batch.bcart.BCartProductDescription*"
cd backend && ./gradlew test --tests "jp.co.oda32.api.bcart.BCartProductController*"
```

### E2Eテスト
```bash
cd frontend && npx playwright test e2e/bcart-products.spec.ts
```

### モックデータ設計

E2Eテストでは以下のモックレスポンスを `page.route()` で設定する:

```typescript
// GET /api/v1/bcart/products — 商品一覧モック
const mockProducts = [
  {
    id: 101, name: 'エリエール ペーパータオル スマートタイプ',
    catchCopy: '業務用ペーパータオル', categoryId: 129, categoryName: '紙製品',
    flag: '表示', setCount: 2, updatedAt: '2026-04-01T00:00:00'
  },
  {
    id: 102, name: 'キレイキレイ 薬用泡ハンドソープ',
    catchCopy: '殺菌・消毒', categoryId: 131, categoryName: '衛生・感染対策',
    flag: '表示', setCount: 3, updatedAt: '2026-04-02T00:00:00'
  }
];

// GET /api/v1/bcart/products/101 — 商品詳細モック
const mockProductDetail = {
  id: 101, name: 'エリエール ペーパータオル スマートタイプ',
  catchCopy: '業務用ペーパータオル', categoryId: 129, categoryName: '紙製品',
  description: '<p>200枚入り中判サイズ</p>',
  prependText: '', appendText: '', middleText: '',
  rvPrependText: '', rvAppendText: '', rvMiddleText: '',
  metaTitle: 'ペーパータオル', metaKeywords: 'ペーパータオル,業務用',
  metaDescription: '業務用ペーパータオル200枚入り',
  flag: '表示',
  productSets: [
    { id: 1001, name: '1ケース（30パック）', unitPrice: 3500, purchasePrice: 2800 },
    { id: 1002, name: '5ケースセット', unitPrice: 16000, purchasePrice: 13000 }
  ]
};

// GET /api/v1/bcart/products/101/history — 変更履歴モック
const mockHistory = [
  {
    id: 1, changeType: 'DESCRIPTION', fieldName: 'description',
    beforeValue: '旧説明文', afterValue: '<p>200枚入り中判サイズ</p>',
    changedBy: 1, changedAt: '2026-04-05T10:30:00', bCartReflected: true
  },
  {
    id: 2, changeType: 'DESCRIPTION', fieldName: 'catchCopy',
    beforeValue: '旧キャッチコピー', afterValue: '業務用ペーパータオル',
    changedBy: 1, changedAt: '2026-04-03T09:00:00', bCartReflected: false
  }
];
```

---

## 7. 合否判定基準

- 高優先度テストケース: 100% パス必須
- 中優先度テストケース: 90% 以上パス
- 低優先度テストケース: 既知の問題としてチケット化可
- バッチのレート制限テスト（UT-16）: 実行時間の都合で手動確認でも可
