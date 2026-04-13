# フロントエンド 全体コードレビュー

**対象**: `frontend/` 配下の全ソースファイル（ブランチdiff以外の既存コード）
**レビュー日**: 2026-04-06
**レビュアー**: Claude Opus 4.6

---

## 総合評価

コードベースは一貫した設計パターンに従っており、品質は高い。共通コンポーネント（`DataTable`, `SearchForm`, `SearchableSelect`）の抽出は適切で、各ページが同一パターンで実装されている。以下に改善すべき点を報告する。

---

## セキュリティ

### [HIGH] S-01: JWTトークンがlocalStorageに平文保存

- **ファイル**: `lib/auth.tsx:97`, `lib/api-client.ts:18`
- **問題**: JWTトークンを `localStorage` に保存している。XSS脆弱性がある場合、トークンが窃取される。`dangerouslySetInnerHTML` は未使用のため直接のXSSリスクは低いが、サードパーティライブラリ経由の攻撃は防げない。
- **推奨**: `httpOnly` Cookie にトークンを格納するようバックエンドと協調して変更する。最低限、トークンの有効期限を短くし、リフレッシュトークン機構を導入する。

### [MEDIUM] S-02: トークンリフレッシュ機構の欠如

- **ファイル**: `lib/auth.tsx`, `lib/api-client.ts`
- **問題**: トークン期限切れ時のリフレッシュ機構がない。401を受けたら即座に `/login` にリダイレクトするのみ。長時間操作中にトークンが切れると、入力中のデータが失われる。
- **推奨**: リフレッシュトークン機構を導入し、401受信時にリフレッシュを試みてからリトライするインターセプタを実装する。

### [LOW] S-03: 認証ガードがクライアントサイドのみ

- **ファイル**: `app/(authenticated)/layout.tsx`
- **問題**: 認証チェックは `useEffect` によるクライアントサイドリダイレクトのみ。SSR/SSG時に未認証コンテンツが一瞬表示される可能性がある（現状は `return null` で軽減済み）。Next.js のミドルウェアでサーバーサイド認証ガードを追加するとより堅牢。
- **推奨**: `middleware.ts` でトークンの存在チェックを行い、サーバーサイドでリダイレクトする。

---

## アーキテクチャ・設計

### [MEDIUM] A-01: SearchFormがEnterキーによるsubmitをサポートしない

- **ファイル**: `components/features/common/SearchForm.tsx`
- **問題**: `SearchForm` は `<form>` タグではなく `<div>` で構成されている。ユーザーが検索フィールドでEnterキーを押しても検索が実行されない。
- **推奨**: `<form onSubmit={...}>` でラップし、Enter キーで `onSearch` が呼ばれるようにする。

```tsx
// 修正案
<form onSubmit={(e) => { e.preventDefault(); onSearch() }}>
  ...
</form>
```

### [MEDIUM] A-02: User型が2箇所で重複定義

- **ファイル**: `lib/auth.tsx:6-13`, `types/index.ts:1-8`
- **問題**: `User` インターフェースが `lib/auth.tsx` と `types/index.ts` の両方で定義されている。フィールドが追加された場合に不整合が発生するリスクがある。
- **推奨**: `types/index.ts` の `User` 型を正とし、`lib/auth.tsx` からはインポートして使用する。

### [LOW] A-03: インライン型定義がtypesディレクトリに集約されていない

- **ファイル**: `components/pages/stock/index.tsx:14-25`, `components/pages/bcart/shipping.tsx:13-18`, `components/pages/finance/accounts-payable.tsx:11-21`
- **問題**: 複数のページコンポーネントでAPIレスポンス型がインラインで定義され、`[key: string]: unknown` のインデックスシグネチャ付き。`DataTable` の `Record<string, any>` 制約を満たすためだが、型安全性を弱める。
- **推奨**: `types/` ディレクトリに型を集約し、`DataTable` のジェネリクス制約を改善する（後述 A-04）。

### [LOW] A-04: DataTableのジェネリクス制約が `Record<string, any>`

- **ファイル**: `components/features/common/DataTable.tsx:33`
- **問題**: `T extends Record<string, any>` は型安全性が弱い。`eslint-disable` コメントで `no-explicit-any` を抑制している。
- **推奨**: `T extends Record<string, unknown>` に変更し、`col.render` がない場合の値取得を型安全に改善する。各利用箇所のインライン型から `[key: string]: unknown` も不要になる。

---

## エラーハンドリング

### [HIGH] E-01: グローバルError Boundaryが未設定

- **ファイル**: `app/layout.tsx`, `app/(authenticated)/layout.tsx`
- **問題**: React Error Boundary が設置されていない。コンポーネント内の未ハンドリング例外でアプリ全体がクラッシュし、白画面になる。
- **推奨**: `app/(authenticated)/layout.tsx` に Error Boundary を追加する。Next.js の `error.tsx` ファイルでも代替可能。

### [MEDIUM] E-02: uploadFormのエラーレスポンス解析が不整合

- **ファイル**: `lib/api-client.ts:97`
- **問題**: `uploadForm` はエラー時に `response.json()` を試みるが、`request` 関数は `response.text()` を使用している。バックエンドがHTMLエラーページを返す場合、`json()` は失敗する（`catch` で処理はされるが `statusText` にフォールバック）。
- **推奨**: 両関数で同じエラー解析ロジックを使用する。

### [MEDIUM] E-03: APIクライアントの204レスポンスで unsafe cast

- **ファイル**: `lib/api-client.ts:68`
- **問題**: `return undefined as unknown as T` は `T` が `void` 以外の場合に実行時エラーを引き起こす可能性がある。呼び出し側が戻り値を参照した場合 `undefined` のプロパティアクセスでクラッシュする。
- **推奨**: `api.delete` のシグネチャを `void` に限定する（現状は実質そうなっている）か、204レスポンスを明示的に型安全に扱う。

---

## パフォーマンス

### [MEDIUM] P-01: DataTableのページリセットが不完全

- **ファイル**: `components/features/common/DataTable.tsx:40-53`
- **問題**: `data` propsが変更された際に `page` ステートがリセットされない。検索結果が切り替わった時に存在しないページが表示される可能性がある（空テーブル表示）。
- **推奨**: `data` が変更されたら `page` を 0 にリセットする `useEffect` を追加する。

```tsx
useEffect(() => { setPage(0) }, [data])
```

### [MEDIUM] P-02: バッチパネルの並列ステータスポーリングが非効率

- **ファイル**: `components/features/dashboard/BatchPanel.tsx`
- **問題**: ダッシュボードに4つの `BatchPanel` が配置されており、各パネルが独立して5秒間隔のポーリングを実行する。同時に4ジョブ実行すると4つの並列ポーリングが走る。
- **推奨**: 親コンポーネントで一括ポーリングし、結果を各パネルにpropsで渡す。`batch.tsx` の `BatchManagementPage` はこの方式で実装済みなので、ダッシュボード側も統一する。

### [LOW] P-03: 大量データのクライアントサイドソート・フィルタ

- **ファイル**: `components/features/common/DataTable.tsx:45-62`
- **問題**: 全データをクライアント側でフィルタ・ソートしている。数千件以上のデータでは `Object.values(item).some(...)` の全フィールド走査がパフォーマンスに影響する可能性がある。
- **推奨**: 現状の業務データ量であれば問題ないが、将来的にサーバーサイドページネーションへの移行パスを検討する。

### [LOW] P-04: EstimateFormPageの lastSearchedCodes がuseCallbackの依存配列に含まれる

- **ファイル**: `components/pages/estimate/form.tsx:231`
- **問題**: `searchGoodsByCode` の `useCallback` 依存配列に `lastSearchedCodes` が含まれている。このオブジェクトは毎回新規作成されるため、`useCallback` のメモ化効果が事実上無効。
- **推奨**: `lastSearchedCodes` を `useRef` で管理するか、依存配列から除外して関数内で `ref.current` を参照する。

---

## アクセシビリティ

### [MEDIUM] ACC-01: SearchFormに `<form>` タグがなくキーボード操作性が低い

- **ファイル**: `components/features/common/SearchForm.tsx`
- **問題**: 上記 A-01 と同一。`<form>` タグがないためスクリーンリーダーがフォーム領域を認識できず、Enterキーでのsubmitも機能しない。

### [MEDIUM] ACC-02: DataTableのページネーションボタンに aria-label がない

- **ファイル**: `components/features/common/DataTable.tsx:142-156`
- **問題**: ページネーションボタン（先頭、前、次、最後）にアイコンのみが表示されるが `aria-label` が未設定。スクリーンリーダーで操作内容が伝わらない。
- **推奨**: 各ボタンに `aria-label="最初のページ"` 等を追加する。

### [LOW] ACC-03: テーブル行クリックがキーボードで操作できない

- **ファイル**: `components/features/common/DataTable.tsx:119-129`
- **問題**: `onRowClick` が設定されたテーブル行は `onClick` のみでアクセス可能。`tabIndex`, `onKeyDown`, `role="button"` がないためキーボードでは選択できない。
- **推奨**: クリック可能な行に `tabIndex={0}`, `role="row"`, `onKeyDown` (Enter/Space) を追加する。

---

## 型安全性

### [MEDIUM] T-01: SalesGoodsDetailPageで手動バリデーション（react-hook-form未使用）

- **ファイル**: `components/pages/sales-goods/detail.tsx:188-193`
- **問題**: 編集フォームで12個の `useState` を個別に管理し、保存時に手動で必須チェックしている。同ファイル `goods/detail.tsx` では `react-hook-form` + `zod` で適切にバリデーションしており、パターンが不統一。
- **推奨**: `react-hook-form` + `zod` スキーマに統一する。

### [LOW] T-02: 数値フィールドがstring型で管理される箇所

- **ファイル**: `types/goods-schemas.ts:7-8`, `components/pages/purchase-price/bulk-input.tsx:30-31`
- **問題**: Zodスキーマで `makerNo`, `caseContainNum`, `purchasePrice` 等が `z.string()` で定義されている。`<Input type="number">` と組み合わせる際に `Number()` 変換が各所で必要になり、変換漏れのリスクがある。
- **推奨**: `z.coerce.number()` を使用し、スキーマレベルで型変換を行う。

---

## コード品質・一貫性

### [MEDIUM] Q-01: DataTableで配列インデックスをkeyに使用

- **ファイル**: `components/features/common/DataTable.tsx:120`
- **問題**: `key={i}` で配列インデックスをReactのkeyに使用している。ソートやフィルタでデータ順序が変わると、DOM再利用が不適切になり予期しない挙動が発生する可能性がある。
- **推奨**: `Column` に `keyField` を追加するか、`DataTable` propsに `rowKey` 関数を追加する。

### [LOW] Q-02: module-scoped mutable変数

- **ファイル**: `components/pages/purchase-price/bulk-input.tsx:34`
- **問題**: `let nextId = 1` がモジュールスコープで宣言されている。Reactのコンカレントモードやhot reloadで意図しない挙動が起きる可能性がある。
- **推奨**: `useRef` で管理するか、`crypto.randomUUID()` を使用する（`estimate/form.tsx` では正しく `crypto.randomUUID()` を使用済み）。

### [LOW] Q-03: ダッシュボードBatchPanelとバッチ管理ページでポーリングロジックが重複

- **ファイル**: `components/features/dashboard/BatchPanel.tsx`, `components/pages/batch.tsx`
- **問題**: バッチステータスのポーリング + 完了/失敗判定ロジックが2箇所で独立実装されている。
- **推奨**: カスタムフック `useBatchExecution(jobName)` に抽出し、両方から利用する。

### [LOW] Q-04: useShopsの呼び出し一貫性

- **ファイル**: `components/pages/purchase-price/index.tsx:58`
- **問題**: `useShops()` を引数なし（= `enabled: true`）で呼び出しているが、admin以外のユーザーではショップ一覧は不要。他の画面では `useShops(isAdmin)` と条件付きで呼び出しているが、ここでは常にfetchされる。
- **推奨**: `useShops(isAdmin)` に統一する。

---

## 改善推奨の優先順位

| 優先度 | ID | 概要 |
|-------|----|------|
| **高** | E-01 | Error Boundary の設置 |
| **高** | S-01 | JWTトークンの保存方式見直し |
| **中** | A-01/ACC-01 | SearchFormのform要素化 |
| **中** | S-02 | トークンリフレッシュ機構 |
| **中** | P-01 | DataTableのページリセット |
| **中** | ACC-02 | ページネーションのaria-label |
| **中** | T-01 | SalesGoodsDetailのフォーム統一 |
| **中** | Q-01 | DataTableのkey改善 |
| **中** | E-02 | APIクライアントのエラー解析統一 |
| **中** | P-02 | BatchPanelポーリング集約 |
| **低** | その他 | 型統一、useRef化等 |
