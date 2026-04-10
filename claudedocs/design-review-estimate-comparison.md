# 比較見積機能 設計レビュー

**対象:** `claudedocs/design-estimate-comparison.md`
**レビュー日:** 2026-04-09
**ステータス:** Needs Revision（3件の修正後に承認可）

---

## サマリー

全体的に良い設計。既存のビュー・サービスを最大限活用する方針は適切で、新規DBテーブルなしの判断も正しい。ただし、**見積作成画面へのデータ引き継ぎ方法** に実装不可能な前提があり、修正が必要。

---

## Critical Issues（実装前に修正必須）

### C-1: 見積作成遷移時の query params によるデータ引き継ぎは実装不可

**設計書の記載（操作フロー 6）:**
> `[見積作成→] → /estimates/create に遷移`
> `query: shopNo, partnerNo, destinationNo, goodsNo, goodsCode, goodsPrice, purchasePrice, containNum を引き継ぎ`

**問題点:**
1. 既存の `EstimateFormPage`（`form.tsx`）は query params からの初期値注入に **未対応**。`estimateNo` prop のみ受け付ける
2. 商品データを URL query params で渡すと、1商品あたり約100-200文字 → 10商品で2KB近くになり、ブラウザの URL 長制限（2KB-8KB）に抵触するリスク
3. 商品名に日本語を含むため、URL エンコード後さらに膨張する

**修正案:**
`sessionStorage` を中間ストアとして使用する。既存の `useSearchParamsStorage` パターンに倣い、比較画面で選択した商品データを sessionStorage に格納し、見積作成画面で読み取る。

```typescript
// 比較画面側（遷移前）
sessionStorage.setItem('estimate-prefill', JSON.stringify({
  shopNo, partnerNo, destinationNo,
  details: [{ goodsNo, goodsCode, goodsName, goodsPrice, purchasePrice, containNum }]
}))
router.push('/estimates/create')

// 見積作成画面側（マウント時）
const prefill = sessionStorage.getItem('estimate-prefill')
if (prefill) {
  const data = JSON.parse(prefill)
  // フォーム初期値に反映
  sessionStorage.removeItem('estimate-prefill') // 使い捨て
}
```

見積作成画面の `form.tsx` への改修は最小限（マウント時の sessionStorage チェック追加のみ）。

---

## Major Issues（対応推奨）

### M-1: 新規API `compare-goods` が goodsNo 必須だが、未登録商品（goodsNo=null）を比較できない

**問題点:**
`GoodsSearchDialog` は `source: 'PRICE_PLAN'` の商品を返す場合があり、これらは `goodsNo: null`（まだ商品マスタに未登録）。設計書の API は `goodsNoList: List<Integer>` で受け取るため、こうした商品の価格情報を取得できない。

**影響:** 見積取込から追加された仕入価格変更予定の商品（マスタ未登録）を比較対象にできない。

**修正案:**
比較商品の追加方法を2パターンに分ける:
- **goodsNo あり** → `compare-goods` API で価格情報を一括取得
- **goodsNo なし**（PRICE_PLAN/QUOTE_IMPORT 由来）→ `GoodsSearchDialog` / `goods-search` API から返される情報をそのままフロントエンド側の `ComparisonItem` に直接セット

フロントエンドの `ComparisonItem.goods` の型で `goodsNo: number | null` を許容する（設計書では `goodsNo: number` と非null になっている）。

### M-2: 状態管理で「TanStack Query 不要」は不適切

**設計書の記載（状態管理）:**
> `ローカルステート（useState）で管理。TanStack Query不要（比較はCRUD操作なし）`

**問題点:**
- `compare-goods` API 呼び出し、得意先一覧（`usePartners`）、配送先一覧（`useDestinations`）、仕入先一覧（`useSuppliers`）は TanStack Query を使うべき
- 既存パターンでもマスタ取得は全て TanStack Query（`useMasters` hook）
- API 呼び出しを `useState` + `useEffect` で直書きすると、既存コードベースとの一貫性が崩れる

**修正案:**
- マスタ取得: 既存の `useShops`, `usePartners`, `useDestinations` hook をそのまま使用（TanStack Query）
- 比較商品の価格一括取得: TanStack Query の `useQuery` で実装（`enabled` で条件制御）
- 比較テーブル内のシミュレーション入力値（販売単価・数量）: `useState` で管理 ← これだけローカル

### M-3: 得意先変更時の価格再取得フローが未定義

**問題点:**
得意先を変更すると特値（`VEstimateGoodsSpecial`）が変わるため、既に追加済みの比較商品の価格情報を再取得する必要がある。設計書ではリスク表で「debounce」とだけ記載されているが、具体的なフローがない。

**修正案:**
得意先・配送先の変更を TanStack Query の queryKey に含め、変更時に自動再フェッチさせる。

```typescript
useQuery({
  queryKey: ['compare-goods', shopNo, goodsNoList, partnerNo, destinationNo],
  queryFn: () => fetchCompareGoods(shopNo, goodsNoList, partnerNo, destinationNo),
  enabled: goodsNoList.length > 0 && shopNo != null,
})
```

これで得意先変更時の再取得が自然に実現され、debounce ロジックも不要になる。

---

## Minor Issues（改善提案）

### m-1: 粗利計算ロジックの重複

**問題点:**
`calcProfit`, `calcProfitRate`, `calcCaseProfit` は見積フォーム (`form.tsx`) にも同じロジックがある。比較画面でも同じものを書くと重複になる。

**修正案:**
共通ユーティリティ `lib/estimate-calc.ts` に切り出し、両画面から import する。

### m-2: 基準品の変更操作「ドラッグ or ボタン」は過剰

**問題点:**
ドラッグ操作の実装は shadcn/ui 標準では提供されず、`dnd-kit` 等の追加ライブラリが必要。MVP としてはオーバーエンジニアリング。

**修正案:**
Phase 1 では各列の「★ 基準品にする」ボタンのみで十分。ドラッグは Phase 2 で検討。

### m-3: 印刷レイアウトの考慮不足

**問題点:**
admin限定の仕入価格・粗利率は印刷時に出力するか未定義。得意先に渡す資料であれば仕入情報は除外すべき。

**修正案:**
印刷モードでは得意先向け表示（商品名・規格・販売単価・入数のみ）と社内向け表示（全項目）を切り替えるチェックボックスを設ける。

### m-4: サイドバーメニューの配置場所が未指定

**問題点:**
チェックリストに「サイドバーメニュー追加」とあるが、どのグループのどの位置に配置するか未記載。

**修正案:**
既存の「見積・財務」グループ内、「見積一覧」の直下に配置。

```typescript
{ title: '比較見積', url: '/estimates/compare', icon: ArrowLeftRight }
```

---

## Tech Stack チェックリスト

### Spring Boot
| チェック項目 | 結果 |
|-------------|------|
| Layer構成（Controller→Service→Repository） | OK — Controller は薄く、既存 Service を流用 |
| DTO分離（Entity 直接返却なし） | OK — `CompareGoodsResponse` record を定義 |
| コンストラクタインジェクション | OK — 既存 Controller は `@RequiredArgsConstructor` |
| バリデーション | **要追記** — `goodsNoList` が空リストの場合のバリデーション |
| N+1問題 | OK — `VEstimateGoodsService.findGoods()` は IN 句で一括取得 |
| Flyway Migration | OK — DB変更なし |

### Next.js / React
| チェック項目 | 結果 |
|-------------|------|
| TypeScript 厳密性 | **要修正** — `CompareGoodsResponse.goodsNo` を `number \| null` に |
| Immutable 更新 | OK — `useState` + スプレッド想定 |
| TanStack Query 使用 | **要修正** — M-2 参照。API呼び出しには TanStack Query を使うべき |
| ローディング/エラー状態 | **要追記** — 商品追加時のローディング表示が未設計 |
| コンポーネント分割 | OK — 適切なサブコンポーネント構成 |

---

## 判定: ~~Needs Revision~~ → **Approved** (Rev.2 で全件対応済)

---

## 対応結果（Rev.2 反映）

| 指摘ID | 判定 | 対応内容 |
|--------|------|----------|
| C-1 | Accept | sessionStorage `estimate-prefill` 方式に変更。form.tsx への小改修を含む |
| M-1 | Accept | `ComparisonGoodsData` 型を新設し goodsNo=null を許容。データフロー図を追加 |
| M-2 | Accept | マスタ取得は既存hook、compare-goods API は useQuery で実装。ローカルステートはシミュレーション値のみ |
| M-3 | Accept | queryKey に partnerNo/destinationNo を含め自動再フェッチ |
| m-1 | Accept | `lib/estimate-calc.ts` に共通化 |
| m-2 | Accept | ドラッグ削除、「★ 基準品にする」ボタンのみに変更 |
| m-3 | Accept | 印刷時の得意先向け/社内向け切替チェックボックスを追加 |
| m-4 | Accept | 「見積・財務」グループ内「見積一覧」直下に配置を明記 |
