# 比較見積機能 設計書

> **Rev.2** (2026-04-09) — 設計レビュー指摘 C-1, M-1〜M-3, m-1〜m-4 を反映

## 1. Background / Problem

### 現状の課題
- 仕入先が値上げした際、代替商品（他メーカー・他仕入先）との価格比較を **Excel や紙ベース** で行っている
- 既存の見積機能は「1得意先 × 1見積」が前提で、**同一商品カテゴリの代替品を横並び比較** する仕組みがない
- 得意先への提案時に「現行品 vs 代替品」の比較表を手作業で作成しており、非効率

### 解決したいこと
- 商品を横並びで比較し、価格・粗利率・仕様を一目で把握できる画面を提供
- 比較結果から見積明細への反映をスムーズに行える導線を作る
- 得意先向けの比較見積書（PDF）を出力できるようにする

---

## 2. Requirements

### 機能要件（FR）

| # | 要件 | 優先度 |
|---|------|--------|
| FR-1 | 基準商品（現行品）を選択し、代替候補商品を複数追加して横並び比較できる | Must |
| FR-2 | 比較項目: 商品コード、商品名、規格、仕入先、仕入価格、販売単価、入数、粗利率、粗利額 | Must |
| FR-3 | 比較テーブル上で販売単価・数量を仮入力し、粗利シミュレーションできる | Must |
| FR-4 | 商品検索は既存の GoodsSearchDialog + コード検索を流用 | Must |
| FR-5 | 比較結果から「この商品で見積作成」ボタンで見積作成画面に遷移（商品情報を引き継ぎ） | Must |
| FR-6 | 比較セッションの一時保存（ブラウザ sessionStorage） | Should |
| FR-7 | 比較見積書PDF出力（基準品 vs 推奨品の比較表形式） | Could |
| FR-8 | 得意先を指定した場合、特値（VEstimateGoodsSpecial）を反映 | Must |

### 非機能要件（NFR）

| # | 要件 |
|---|------|
| NFR-1 | 比較対象は最大10商品まで（画面横スクロール上限） |
| NFR-2 | 商品検索のレスポンスは既存API同等（500ms以内） |
| NFR-3 | admin（shopNo=0）のみ仕入価格・粗利率を表示 |

---

## 3. Constraints

### 技術的制約
- 新規DBテーブルは追加しない（既存のマスタ・ビューを活用）
- 比較データはフロントエンドの状態として管理（永続化不要）
- PDF出力は Phase 2（Could）とし、Phase 1 は画面比較のみ

### ビジネス制約
- 比較対象は同一ショップ内の商品に限定（shopNo固定）
- 得意先・配送先を指定した場合のみ特値を反映

---

## 4. Proposed Solution

### アーキテクチャ概要

```
┌─────────────────────────────────────────────────┐
│  Frontend (Next.js)                             │
│                                                 │
│  /estimates/compare (新規ページ)                 │
│  ┌───────────────────────────────────────────┐  │
│  │ ComparisonPage                            │  │
│  │ ┌──────────┐ ┌──────────────────────────┐ │  │
│  │ │ 条件設定  │ │ 比較テーブル              │ │  │
│  │ │ ・得意先  │ │  基準品 │ 代替A │ 代替B  │ │  │
│  │ │ ・配送先  │ │  ───────┼───────┼─────── │ │  │
│  │ │          │ │  単価   │ 単価  │ 単価   │ │  │
│  │ │          │ │  粗利率 │ 粗利率│ 粗利率 │ │  │
│  │ └──────────┘ └──────────────────────────┘ │  │
│  │ ┌──────────────────────────────────────┐  │  │
│  │ │ アクション: 見積作成へ / 印刷         │  │  │
│  │ └──────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  既存API呼出:                                    │
│  GET /estimates/goods-search (コード検索)         │
│  GET /sales-goods/master (商品一覧)              │
│  GET /estimates/price-plan-goods (変更予定)       │
└────────────────┬────────────────────────────────┘
                 │ 新規API 1本
                 ▼
┌─────────────────────────────────────────────────┐
│  Backend (Spring Boot)                          │
│                                                 │
│  GET /api/v1/estimates/compare-goods             │
│  → 複数goodsNoの価格情報を一括取得               │
│    (VEstimateGoods / VEstimateGoodsSpecial)       │
│                                                 │
│  既存Service流用:                                 │
│  - VEstimateGoodsService                        │
│  - VEstimateGoodsSpecialService                 │
│  - EstimateGoodsSearchService                   │
└─────────────────────────────────────────────────┘
```

### 新規API

**`GET /api/v1/estimates/compare-goods`**

複数商品の価格情報を一括取得する。フロントエンドで比較テーブルを構築するためのデータソース。
**注意:** goodsNo が null（マスタ未登録）の商品はこの API を経由せず、フロントエンド側で商品検索時の取得情報をそのまま使用する（→ M-1 対応）。

```
Query Parameters:
  shopNo: Integer (必須)
  goodsNoList: List<Integer> (必須, カンマ区切り, 1件以上)
  partnerNo: Integer (任意 — 特値取得用)
  destinationNo: Integer (任意 — 特値取得用)

Validation:
  - goodsNoList が空の場合は 400 Bad Request

Response: List<CompareGoodsResponse>
```

---

## 5. Data Model / DB Changes

### 新規テーブル: なし

既存ビュー・テーブルを活用：

| データソース | 取得情報 | 用途 |
|------------|---------|------|
| `v_estimate_goods` | 通常価格 (nowGoodsPrice, purchasePrice, containNum) | 標準比較 |
| `v_estimate_goods_special` | 特値 (partnerNo + destinationNo 別) | 得意先指定時 |
| `m_goods` | 商品名、規格、JANコード、メーカー | 基本情報 |
| `m_sales_goods` | 商品コード、カテゴリNo、仕入先 | 販売情報 |

### 重要: 仕入価格は (得意先 × 配送先) で異なる場合がある

`m_purchase_price` テーブルは `(supplier_no, shop_no, goods_no, partner_no, destination_no)` を
ユニークキーとしており、同一商品でも納品先別に仕入単価が異なるケースを保持できる。

**実例**: イトマン製イッコト芯なしSLIM 6R-180m
- 標準仕入価格: ¥465 (partner_no=0, destination_no=0)
- マツダスタジアム向け特値: ¥440 (partner_no=110, destination_no=540)

**比較見積機能での扱い:**
- 得意先・配送先を未指定 → 標準仕入価格 (¥465) で比較・粗利計算
- 得意先・配送先を指定 → 該当する特値 (¥440) があればそれを優先、なければ標準にフォールバック
- API `compare-goods` は既に `partnerNo`/`destinationNo` をクエリパラメータとして受け取るため、
  サービス層で `m_purchase_price` 検索時にも同条件で絞り込む必要がある（**実装側で対応要**）

**現状の制限と TODO:**
- `VEstimateGoodsService.findGoods()` / `VEstimateGoodsSpecialService.findGoods()` は
  販売側ビュー (`v_estimate_goods` / `v_estimate_goods_special`) を見ているが、
  これらのビューが `m_purchase_price` の partner/destination 別レコードを反映しているか要検証
- 反映していない場合、`EstimateCompareService` で `MPurchasePriceService.findByPK(shopNo, supplierNo, goodsNo, partnerNo, destinationNo)` 相当の
  ロックアップロジックを追加する必要がある（Phase 2 課題）

**関連: AI見積取込スキル (s-quote-import) の課題**

`QuoteImportService.createNewAndMatch()` は `partnerNo=0, destinationNo=0` 固定で
仕入価格変更予定を登録するため、特値を扱えない。

正しい運用フロー（Claude Code が能動的に確認すべき）:
1. PDFから商品を抽出
2. 新規商品の場合、Claude Code が「この価格は標準ですか？特定得意先向けの特値ですか？」と質問
3. 標準なら partnerNo=0/destinationNo=0、特値なら該当の partnerNo/destinationNo を指定
4. 場合によっては「標準価格」と「特値」を **両方** 登録する（同じ商品で2つの change plan を作る）

→ s-quote-import スキルの Step 4 (3層マッチング) と Step 6 (一括登録) の間に
  「価格スコープ確認ステップ」を追加する。Web画面ではなく Claude Code 対話で実施する。
| `m_purchase_price_change_plan` | 価格変更予定 (before/after) | 変更予定表示 |

---

## 6. API / UI Changes

### 6.1 新規 API エンドポイント

#### `GET /api/v1/estimates/compare-goods`

**Request:**
```
GET /api/v1/estimates/compare-goods?shopNo=1&goodsNoList=100,200,300&partnerNo=5&destinationNo=10
```

**Response DTO: `CompareGoodsResponse`**

```java
public record CompareGoodsResponse(
    Integer goodsNo,
    String goodsCode,
    String goodsName,
    String specification,
    String janCode,
    String makerName,          // m_goods.maker
    String supplierName,       // m_sales_goods.m_supplier
    Integer supplierNo,
    BigDecimal purchasePrice,  // 仕入価格
    BigDecimal nowGoodsPrice,  // 現行販売単価
    BigDecimal containNum,     // 入数
    BigDecimal changeContainNum, // 変更後入数
    String pricePlanInfo,      // 価格変更予定テキスト ("2026-05-01 より 100円→120円")
    BigDecimal planAfterPrice  // 変更後仕入価格（あれば）
) {}
```

### 6.2 フロントエンド新規ページ

**ルーティング:** `/estimates/compare`

**ファイル構成:**
```
frontend/
├── app/(authenticated)/estimates/compare/
│   └── page.tsx                          # ページラッパー
├── components/pages/estimate/
│   └── comparison.tsx                    # メインコンポーネント
└── types/
    └── estimate.ts                       # 型追加（CompareGoodsResponse等）
```

### 6.3 UI 設計

#### 画面レイアウト

```
┌──────────────────────────────────────────────────────────────┐
│ [← 見積一覧]              比較見積                           │
├──────────────────────────────────────────────────────────────┤
│ 条件設定                                                     │
│ ┌────────────┐ ┌────────────┐ ┌────────────┐                │
│ │ 店舗 ▼     │ │ 得意先 ▼   │ │ 配送先 ▼   │                │
│ └────────────┘ └────────────┘ └────────────┘                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│ 比較商品 (3/10)                          [＋ 商品を追加]     │
│                                                              │
│ ┌──────────────┬──────────────┬──────────────┬────────────┐  │
│ │              │ ★ 基準品     │ 代替品A      │ 代替品B    │  │
│ ├──────────────┼──────────────┼──────────────┼────────────┤  │
│ │ 商品コード   │ KAO-001      │ LION-001     │ SARAYA-01  │  │
│ │ 商品名       │ 花王 除菌… │ ライオン… │ サラヤ…  │  │
│ │ 規格         │ 5L           │ 4.5L         │ 5L         │  │
│ │ メーカー     │ 花王         │ ライオン     │ サラヤ     │  │
│ │ 仕入先       │ 花王プロ     │ ライオンH    │ サラヤ     │  │
│ ├──────────────┼──────────────┼──────────────┼────────────┤  │
│ │ 仕入価格     │ ¥1,200       │ ¥1,050  ▼   │ ¥1,100 ▼  │  │
│ │ 価格変更予定 │ 5/1→¥1,300  │ —            │ —          │  │
│ │ 入数         │ 3            │ 3            │ 3          │  │
│ ├──────────────┼──────────────┼──────────────┼────────────┤  │
│ │ 販売単価     │ [¥1,800   ]  │ [¥1,700   ]  │ [¥1,650 ]  │  │
│ │ 数量(ケース) │ [10       ]  │ [10       ]  │ [10     ]  │  │
│ ├──────────────┼──────────────┼──────────────┼────────────┤  │
│ │ 粗利額       │ ¥600         │ ¥650    ↑   │ ¥550       │  │
│ │ 粗利率       │ 33.3%        │ 38.2%   ↑   │ 33.3%      │  │
│ │ ケース粗利   │ ¥1,800       │ ¥1,950  ↑   │ ¥1,650     │  │
│ │ 合計粗利     │ ¥18,000      │ ¥19,500 ↑   │ ¥16,500    │  │
│ ├──────────────┼──────────────┼──────────────┼────────────┤  │
│ │              │              │ [見積作成→]  │ [見積作成→]│  │
│ │              │              │ [✕ 除外]     │ [✕ 除外]   │  │
│ └──────────────┴──────────────┴──────────────┴────────────┘  │
│                                                              │
│ ↑↓ = 基準品との差分表示 (緑=改善, 赤=悪化)                   │
│                                                              │
│ [☐ 得意先向け表示(仕入情報を隠す)]            [印刷]         │
└──────────────────────────────────────────────────────────────┘
```

#### 操作フロー

```
1. ページ遷移
   見積一覧 → [比較見積] ボタン → /estimates/compare

2. 条件設定
   店舗選択（admin時） → 得意先選択（任意） → 配送先選択（任意）

3. 基準品設定
   [＋ 商品を追加] → GoodsSearchDialog or コード入力
   → 最初の商品が「基準品」として ★ マーク付与
   → 基準品は後から変更可能（各列の「★ 基準品にする」ボタン）

4. 代替品追加
   [＋ 商品を追加] → 同様に検索・選択
   → 追加ごとに比較テーブルの列が増える

5. シミュレーション
   販売単価・数量を各商品で入力
   → 粗利額・粗利率・合計粗利がリアルタイム計算
   → 基準品との差分を ↑↓ 矢印 + 色で表示

6. アクション
   [見積作成→] → sessionStorage にデータ格納後、/estimates/create に遷移
     sessionStorage key: 'estimate-prefill'
     格納データ: { shopNo, partnerNo, destinationNo, details: [{ goodsNo, goodsCode,
                   goodsName, specification, goodsPrice, purchasePrice, containNum, supplierNo }] }
     見積作成画面はマウント時に sessionStorage をチェックし、存在すれば初期値に反映（使い捨て）
   [印刷] → 印刷モード切替（得意先向け / 社内向け）→ window.print()
```

#### コンポーネント構成

```typescript
// comparison.tsx - メインコンポーネント
EstimateComparisonPage
├── ConditionSection          // 店舗・得意先・配送先選択
│   ├── SearchableSelect      // (既存) 店舗
│   ├── SearchableSelect      // (既存) 得意先
│   └── SearchableSelect      // (既存) 配送先
├── ComparisonTable           // 比較テーブル本体
│   ├── ComparisonColumn      // 商品1列分
│   │   ├── GoodsInfoSection  // 商品基本情報（読み取り専用）
│   │   ├── PriceInfoSection  // 価格情報（admin のみ仕入価格表示）
│   │   ├── SimulationSection // 販売単価・数量入力 + 粗利計算
│   │   └── ActionSection     // 見積作成・除外ボタン
│   └── AddColumnButton       // [＋ 商品を追加]
└── GoodsSearchDialog         // (既存) 商品検索ダイアログ
```

#### フロントエンド型定義（追加分）

```typescript
// types/estimate.ts に追加

/** compare-goods API レスポンス（goodsNo ありの商品） */
export interface CompareGoodsResponse {
  goodsNo: number
  goodsCode: string
  goodsName: string
  specification: string | null
  janCode: string | null
  makerName: string | null
  supplierName: string | null
  supplierNo: number | null
  purchasePrice: number | null
  nowGoodsPrice: number | null
  containNum: number | null
  changeContainNum: number | null
  pricePlanInfo: string | null
  planAfterPrice: number | null
}

/** 比較テーブル内の1商品分のデータ（API取得品 + マスタ未登録品の両方を統一） */
export interface ComparisonGoodsData {
  goodsNo: number | null        // null = マスタ未登録（PRICE_PLAN/QUOTE_IMPORT由来）
  goodsCode: string
  goodsName: string
  specification: string | null
  janCode: string | null
  makerName: string | null
  supplierName: string | null
  supplierNo: number | null
  purchasePrice: number | null
  nowGoodsPrice: number | null
  containNum: number | null
  changeContainNum: number | null
  pricePlanInfo: string | null
  planAfterPrice: number | null
  source: 'GOODS' | 'PRICE_PLAN' | 'QUOTE_IMPORT'  // データの出所
}

export interface ComparisonItem {
  id: string                        // UUID (React key用)
  goods: ComparisonGoodsData        // 商品データ（API取得 or 検索結果から直接セット）
  isBase: boolean                   // 基準品フラグ
  simulatedPrice: number | null     // 入力: 販売単価
  simulatedQty: number | null       // 入力: 数量（ケース）
}
```

#### 状態管理

```typescript
// ── サーバーステート（TanStack Query）──
// マスタ取得: 既存 hook をそのまま使用
const { data: shops } = useShops(isAdmin)
const { data: partners } = usePartners(shopNo)
const { data: destinations } = useDestinations(partnerNo)

// 比較商品の価格一括取得（goodsNo あり の商品のみ）
const registeredGoodsNos = items
  .filter(item => item.goods.goodsNo != null)
  .map(item => item.goods.goodsNo!)

const { data: compareGoods, isLoading } = useQuery({
  queryKey: ['compare-goods', shopNo, registeredGoodsNos, partnerNo, destinationNo],
  queryFn: () => api.get<CompareGoodsResponse[]>(
    `/estimates/compare-goods?shopNo=${shopNo}&goodsNoList=${registeredGoodsNos.join(',')}`
    + (partnerNo ? `&partnerNo=${partnerNo}` : '')
    + (destinationNo ? `&destinationNo=${destinationNo}` : '')
  ),
  enabled: registeredGoodsNos.length > 0 && shopNo != null,
})
// → partnerNo/destinationNo 変更時に queryKey が変わり自動再フェッチ（M-3 対応）

// ── ローカルステート（useState）──
// 条件設定
const [shopNo, setShopNo] = useState<number | null>(null)
const [partnerNo, setPartnerNo] = useState<number | null>(null)
const [destinationNo, setDestinationNo] = useState<number | null>(null)

// 比較商品リスト + シミュレーション入力値
const [items, setItems] = useState<ComparisonItem[]>([])

// sessionStorage に保存/復元（ページ遷移で消えないように）
useEffect(() => {
  sessionStorage.setItem('estimate-compare', JSON.stringify({ shopNo, partnerNo, destinationNo, items }))
}, [shopNo, partnerNo, destinationNo, items])
```

**商品追加時のデータフロー（M-1 対応）:**
```
商品検索（GoodsSearchDialog / コード検索）
  │
  ├─ goodsNo あり（マスタ登録済）
  │   → items に仮追加 → compare-goods API が自動再フェッチ → 価格情報をマージ
  │
  └─ goodsNo なし（PRICE_PLAN / QUOTE_IMPORT 由来）
      → 検索結果の情報を ComparisonGoodsData に直接マッピングして items に追加
      → compare-goods API は呼ばない（goodsNo がないため）
```

#### 粗利計算ロジック（共通ユーティリティ化 — m-1 対応）

既存の見積フォーム (`form.tsx`) と同じ計算ロジックを使用。
重複を避けるため `lib/estimate-calc.ts` に切り出し、両画面から import する。

```typescript
// lib/estimate-calc.ts
export const calcProfit = (price: number, cost: number) => price - cost
export const calcProfitRate = (price: number, cost: number) => (1 - cost / price) * 100
export const calcCaseProfit = (profit: number, containNum: number) => profit * containNum
export const calcTotalProfit = (caseProfit: number, qty: number) => caseProfit * qty
```

---

## 7. Edge Cases

| ケース | 対応 |
|--------|------|
| 商品が v_estimate_goods に存在しない | m_goods の情報のみ表示（価格は null → 手入力促す） |
| goodsNo=null の商品（マスタ未登録） | 検索結果の情報をそのまま使用。compare-goods API は呼ばない |
| 得意先未指定で特値が取れない | 通常価格 (v_estimate_goods) のみ使用 |
| 得意先変更時の価格更新 | TanStack Query の queryKey に partnerNo を含め自動再フェッチ |
| 同一商品を2回追加 | goodsNo 重複チェック → トースト警告（goodsNo=null は goodsCode で判定） |
| 10商品上限を超えて追加 | ボタン非活性 + ツールチップ表示 |
| 基準品を除外 | 残った商品の先頭を自動で基準品に昇格 |
| 全商品を除外 | 空状態表示（「商品を追加してください」） |
| 販売単価が仕入価格以下（赤字） | 粗利額を赤字表示 + 警告アイコン |
| 見積作成遷移時にpartnerNo未設定 | 見積作成画面で得意先を選択してもらう（goodsのみ引き継ぎ） |
| compare-goods API ローディング中 | 価格列にスケルトン表示、シミュレーション入力は無効化 |
| 印刷時の仕入情報表示 | チェックボックスで「得意先向け（価格のみ）/ 社内向け（全項目）」切替 |

---

## 8. Risks and Mitigations

| リスク | 影響 | 対策 |
|--------|------|------|
| 比較商品の価格データが古い | 不正確な比較 | API取得時にリアルタイムでビュー参照（キャッシュなし） |
| 横スクロールUI の使いにくさ | UX低下 | 10商品上限 + sticky 行ヘッダー + 基準品列固定 |
| 得意先変更時の特値再取得 | パフォーマンス | 得意先変更時にのみ一括再取得（debounce） |
| 印刷レイアウトの崩れ | 紙出力品質 | `@media print` で横向き + カラム数制限テスト |

---

## 9. Rollout Plan

### Phase 1（MVP — 本設計の対象）
- 比較画面の実装（商品追加・比較テーブル・シミュレーション）
- 見積作成への遷移連携
- 印刷対応（`window.print()`）

### Phase 2（将来拡張）
- 比較見積書PDF出力（専用フォーマット）
- 比較セッションのDB保存・共有
- カテゴリベースの代替品自動サジェスト（categoryNo 活用）
- 既存見積明細からの比較画面起動（「この商品の代替を探す」）

---

## 10. 実装チェックリスト

### Backend
- [ ] `CompareGoodsResponse` DTO 作成
- [ ] `EstimateController` に `GET /compare-goods` エンドポイント追加
- [ ] `goodsNoList` 空チェックバリデーション
- [ ] 既存 `VEstimateGoodsService` / `VEstimateGoodsSpecialService` 活用

### Frontend
- [ ] `CompareGoodsResponse`, `ComparisonGoodsData`, `ComparisonItem` 型定義追加 (`types/estimate.ts`)
- [ ] 粗利計算ロジック共通化 (`lib/estimate-calc.ts`) + 見積フォームからの import 切替
- [ ] `/estimates/compare` ページ作成
- [ ] `EstimateComparisonPage` コンポーネント実装
- [ ] 条件設定セクション（店舗・得意先・配送先）— 既存 hook 使用
- [ ] 比較テーブル（横並びカラム表示 + 基準品列固定 + 横スクロール）
- [ ] 商品追加フロー（goodsNo あり → API 取得 / null → 検索結果直接マッピング）
- [ ] TanStack Query で compare-goods API 呼び出し（queryKey に partnerNo/destinationNo 含む）
- [ ] シミュレーション入力 + 粗利リアルタイム計算
- [ ] 基準品との差分表示（色分け + 矢印）
- [ ] ローディング状態（API取得中のスケルトン表示）
- [ ] GoodsSearchDialog 連携（既存流用）
- [ ] 見積作成画面への遷移連携（sessionStorage `estimate-prefill` 経由）
- [ ] 見積作成画面のマウント時 sessionStorage チェック追加 (`form.tsx` 小改修)
- [ ] sessionStorage による比較セッション状態保存
- [ ] 印刷用レイアウト（`@media print` + 得意先向け/社内向け切替）
- [ ] サイドバー「見積・財務」グループ内「見積一覧」直下に「比較見積」追加（icon: `ArrowLeftRight`）
