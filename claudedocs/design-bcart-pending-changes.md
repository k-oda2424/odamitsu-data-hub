# B-CART商品 変更点一覧・一括反映機能 設計書

作成日: 2026-05-01
対象: B-CART商品マスタ管理 Phase 3-A（価格・配送サイズの編集 → 反映）

---

## 1. 背景 / 課題

### 現状
- B-CART商品マスタは `bCartProductsImport` で取込まれ `b_cart_products` / `b_cart_product_sets` に保存されている。
- 商品説明（description, catch_copy 等）の編集 → B-CART反映は Phase 2 で完了済み（`bCartProductDescriptionUpdate` バッチ + `b_cart_change_history`）。
- **価格 (`unit_price`) と配送サイズ (`shipping_size`) の B-CART 反映機能が未実装。**
  - `BCartGoodsPriceTableUpdateTasklet` は空スタブ（execute 内に return FINISHED のみ）。
  - 詳細画面 `/bcart/products/{id}` の「セット一覧」タブは display-only。
- 配送サイズは送料設定のために最近追加された属性で、B-CARTで設定 → 同期で取込はできるが、**逆方向（本システムから B-CART へ反映）はできない**。

### 解決したいこと
1. B-CART 商品セット単位の `unit_price` と `shipping_size` を本システムの UI で編集可能にする。
2. 編集された商品セットを「変更点一覧」画面で **before / after の差分付き** で一覧表示する。
3. 一覧画面から **選択した変更を一括で B-CART へ PATCH 反映** できるようにする。
4. 反映前の値を `b_cart_change_history` に記録してロールバック可能にする（既存パターン踏襲）。

### スコープ外（Phase 3-B 以降）
- `group_price` / `special_price` / `volume_discount`（JSON 構造で複雑度高）
- `purchase_price`（社内データのため B-CART 反映不要）
- 価格変更予定機能（`b_cart_scheduled_change`）
- 販売商品マスタ（`m_sales_goods`）から B-CART への自動反映バッチ（別タスク予定）

---

## 2. 要件

### 機能要件
| # | 要件 | 優先度 |
|---|------|--------|
| F1 | B-CART商品詳細画面「セット一覧」タブで `unit_price` と `shipping_size` をインライン編集 | 必須 |
| F2 | 編集保存時に変更履歴 (`b_cart_change_history`) へ before/after を記録、`b_cart_price_reflected=false` に更新 | 必須 |
| F3 | 「変更点一覧」画面 `/bcart/pending-changes` で未反映の差分をテーブル表示（before / after の両方を視覚的に対比） | 必須 |
| F4 | 一覧で変更行を選択（チェックボックス） → 「選択を反映」ボタンで該当セットを PATCH | 必須 |
| F5 | 「全件反映」ボタンで全未反映を一括反映 | 必須 |
| F6 | 反映成功時は履歴行を `b_cart_reflected=true` に更新、`b_cart_product_sets.b_cart_price_reflected=true` に戻す | 必須 |
| F7 | 反映失敗時は対象を未反映のまま残し、UI にエラー件数を表示 | 必須 |
| F8 | サイドバー「B-CART」グループに「変更点一覧」リンクを追加（未反映件数バッジ付き） | 高 |

### 非機能要件
- B-CART API レート制限: 300 req / 5分。**同期 API では sleep を行わない**（Tomcat スレッド枯渇防止）。1 回の反映は **最大 200 件まで** に制限し、超過時は 400 BAD_REQUEST を返してユーザーに分割実行を促す。
- 一括反映は **同期エンドポイント**（バッチ起動ではない）。理由: 想定件数（数十件程度）で完結し、画面でリアルタイムに進捗・成否を確認できる方が UX 良い。レート制限到達時は B-CART API が 429 を返すので、当該行は失敗としてカウント・表示しユーザーに再試行を委ねる。
- 編集保存はトランザクション境界内で履歴と本テーブルを同時更新（不整合防止）。`@Transactional` は `public` メソッドに付与しないと CGLIB プロキシが効かない点を実装時に厳守する。
- 反映処理の DB 更新（履歴 + 本テーブル）も `public @Transactional` メソッドにまとめ、PATCH 成功 → DB 更新失敗時にロールバックを保証する。
- 同時編集（同じセットを2人が編集）は楽観的ロック非対応（運用上の制約として明示。将来必要なら `updated_at` チェック追加）。
- 反映ループの競合回避: aggregate 時に対象 `b_cart_change_history.id` のリストを保持し、UPDATE は `WHERE id IN (...)` 限定で実行する。"未反映条件" だけで UPDATE するとループ実行中に追加された新規編集を誤って `reflected=true` にマークしてしまうため。

---

## 3. 制約

### 技術的制約
- B-CART API: `PATCH /api/v1/product_sets/{id}` でフォームエンコード形式を受け付ける（既存 `BCartProductDescriptionUpdateTasklet` が `/products/{id}` で実証済の同パターン）。**`product_sets` エンドポイントの実フィールド名は実装時に B-CART API 仕様書 / 1件テストで確認** する。
- `b_cart_change_history` テーブルは Phase 1 で作成済み（target_type='PRODUCT_SET' を新規利用）。
- `b_cart_product_sets.b_cart_price_reflected` カラムは存在するが現在 boolean 値が更新される箇所なし。今回これを実運用フラグに昇格させる。
- `shipping_size` は B-CART API 同期で取込済（`BCartProductSets.shippingSize` BigDecimal）。

### ビジネス制約
- 本機能は管理者・経理担当者など限定ユーザー想定（権限分離は将来課題、今回は `isAuthenticated()` のみ）。
- 失敗時に B-CART 側だけ更新されてローカル DB が古いままになる事故を防ぐため、**PATCH 成功 → ローカル `b_cart_reflected=true` 更新** の順序を厳守。

---

## 4. 提案するソリューション

### アーキテクチャ概要

```
┌──────────────────────────────────────────────────────────┐
│ Frontend (Next.js)                                        │
│ ┌──────────────────────────┐  ┌──────────────────────┐    │
│ │ B-CART商品詳細画面         │  │ 変更点一覧画面 (新規)  │    │
│ │ /bcart/products/{id}      │  │ /bcart/pending-changes│   │
│ │ - セット一覧タブ拡張       │  │ - diff テーブル        │    │
│ │   unit_price/shipping_size│  │ - 選択 / 全件反映ボタン  │    │
│ │   インライン編集 + 保存    │  │ - チェックボックス選択  │    │
│ └────────┬─────────────────┘  └────────┬─────────────┘    │
└──────────┼─────────────────────────────┼──────────────────┘
           │                             │
           ▼                             ▼
┌──────────────────────────────────────────────────────────┐
│ Backend (Spring Boot)                                     │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ BCartProductController (拡張)                          │ │
│ │ - PUT /bcart/product-sets/{id}/pricing                │ │
│ │   body: { unitPrice?, shippingSize? }                 │ │
│ │ - GET /bcart/pending-changes                          │ │
│ │ - POST /bcart/pending-changes/reflect                 │ │
│ │   body: { productSetIds: [...] | "ALL" }              │ │
│ └────────┬─────────────────────────┬───────────────────┘ │
│          │                         │                      │
│          ▼                         ▼                      │
│ ┌─────────────────────┐  ┌──────────────────────────┐    │
│ │ BCartProductSets     │  │ BCartProductSetsReflect  │    │
│ │ PricingService (新)  │  │ Service (新)              │    │
│ │ - 編集 + 履歴記録     │  │ - PATCH /product_sets/{id}│   │
│ │   トランザクション   │  │ - 履歴/本テーブル更新     │    │
│ └─────────────────────┘  └──────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────┐
│ PostgreSQL                                                │
│ b_cart_product_sets  b_cart_change_history  既存活用       │
└──────────────────────────────────────────────────────────┘
                          │
                          ▼
                   B-CART REST API
                  PATCH /product_sets/{id}
```

### 責務分離
- **Controller**: HTTP 入出力、Bean Validation、認可
- **PricingService**: 編集 1 件分のトランザクション（history 追加 + 本テーブル更新）
- **ReflectService**: 反映ロジック（DB 読取 → B-CART API 呼出 → 結果を DB 反映）
- **HTTP クライアント**: 既存の `bCartHttpClient` (OkHttpClient Bean) を再利用

---

## 5. データモデル / DB 変更

### 既存テーブル活用、**スキーマ変更なし**
- `b_cart_change_history` (Phase 1 で作成済) — `target_type='PRODUCT_SET'`, `change_type` ∈ {`UNIT_PRICE`, `SHIPPING_SIZE`}, `field_name` ∈ {`unit_price`, `shipping_size`} を新規利用
- `b_cart_product_sets.b_cart_price_reflected` (boolean, 既存) — 編集時に false、反映成功で true

### Migration
**不要**。既存スキーマで完結する。

### 履歴記録のセマンティクス
1 編集 = N 件のフィールド変更 → **1 フィールド = 1 history 行**
- 例: ある商品セットで unit_price (1000→1100) と shipping_size (0→0.5) を同時編集 → 2 行 INSERT
- before_value: **編集直前の `b_cart_product_sets.<field>` の値（ローカル DB 値）を文字列化**したもの。**B-CART 上の現在値を保証するものではない**（B-CART 同期バッチが間に走った場合、B-CART 値とは乖離しうる）。
- after_value: ユーザー入力値（`BigDecimal#toPlainString()` で文字列化、表記揺れ防止）
- changed_by: ログインユーザー (`SecurityContextHolder` の `loginUserNo`)
- **before_value のロールバック性**: 「最古 before に戻せば編集前のローカル状態」になる。ただし B-CART 側で別途変更があれば B-CART は別の値を持つので、ロールバックの「正しさ」はローカル基準のみ。

### 同一フィールド連続編集
1 つの商品セットの同じフィールドを連続編集 → 複数行の history 行が積み上がる。  
反映処理時:
- グルーピングキー = (target_id, field_name)
- 同グループ内 → **最古行の before_value** + **最新行の after_value** を採用（中間値は無視、PATCH には最新値を送る）

---

## 6. API / UI 変更

### 6-1. Backend API（**新規コントローラ 2 つ**）

設計レビュー M-5 を反映し、既存 `BCartProductController`（`@RequestMapping("/api/v1/bcart/products")` 固定）には追加せず、URL パスを正しくマッピングできる新規コントローラを 2 つ作成する。

| コントローラ | RequestMapping | 担当エンドポイント |
|---|---|---|
| **`BCartProductSetsController`**（新規） | `/api/v1/bcart/product-sets` | PUT `/{setId}/pricing` |
| **`BCartPendingChangesController`**（新規） | `/api/v1/bcart/pending-changes` | GET, POST `/reflect`, GET `/count` |

#### PUT `/api/v1/bcart/product-sets/{setId}/pricing` （新規）

**Request:**
```json
{
  "unitPrice": 1100.00,
  "shippingSize": 0.5
}
```
- 両フィールドとも optional。null 不送信時は未変更扱い。
- Bean Validation: `unitPrice >= 0`, `shippingSize >= 0 && shippingSize <= 1`（B-CART 仕様の上限）

**処理フロー (`BCartProductSetsPricingService.update()`):**
```java
@Transactional
public void update(Long setId, Pricing req, Integer userNo) {
  BCartProductSets set = repo.findById(setId).orElseThrow();
  
  if (req.unitPrice != null && !Objects.equals(set.getUnitPrice(), req.unitPrice)) {
    historyRepo.save(BCartChangeHistory.of(
      "PRODUCT_SET", setId, "UNIT_PRICE", "unit_price",
      String.valueOf(set.getUnitPrice()), String.valueOf(req.unitPrice),
      userNo
    ));
    set.setUnitPrice(req.unitPrice);
  }
  // 同様に shippingSize
  
  if (anyChanged) {
    set.setBCartPriceReflected(false);
    repo.save(set);
  }
}
```

**Response:** 200 OK 更新後の `BCartProductSetsResponse`

#### GET `/api/v1/bcart/pending-changes` （新規）

**Query:** なし。サーバー側で **`LIMIT 500` を SQL に明示**（500 件超えはまず起こらないが、暴発時の保険）。500 件を超える場合は HTTP 200 + `truncated: true` で件数のみ通知し、UI で「件数が多すぎます。詳細画面から個別反映してください」と促す。

**Response:**
```json
[
  {
    "productSetId": 12345,
    "productId": 678,
    "productName": "ラ・フランス 2L 5kg",
    "setName": "ケース(10入)",
    "productNo": "ABC-123",
    "janCode": "4901234567890",
    "changes": [
      { "field": "unitPrice", "before": "1000.00", "after": "1100.00", "changedAt": "2026-05-01T10:30:00", "changedBy": "k_oda" },
      { "field": "shippingSize", "before": "0", "after": "0.5", "changedAt": "2026-05-01T10:30:00", "changedBy": "k_oda" }
    ]
  }
]
```

**SQL (概念):**

主源泉を **`b_cart_change_history` 単一テーブルに統一**（レビュー M-2 反映）。`b_cart_product_sets.b_cart_price_reflected` は補助マーカーとしてのみ使用し、SQL の `WHERE` には含めない。

```sql
WITH unreflected AS (
  SELECT target_id AS product_set_id, field_name,
         (array_agg(before_value ORDER BY changed_at ASC))[1]    AS before_value,
         (array_agg(after_value  ORDER BY changed_at DESC))[1]   AS after_value,
         MAX(changed_at)                                          AS last_changed_at
  FROM b_cart_change_history
  WHERE target_type='PRODUCT_SET' AND b_cart_reflected = false
    AND field_name IN ('unit_price', 'shipping_size')
  GROUP BY target_id, field_name
)
SELECT ps.id              AS product_set_id,
       ps.product_id,
       p.name             AS product_name,
       ps.name            AS set_name,
       ps.product_no,
       ps.jan_code,
       json_agg(json_build_object(
         'field',       u.field_name,
         'before',      u.before_value,
         'after',       u.after_value,
         'changedAt',   u.last_changed_at
       )) AS changes
FROM unreflected u
JOIN b_cart_product_sets ps ON ps.id = u.product_set_id
JOIN b_cart_products      p ON p.id  = ps.product_id
GROUP BY ps.id, ps.product_id, p.name, ps.name, ps.product_no, ps.jan_code
ORDER BY MAX(u.last_changed_at) DESC
LIMIT 500;
```
※ 実装時はネイティブクエリ + DTO projection。

#### POST `/api/v1/bcart/pending-changes/reflect` （新規）

**Request:**
```json
{ "productSetIds": [12345, 67890] }
```
または
```json
{ "all": true }
```

**Response:**
```json
{
  "succeeded": 2,
  "failed": 1,
  "results": [
    { "productSetId": 12345, "status": "SUCCESS" },
    { "productSetId": 67890, "status": "FAILED", "errorMessage": "B-CART API returned 422: ..." }
  ]
}
```

**処理フロー (`BCartProductSetsReflectService.reflect()`):**

レビュー C-1, C-2, M-1, M-3 を反映:
- 件数上限 200 件、超過時は 400
- aggregate 時に対象 history `id` をリスト保持し UPDATE は `WHERE id IN (...)` 限定
- `markReflected` は `public @Transactional`（別 Bean メソッド）として呼出し、CGLIB プロキシで効くようにする
- レスポンスボディ取得は `res.body() != null ? res.body().string() : ""` で NPE 防止
- 同期 API では sleep を行わない（B-CART 429 時は失敗としてカウント）

```java
// Controller
@PostMapping("/reflect")
public ResponseEntity<BCartReflectResult> reflect(@Valid @RequestBody BCartReflectRequest req) {
  List<Long> ids = req.resolveIds(/* "ALL" 解決はサービスへ */);
  if (ids.size() > 200) return ResponseEntity.badRequest().body(...);
  return ResponseEntity.ok(reflectService.reflect(ids));
}

// Service
@Service
@RequiredArgsConstructor
public class BCartProductSetsReflectService {
  private final BCartChangeHistoryRepository historyRepo;
  private final BCartProductSetsRepository setRepo;
  private final BCartReflectTransactionService txService; // public @Transactional 専用
  @Qualifier("bCartHttpClient") private final OkHttpClient httpClient;

  public BCartReflectResult reflect(List<Long> setIds) {
    BCartReflectResult result = new BCartReflectResult();
    for (Long setId : setIds) {
      // 1. aggregate（id 配列を保持）
      AggregatedChange agg = aggregateUnreflectedFields(setId);  // (fields, historyIds)
      if (agg.fields().isEmpty()) {
        result.addSkipped(setId, "no unreflected changes");
        continue;
      }
      try {
        // 2. PATCH
        patchToBCart(setId, agg.fields());
        // 3. DB 更新（別 Bean の public @Transactional 経由）
        txService.markReflected(setId, agg.historyIds());
        result.addSuccess(setId);
      } catch (IOException e) {
        log.error("Reflect failed for set {}: {}", setId, e.getMessage());
        result.addFailure(setId, e.getMessage());
      }
    }
    return result;
  }

  private AggregatedChange aggregateUnreflectedFields(Long setId) {
    List<BCartChangeHistory> rows = historyRepo
        .findUnreflectedForProductSet(setId, List.of("unit_price", "shipping_size"));
    Map<String, String> latestAfter = new HashMap<>();
    List<Long> historyIds = new ArrayList<>();
    rows.stream()
        .sorted(Comparator.comparing(BCartChangeHistory::getChangedAt))
        .forEach(h -> {
          latestAfter.put(h.getFieldName(), h.getAfterValue());  // 最新 after で上書き
          historyIds.add(h.getId());
        });
    return new AggregatedChange(latestAfter, historyIds);
  }

  private void patchToBCart(Long setId, Map<String,String> fields) throws IOException {
    FormBody.Builder fb = new FormBody.Builder(StandardCharsets.UTF_8);
    fields.forEach(fb::add);
    HttpUrl url = new HttpUrl.Builder()
        .scheme("https").host("api.bcart.jp")
        .addPathSegment("api").addPathSegment("v1")
        .addPathSegment("product_sets").addPathSegment(String.valueOf(setId))
        .build();
    Request req = new Request.Builder()
        .url(url).patch(fb.build())
        .addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
        .build();
    try (Response res = httpClient.newCall(req).execute()) {
      if (!res.isSuccessful()) {
        String body = res.body() != null ? res.body().string() : "";
        throw new IOException("PATCH product_sets/" + setId + " failed: " + res.code() + " " + body);
      }
    }
  }
}

// 別 Bean: public @Transactional でプロキシを効かせる
@Service
@RequiredArgsConstructor
public class BCartReflectTransactionService {
  private final BCartChangeHistoryRepository historyRepo;
  private final BCartProductSetsRepository setRepo;

  @Transactional
  public void markReflected(Long setId, List<Long> historyIds) {
    historyRepo.markReflectedByIds(historyIds);              // UPDATE ... WHERE id IN (...)
    setRepo.findById(setId).ifPresent(s -> {
      s.setBCartPriceReflected(true);
      setRepo.save(s);
    });
  }
}
```

**注意:**
- PATCH 失敗 → `markReflected` を呼ばない → ローカル状態と B-CART は乖離しないまま再試行可能。
- `markReflected` は **history id list 限定 UPDATE** なので、ループ実行中に追加された新規編集を誤マークしない（M-1）。
- `BCartReflectTransactionService` は `BCartProductSetsReflectService` 自身に `@Transactional public` を置くと、サービス内ループからの自己呼出で CGLIB プロキシが効かない可能性があるため、別 Bean に分離している（C-1）。

#### GET `/api/v1/bcart/pending-changes/count` （新規、サイドバーバッジ用）

**Response:** `{ "count": 5 }`

---

### 6-2. Frontend UI

#### B-CART商品詳細画面 「セット一覧」タブ拡張

現状の display-only セット一覧を**インライン編集対応**にする。

**画面イメージ:**
```
┌─────────────────────────────────────────────────────────────┐
│ セット名      品番        JAN              単価       配送Sz  保存│
│ ケース(10入)  ABC-123     4901234567890   [1100   ]  [0.5 ] [💾]│
│ バラ          ABC-123-S   4901234567891   [120    ]  [0.1 ] [💾]│
└─────────────────────────────────────────────────────────────┘
```
- `unit_price`, `shipping_size` が `<Input type="number">` でインライン編集可
- 1 行ずつ「保存」ボタン（鉛筆アイコン or `<Save>`）— 行の値が dirty なら enabled
- 保存成功時に toast `"セット {setName} を保存しました（B-CARTには未反映）"` + ヘッダーに警告バッジ「未反映あり」を出す（任意）
- 既存の「B-CARTに反映」ボタン（商品説明用）はそのまま、価格・配送サイズの反映は専用画面で行う旨をツールチップで補足

#### 変更点一覧画面 `/bcart/pending-changes` （新規）

**画面イメージ:**
```
┌────────────────────────────────────────────────────────────────────┐
│ B-CART 変更点一覧                              [選択を反映] [全件反映]│
├────────────────────────────────────────────────────────────────────┤
│ 5 件の未反映変更があります                                          │
│ ┌──┬───────┬───────────┬──────────┬─────────────────────────────┐ │
│ │☐ │商品名 │ セット名    │ 品番      │ 変更内容                    │ │
│ ├──┼───────┼───────────┼──────────┼─────────────────────────────┤ │
│ │☑ │ラ.. 5k│ ケース(10入)│ ABC-123  │ 単価:    1000 → 1100        │ │
│ │  │       │           │          │ 配送Sz:  0 → 0.5            │ │
│ ├──┼───────┼───────────┼──────────┼─────────────────────────────┤ │
│ │☑ │○○製品 │ バラ        │ XYZ-001  │ 単価:    250 → 280          │ │
│ └──┴───────┴───────────┴──────────┴─────────────────────────────┘ │
│                                                                    │
│ 反映結果（最後の実行）:                                              │
│ ✅ 成功: 2 件   ❌ 失敗: 0 件                                        │
└────────────────────────────────────────────────────────────────────┘
```

**コンポーネント:**
- ファイル: `frontend/components/pages/bcart/pending-changes.tsx`
- ルート: `frontend/app/(authenticated)/bcart/pending-changes/page.tsx`
- TanStack Query: `['bcart', 'pending-changes']`, `['bcart', 'pending-changes-count']`
- 行ごとにチェックボックス、ヘッダーにマスターチェックボックス
- 「選択を反映」「全件反映」 → POST `/bcart/pending-changes/reflect` → 成功 toast + クエリ無効化
- 反映中は spinner 表示。失敗行は赤背景 + エラーメッセージを expandable

#### サイドバー
**変更:** `frontend/components/layout/Sidebar.tsx` の "B-CART" グループに新メニュー追加

```tsx
{ title: 'B-CART変更点一覧', icon: Diff, href: '/bcart/pending-changes' }
```

`Diff` アイコンを採用（レビュー m-2 反映、`AlertTriangle` は警告系で誤解を招くため）。未反映件数バッジ表示は **Phase 3-A スコープ外**（後続改善）。

---

## 7. エッジケース

| ケース | 対応 |
|--------|------|
| ユーザーが編集後すぐ画面リロードして同じ値で再編集 | before/after が同じならスキップ（履歴行を作らない） |
| 同一セット同一フィールドを複数回編集 | 履歴は積みあがる。反映処理で「最古 before + 最新 after」に集約 |
| 反映途中で B-CART API 障害 | 未反映行はそのまま残り、次回再試行で再送 |
| 反映途中で別ユーザーが同セットを編集 | 反映処理は反映時点の最新 history を使用。新規編集は次回反映に持ち越し |
| 編集途中で誰かが B-CART 同期バッチを実行 | 同期で `b_cart_product_sets` の値が上書きされる。未反映 history はそのまま残る → 反映時に B-CART は同期で上書きされた値を再度上書き（最終的にユーザー編集値で確定）。**通知だけ** 行うのは Phase 3-B 検討 |
| `unit_price` を null に変更 | バリデーションエラー（B-CART 必須項目） |
| `shipping_size` に 1.0 超を入力 | バリデーションエラー（仕様上の上限） |
| 反映対象 0 件 | 「反映する変更がありません」トースト |
| `b_cart_price_reflected=false` だが履歴行がない | 不整合状態（手動 SQL の影響等）。反映処理は履歴行のみ参照するので無害。一覧表示でも履歴 join なので非表示 |
| B-CART API レート制限到達 | 429 レスポンスを失敗としてカウント、ユーザーに「5分後に再試行」と表示 |

---

## 8. リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| `PATCH /api/v1/product_sets/{id}` のフィールド名が想定と異なる | 高 | 実装初期に curl で 1 件テスト、B-CART API ドキュメント確認。フィールド名定数化で変更容易に |
| 反映成功 / DB 更新失敗の不整合（ローカル DB が古いまま、B-CART だけ新しい） | 中 | DB 更新は `@Transactional`、PATCH 後の DB 更新失敗時はログ・アラート（リトライ手段は手動 SQL） |
| 大量編集後の一括反映でレート制限超過 | 中 | 250件で 5分待機の sleep を組み込み（Phase 2 と同パターン）。今回は同期 API なので超大量時はバッチ化検討（Phase 3-B） |
| 楽観ロック未対応で同時編集が発生 | 低 | 運用ルールで回避。`updated_at` 楽観ロックは Phase 3-B |
| `before_value` 文字列化の format が float 表記揺れ | 低 | `BigDecimal#toPlainString()` で統一 |

---

## 9. ロールアウト計画

### Phase 3-A.1: バックエンド実装
1. DTO: `BCartProductSetPricingRequest`, `BCartPendingChangeResponse`, `BCartReflectRequest`, `BCartReflectResult`
2. Service: `BCartProductSetsPricingService` (編集 + 履歴記録), `BCartProductSetsReflectService` (反映ループ + PATCH), `BCartReflectTransactionService` (`public @Transactional` の DB 更新専用 Bean)
3. Controller: **新規 2 つ** — `BCartProductSetsController` + `BCartPendingChangesController`
4. Repository: `BCartChangeHistoryRepository` に集約クエリ + `markReflectedByIds(List<Long>)` 追加
5. **削除前確認**: `grep -r "BCartGoodsPriceTableUpdate"` で全参照を確認 → 参照ゼロを確認後にスタブ `BCartGoodsPriceTableUpdateTasklet` を削除。`BatchController` のジョブ一覧に当該ジョブが含まれていないことも確認済（既存）。
6. 1 件スモークテスト: 開発環境で curl にて `PATCH /api/v1/product_sets/{id}` の `unit_price` / `shipping_size` フィールド名で B-CART API が 200 を返すことを確認 → 設計書の §10 確認待ち事項を消し込む。

### Phase 3-A.2: フロントエンド実装
1. 詳細画面 `/bcart/products/{id}` セット一覧タブ: インライン編集 + 行単位保存
2. 新規画面 `/bcart/pending-changes`: テーブル + diff + 一括反映 + 件数 truncated メッセージ
3. サイドバー: B-CART グループに「変更点一覧」追加（アイコン: `Diff`）
4. 型定義 `frontend/types/bcart.ts`:
   - 既存 `BCartProductSet` に **`shippingSize: number | null`** を追加（レビュー m-1）
   - 新規型: `BCartPendingChange`, `BCartReflectResult`
   - バックエンド `BCartProductResponse.sets[]` の DTO に `shippingSize` が含まれていることを確認・必要なら追加

### Phase 3-A.3: テスト
- E2E: `frontend/e2e/bcart-pending-changes.spec.ts`
  - 編集→未反映表示→反映→反映済みになる、までを mock + 実バックエンドで両方
- 1 件実データで B-CART API 疎通確認（実装フェーズ最後）

### ロールバック計画
- DB スキーマ変更なしのため即座にロールバック可能
- 新エンドポイント / 画面を消すだけで原状復帰
- 既存 `BCartGoodsPriceTableUpdateTasklet` 削除はスタブのため復元する価値なし

### Feature Flag
不要（管理者のみがアクセスする画面・操作のため、誤爆リスク低い）

---

## 10. 確認待ち事項

- [ ] B-CART API 仕様書 (`PATCH /api/v1/product_sets/{id}`) で `unit_price`, `shipping_size` が更新可能フィールドであることの確認 — **実装初期に curl でスモークテスト**
- [ ] `shipping_size` の B-CART 上限値（1.0 で正しいか）

---

## 11. 関連ファイル

### 既存（参照・拡張対象）
- `backend/src/main/java/jp/co/oda32/api/bcart/BCartProductController.java` (拡張)
- `backend/src/main/java/jp/co/oda32/domain/model/bcart/BCartProductSets.java` (変更なし)
- `backend/src/main/java/jp/co/oda32/domain/model/bcart/BCartChangeHistory.java` (変更なし)
- `backend/src/main/java/jp/co/oda32/batch/bcart/BCartProductDescriptionUpdateTasklet.java` (パターン参照)
- `frontend/components/pages/bcart/product-detail.tsx` (拡張)
- `frontend/components/pages/bcart/products.tsx` (変更なし)
- `frontend/components/layout/Sidebar.tsx` (1 メニュー追加)

### 新規
- `backend/src/main/java/jp/co/oda32/api/bcart/BCartProductSetsController.java`
- `backend/src/main/java/jp/co/oda32/api/bcart/BCartPendingChangesController.java`
- `backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartProductSetsPricingService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartProductSetsReflectService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartReflectTransactionService.java`
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartProductSetPricingRequest.java`
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartPendingChangeResponse.java`
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartReflectRequest.java`
- `backend/src/main/java/jp/co/oda32/dto/bcart/BCartReflectResult.java`
- `frontend/components/pages/bcart/pending-changes.tsx`
- `frontend/app/(authenticated)/bcart/pending-changes/page.tsx`
- `frontend/e2e/bcart-pending-changes.spec.ts`

### 削除
- `backend/src/main/java/jp/co/oda32/batch/bcart/BCartGoodsPriceTableUpdateTasklet.java` (空スタブ)
- 関連 Bean 登録（あれば）
