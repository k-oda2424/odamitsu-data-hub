# フロントエンドコードレビュー: B-Cart出荷情報入力 + ダッシュボードリンク化

対象ブランチ: 未コミット変更 (vs `main`)
対象ファイル:
- 新規: `frontend/types/bcart-shipping.ts`, `frontend/e2e/bcart-shipping-input.spec.ts`
- 変更: `frontend/components/pages/bcart/shipping.tsx`, `frontend/components/features/dashboard/BatchPanel.tsx`, `frontend/components/features/dashboard/WorkflowGuide.tsx`, `frontend/components/pages/dashboard.tsx`, `frontend/components/layout/Sidebar.tsx`, `frontend/e2e/dashboard.spec.ts`

Severity 凡例: 🔴 Critical (マージブロック) / 🟡 Warning (要修正) / 🔵 Info (改善提案)

---

## サマリ

| Severity | 件数 |
|---|---|
| 🔴 Critical | 4 |
| 🟡 Warning | 9 |
| 🔵 Info | 7 |

特に **E2E テストと実装の不一致** が目立ち、現状ではテストが全滅する可能性が高い。また `adminMessage` を `null as unknown as string` で型システムを欺く箇所は **Critical** として早急な修正を推奨する。

---

## 🔴 Critical

### C-1. E2E テスト内のセレクタ・期待文字列が実装と一致しない

**ファイル**: `frontend/e2e/bcart-shipping-input.spec.ts`

実装 (`shipping.tsx`) と E2E スペックの間で、多数のユーザー可視文字列とセレクタが食い違っており、**ほぼすべてのテストがフェイルする**。

| 行 | テスト側の期待 | 実装 (`shipping.tsx`) |
|---|---|---|
| L100 | `input[placeholder="送り状番号"]` | 送り状番号の `<Input>` に `placeholder` が無い (L340) |
| L101, L106, L183 | ボタン名 `入力内容を保存 (1)` / `入力内容を保存` | 実装は `出荷情報更新` (L441) |
| L122, L141 | ボタン名 `選択行を一括更新` | 実装は `選択した項目を一括更新` (L434) |
| L129 | toast `選択行のステータスを更新しました` | 実装は `選択した項目のステータスを更新しました` (L145) |
| L143 | toast `未保存の編集があります。先に保存してください` | 実装は `未保存の編集があります。先に出荷情報更新してください` (L205) |
| L184 | ボタン名 `保存` | 実装は `confirmLabel="更新"` (L453) |
| L186 | toast `出荷情報を保存しました` | 実装は `出荷情報を更新しました` (L135) |

**修正案** (いずれかに統一):
- A案: 実装の文言を仕様書と合わせて統一する（推奨: 「保存」系で揃えると UX 一貫性も高い）
- B案: E2E スペックを現行実装に合わせる

加えて `row101.locator('input[placeholder="送り状番号"]')` を成立させるため、実装 L340 に `placeholder="送り状番号"` を追加することを推奨（可読性向上）。

---

### C-2. `adminMessage` の型安全性を破壊する `null as unknown as string`

**ファイル**: `frontend/components/pages/bcart/shipping.tsx:56`

```ts
adminMessage: row.adminMessageDirty ? (row.adminMessage ?? '') : (null as unknown as string),
```

`BCartShippingUpdateRequest.adminMessage: string` の型契約を **実行時に null** で破っている。TypeScript の型システムを二段キャストで欺くのは `any` 相当のアンチパターン。

**修正案**: DTO 側を optional + null 許容にする。

```ts
// types/bcart-shipping.ts
export interface BCartShippingUpdateRequest {
  bCartLogisticsId: number
  deliveryCode: string
  shipmentDate: string
  memo: string
  /** null = 未編集（サーバー側で既存値保持） */
  adminMessage: string | null
  shipmentStatus: BCartShipmentStatus
}
```

```ts
// shipping.tsx L56
adminMessage: row.adminMessageDirty ? (row.adminMessage ?? '') : null,
```

バックエンド DTO (`BCartShippingUpdateRequest`) も `@Nullable` (Jackson なら単に `String`) で受ける想定と整合していることを合わせて確認すること。

---

### C-3. `useEffect` で `listQuery.data` からローカル state へコピー (TanStack Query アンチパターン)

**ファイル**: `frontend/components/pages/bcart/shipping.tsx:115-130`

サーバーステートを `rows` にコピーし、`setSelected(new Set())` を同じ effect で行っている。再フェッチ (mutation の `invalidateQueries`) のたびに **選択状態がリセット** され、かつ `listQuery.data` が参照同一のまま値だけ変わるケースで stale になる可能性がある。

また `refetchOnWindowFocus: false` のためウィンドウフォーカスでは再初期化されないが、`invalidateQueries` 後の再フェッチ時にユーザーの途中編集 (dirty 行) が **黙って消える**。

**修正案**:
1. ローカル編集状態は「編集差分 Map」として別管理し、表示は `listQuery.data` + 差分マージで行う。
2. もしくは、意図的リセット（検索押下/保存成功）を明示トリガーにして、`useEffect([listQuery.data])` ではなく mutation の `onSuccess` で invalidate + reset を行う。

最低限、現状維持する場合でも「dirty 行が 1 件でもあれば data 更新時に `window.confirm` で警告」するなどのガードが必要。そうでないと `bulkStatusMutation.onSuccess` の `invalidateQueries` 実行 → 編集中の他行が消える。

---

### C-4. `dashboard.spec.ts` 期待値と実装の大小文字不一致

**ファイル**: `frontend/e2e/dashboard.spec.ts:31`

```ts
await expect(page.getByRole('heading', { name: 'B-CART出荷情報入力' })).toBeVisible()
```

実装 (`dashboard.tsx:92`) は `"B-CART出荷情報入力"` で一致するが、`Sidebar.tsx:126` は `"B-Cart出荷情報入力"`（小文字 Cart）。ページ内タイトル (`shipping.tsx:221`) も `"B-Cart出荷情報入力"`。

**表記が 3 箇所でばらついている** ため、以下のいずれかに統一すべき。

| 場所 | 現在 |
|---|---|
| `Sidebar.tsx:126` | B-Cart出荷情報入力 |
| `dashboard.tsx:92` (BatchPanel title) | B-CART出荷情報入力 |
| `shipping.tsx:221` (PageHeader) | B-Cart出荷情報入力 |
| `dashboard.spec.ts:31` | B-CART出荷情報入力 |
| `bcart-shipping-input.spec.ts:84` | B-Cart出荷情報入力 |

**修正案**: プロジェクト内の他 B-CART* 箇所と統一（CLAUDE.md の external-systems.md では「B-CART」表記）。`"B-CART出荷情報入力"` に全面統一を推奨。

---

## 🟡 Warning

### W-1. Sentinel 値 `'__none__'` の Select 値管理

**ファイル**: `shipping.tsx:237-249, 420-432`

`Select` の value に空文字を使いたいが shadcn/ui が空文字 value を許さないため `'__none__'` を入れる回避策。動作はするが、`statusInput === '__none__'` のような偶発的衝突に弱い。

**修正案**: 共通定数化して import で共有。
```ts
export const SELECT_NONE = '__none__' as const
```

また `<Select value={statusInput || '__none__'} ...>` は `statusInput` が `''` の時に `'__none__'` となりペアの onValueChange で `''` に戻す、の二重変換。`useMemo`/ラッパー関数化で凝集度を上げると保守性が増す（Info に近いが頻出なので Warning）。

---

### W-2. `useEffect` 依存配列に `launchedAt` を含めていない懸念

**ファイル**: `frontend/components/features/dashboard/BatchPanel.tsx:78-90`

```ts
useEffect(() => {
  if (!polling || !statusQuery.data || !launchedAt) return
  ...
}, [polling, statusQuery.data, launchedAt])
```

依存は網羅されていて問題なし。ただし `statusQuery.data` は TanStack Query が内部で参照識別を保っているが、同一オブジェクトでの更新時に effect が走らない可能性は薄い。動作上は問題ないが、`setPolling(false)` で `enabled: polling && !!jobName` が false になって以降、`statusQuery.data` は残る。そのため effect が「完了判定直後の依存再評価」で再トリガーされる懸念は無い (早期 return で弾かれる)。
**結論**: 現状維持で OK。念のためコメント追記を推奨。

---

### W-3. `handleMemberImport` の `setTimeout` によるステータス擬似遷移

**ファイル**: `frontend/components/features/dashboard/WorkflowGuide.tsx:70-74`

```ts
setTimeout(() => setStatus('completed'), 2000)
```

- アンマウントで cleanup されない → メモリリークは些細だが、unmount 後 setState で警告は出ない (React 19 で静音化)
- 実際のバッチ完了と関係なく 2 秒後に固定で「完了」表示 → ユーザーに誤認を与える

**修正案**: `BatchPanel` と同じく `statusQuery` ポーリングに統合するか、そもそも「起動しました」toast のみで表示完結させ `completed`/`failed` の永続表示は取りやめる。

```ts
const handleMemberImport = async () => {
  setStatus('running')
  try {
    await api.post(`/batch/execute/${MEMBER_IMPORT_JOB}`)
    toast.success('新規会員取込バッチを起動しました')
    setStatus('idle') // すぐ戻す。詳細はバッチ管理画面で確認
  } catch {
    setStatus('idle')
    toast.error('新規会員取込バッチの起動に失敗しました')
  }
}
```

---

### W-4. `listQuery` の `staleTime` 未指定でフォーカス外再マウント時の不要フェッチ

**ファイル**: `shipping.tsx:98-112`

`refetchOnWindowFocus: false` は付いているが、`staleTime` が 0 のため同コンポーネント再マウント時に即再フェッチが走る。

**修正案**:
```ts
staleTime: 30_000, // 30 秒は fresh 扱い
```

---

### W-5. `isRowLocked` がコンポーネント外、かつ参照先の hoist 依存

**ファイル**: `shipping.tsx:177-185, 472-474`

`isRowLocked` は関数宣言なので hoisting で動く（JS セマンティクスで問題なし）が、**`export function BCartShippingPage()` の内部 (L177, 185, 301) で参照→外部 (L472) で定義** の構造は可読性が低い。

**修正案**: コンポーネント関数の **上** に移動。同じファイル内の `isKnownStatus`, `toRequest`, `formatApiError` と並べる。

---

### W-6. `goodsInfo.map((g, i) => <li key={...i}>)` の index key

**ファイル**: `shipping.tsx:333-335`

```tsx
{row.goodsInfo.map((g, i) => (
  <li key={`${row.bCartLogisticsId}-g-${i}`}>{g}</li>
))}
```

`goodsInfo: string[]` は追加/削除される動的リストではなく、1 行分の静的内容のため現状は許容。ただし同じ文字列が重複する可能性もあり、React は key が重複すると警告を出す。

**修正案**: 内容と index の併用で重複対策。
```tsx
<li key={`${row.bCartLogisticsId}-g-${i}-${g}`}>{g}</li>
```

---

### W-7. `Set<number>` の state 管理で関数型更新になっていない箇所

**ファイル**: `shipping.tsx:129`

```ts
setSelected(new Set())
```

`useEffect` 内で直接セット。問題はないが、`toggleSelectAll` (L181-183) は `setSelected(new Set(...))` で非関数型。連続クリック時に race はないが、Strict Mode の二重実行で `rows` のスナップショット次第で挙動が微妙に変わる可能性がある。

**修正案**: 現状は `rows` 依存のため関数型にしにくい。`allSelectable` を `useMemo` 化して安定させるとより安全。

---

### W-8. `adminMessage` 表示の空文字と null の差分消失

**ファイル**: `shipping.tsx:123, 400`

API から `adminMessage: null` を受けて `''` に正規化 (L123) → ユーザーが何も触らなければ `adminMessageDirty: false` のまま送信時に null へ戻る (L56)。問題ないが、ユーザーが意図的に既存の値を「空文字にクリア」した場合 (`''` → `''` onChange) に `adminMessageDirty: true` となり **空文字送信**。意図通り。

ただし `handleRowChange` は `patch` のキー有無で判定するため、例えば `''` → `''` への onChange でも `adminMessageDirty: true` となる。React は value 変化なしでは onChange を発火しないので実害は低いが、IME 確定や paste で同一文字列の再入力が起きるケースはある。

**修正案**: 値が実際に変わった時のみ dirty にする。
```ts
if (Object.prototype.hasOwnProperty.call(patch, 'adminMessage') && patch.adminMessage !== r.adminMessage) {
  next.adminMessageDirty = true
}
```

---

### W-9. `bulkStatus as BCartShipmentStatus` のキャスト (L463)

**ファイル**: `shipping.tsx:463`

`confirmBulk` で `!bulkStatus` を弾いた後、`ConfirmDialog.onConfirm` 内で `bulkStatus as BCartShipmentStatus` と再キャスト。`bulkStatus: BCartShipmentStatus | ''` が state のため、ダイアログ open 中に（理論上）空文字化されると runtime error の risk。

**修正案**: ダイアログを開く時点で値を snapshot する。
```ts
const [pendingBulkStatus, setPendingBulkStatus] = useState<BCartShipmentStatus | null>(null)
// confirmBulk 内
setPendingBulkStatus(bulkStatus)
setBulkDialogOpen(true)
// onConfirm
if (pendingBulkStatus) bulkStatusMutation.mutate({ ..., shipmentStatus: pendingBulkStatus })
```

---

## 🔵 Info

### I-1. `BatchPanel` の `linkHref` モードは別タブ固定

**ファイル**: `BatchPanel.tsx:133`

```tsx
<a href={linkHref} target="_blank" rel="noopener noreferrer">
```

B-Cart出荷情報入力は **同一アプリ内の Next.js ルート** (`/bcart/shipping`) であり、別タブではなく SPA ナビゲーションが適切。`next/link` を使うことで prefetch も効く。

**修正案**:
```tsx
import Link from 'next/link'
// ...
<Button asChild size="sm" className={...}>
  <Link href={linkHref}>
    {linkLabel ?? '画面を開く'}
  </Link>
</Button>
```

外部リンクの場合のみ `target="_blank"` にする設計に変更を推奨。

---

### I-2. `linkMode` 時に `jobName` が無くても `statusQuery` が enabled: false で動作するが依存配列に残る

**ファイル**: `BatchPanel.tsx:71-76`

`enabled: polling && !!jobName` で弾かれるので動作上問題ないが、`linkHref` モードでは `statusQuery` を完全に無効化できる方が綺麗。

```ts
enabled: !isLinkMode && polling && !!jobName,
```

---

### I-3. PageHeader タイトルに `description` や breadcrumb が無い

**ファイル**: `shipping.tsx:221`

他の画面 (例: estimate-comparison) と比べると情報量が少ない。任意。

---

### I-4. テーブル内 `<Textarea rows={2}>` の UX

**ファイル**: `shipping.tsx:376-378, 403-405`

rowspan=2 テーブル内で Textarea を 2 行固定表示。内容が 3 行以上のメモになるとスクロールが発生し、編集しづらい。`autoResize` 実装、もしくは `max-h` + `overflow-y-auto` で最低限のヒントを出すと良い。

---

### I-5. `smileSerialNoList.join(', ')` の空配列ガード

**ファイル**: `shipping.tsx:387`

```tsx
{row.smileSerialNoList.length === 0 ? '' : row.smileSerialNoList.join(', ')}
```

`[].join(', ')` は `''` なので三項演算子は冗長。
```tsx
{row.smileSerialNoList.join(', ')}
```

---

### I-6. `mockBcartShippingList` の URL 判定が `!url.searchParams.get('action')`

**ファイル**: `bcart-shipping-input.spec.ts:52`

`action` パラメータを除外しているが、実装側には `action` なる query は無く、意図不明。単に `url.pathname === '/api/v1/bcart/shipping'` で十分。

---

### I-7. `'use client'` 境界は適切

**ファイル**: 全変更ファイル

対話的な state / mutation を持つページ・パネル系は `'use client'` が適切に付与されている。Sidebar, 既存パターン踏襲。問題なし。

---

## アクセシビリティ観点

- 🟡 `shipping.tsx:272` `<span>選択</span>` ラベルは Checkbox の `aria-label="全選択"` で補われており、screen reader 観点は OK。
- 🔵 `shipping.tsx:316` `aria-label={\`row-${id}\`}` は機械的な ID で、screen reader ユーザーには意味不明。`aria-label={\`${row.partnerName ?? ''} の行を選択\`}` の方が親切（ただし E2E で `row-101` セレクタ依存のため、`data-testid="row-checkbox-101"` に移管推奨）。
- 🔵 テーブル全体の `scope="col"` / `scope="row"` 属性は shadcn/ui 標準に任せているが、rowspan/colspan 併用の複雑ヘッダーでは `<th id>` + `<td headers>` の明示が WCAG 推奨。任意。

---

## 総評

- **Critical 4 件** のうち C-1/C-4 は単純な文字列修正で解消可能だが、現状 E2E は動作しない見込み。
- **C-2 (null as string)** と **C-3 (サーバーステートコピー)** は設計レベルの修正。マージ前に対処推奨。
- BatchPanel の `linkHref` モード追加自体はシンプルで副作用の無い良い拡張。Next.js 内部リンクに変える (I-1) とさらに良い。
- WorkflowGuide への「新規会員取込」ボタン移動は UX 改善として妥当だが、擬似完了状態の `setTimeout` (W-3) は誤認リスクがあり要修正。

修正優先度:
1. C-1, C-4 (E2E 文字列統一)
2. C-2 (DTO を `string | null` に)
3. C-3 (dirty 行ガードまたは差分マージ設計)
4. W-3 (擬似 completed 廃止)
5. W-4 (staleTime 追加)
6. 残り順次
