# Frontend エラーハンドリング統一 (G3-M12)

## 1. 背景・問題

`lib/api-client.ts` の `request()` は、`!response.ok` 時に response body を `text()` で読み出してそのまま `ApiError.message` に詰めていた。

```ts
// 旧実装
if (!response.ok) {
  const errorBody = await response.text()
  throw new ApiError(response.status, errorBody || response.statusText)
}
```

backend の `FinanceExceptionHandler` 等は JSON `ErrorResponse({ message, code, timestamp, ... })` を返すため、UI 側では `toast.error(e.message)` で生 JSON 文字列がそのまま toast に出てしまう問題があった (例: `{"message":"per-supplier 1 円不一致 ...","code":"PER_SUPPLIER_MISMATCH","timestamp":"2026-05-06T..."}` が丸ごと表示)。

`uploadForm` / `downloadBlob` 側では既に `response.json()` パースによる `message` 抽出が一部実装されていたため、エントリポイントで挙動がブレていた。

## 2. 設計

### 2.1 `ApiError` の拡張

| field | 旧 | 新 |
|---|---|---|
| `status` | あり | あり |
| `message` | text body / statusText | parsed JSON `message` または raw text fallback |
| `code` | なし | parsed JSON `code` (例: `MF_TENANT_MISMATCH`) |
| `body` | なし | parsed JSON 全体 (詳細フィールド参照用) |
| `bodyText` | なし | raw response text (parse 失敗時 / debug 用) |

```ts
class ApiError extends Error {
  status: number
  code?: string
  body?: unknown
  bodyText?: string
}
```

### 2.2 共通 `parseErrorBody` ヘルパ

`request` / `uploadForm` / `downloadBlob` の 3 箇所で同じロジックを再実装すると drift しやすいため、共通ヘルパに切り出し:

- 空 text → fallback message のみ
- JSON parse 成功 → `{ message, code, body }` を抽出
- JSON parse 失敗 → 1000 文字以下かつ `<` 始まりでない場合のみ raw text を message に採用 (HTML エラーページの混入防止)

### 2.3 `handleApiError(error, options?)` ヘルパ

`frontend/lib/api-error-handler.ts` (新規) に共通の toast / 誘導ロジックを集約。各ページの mutation `onError` で呼び出す。

```ts
useMutation({
  ...
  onError: (e) => handleApiError(e, { fallbackMessage: '反映失敗' }),
})
```

#### 2.3.1 主要 business code

| code | toast 内容 | action button | HTTP |
|---|---|---|---|
| `PER_SUPPLIER_MISMATCH` | `error.message` + 修正/承認の誘導 | – | 422 |
| `MF_TENANT_MISMATCH` | テナント不一致 (別会社誤接続疑い) | MF 連携設定へ遷移 | 409 |
| `MF_TENANT_BINDING_FAILED` | tenant 取得失敗 | 再認可 | 503 |
| `MF_HOST_NOT_ALLOWED` | OAuth ホスト allowlist 違反 | – | 400 |
| `MF_REAUTH_REQUIRED` | 再認可が必要 | 再認可 | 401 |
| `MF_SCOPE_INSUFFICIENT` | scope 不足 | 再認可 | 403 |

#### 2.3.2 default (未知 code)

- 5xx → 「サーバーエラー」+ `error.message`
- 401/403 → 「権限エラー」+ `error.message`
- それ以外 → `error.message`

#### 2.3.3 非 ApiError

`error instanceof Error` の場合は fallback message + description。ネットワーク切断等のフォールバック。

### 2.4 既存ページの統一

scope 限定 (高頻度 / 業務 code が出やすい finance ページ) のみ統一:

- `components/pages/finance/payment-mf-import.tsx` (4 箇所: 3 mutation + 1 try/catch)
- `components/pages/finance/mf-integration.tsx` (4 箇所: saveMutation / connectMutation / revokeMutation / MfDiagnosticsCard)
- `components/pages/finance/integrity-report.tsx` (2 mutation)
- `components/pages/finance/accounts-payable.tsx` (3 mutation + runBatchMutation の fallback)

`useQuery` の error 副作用 (v5 の `onError` 削除に伴い `useEffect` 化されたもの) は、画面ごとに 401/403/400 の独自誘導が組まれているため**今回は手をつけない**。`handleApiError` への移行はページ単位で別タスク。

## 3. 互換性

- 既存テストへの影響なし: `ApiError.message` は依然として人間可読のメッセージ (旧実装の raw JSON 文字列が綺麗な業務メッセージに改善)
- `(e as Error).message` で参照していた箇所は引き続き動作する
- `e instanceof ApiError && e.status === 422` 等の status 分岐も従来通り動作

## 4. 拡張方針 (新 code を追加する場合)

1. backend `FinanceExceptionHandler` (or 該当 advice) で `ErrorResponse.of(message, code)` を返す
2. `frontend/lib/api-error-handler.ts` の `switch` に case を追加
3. UI 上の誘導 (action button 遷移先 / description 文言) を case 内に記述
4. (任意) 設計書の table を更新

action callback 内では `useRouter` 等の hook が使えないため、`window.location.href` で遷移する。SPA 内での push が必要な場合は将来的に `handleApiError` を React component 内 hook (`useApiErrorHandler`) としてラップ可能。

## 5. 残課題 (今回 scope 外)

- 全 finance / 非 finance ページの `toast.error(e.message)` 残存箇所統一 (G3-M12 では主要 4 ページのみ)
  - `cashbook-import.tsx` / `mf-client-mappings.tsx` / `mf-journal-rules.tsx` / `payment-mf-rules.tsx` / `accounts-receivable.tsx` / `supplier-balances.tsx` / `supplier-opening-balance.tsx` 等
- `useQuery` の `useEffect` 副作用エラー処理を `handleApiError` に集約
- frontend に unit test 環境 (vitest) を導入し `handleApiError` を回帰テスト
- E2E で「`MF_TENANT_BINDING_FAILED` toast → 再認可ボタンから遷移」をカバー
