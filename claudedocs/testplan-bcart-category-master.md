# B-CARTカテゴリマスタ Phase 1 テスト計画

## 対象スコープ

- DB: `b_cart_categories`, `b_cart_change_history`
- Backend: `BCartCategories` Entity / Repository / Service / Controller (CRUD + tree)
- Batch: カテゴリ同期 (GET B-CART API -> DB), カテゴリ反映 (DB -> PATCH B-CART API)
- Frontend: カテゴリマスタ画面 (`/bcart-categories`) ツリー表示 + 詳細編集フォーム

## API エンドポイント一覧

| Method | Path | 概要 |
|--------|------|------|
| GET | `/api/v1/bcart/categories` | カテゴリツリー一覧 |
| GET | `/api/v1/bcart/categories/{id}` | カテゴリ詳細 |
| PUT | `/api/v1/bcart/categories/{id}` | カテゴリ更新 |
| POST | `/api/v1/bcart/categories` | カテゴリ新規作成 |
| PUT | `/api/v1/bcart/categories/{id}/priority` | 表示順変更 |
| GET | `/api/v1/bcart/categories/{id}/products` | 所属商品一覧 |

---

## 1. E2E テスト (Playwright)

ファイル: `frontend/e2e/bcart-categories.spec.ts`

パターン: `mockAllApis(page)` + `loginAndGoto(page, '/bcart-categories')`

### 必要モックデータ (`mock-api.ts` に追加)

```typescript
export const MOCK_BCART_CATEGORY_TREE = [
  {
    id: 129, name: '紙製品', parentCategoryId: null, priority: 1, flag: 1,
    bCartReflected: true, productCount: 5,
    children: [
      { id: 201, name: 'ペーパータオル・ハンドタオル', parentCategoryId: 129, priority: 1, flag: 1, bCartReflected: true, productCount: 3, children: [] },
      { id: 202, name: 'トイレットペーパー', parentCategoryId: 129, priority: 2, flag: 1, bCartReflected: false, productCount: 2, children: [] },
    ],
  },
  {
    id: 130, name: '洗剤・清掃用品', parentCategoryId: null, priority: 2, flag: 1,
    bCartReflected: true, productCount: 3,
    children: [
      { id: 203, name: '台所用洗剤', parentCategoryId: 130, priority: 1, flag: 1, bCartReflected: true, productCount: 1, children: [] },
      { id: 204, name: 'トイレ・バス洗剤', parentCategoryId: 130, priority: 2, flag: 0, bCartReflected: true, productCount: 0, children: [] },
    ],
  },
]

export const MOCK_BCART_CATEGORY_DETAIL = {
  id: 201, name: 'ペーパータオル・ハンドタオル', description: 'PC用説明文',
  rvDescription: 'レスポンシブ用説明文', parentCategoryId: 129,
  metaTitle: 'ペーパータオル', metaKeywords: 'ペーパータオル,ハンドタオル',
  metaDescription: 'ペーパータオル・ハンドタオルのカテゴリ',
  priority: 1, flag: 1, bCartReflected: true,
}

export const MOCK_BCART_CATEGORY_PRODUCTS = [
  { productId: 1001, name: 'エリエール ペーパータオル', categoryId: 201, flag: 1 },
  { productId: 1002, name: 'スコッティ ハンドタオル', categoryId: 201, flag: 1 },
]
```

### テストケース

| ID | カテゴリ | テスト名 | ステップ | 期待結果 | 優先度 |
|----|---------|---------|---------|---------|-------|
| E01 | 初期表示 | ページヘッダーとアクションボタンが表示される | `/bcart-categories` に遷移 | 「B-CARTカテゴリマスタ」見出し、「同期」「新規作成」ボタンが表示 | High |
| E02 | ツリー表示 | 親カテゴリがツリーに表示される | ページ読み込み | 「紙製品」「洗剤・清掃用品」が左ペインに表示 | High |
| E03 | ツリー表示 | 親カテゴリを展開すると子カテゴリが表示される | 「紙製品」をクリックして展開 | 「ペーパータオル・ハンドタオル」「トイレットペーパー」が表示 | High |
| E04 | ツリー表示 | 子カテゴリ数バッジが表示される | ツリー表示を確認 | 「紙製品 (5)」「洗剤・清掃用品 (3)」のように商品数が表示 | Medium |
| E05 | ツリー表示 | 非表示カテゴリにアイコンが付く | ツリーを展開して「トイレ・バス洗剤」(flag=0) を確認 | 非表示を示すアイコンまたはスタイルが適用されている | Medium |
| E06 | ツリー表示 | 未反映カテゴリにインジケータが付く | ツリーを展開して「トイレットペーパー」(bCartReflected=false) を確認 | 未反映を示すバッジまたはドットが表示 | Medium |
| E07 | 詳細表示 | カテゴリ選択で右ペインに詳細が表示される | 「ペーパータオル・ハンドタオル」をクリック | 右ペインにカテゴリ名、親カテゴリ、優先度、状態、説明、META情報が表示 | High |
| E08 | 詳細表示 | 未選択時は詳細ペインにガイドメッセージが表示される | ページ初期表示 | 「カテゴリを選択してください」等のメッセージが表示 | Medium |
| E09 | 詳細表示 | 所属商品数と「商品一覧を見る」リンクが表示される | カテゴリを選択 | 「所属商品数: 3件」と商品一覧リンクが表示 | Medium |
| E10 | 編集 | カテゴリ名を変更して保存できる | 1. カテゴリを選択 2. カテゴリ名を変更 3.「保存」ボタンをクリック | PUT APIが呼ばれ、成功トーストが表示 | High |
| E11 | 編集 | 説明文（PC/レスポンシブ）を編集して保存できる | 1. カテゴリを選択 2. PC説明とレスポンシブ説明を編集 3.「保存」 | PUT APIにdescription, rvDescriptionが送信される | High |
| E12 | 編集 | META情報を編集して保存できる | 1. カテゴリを選択 2. title/keywords/descriptionを編集 3.「保存」 | PUT APIにmetaTitle, metaKeywords, metaDescriptionが送信される | Medium |
| E13 | 編集 | 表示/非表示の切り替えができる | 1. カテゴリを選択 2. 状態を「非表示」に切り替え 3.「保存」 | PUT APIにflag=0が送信される | High |
| E14 | 編集 | 優先度を変更して保存できる | 1. カテゴリを選択 2. 表示優先度を変更 3.「保存」 | PUT APIにpriority値が送信される | Medium |
| E15 | 編集 | 保存成功後にツリーが更新される | カテゴリ名を変更して保存 | ツリー上のカテゴリ名が変更後の値に更新（invalidateQueries） | High |
| E16 | 新規作成 | 新規作成ダイアログが開く | 「新規作成」ボタンをクリック | ダイアログにカテゴリ名と親カテゴリ選択が表示 | High |
| E17 | 新規作成 | 親カテゴリを選択してカテゴリを作成できる | 1. ダイアログでカテゴリ名入力 2. 親カテゴリ選択 3.「作成」 | POST APIが呼ばれ、成功トーストが表示、ツリーが更新 | High |
| E18 | 新規作成 | カテゴリ名が空の場合はバリデーションエラー | 1. カテゴリ名を空にして「作成」 | バリデーションエラーメッセージが表示、APIは呼ばれない | Medium |
| E19 | 同期 | 同期ボタンでバッチが実行される | 「同期」ボタンをクリック | バッチ実行APIが呼ばれ、実行中表示後にツリーが再取得される | High |
| E20 | エラー | API取得エラー時にエラーメッセージが表示される | カテゴリ一覧APIが500を返すモックに差し替え | エラーメッセージまたはエラートーストが表示 | Medium |
| E21 | エラー | 保存失敗時にエラートーストが表示される | PUT APIが400を返すモックに差し替え | エラートースト（sonner）が表示 | Medium |

### mock-api.ts への追加ルート

```typescript
// ---- B-CART Categories ----
await page.route(
  (url) => /^\/api\/v1\/bcart\/categories\/\d+\/products$/.test(url.pathname),
  async (route) => { await json(route, MOCK_BCART_CATEGORY_PRODUCTS) },
)

await page.route(
  (url) => /^\/api\/v1\/bcart\/categories\/\d+\/priority$/.test(url.pathname),
  async (route) => { await json(route, { message: '更新しました' }) },
)

await page.route(
  (url) => /^\/api\/v1\/bcart\/categories\/\d+$/.test(url.pathname),
  async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await json(route, MOCK_BCART_CATEGORY_DETAIL)
    } else if (method === 'PUT') {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { ...MOCK_BCART_CATEGORY_DETAIL, ...body })
    } else { await route.fallback() }
  },
)

await page.route(
  (url) => url.pathname === '/api/v1/bcart/categories',
  async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await json(route, MOCK_BCART_CATEGORY_TREE)
    } else if (method === 'POST') {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { id: 999, ...body })
    } else { await route.fallback() }
  },
)
```

---

## 2. API 統合テスト

ファイル: `backend/src/test/java/jp/co/oda32/api/bcart/BCartCategoryControllerTest.java`

パターン: `@WebMvcTest` + `@MockBean` Service層 (既存の `AuthControllerTest` パターンに準拠)

| ID | カテゴリ | テスト名 | リクエスト | 期待結果 | 優先度 |
|----|---------|---------|-----------|---------|-------|
| A01 | GET ツリー | カテゴリツリーが正しく返却される | `GET /api/v1/bcart/categories` | 200, 親カテゴリにchildren配列がネスト | High |
| A02 | GET ツリー | カテゴリが0件のとき空配列が返る | `GET /api/v1/bcart/categories` (0件) | 200, `[]` | Low |
| A03 | GET 詳細 | カテゴリ詳細が返却される | `GET /api/v1/bcart/categories/201` | 200, 全フィールドが返却 | High |
| A04 | GET 詳細 | 存在しないカテゴリIDで404 | `GET /api/v1/bcart/categories/99999` | 404 | High |
| A05 | PUT 更新 | カテゴリ情報を更新できる | `PUT /api/v1/bcart/categories/201` + JSON body | 200, 更新後データ返却, `bCartReflected=false` | High |
| A06 | PUT 更新 | 必須フィールド(name)がnullで400 | `PUT /api/v1/bcart/categories/201` name=null | 400, バリデーションエラー | High |
| A07 | PUT 更新 | name が256文字以上で400 | `PUT /api/v1/bcart/categories/201` name=256chars | 400 | Medium |
| A08 | PUT 更新 | 存在しないIDで404 | `PUT /api/v1/bcart/categories/99999` | 404 | Medium |
| A09 | PUT 更新 | 更新時に変更履歴が記録される | `PUT /api/v1/bcart/categories/201` | `b_cart_change_history` に1件INSERT (target_type=CATEGORY, before_snapshot含む) | High |
| A10 | POST 作成 | カテゴリを新規作成できる | `POST /api/v1/bcart/categories` + JSON body | 200/201, 作成データ返却 | High |
| A11 | POST 作成 | 親カテゴリIDが不正な場合400 | `POST /api/v1/bcart/categories` parentCategoryId=99999 | 400, エラーメッセージ | Medium |
| A12 | POST 作成 | name が空文字で400 | `POST /api/v1/bcart/categories` name="" | 400 | Medium |
| A13 | PUT priority | 表示順を更新できる | `PUT /api/v1/bcart/categories/201/priority` priority=5 | 200 | Medium |
| A14 | GET products | 所属商品一覧が返却される | `GET /api/v1/bcart/categories/201/products` | 200, 商品リスト | Medium |
| A15 | GET products | 商品0件のカテゴリで空配列 | `GET /api/v1/bcart/categories/204/products` | 200, `[]` | Low |

---

## 3. バックエンド単体テスト

### 3-1. BCartCategoryService

ファイル: `backend/src/test/java/jp/co/oda32/domain/service/bcart/BCartCategoryServiceTest.java`

パターン: `@ExtendWith(MockitoExtension.class)` + `@Mock` Repository + `@InjectMocks` Service

| ID | カテゴリ | テスト名 | 処理 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| S01 | ツリー構築 | 全カテゴリをツリー構造に変換できる | フラットな親子データから `getCategoryTree()` 呼び出し | 親カテゴリのchildren に子カテゴリがネスト。親11件, 子38件の構造 | High |
| S02 | ツリー構築 | 親カテゴリのみの場合childrenが空配列 | 子なし親カテゴリデータで `getCategoryTree()` | `children: []` | Medium |
| S03 | ツリー構築 | priorityの昇順でソートされる | priority異なる複数カテゴリで `getCategoryTree()` | 親カテゴリ同士、子カテゴリ同士がpriority昇順 | High |
| S04 | 詳細取得 | IDで詳細を取得できる | `getCategoryDetail(201)` | 全フィールドが返却される | High |
| S05 | 詳細取得 | 存在しないIDでEntityNotFoundExceptionがthrow | `getCategoryDetail(99999)` | 例外がthrowされる | High |
| S06 | 更新 | カテゴリ情報を更新しbCartReflectedがfalseになる | `updateCategory(201, request)` | DBが更新され、`bCartReflected=false`にセット | High |
| S07 | 更新 | 更新前のスナップショットがb_cart_change_historyに保存される | `updateCategory(201, request)` | changeHistory.save() が呼ばれ、target_type=CATEGORY, before_snapshot にJSON | High |
| S08 | 更新 | 変更がないフィールドは履歴に含めない | 同じ値で `updateCategory()` | before_value == after_value のフィールドは記録しない（or 全体スナップショットのみ） | Medium |
| S09 | 作成 | 新規カテゴリを作成できる | `createCategory(request)` name="新カテゴリ", parentCategoryId=129 | repository.save() が呼ばれ、新IDで保存 | High |
| S10 | 作成 | 存在しない親カテゴリIDで例外 | `createCategory(request)` parentCategoryId=99999 | IllegalArgumentException (or EntityNotFoundException) | Medium |
| S11 | 作成 | parentCategoryId=nullで親カテゴリとして作成 | `createCategory(request)` parentCategoryId=null | parentCategoryIdがnullで保存される | Medium |
| S12 | priority | 表示順の更新ができる | `updatePriority(201, 5)` | priorityが5に更新される | Medium |

### 3-2. BCartCategorySyncTasklet (同期バッチ)

ファイル: `backend/src/test/java/jp/co/oda32/batch/bcart/BCartCategorySyncTaskletTest.java`

パターン: `@ExtendWith(MockitoExtension.class)` + `@Mock` RestTemplate/WebClient + Repository

| ID | カテゴリ | テスト名 | 処理 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| B01 | 同期正常 | B-CART APIから取得した全カテゴリがDBにUPSERTされる | APIが49件返却 | repository.saveAll() が49件で呼ばれる | High |
| B02 | 同期正常 | 新規カテゴリがINSERTされる | APIに新ID=999のカテゴリ、DB上は存在しない | findById(999)==empty, save()が呼ばれる | High |
| B03 | 同期正常 | 既存カテゴリが更新される | APIのid=129のnameが変更されている | 既存レコードのnameが更新される | High |
| B04 | 同期正常 | API側に存在しないカテゴリのflagが0に更新される | DB上にid=500あり、API結果に500なし | id=500のflag=0に更新 | High |
| B05 | 同期正常 | parent_category_idが正しくセットされる | 親子関係のあるカテゴリデータ | 子カテゴリのparentCategoryIdが親IDに一致 | Medium |
| B06 | 同期エラー | B-CART APIが5xxを返した場合にステップが失敗する | APIが500返却 | RepeatStatus.FINISHED ではなく例外がthrow (or FAILED) | High |
| B07 | 同期エラー | B-CART APIがタイムアウトした場合のハンドリング | API呼び出しがタイムアウト | 適切な例外がthrow、DBは変更されない | Medium |
| B08 | 同期冪等性 | 同じデータで2回実行してもデータが重複しない | 同一データで2回execute() | レコード数が変わらない（UPSERT） | High |

### 3-3. BCartCategoryUpdateTasklet (反映バッチ)

ファイル: `backend/src/test/java/jp/co/oda32/batch/bcart/BCartCategoryUpdateTaskletTest.java`

| ID | カテゴリ | テスト名 | 処理 | 期待結果 | 優先度 |
|----|---------|---------|------|---------|-------|
| R01 | 反映正常 | 未反映カテゴリがB-CART APIにPATCHされる | bCartReflected=false のカテゴリが2件 | PATCH APIが2回呼ばれる | High |
| R02 | 反映正常 | 反映成功後にbCartReflectedがtrueに更新される | PATCH API成功 | bCartReflected=trueに更新 | High |
| R03 | 反映正常 | 未反映カテゴリが0件の場合はAPIを呼ばない | bCartReflected=false が0件 | PATCH APIは0回呼ばれる、正常終了 | Medium |
| R04 | 反映正常 | リクエストがform-urlencoded形式で送信される | PATCH API呼び出し時のContent-Typeを検証 | `application/x-www-form-urlencoded` | High |
| R05 | 反映正常 | 日本語がUTF-8でURLエンコードされる | カテゴリ名に日本語を含むデータ | URLエンコードされた日本語が送信される | Medium |
| R06 | 反映エラー | PATCH APIが4xxを返した場合、そのカテゴリは未反映のまま | 1件目成功、2件目で400 | 1件目はtrue、2件目はfalseのまま（部分成功） | High |
| R07 | 反映エラー | PATCH APIが5xxを返した場合のリトライ/エラーハンドリング | APIが500返却 | 適切なログ出力、bCartReflected=falseのまま | Medium |
| R08 | レート制限 | 250件以上の場合にレート制限が適用される | 未反映カテゴリ300件 | 250件目以降に待機処理（設計上はカテゴリ49件で発生しないが実装確認） | Low |

---

## 4. テスト実行手順

### E2E テスト

```bash
cd frontend && npx playwright test e2e/bcart-categories.spec.ts
```

### バックエンド単体テスト

```bash
cd backend && ./gradlew test --tests "jp.co.oda32.domain.service.bcart.*"
cd backend && ./gradlew test --tests "jp.co.oda32.batch.bcart.*"
cd backend && ./gradlew test --tests "jp.co.oda32.api.bcart.*"
```

---

## 5. テストカバレッジ目標

| レイヤー | 対象クラス | 目標カバレッジ |
|---------|-----------|-------------|
| Service | `BCartCategoryService` | 90%+ |
| Batch | `BCartCategorySyncTasklet` | 85%+ |
| Batch | `BCartCategoryUpdateTasklet` | 85%+ |
| Controller | `BCartCategoryController` | 80%+ (統合テスト) |
| E2E | カテゴリマスタ画面 | 主要ユーザーフロー100% |

---

## 6. テスト優先順位

Phase 1 実装と並行して以下の順で作成:

1. **Service 単体テスト** (S01-S12) -- ツリー構築ロジックが核心のため最優先
2. **同期バッチテスト** (B01-B08) -- 外部API連携の正確性担保
3. **API 統合テスト** (A01-A15) -- エンドポイントの動作保証
4. **E2E テスト** (E01-E21) -- UI完成後に実装
5. **反映バッチテスト** (R01-R08) -- form-urlencoded送信の正確性担保
