# 設計書: 作成済み見積から比較見積を作成する機能

- **作成日**: 2026-04-10
- **作成者**: kazuki + Claude
- **ブランチ**: `feat/quote-import-and-nfkc-search`
- **ステータス**: レビュー待ち

---

## 1. Background / Problem

### 現状の課題
- 比較見積画面（`/estimates/compare`）は、商品検索ダイアログから1件ずつ商品を追加する UI のみ提供されている
- 既に作成済みの見積について「この見積の全商品を横並びで比較したい」「販売単価を動かして粗利シミュレーションしたい」というユースケースに対応できない
- 現在は **比較 → 見積作成** の一方向導線のみ（`estimate-prefill` sessionStorage 経由）

### やりたいこと
作成済みの見積（`/estimates/{estimateNo}`）から **ワンクリックで比較見積画面を開き、その見積の全明細を比較表に引き継ぐ**。
ユーザーは引き継がれた明細に対して販売単価・数量のシミュレーション、基準品の切替、印刷が可能。

### なぜ今か
- 見積修正フローで「他の価格パターンを検討したい」というニーズがあり、既存の比較画面を再利用すれば低コストで提供可能
- `/compare-goods` API・`estimate-calc.ts`・`ComparisonTable` など既存資産がそのまま使える

---

## 2. Requirements

### 機能要件
| # | 要件 |
|---|---|
| FR-1 | 見積詳細画面に「比較見積へ」ボタンを配置する |
| FR-2 | ボタンは **ステータスが `00`（作成）または `20`（修正）** のときのみ表示する（= `isEditable` と同条件） |
| FR-3 | クリック時、見積の `shopNo` / `partnerNo` / `destinationNo` を比較画面のヘッダに引き継ぐ |
| FR-4 | 見積明細の全商品を比較画面の比較対象として追加する |
| FR-5 | 各商品の **シミュレーション販売単価（`simulatedPrice`）の初期値に、見積明細の `goodsPrice` をセットする** |
| FR-6 | 明細件数が **11件以上の場合は先頭10件（displayOrder 昇順）のみ引き継ぎ**、警告トーストで通知する |
| FR-7 | 未登録商品（`goodsNo == null`）は既存の「見積修正」と同仕様で、商品名・規格・原価・入数・販売単価のみ引き継ぎ、`supplierNo`/`supplierName` は `null` とする（`TEstimateDetail` に supplier カラムが存在しないため復元不可） |
| FR-8 | 登録済み商品（`goodsNo != null`）は既存の `/compare-goods` API で最新のマスタ価格・仕入先情報を自動補完する（enrichedItems ロジック再利用） |
| FR-9 | 最初の1件目を自動的に基準品（`isBase: true`）に設定する |
| FR-10 | 比較画面に遷移した際、画面上部に「見積 #xxx から引継ぎ」バッジを表示する。バッジはクリックで元の見積詳細画面（`/estimates/{sourceEstimateNo}`）に遷移できるリンクとする |
| FR-11 | 比較画面マウント時、既存の `estimate-compare` に **items が1件以上含まれる場合**、確認ダイアログを表示して handoff 適用（上書き）/ 保留 を選択させる。items が0件の場合は確認なしで handoff を適用する |

### 非機能要件
- **パフォーマンス**: 追加のAPIコールなし（既存の `GET /estimates/{no}` 応答をそのまま変換するだけ）
- **データ整合性**: sessionStorage の handoff キーは使用後に必ず削除する
- **セキュリティ**: 既存の認可（`/estimates/{no}` GET 時の店舗権限チェック）で担保される
- **互換性**: 既存の `/estimates/compare` 単体起動（商品検索からの追加）は従来どおり動作する

---

## 3. Constraints

### 技術制約
- **バックエンド変更なし**: `TEstimateDetail` に `supplier_no` カラムが無いため、未登録商品の仕入先情報はそもそもDB上に存在しない → フロントのみで実装可能な範囲に仕様を合わせる（Q4 回答準拠）
- **比較画面の上限10件**: 現行 `MAX_ITEMS = 10` を維持。見積が11件以上の明細を持つ場合は切り詰めで対応（Q3 回答準拠）
- **sessionStorage 競合**: 現行 `/estimates/compare` は既に `estimate-compare` キーで独自の画面状態を永続化している（`comparison.tsx:23, 57-80`）。このキーを handoff にそのまま使うと、遷移前の画面状態を上書きしてしまう危険がある → **別のキーを用いる**

### ビジネス制約
- 既存の比較画面のユーザー体験を壊さない（商品検索から立ち上げる使い方は変わらない）

### スケジュール制約
- 特になし

---

## 4. Proposed Solution

### アーキテクチャ概要

```
[見積詳細ページ]                    [比較見積ページ]
components/pages/                  components/pages/
estimate/detail.tsx                estimate/comparison.tsx
        │
        │ 1) クリック
        ▼
  "比較見積へ" ボタン
        │
        │ 2) 見積データを変換
        ▼
  buildCompareHandoff(estimate)  ─── 純粋関数（テスト容易）
        │
        │ 3) sessionStorage に保存
        ▼
  sessionStorage.setItem(
    'estimate-compare-handoff',
    JSON.stringify(handoff)
  )
        │
        │ 4) router.push('/estimates/compare')
        ▼
  /estimates/compare
        │
        │ 5) マウント時 useEffect で
        │    handoff キーを優先読込
        ▼
  restoreFromHandoff() or restoreFromSession()
        │
        │ 6) items, shopNo, partnerNo, destinationNo を state に反映
        ▼
  以降は既存の enrichedItems / compareQuery フローに乗る
```

### 設計の要点

1. **Handoff は独立した sessionStorage キー `estimate-compare-handoff` を使う**
   - 比較画面の自己永続化キー `estimate-compare` と分離することで、既存の画面状態復元ロジックを壊さない
   - 比較画面のマウント時、**handoff を優先** → あれば読み込んで削除 → state 初期化、なければ従来どおり `estimate-compare` から復元
   - これにより、handoff → compare 遷移時の画面は「引き継いだ状態」で起動し、その後の状態変更は従来通り `estimate-compare` に自動保存される

2. **変換ロジックは純粋関数に切り出す**
   - `lib/estimate-handoff.ts`（新規）に `buildCompareHandoffFromEstimate(estimate)` を配置
   - 単体テスト容易・副作用なし

3. **比較画面側の変更は最小**
   - `comparison.tsx` の useEffect 1 箇所に handoff 読み込み分岐を追加
   - 既存の `enrichedItems` / `compareQuery` は **登録済み商品のみ API 呼び出し** する設計になっているため、未登録商品が混ざっていても問題なし（`comparison.tsx:84-90` の filter で担保済み）

4. **「引継ぎ元見積番号」の表示**
   - handoff payload に `sourceEstimateNo` を含め、比較画面の state に保持
   - ページタイトル横またはヘッダカード内に「見積 #123 から引継ぎ」バッジを表示
   - リセットボタンを押すか、商品を1件も無しの状態にした場合は表示を消す（任意）

### コンポーネント変更マップ

| ファイル | 種別 | 変更内容 |
|---|---|---|
| `frontend/lib/estimate-handoff.ts` | **新規** | `buildCompareHandoffFromEstimate()` 純粋関数 |
| `frontend/components/pages/estimate/detail.tsx` | 変更 | 「比較見積へ」ボタン追加（`isEditable()` の条件下のみ）+ handoff 保存 + 遷移 |
| `frontend/components/pages/estimate/comparison.tsx` | 変更 | mount useEffect に handoff 優先読込ロジック、`sourceEstimateNo` state、引継ぎ元バッジ表示 |
| `frontend/types/estimate.ts` | 変更 | `CompareHandoffPayload` 型追加 |

バックエンド: **変更なし**。

---

## 5. Data Model / DB Changes

**DB変更なし**。

### フロントの型定義（新規）

`frontend/types/estimate.ts` に追記：

```ts
/**
 * 見積 → 比較見積への引継ぎペイロード
 * sessionStorage key: 'estimate-compare-handoff'
 */
export interface CompareHandoffPayload {
  sourceEstimateNo: number
  shopNo: number
  partnerNo: number | null
  destinationNo: number | null
  items: Array<{
    goodsNo: number | null
    goodsCode: string
    goodsName: string
    specification: string | null
    purchasePrice: number | null
    containNum: number | null
    /** 見積の goodsPrice → 比較画面の simulatedPrice 初期値 */
    simulatedPrice: number | null
  }>
}
```

---

## 6. API / UI Changes

### API 変更
**なし**。既存の `GET /api/v1/estimates/{estimateNo}` と `GET /api/v1/estimates/compare-goods` をそのまま使用。

### UI 変更

#### 6-1. 見積詳細ページ（`/estimates/{estimateNo}`）

**操作ボタン領域**（`detail.tsx:114-147`）の「PDF」と「戻る」の間に「比較見積へ」ボタンを追加。

```
[修正] [削除] [印刷] [PDF] [比較見積へ] [戻る]
                          ^^^^^^^^^^^
                          isEditable() 条件下のみ表示
```

ボタン仕様：
- ラベル: `比較見積へ`
- アイコン: `BarChart3`（lucide-react）
- variant: `outline`
- 表示条件: `isEditable(est)` が true（ステータス `00` or `20`）
- クリック動作:
  1. `buildCompareHandoffFromEstimate(est)` で payload 構築
  2. 明細が11件以上なら `toast.warning('明細が10件を超えています。先頭10件のみ比較対象に追加します（残り{N}件は除外）')`
  3. `sessionStorage.setItem('estimate-compare-handoff', JSON.stringify(payload))`
  4. `router.push('/estimates/compare')`

#### 6-2. 比較見積ページ（`/estimates/compare`）

##### 6-2-1. 引継ぎ元バッジ（FR-10）

PageHeader と条件セクションの間に表示する。

```
比較見積                              [見積一覧へ]
─────────────────────────────────────────────
[📄 見積 #123 から引継ぎ →]  ← クリックで /estimates/123 へ遷移
─────────────────────────────────────────────
店舗 [小田光商事]  得意先 [株式会社A]  配送先 [－]
```

仕様：
- `sourceEstimateNo` state が null でない場合のみ表示
- shadcn/ui の `Badge` または軽量な `<Link>` コンポーネントで実装
- ラベル: `見積 #{sourceEstimateNo} から引継ぎ`
- アイコン: `FileText`（lucide-react、左）+ `ArrowUpRight`（右、クリック可能であることを示唆）
- クリック動作: `router.push('/estimates/{sourceEstimateNo}')`
- 印刷時（`print:hidden`）は非表示
- バッジ右側に小さな × ボタンを配置し、引継ぎ元表示をクリアできる（items は保持する）
  - クリック時: `setSourceEstimateNo(null)` のみ。items には影響させない
  - 理由: ユーザーが「引継ぎ関係をリセットして独立した比較見積として使いたい」ケースに対応

##### 6-2-2. handoff 優先読込 + 確認ダイアログ（FR-11 / EC-7 対応）

**restore ロジック変更**（`comparison.tsx:56-69`）:

```tsx
const [sourceEstimateNo, setSourceEstimateNo] = useState<number | null>(null)
// 確認ダイアログ用の保留 handoff
const [pendingHandoff, setPendingHandoff] = useState<CompareHandoffPayload | null>(null)

useEffect(() => {
  const handoffRaw = sessionStorage.getItem('estimate-compare-handoff')
  const sessionRaw = sessionStorage.getItem(SESSION_KEY)

  // 既存 session に有効な items が何件あるか
  let existingItemsCount = 0
  let existingSession: { shopNo?: string; partnerNo?: string; destinationNo?: string; items?: ComparisonItem[] } | null = null
  if (sessionRaw) {
    try {
      existingSession = JSON.parse(sessionRaw)
      existingItemsCount = existingSession?.items?.length ?? 0
    } catch { /* ignore */ }
  }

  if (handoffRaw) {
    try {
      const handoff = JSON.parse(handoffRaw) as CompareHandoffPayload

      if (existingItemsCount > 0) {
        // 既存作業あり → 確認ダイアログを出す（state に保留）
        // 既存 session の state を先に復元しておく（キャンセル時のため）
        if (existingSession) {
          if (existingSession.shopNo) setShopNo(String(existingSession.shopNo))
          if (existingSession.partnerNo) setPartnerNo(String(existingSession.partnerNo))
          if (existingSession.destinationNo) setDestinationNo(String(existingSession.destinationNo))
          if (existingSession.items) setItems(existingSession.items)
        }
        setPendingHandoff(handoff)
        // handoff はまだ削除しない（ユーザー選択待ち）
        setRestored(true)
        return
      }

      // 既存作業なし → 即座に handoff を適用
      applyHandoff(handoff)
      sessionStorage.removeItem('estimate-compare-handoff')
      setRestored(true)
      return
    } catch {
      // JSON破損 → handoff 削除してフォールバック
      sessionStorage.removeItem('estimate-compare-handoff')
    }
  }

  // 通常の自己永続化キーから復元
  if (existingSession) {
    if (existingSession.shopNo) setShopNo(String(existingSession.shopNo))
    if (existingSession.partnerNo) setPartnerNo(String(existingSession.partnerNo))
    if (existingSession.destinationNo) setDestinationNo(String(existingSession.destinationNo))
    if (existingSession.items) setItems(existingSession.items)
  }
  setRestored(true)
}, [])

// handoff を state に反映する共通関数
const applyHandoff = useCallback((handoff: CompareHandoffPayload) => {
  setShopNo(String(handoff.shopNo))
  setPartnerNo(handoff.partnerNo ? String(handoff.partnerNo) : '')
  setDestinationNo(handoff.destinationNo ? String(handoff.destinationNo) : '')
  setSourceEstimateNo(handoff.sourceEstimateNo)
  setItems(handoff.items.map((it, idx) => ({
    id: crypto.randomUUID(),
    goods: {
      goodsNo: it.goodsNo,
      goodsCode: it.goodsCode,
      goodsName: it.goodsName,
      specification: it.specification,
      janCode: null,
      makerName: null,
      supplierName: null,
      supplierNo: null,
      purchasePrice: it.purchasePrice,
      nowGoodsPrice: null,
      containNum: it.containNum,
      changeContainNum: null,
      pricePlanInfo: null,
      planAfterPrice: null,
      source: it.goodsNo != null ? 'GOODS' : 'QUOTE_IMPORT',
    },
    isBase: idx === 0, // 先頭を基準品に
    simulatedPrice: it.simulatedPrice, // 見積の goodsPrice を初期値
    simulatedQty: null,
  })))
}, [])

// ダイアログ「置き換える」クリック時
const handleConfirmHandoff = () => {
  if (!pendingHandoff) return
  applyHandoff(pendingHandoff)
  sessionStorage.removeItem('estimate-compare-handoff')
  setPendingHandoff(null)
}

// ダイアログ「キャンセル」クリック時
const handleCancelHandoff = () => {
  // handoff は削除せず残す。画面上部に再選択バーを表示するため
  setPendingHandoff(null)
  // 再選択バー表示用に別 state で保持
  setDeferredHandoff(pendingHandoff)
}
```

##### 6-2-3. 確認ダイアログ（shadcn/ui AlertDialog）

`pendingHandoff != null` の間表示する：

```
┌──────────────────────────────────────────────┐
│ 比較内容を置き換えますか？                    │
├──────────────────────────────────────────────┤
│ 現在、比較見積に {N} 件の商品が表示されて     │
│ います。                                      │
│                                               │
│ 見積 #{sourceEstimateNo} から引き継いだ       │
│ 内容（{M} 件）で置き換えますか？              │
│                                               │
│ ※置き換えると現在の入力内容は失われます      │
├──────────────────────────────────────────────┤
│              [キャンセル]  [置き換える]       │
└──────────────────────────────────────────────┘
```

- **置き換える**: `applyHandoff(pendingHandoff)` → handoff 削除 → 比較画面が見積内容で初期化される
- **キャンセル**: ダイアログを閉じて現状維持。ただし handoff は **sessionStorage に残したまま** `deferredHandoff` state に退避し、画面上部に再選択バーを表示（6-2-4）

##### 6-2-4. 引継ぎ保留バー（キャンセル後の UX）

キャンセル時に PageHeader 下に表示する通知バー：

```
[!] 見積 #123 からの引継ぎが保留されています  [今すぐ切替] [破棄]
```

- **今すぐ切替**: 確認ダイアログを再度表示（または直接 `applyHandoff`）
- **破棄**: `sessionStorage.removeItem('estimate-compare-handoff')` + `setDeferredHandoff(null)` → バー消去
- バーは `deferredHandoff != null` のときだけ表示
- リロードしても handoff が消費されていないため再度マウント時に同じフローに入る

##### 6-2-5. state 追加（単一 state に統合）

レビュー指摘 M-1 を反映し、`pendingHandoff` / `deferredHandoff` の 2 state を **単一 state** に統合する。
phase によって UI 表示を切り替えることで、state 同期バグを防ぎ derived value だけで描画判定可能にする。

```tsx
type HandoffState =
  | { phase: 'pending'; payload: CompareHandoffPayload }   // 確認ダイアログ表示中
  | { phase: 'deferred'; payload: CompareHandoffPayload }  // キャンセル後、保留バー表示中
  | null

const [sourceEstimateNo, setSourceEstimateNo] = useState<number | null>(null)
const [handoff, setHandoff] = useState<HandoffState>(null)

// derived values
const showConfirmDialog = handoff?.phase === 'pending'
const showDeferredBar = handoff?.phase === 'deferred'
```

ハンドラ：

```tsx
// ダイアログ「置き換える」
const handleConfirmHandoff = () => {
  if (handoff?.phase !== 'pending') return
  applyHandoff(handoff.payload)
  sessionStorage.removeItem('estimate-compare-handoff')
  setHandoff(null)
}

// ダイアログ「キャンセル」 → deferred に遷移
const handleCancelHandoff = () => {
  if (handoff?.phase !== 'pending') return
  setHandoff({ phase: 'deferred', payload: handoff.payload })
  // sessionStorage の handoff キーは残す（リロード耐性）
}

// 保留バー「今すぐ切替」 → pending に戻す
const handleResumeHandoff = () => {
  if (handoff?.phase !== 'deferred') return
  setHandoff({ phase: 'pending', payload: handoff.payload })
}

// 保留バー「破棄」
const handleDiscardHandoff = () => {
  sessionStorage.removeItem('estimate-compare-handoff')
  setHandoff(null)
}
```

##### 6-2-6. session 復元ロジック共通化（m-3 対応）

`if (existingSession) { ... }` の重複を避けるため共通関数に抽出する：

```tsx
const restoreSessionState = useCallback((session: SessionState) => {
  if (session.shopNo) setShopNo(String(session.shopNo))
  if (session.partnerNo) setPartnerNo(String(session.partnerNo))
  if (session.destinationNo) setDestinationNo(String(session.destinationNo))
  if (session.items) setItems(session.items)
}, [])
```

##### 6-2-7. sessionStorage 失敗時の挙動明確化（m-4 対応）

「比較見積へ」ボタンクリック時の handoff 保存に失敗した場合：
```tsx
try {
  sessionStorage.setItem('estimate-compare-handoff', JSON.stringify(payload))
  router.push('/estimates/compare')
} catch (e) {
  toast.error('比較画面への引継ぎに失敗しました。ブラウザのストレージ容量を確認してください。', {
    duration: 6000,
  })
  // 遷移せず現画面に留まる（ユーザーは再試行可能）
}
```

### 変換ロジック: `lib/estimate-handoff.ts`（新規）

```ts
import type { EstimateResponse, CompareHandoffPayload } from '@/types/estimate'

const MAX_HANDOFF_ITEMS = 10

export interface BuildCompareHandoffResult {
  payload: CompareHandoffPayload
  truncated: boolean
}

/**
 * 見積レスポンスから比較画面への引継ぎペイロードを構築する。
 * - 明細は displayOrder 昇順
 * - 最大 10 件で切り詰め（truncated フラグで通知用に返却）
 * - simulatedPrice の初期値は見積の goodsPrice
 */
export function buildCompareHandoffFromEstimate(
  estimate: EstimateResponse,
): BuildCompareHandoffResult {
  const allDetails = (estimate.details ?? [])
    .slice()
    .sort((a, b) => a.displayOrder - b.displayOrder)

  const truncated = allDetails.length > MAX_HANDOFF_ITEMS
  const details = allDetails.slice(0, MAX_HANDOFF_ITEMS)

  return {
    truncated,
    payload: {
      sourceEstimateNo: estimate.estimateNo,
      shopNo: estimate.shopNo,
      partnerNo: estimate.partnerNo,
      destinationNo: estimate.destinationNo,
      items: details.map((d) => ({
        goodsNo: d.goodsNo,
        goodsCode: d.goodsCode ?? '',
        goodsName: d.goodsName ?? '',
        specification: d.specification,
        purchasePrice: d.purchasePrice != null ? Number(d.purchasePrice) : null,
        containNum: d.containNum != null ? Number(d.containNum) : null,
        simulatedPrice: d.goodsPrice != null ? Number(d.goodsPrice) : null,
      })),
    },
  }
}
```

---

## 7. Edge Cases

| # | シナリオ | 挙動 |
|---|---|---|
| EC-1 | 見積明細が0件 | 引継ぎは実行するが items は空配列。比較画面は「商品を追加」初期状態になる。ボタンは有効のまま（実害なし） |
| EC-2 | 明細が11件以上 | 先頭10件（displayOrder 昇順）のみ引継ぎ、`toast.warning` で通知。残り件数も併記: `「明細が10件を超えています。先頭10件のみ比較対象に追加します（残り{N}件は除外）」` |
| EC-3 | 全明細が未登録商品（goodsNo=null） | 比較画面は未登録商品のみで起動。`/compare-goods` API は呼ばれない（`registeredGoodsNos.length === 0` で `enabled: false`）。仕入情報列は `-` 表示 |
| EC-4 | 一部のみ未登録商品 | 登録済み分だけ API で enrich、未登録分はそのまま表示（既存 enrichedItems ロジックで自動対応済み） |
| EC-5 | ステータスが `10`（提出済）など | ボタン非表示（FR-2） |
| EC-6 | sessionStorage が無効／容量オーバー | try/catch で握りつぶし、`toast.error('比較画面への引継ぎに失敗しました')` → 比較画面には遷移しない |
| EC-7 | 比較画面で既に作業中の状態（`estimate-compare` に items が1件以上）から見積詳細経由で戻ってくる | 6-2-2 のロジックに従い **確認ダイアログを表示**し、ユーザーに「置き換える/キャンセル」を選ばせる。置き換え時のみ handoff を消費し、items を上書き。キャンセル時は 6-2-4 の「引継ぎ保留バー」を表示して後から切替可能にする。これにより仕掛かり作業の意図しない喪失を防ぐ |
| EC-8 | handoff JSON が破損 | try/catch → 従来の restore にフォールバック |
| EC-9 | `destinationNo` が null の見積 | そのまま null で引継ぎ。比較画面では空の SearchableSelect になる |
| EC-10 | 見積の `goodsPrice` が null | `simulatedPrice` も null で引継ぎ。ユーザーが手入力するか、`nowGoodsPrice` がプレースホルダ表示される |
| EC-11 | 登録済み商品が削除されている（`goodsNo` が DB に存在しない） | `/compare-goods` API は該当行を返さない → enrichedItems は元の handoff 値のまま表示される（grid には出るが enrich されない）。既存仕様と同じ |

---

## 8. Risks and Mitigations

| # | リスク | 発生可能性 | 影響 | 緩和策 |
|---|---|---|---|---|
| R-1 | handoff キーと `estimate-compare` キーの混同 | 低 | 中 | キー名を明確に分離（`estimate-compare-handoff`）、確定時に `removeItem` を呼ぶ。保留中は敢えて削除しないことで再選択を可能にする |
| R-7 | ユーザーが確認ダイアログを何度も閉じてしまい handoff が残り続ける | 低 | 低 | 「引継ぎ保留バー」に「破棄」ボタンを明示的に配置。リロードしても状態が保たれるため、いつでも判断できる |
| R-2 | 未登録商品の表示で列がほぼ空になり、ユーザーが混乱 | 中 | 低 | 既存の比較画面でも未登録商品は同様の表示になる。見積修正画面と同じ挙動であることをユーザーに周知 |
| R-3 | 10件制限がユーザー期待と合わない | 中 | 中 | Q3 回答どおり先頭10件で切る + 警告トースト。将来的な上限緩和はバックログに入れる |
| R-4 | ボタン表示条件が曖昧（提出済は本当に不要か） | 低 | 低 | 提出済見積を比較したいニーズが出たら FR-2 の条件を `isEditable` から外して全ステータス対象に変更する（1行修正で済む） |
| R-5 | 見積詳細の数値が `BigDecimal` で返ってくるが、フロント型は `number` で扱うため精度ロス | 低 | 低 | 既存 `form.tsx` も同様の `Number()` 変換を行っており、同じ精度で問題なく動作している |
| R-6 | 変換ロジックが detail.tsx に直書きされるとテストしにくい | 低 | 中 | **`lib/estimate-handoff.ts` に切り出す**ことで単体テスト容易に |

---

## 9. Rollout Plan

### デプロイ戦略
- フロントのみの変更 → フロント単体デプロイ可
- バックエンド変更なし → DBマイグレーション不要

### Feature flag
- **不要**。追加ボタン1つ + 既存画面の mount ロジック拡張のみで、既存フローに破壊的影響なし
- もし万が一ロールバックが必要になっても、ボタンの条件式を `false` にするだけで無効化可能

### ロールバック手順
1. `detail.tsx` の「比較見積へ」ボタン部分を削除（または `false &&` でコメントアウト）
2. 再デプロイ
3. `comparison.tsx` の handoff 優先読込は残しても既存フローに影響ないので無理に消さなくてよい

### 動作確認項目（テスト計画に引き継ぎ）
- [ ] ステータス `00`（作成）の見積でボタンが表示される
- [ ] ステータス `20`（修正）の見積でボタンが表示される
- [ ] ステータス `10`（提出済）の見積でボタンが非表示
- [ ] ボタンの配置順が `[修正][削除][印刷][PDF][比較見積へ][戻る]` になっている
- [ ] ボタンクリック → 比較画面に遷移し、明細が全件表示される（既存 items 0件の場合）
- [ ] 各明細の `simulatedPrice` に見積の `goodsPrice` がセットされている
- [ ] 先頭の明細が基準品（`isBase=true`）になっている
- [ ] 登録済み商品は `/compare-goods` API で仕入先・最新価格が補完される
- [ ] 未登録商品（`goodsNo=null`）は仕入先・仕入価格が `-` で表示される
- [ ] 11件以上の見積で警告トーストが出て、先頭10件のみ表示される
- [ ] 比較画面で販売単価を変更 → 粗利の差分表示（↑/↓）が動作する
- [ ] 「見積 #xxx から引継ぎ」バッジが表示される
- [ ] バッジをクリック → 元の見積詳細ページ（`/estimates/{sourceEstimateNo}`）に遷移する
- [ ] バッジの × ボタンで `sourceEstimateNo` のみクリアされ items は保持される
- [ ] **EC-7: 既存 items あり + handoff → 確認ダイアログが表示される**
- [ ] **EC-7: ダイアログ「置き換える」→ items が見積内容に差し替わる**
- [ ] **EC-7: ダイアログ「キャンセル」→ 既存 items 保持 + 引継ぎ保留バー表示**
- [ ] **EC-7: 保留バー「今すぐ切替」→ ダイアログ再表示または即適用**
- [ ] **EC-7: 保留バー「破棄」→ handoff 削除、バー消える**
- [ ] **EC-7: キャンセル後リロード → 再び保留バーが表示される（handoff が保持されているため）**
- [ ] 既存 items 0件で handoff あり → 確認ダイアログなしで即適用
- [ ] sessionStorage が無効化された環境でエラーが発生しない

---

## 10. Open Questions / Future Work

- **Future work**: `TEstimateDetail` に `supplier_no` カラムを追加すれば、未登録商品の仕入先情報を見積修正・比較画面の両方で復元できるようになる。ただし本タスクのスコープ外（Q5 回答: フロントのみで完結させる）
- **Future work**: 比較画面の上限10件を15〜20に緩和する検討（R-3）。現段階では既存仕様を維持
- **Future work**: 「見積 #xxx から引継ぎ」バッジをクリックで元の見積詳細に戻れるリンクにする（UX 向上）

---

## 付録: 影響ファイル一覧

**変更**:
- `frontend/components/pages/estimate/detail.tsx` — ボタン追加
- `frontend/components/pages/estimate/comparison.tsx` — handoff 読込 + 引継ぎ元バッジ
- `frontend/types/estimate.ts` — `CompareHandoffPayload` 型追加

**新規**:
- `frontend/lib/estimate-handoff.ts` — 変換純粋関数

**バックエンド**: 変更なし
**DB**: 変更なし
**テスト**: Playwright E2E でボタン → 比較画面遷移の一連を追加
