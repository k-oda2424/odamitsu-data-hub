# Finance 例外階層 (T5)

## 1. 設計方針

Finance パッケージ内 (`jp.co.oda32.api.finance.*`, `jp.co.oda32.domain.service.finance.*`) で発生する
業務エラー / 内部状態異常を、意図に応じて型レベルで明確化する。

| 意図 | 型 | HTTP | client への message |
|---|---|---|---|
| ユーザーに伝える業務エラー | `FinanceBusinessException` | 400 | 元メッセージそのまま |
| 内部状態異常 (機微情報含む可能性) | `FinanceInternalException` | 422 | 「内部エラーが発生しました」固定 |
| 引数バリデーション (null / 形式不正) | `IllegalArgumentException` | 400 | 元メッセージそのまま (GlobalExceptionHandler) |
| プログラマバグ (assertion 違反) | `IllegalStateException` | 422 | 「内部エラーが発生しました」固定 (FinanceExceptionHandler) |
| 認可違反 (他 shop / 他事業部) | `ResponseStatusException(FORBIDDEN)` | 403 | 元メッセージ |
| リソース不在 | `ResponseStatusException(NOT_FOUND)` | 404 | 元メッセージ |

## 2. 既存 Handler との関係

```
HTTP request
    │
    ▼
FinanceExceptionHandler (basePackages = jp.co.oda32.api.finance) ← 優先
    ├─ MfReAuthRequiredException → 401
    ├─ MfScopeInsufficientException → 403
    ├─ MfTenantMismatchException → 409
    ├─ FinanceBusinessException → 400 (NEW T5)
    ├─ FinanceInternalException → 422 (NEW T5)
    └─ IllegalStateException → 422 (汎用化、後方互換)
            │
            ▼
GlobalExceptionHandler (Finance package 外でも有効)
    ├─ MethodArgumentNotValidException → 400 + field errors
    ├─ BadCredentialsException → 401
    ├─ MaxUploadSizeExceededException → 400
    ├─ IllegalArgumentException → 400 + message
    ├─ AccessDeniedException → 403
    ├─ ResponseStatusException → 元 status + reason
    └─ Exception → 500 + 「システムエラーが発生しました」
```

## 3. 開発ガイドライン

### 新規コードでの選択

- **業務メッセージを伝えたい** → `FinanceBusinessException`
  - 例: 「未登録の送り先があります（3件）」「指定期間に MF 出力対象の売掛金がありません」
- **内部 state 異常 (機微情報含む可能性)** → `FinanceInternalException`
  - 例: 「買掛金集計の taxRate が null です: shopNo=1, supplierNo=123」「経理ステータス SQL の実行に失敗しました」
- **引数バリデーション** → `IllegalArgumentException`
  - 例: 「fromDate は toDate 以前を指定してください」「targetDate は yyyyMMdd 形式」
- **プログラマバグ (内部 assertion)** → `IllegalStateException` 維持
  - 例: 「未知の税区分リゾルバ: ...」「aggregateMonth: empty group」「SHA-256 unavailable」

`IllegalStateException` の **新規使用は避ける**。
理由: ハンドラで「内部エラーが発生しました」に汎用化されるため、ユーザーに伝えたい業務メッセージが消失する。

### 機微情報判定の指針

以下を含むメッセージは `FinanceInternalException` (汎用化される):
- 内部 ID (supplier_no, partner_no, user_no 等)
- DB カラム名 / SQL 断片
- API キー / トークン / レスポンス raw body
- ファイルパス / スタックトレース風情報

以下を含むメッセージは `FinanceBusinessException` (そのまま返す):
- ユーザーが画面で操作可能な事項 (「マスタ登録後に再試行」等)
- 件数 / 期間情報 (「3件」「指定期間」等)
- ユーザーが認知している取引先名 / 支払先名 (内部 ID ではない自然名)

## 4. 既存コードからの移行 (T5 で実施)

Phase 別に段階適用:

### Phase 3 (置換) 実施分 (2026-05-04 T5)

**FinanceBusinessException 化** (4 箇所):
- `PaymentMfImportService.java:175` 「未登録の送り先があります」(Cluster C M1 既知バグ修正)
- `MfJournalFetcher.java:86` 「MF fiscal year 境界エラー」
- `AccountsReceivableController.java:271` 「指定期間に MF 出力対象の売掛金がありません」 (旧 ResponseStatusException)
- `FinanceController.java:336` 「出力対象のデータがありません」 (旧 ResponseStatusException)

**FinanceInternalException 化** (6 箇所):
- `AccountingStatusService.java:145` 「経理ステータス SQL の実行に失敗しました」
- `PaymentMfCsvWriter.java:66` 「CSV出力に失敗しました」
- `SalesJournalCsvService.java:201` 「アカウントマスタで重複キー」
- `PurchaseJournalCsvService.java:193` 「金額に小数点以下が含まれています」
- `TAccountsPayableSummaryService.java:170` 「買掛金集計の taxRate が null」
- `MfJournalFetcher.java:156` 「MF journals ページング safeguard を超過」
- `AccountsReceivableController.java:135` 「集計ジョブが見つかりません」

**維持** (内部 assertion / プログラマバグ系):
- `AccountsPayableLedgerService.java:133` "aggregateMonth: empty group"
- `MfTaxResolver.java:30` "未知の税区分リゾルバ"
- `MfApiClient.java:238, 298` MF API レスポンス protocol 違反
- `PaymentMfImportService.java:1437` "SHA-256 unavailable"
- `MfOauthService.java:344` "MF tenant id を取得できませんでした"
- `MfOauthStateStore.java:112` "SHA-256 が利用できません"

**維持** (ResponseStatusException FORBIDDEN/NOT_FOUND):
- `AccountsReceivableController.java:306` "他ショップ" (FORBIDDEN)
- `FinanceController.java:180, 447` "他ショップ/他事業部" (FORBIDDEN)
- `FinanceController.java:533, 550` "グループが見つかりません" (NOT_FOUND)

## 5. Frontend 対応

### 現状調査結果 (Phase 0)

`frontend/lib/api-client.ts`:
- `class ApiError extends Error { status: number }` のみ。`body` フィールド無し。
- 非 ok レスポンス時は `throw new ApiError(status, errorBody as text)`。
  body 全体が `e.message` に入るため、JSON エラーボディの場合は raw JSON 文字列がそのまま入る。
- 例外: `uploadForm` (postForm) のみ `errorBody.message` を取り出す。
- `downloadBlob` は `body.message` を抽出する。

### 主要 page のエラー処理パターン

ほぼ全ページが `onError: (e: Error) => toast.error(e.message)` を採用。
status code 分岐は `accounts-payable-ledger.tsx` (401/403 のみ判別) など僅か。
JSON.parse(e.message) で `body.message` を取り出すのは `bcart/shipping.tsx` のみ。

### 業務メッセージ消失影響

- 旧: `IllegalStateException("未登録の送り先があります（3件）")` → 422 + body `{message: "内部エラーが発生しました"}` →
  toast に「内部エラーが発生しました」と表示され業務情報が消失。
- 新: `FinanceBusinessException("未登録の送り先があります（3件）")` → 400 + body `{message: "未登録の送り先があります（3件）", code: "BUSINESS_ERROR", ...}` →
  `e.message` には raw JSON 文字列全体が入る。

### Frontend 側の追加対応 (将来課題、本 T5 では未実施)

`api-client.ts` で非 ok レスポンスの JSON parse + `body.message` 抽出を一元化することで、
`postForm` 以外の `get/post/put/patch/delete` でも `e.message` に業務メッセージが入るようになる。
ただしこれは互換性影響が広いため別タスク (T5 後続) で扱う。

当面は:
- `bcart/shipping.tsx` 同様、各 page で `JSON.parse(e.message)` する pattern が必要なケースは個別対応
- もしくは `api-client.ts` 側で raw text→JSON 抽出 fallback を入れる

将来の拡張: ErrorResponse の `code` フィールドで案件別分岐可能 (i18n 不要なので当面不要)。

## 6. 関連 DESIGN-DECISION

- T5 (本ドキュメント): 例外ハンドリング統一
- Cluster F SF-25: FinanceExceptionHandler 導入
- Cluster C M1: IllegalStateException → IllegalArgumentException 格上げ実例 (PaymentMfImportService の既知バグ報告)
- MA-01 (FinanceExceptionHandler 内コメント): IllegalStateException メッセージは内部詳細を含むため client には汎用化

## 7. テスト

`FinanceExceptionHandlerTest` (新規):
- `FinanceBusinessException` → 400 + 元メッセージ + 指定 code
- `FinanceBusinessException` (code 未指定) → 400 + "BUSINESS_ERROR"
- `FinanceInternalException` → 422 + 「内部エラーが発生しました」 + "INTERNAL_ERROR"
