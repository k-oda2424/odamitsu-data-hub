# 見積作成画面 設計書

| 項目 | 内容 |
|------|------|
| 対象機能 | 見積作成・修正（estimate_create / estimate_edit） |
| 対応設計書 | DD_05_見積管理.md |
| 作成日 | 2026-04-06 |

---

## 1. 概要

### 1.1 現状
- 見積一覧（`/estimates`）: 実装済
- 見積明細表示（`/estimates/{estimateNo}`）: 実装済（御見積書・印刷対応）
- 見積ステータス更新（`PUT /estimates/{estimateNo}/status`）: 実装済
- **見積作成・修正: 未実装**

### 1.2 旧システムとの差異方針

| 旧システム（stock-app） | 新システム |
|------------------------|-----------|
| 入力画面 → 確認画面 → 登録（3ステップ） | 入力画面で直接保存（SPA、1ステップ） |
| 行追加はPOST（画面リロード） | クライアントサイドで動的追加 |
| 商品コードblurでAjax取得 | 同等（API経由） |
| 粗利計算はJS + サーバー両方 | フロントエンドのみで計算 |
| Thymeleaf + jQuery | React + shadcn/ui |

---

## 2. API設計

### 2.1 新規エンドポイント

#### POST /api/v1/estimates — 見積新規作成

**Request Body:**
```json
{
  "shopNo": 1,
  "partnerNo": 100,
  "destinationNo": 0,
  "estimateDate": "2026-04-06",
  "priceChangeDate": "2026-05-01",
  "note": "備考テキスト",
  "details": [
    {
      "goodsNo": 500,
      "goodsCode": "00112531",
      "goodsName": "ファミリーフレッシュ 4.5L",
      "specification": "4.5L",
      "goodsPrice": 1400,
      "purchasePrice": 1000,
      "containNum": 4,
      "changeContainNum": null,
      "profitRate": 28.6,
      "detailNote": "",
      "displayOrder": 1
    }
  ]
}
```

**Response:** `201 Created` — `EstimateResponse`（fromWithDetails）

**処理フロー:**
1. バリデーション（shopNo, partnerNo, estimateDate, priceChangeDate 必須）
2. TEstimate ヘッダ insert（estimateStatus = "00"）
3. TEstimateDetail 明細 insert（estimateDetailNo を 1 から連番）
4. 親子得意先同期（セクション4参照）
5. レスポンス返却

#### PUT /api/v1/estimates/{estimateNo} — 見積修正

**Request Body:** POST と同一構造

**Response:** `200 OK` — `EstimateResponse`（fromWithDetails）

**処理フロー:**
1. 既存見積取得（存在チェック）
2. バリデーション
3. ヘッダ更新（estimateStatus → "20" 修正）
4. 明細を物理削除 → 再 insert（delete-insert パターン）
5. 親子得意先同期
6. レスポンス返却

#### DELETE /api/v1/estimates/{estimateNo} — 見積論理削除

**Response:** `204 No Content`

**処理フロー:**
1. ヘッダの estimateStatus → "50", del_flg → "1"
2. 明細の del_flg → "1"（deleteByDelFlg）

#### GET /api/v1/estimates/goods-search — 商品コード/JANコード検索（Ajax用）

**Query Parameters:**
- `shopNo` (必須), `code` (必須), `partnerNo` (任意), `destinationNo` (任意)

**処理フロー（8文字以下=商品コード）:**
1. 販売商品マスタ（m_sales_goods）の商品コード
2. 販売商品ワーク（w_sales_goods）の商品コード
3. 仕入価格変更予定（m_purchase_price_change_plan）の商品コード

**処理フロー（9文字以上=JANコード）:**
1. 商品マスタ（m_goods）のJANコード
2. 仕入価格変更予定のJANコード
3. 見積取込明細（t_quote_import_detail、PENDING）のJANコード

#### GET /api/v1/estimates/price-plan-goods — メーカー見積商品検索（ポップアップ用）

**Query Parameters:**
- `shopNo` (任意), `goodsName` (任意)

**処理フロー:**
1. m_purchase_price_change_plan を商品名でNFKCあいまい検索
2. t_quote_import_detail（PENDING）を商品名でNFKCあいまい検索
3. 重複排除してマージ

**レスポンスフィールド:**
```json
{
  "goodsNo": null,
  "goodsCode": "00112531",
  "goodsName": "ファミリーフレッシュ 4.5L",
  "purchasePrice": 1000,
  "containNum": 4,
  "janCode": "4901234567890",
  "source": "GOODS",
  "purchasePriceChangePlanNo": null
}
```

#### GET /api/v1/estimates/goods-search — 商品詳細検索（選択後の特値・specification取得）

**Query Parameters:**
- `shopNo` (必須)
- `goodsNo` (必須)
- `partnerNo` (任意)
- `destinationNo` (任意)

**処理フロー:**
1. `VEstimateGoodsService.findGoods(shopNo, [goodsNo])` で通常価格取得
2. partnerNo 指定時は `VEstimateGoodsSpecialService.findGoods(...)` で特値取得
3. 特値があれば特値を優先
4. MGoods から specification を取得

### 2.2 リクエスト DTO

#### EstimateCreateRequest
```java
@Data
public class EstimateCreateRequest {
    @NotNull private Integer shopNo;
    @NotNull private Integer partnerNo;
    private Integer destinationNo;
    @NotNull private LocalDate estimateDate;
    @NotNull private LocalDate priceChangeDate;
    private String note;
    @Valid @NotEmpty
    private List<EstimateDetailCreateRequest> details;
}
```

#### EstimateDetailCreateRequest
```java
@Data
public class EstimateDetailCreateRequest {
    @NotNull private Integer goodsNo;
    @NotNull private String goodsCode;
    private String goodsName;
    private String specification;
    @NotNull private BigDecimal goodsPrice;
    private BigDecimal purchasePrice;
    private BigDecimal containNum;
    private BigDecimal changeContainNum;
    private BigDecimal profitRate;
    private String detailNote;
    private int displayOrder;
}
```

#### EstimateGoodsSearchResponse
```java
@Data @Builder
public class EstimateGoodsSearchResponse {
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private String specification;
    private BigDecimal purchasePrice;
    private BigDecimal containNum;
    private BigDecimal changeContainNum;
    private BigDecimal nowGoodsPrice;
    private String pricePlanInfo;
}
```

---

## 3. フロントエンド設計

### 3.1 ルーティング

| パス | ページ | モード |
|------|--------|--------|
| `/estimates/create` | 見積作成 | 新規 |
| `/estimates/{estimateNo}/edit` | 見積修正 | 編集 |

### 3.2 コンポーネント構成

```
app/(authenticated)/estimates/create/page.tsx        — ページラッパー
app/(authenticated)/estimates/[estimateNo]/edit/page.tsx — ページラッパー
components/pages/estimate/form.tsx                   — 見積フォーム（共用）
```

### 3.3 見積フォーム（EstimateFormPage）

#### Props
```typescript
interface EstimateFormPageProps {
  estimateNo?: number  // undefined = 新規、number = 修正
}
```

#### ヘッダ部フォーム項目

| 項目 | コンポーネント | 必須 | 備考 |
|------|-------------|------|------|
| 店舗 | `Select`（admin時）/ hidden | Yes | `useShops()` |
| 得意先 | `SearchableSelect` | Yes | `usePartners(shopNo)` |
| 納品先 | `SearchableSelect` clearable | No | `useDestinations(partnerNo)` 得意先選択後に有効化 |
| 見積日 | `Input type="date"` | Yes | デフォルト: 今日 |
| 価格改定日 | `Input type="date"` | Yes | |
| 備考 | `Textarea` | No | |

#### 明細テーブル

| カラム | 入力/表示 | 説明 |
|--------|---------|------|
| 商品コード | Input + 検索 | blur で `/estimates/goods-search` を呼び出し |
| 商品名 | 表示 | API レスポンスから自動設定 |
| 仕様 | 表示 | API レスポンスから自動設定 |
| 原価 | 表示（admin のみ） | API レスポンスから自動設定 |
| 原価改訂予定 | 表示（admin のみ） | pricePlanInfo |
| 見積単価 | Input (number) | ユーザー入力。変更時に粗利を再計算 |
| 入数 | 表示 | API レスポンスから自動設定 |
| 粗利 | 表示（admin のみ） | `goodsPrice - purchasePrice` |
| 粗利率 | 表示（admin のみ） | `(1 - purchasePrice / goodsPrice) * 100` |
| ケース粗利 | 表示（admin のみ） | `profit * containNum` |
| 備考 | Input | |
| 表示順 | Input (number) | デフォルト: 行番号 |
| 削除 | Button (Trash2) | 行削除 |

#### 明細行の状態管理

```typescript
interface EstimateDetailRow {
  id: string                    // React key（nanoid）
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string
  purchasePrice: number | null
  pricePlanInfo: string
  goodsPrice: number | null
  containNum: number | null
  changeContainNum: number | null
  profitRate: number | null
  detailNote: string
  displayOrder: number
}
```

- `useState<EstimateDetailRow[]>` で管理
- 新規時: 空行 1 行で初期化
- 修正時: API レスポンスの details から変換

#### 粗利計算（クライアントサイド）

```typescript
function calcProfit(goodsPrice: number, purchasePrice: number) {
  return goodsPrice - purchasePrice
}

function calcProfitRate(goodsPrice: number, purchasePrice: number) {
  if (goodsPrice === 0) return 0
  return (1 - purchasePrice / goodsPrice) * 100
}

function calcCaseProfit(profit: number, containNum: number) {
  return profit * containNum
}
```

旧システムでは goodsPrice / profit / profitRate / caseProfit の4方向相互計算が可能だったが、
新システムでは **goodsPrice のみ入力可** とし、他は自動計算の表示のみとする（シンプル化）。

#### ボタン

| ボタン | 処理 |
|--------|------|
| 行追加 | `rows` に空行を追加 |
| 保存 | バリデーション → POST or PUT → 成功時に詳細画面へ遷移 |
| キャンセル | 一覧に戻る（`router.push('/estimates')`） |

#### 保存処理フロー

```
1. クライアントバリデーション
   - shopNo, partnerNo, estimateDate, priceChangeDate 必須チェック
   - details に有効行（goodsCode + goodsPrice が入力済み）が 1 行以上
   - 空行（goodsCode 未入力 or goodsPrice=0）は送信時に自動除外

2. API 呼び出し
   - 新規: POST /api/v1/estimates
   - 修正: PUT /api/v1/estimates/{estimateNo}

3. 成功時
   - toast.success('見積を保存しました')
   - router.push(`/estimates/${response.estimateNo}`)

4. エラー時
   - toast.error('見積の保存に失敗しました')
```

### 3.4 修正モードの初期化

```
GET /api/v1/estimates/{estimateNo}
  → EstimateResponse (fromWithDetails)
  → フォームに展開
```

修正時の注意:
- 既存のステータスが "00"(作成) or "20"(修正) の場合のみ編集可能
- それ以外のステータスは編集不可（ボタンをdisabled/非表示）

### 3.5 一覧画面・詳細画面からの導線

#### 一覧画面（既存）
- 「見積作成」ボタン → `/estimates/create`（実装済のリンク先）

#### 詳細画面（既存の detail.tsx に追加）
- 「修正」ボタン → `/estimates/{estimateNo}/edit`
- 「削除」ボタン → DELETE API → 一覧へ遷移
- 条件: estimateStatus が "00" or "20" の場合のみ表示

---

## 4. 親子得意先同期ロジック

旧システムの `EstimateCreateController` の同期処理を `EstimateController`（またはサービス層）に移植する。

### 4.1 同期タイミング
- 見積の新規作成（POST）時
- 見積の修正（PUT）時

### 4.2 同期フロー

```
見積保存完了後:
  |
  +-- MPartner 取得
  |
  +-- partner.parentPartnerNo != null（子得意先の見積）
  |     |
  |     +-- 新規 → 親見積を新規作成 + 他の子見積を新規作成/更新
  |     +-- 修正 → 親見積を更新 + 他の子見積を更新
  |
  +-- partner.parentPartnerNo == null
        |
        +-- 子得意先が存在する場合（親得意先の見積）
        |     |
        |     +-- 新規 → 全子見積を新規作成（ステータス: 40）
        |     +-- 修正 → 全子見積を更新（ステータス: 40）
        |
        +-- 子得意先なし → 同期不要
```

### 4.3 実装方針

同期ロジックは `EstimateCreateService`（新規作成）としてサービス層に実装する。

```java
@Service
@RequiredArgsConstructor
public class EstimateCreateService {
    private final TEstimateService tEstimateService;
    private final TEstimateDetailService tEstimateDetailService;
    private final MPartnerService mPartnerService;

    @Transactional
    public TEstimate createEstimate(EstimateCreateRequest request) {
        // 1. ヘッダ insert
        // 2. 明細 insert
        // 3. 親子同期
        return estimate;
    }

    @Transactional
    public TEstimate updateEstimate(Integer estimateNo, EstimateCreateRequest request) {
        // 1. ヘッダ update (status → "20")
        // 2. 明細 delete-insert
        // 3. 親子同期
        return estimate;
    }

    @Transactional
    public void deleteEstimate(Integer estimateNo) {
        // 1. 明細 論理削除
        // 2. ヘッダ 論理削除 (status → "50")
    }

    // 親子同期の private メソッド群
    // （旧 EstimateCreateController のロジックを移植）
}
```

---

## 5. 実装ファイル一覧

### バックエンド（新規作成）

| ファイル | 内容 |
|---------|------|
| `dto/estimate/EstimateCreateRequest.java` | 作成・修正リクエスト DTO |
| `dto/estimate/EstimateDetailCreateRequest.java` | 明細リクエスト DTO |
| `dto/estimate/EstimateGoodsSearchResponse.java` | 商品検索レスポンス DTO |
| `domain/service/estimate/EstimateCreateService.java` | 作成・修正・削除 + 親子同期ロジック |

### バックエンド（修正）

| ファイル | 変更内容 |
|---------|---------|
| `api/estimate/EstimateController.java` | POST, PUT, DELETE, goods-search エンドポイント追加 |

### フロントエンド（新規作成）

| ファイル | 内容 |
|---------|------|
| `app/(authenticated)/estimates/create/page.tsx` | 新規作成ページラッパー |
| `app/(authenticated)/estimates/[estimateNo]/edit/page.tsx` | 修正ページラッパー |
| `components/pages/estimate/form.tsx` | 見積フォームコンポーネント |

### フロントエンド（修正）

| ファイル | 変更内容 |
|---------|---------|
| `components/pages/estimate/detail.tsx` | 「修正」「削除」ボタン追加 |
| `types/estimate.ts` | EstimateCreateRequest 型追加 |

---

## 6. 旧システムからの簡略化ポイント

1. **確認画面の廃止**: SPA では入力画面から直接保存。確認が必要なら保存前にダイアログを表示
2. **粗利の4方向計算の廃止**: goodsPrice のみ入力可。profit/profitRate/caseProfit は表示のみ
3. **行追加のサーバーリクエスト廃止**: クライアントサイドで `useState` 配列操作
4. **税込変換**: 保存時ではなく表示時（detail.tsx）で既に対応済み — フォームでは税抜のまま入力
5. **EstimateUtil の移植範囲**: `fillEstimateGoods`（商品検索時の価格選択ロジック）のみ。`resettingEstimateDetailForm`（画面リロード時の再計算）は SPA では不要
