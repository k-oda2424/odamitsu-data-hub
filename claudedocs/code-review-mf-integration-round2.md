# 再レビュー: MF 連携基盤 (Cluster F) 修正後

レビュー日: 2026-05-04
対象: triage SF-01〜SF-22 適用後 (branch `refactor/code-review-fixes`)
レビュアー: Opus サブエージェント (round 2)

## サマリー
- 新規発見: **Critical 0 / Major 4 / Minor 5**
- 既存修正の評価: **おおむね適切**。SF-08/SF-20/SF-21 は意図通り。SF-15 の副作用と SF-22 の片肺実装が残課題。
- 結論: **次ループ必要 (Major 修正のみ)**。Critical 級リグレッションは検出されず、本番投入前に Major 4 件の方針判断を推奨。

---

## Critical (新規発見、即修正必要)

なし。Critical 級のロジックエラー / セキュリティリグレッション / トランザクション破壊は検出されなかった。

---

## Major

### MA-01: SF-15 `FinanceExceptionHandler.handleIllegalState` が **Finance パッケージ全体** に副作用、生メッセージを 422 で露出
**ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java:47-53`

`@RestControllerAdvice(basePackages = "jp.co.oda32.api.finance")` で `IllegalStateException` を 422 + `ErrorResponse(message=ex.getMessage(), code="INTERNAL_ERROR", ...)` に変換するハンドラが追加された。これは MfIntegrationController を念頭に置いたものだが、Finance パッケージ配下の **すべての Controller** に作用する。

#### 副作用 (1): 既存 endpoint の意図しないステータス変化
- 旧来は `GlobalExceptionHandler.handleGeneral(Exception)` (`exception/GlobalExceptionHandler.java:100-109`) が拾って 500 + 汎用メッセージ「システムエラーが発生しました」を返していた。
- SF-15 適用後、Finance パッケージから IllegalStateException が漏れた場合は **422 + 内部メッセージそのまま** に変わる。

#### 副作用 (2): 内部メッセージの露出 (情報漏洩リスク)
Finance Service 内に IllegalStateException を投げる箇所が多数あり、内部状態を記述した日本語メッセージが client に返るようになる:
- `PurchaseJournalCsvService.java:193` `"金額に小数点以下が含まれています: " + amount` ← 金額値が露出
- `SalesJournalCsvService.java:188` 形式同様
- `PaymentMfImportService.java:572` `"CSV 出力可能な行がありません（ルール未登録のみ・補助行なし）: " + skipped`
- `TAccountsPayableSummaryService.java:164` ほか多数

`GlobalExceptionHandler` には `app.expose-exception-detail` というガードがあったが、`FinanceExceptionHandler` には該当ガードなしで常時露出する。

#### 緩和されている点
CashBookController (`CashBookController.java:62`) と PaymentMfImportController (`PaymentMfImportController.java:78,142`) は **ローカル try/catch** が残っていて 422 + 旧フォーマット (`Map.of("message", e.getMessage())`) を返している。そちらは既に message 露出していたため SF-15 で挙動変化なし。**新たに脅威となるのは、ローカル catch が無い endpoint** (例: `FinanceController` 内の MF/レジャー系全般、新規追加 endpoint) で、IllegalStateException を Service 層から漏らしてしまう経路。

#### 推奨
- 案 A: `handleIllegalState` の `body` を `ErrorResponse.of("内部エラーが発生しました", "INTERNAL_ERROR")` に固定し、`ex.getMessage()` は log のみに残す。
- 案 B: `app.expose-exception-detail` を再利用してプロファイル分岐 (dev は raw, prod は汎用)。
- 案 C: SF-15 を `MfIntegrationController` 専用に切り戻し (basePackages を更に絞る、または個別 advice を `@ControllerAdvice(assignableTypes = MfIntegrationController.class)` に)。

---

### MA-02: SF-22 `apiReachable` が **frontend に伝搬されておらず**、毎回の MF API ping が無駄
**ファイル**:
- 追加側: `backend/.../dto/finance/MfHealthResponse.java:42`, `MfHealthCheckService.java:67-77,85-96`
- 未対応: `frontend/types/mf-health.ts:6-11` (MfOauthStatus interface)
- 未対応: `frontend/components/pages/finance/mf-health.tsx` (`apiReachable` への参照ゼロ — `Grep` 結果 0 件)

backend は `connected=true` のたびに `mfApiClient.listAccounts(...)` (= MF /api/v3/accounts への HTTP 1 round-trip) を実行する。しかしフロントエンドの型定義に `apiReachable` フィールドが無く、UI に表示する箇所も存在しない。**毎回のヘルスチェックで MF API への余計な HTTP 通信が発生する** だけで、ユーザーに何も提示されない。

`MfHealthCheckService.check()` は `@Transactional(readOnly=true)` 内から HTTP を呼ぶ点も気になる (前述 SF-20 で OAuth 経路は tx 外に分離した方針と矛盾)。

#### 推奨
1. `frontend/types/mf-health.ts:11` に `apiReachable: boolean | null` を追加。
2. `frontend/components/pages/finance/mf-health.tsx` の OAuth ステータス表示部に `apiReachable` の Badge を追加 (例: 接続中 + ping OK / 接続中 + ping NG)。
3. もし「ping 表示は不要」と判断するなら **backend の `pingMfApi()` を消す** (毎回の round-trip は重い)。

---

### MA-03: `MfHealthCheckService.pingMfApi()` が **token refresh を起動する可能性**、`@Transactional(readOnly=true)` と矛盾
**ファイル**: `backend/.../domain/service/finance/MfHealthCheckService.java:39 (@Transactional readOnly)`, `:85-96 (pingMfApi)`

`pingMfApi()` 内で `mfOauthService.getValidAccessToken()` を呼び、これは期限切れ間近 (5 分以内) なら **MF token endpoint への refresh HTTP** を起動する (`MfOauthService.java:179-193`)。さらに `persistRefreshedToken` で `REQUIRES_NEW` の write tx を走らせる。

`MfHealthCheckService.check()` 自体は `@Transactional(readOnly=true)` (`:39`) のため、内部から書き込み tx を起動する形になる (REQUIRES_NEW なので技術的に動作はするが、ヘルスチェック GET 経由で副作用が起きるのは設計的に違和感)。さらに連続して `mfApiClient.listAccounts` で HTTP 通信。`refetchInterval: 60_000` (mf-integration.tsx:49) で 60 秒ごとに発火するため、token 残時間 < 5 分の状態で開いていると **毎分 token refresh と /accounts 呼び出しが発火** する。

#### 推奨
- `pingMfApi()` は refresh を起動させず、**snapshot ベース** (現在保存されている access_token をそのまま使う) に切り替える。401 が返ったら ping 失敗と判定。
- もしくは `getMfHealth` endpoint 自体を non-transactional にするか、ping を `apiReachable` UI 化と合わせて廃止 (MA-02 と同時対応)。

---

### MA-04: `MfJournalReconcileService.reconcile` が SF-09 移行で **不要な広範囲 fetch** をする
**ファイル**: `backend/.../domain/service/finance/mf/MfJournalReconcileService.java:97-101`

```java
List<MfJournal> mfJournals = mfJournalFetcher
        .fetchJournalsForPeriod(client, accessToken, transactionMonth, transactionMonth)
        .journals().stream()
        .filter(j -> j.transactionDate() != null && j.transactionDate().equals(transactionMonth))
        .toList();
```

`fetchJournalsForPeriod` は `MfJournalFetcher.buildStartDateCandidates` (`:98-116`) で **fromMonth.minusMonths(1).plusDays(1)** を最初の候補に投入する。期間 `(transactionMonth, transactionMonth)` = 1 日分のつもりが、fiscal year 境界 fallback の影響で **MF API には最大 1 ヶ月分の start_date** が要求される (今までの直叩きは 1 日範囲のみ)。

その後 `filter` で `transactionDate == transactionMonth` だけ残すため正しさは保たれる。ただし:
- 取得 journals 件数が約 30 倍 (1 日 → 30 日) に膨らむ
- pagination 数も増える (1000 件 / page で 1〜数ページ)
- `RATE_LIMIT_SLEEP_MS = 350ms` で各 page 間 + 各候補間スリープが発生
- `/reconcile` のレスポンス時間が大幅悪化

#### 推奨
- `MfJournalFetcher.fetchJournalsForPeriod` に「fallback 試行をしない」モードを追加 (Boolean flag) するか、reconcile 用に **直叩き経路を残す**。1 日分 fetch の場合 fiscal year 境界 fallback はそもそも不要 (fromMonth==toMonth のとき同月内なら fiscal year 跨ぎは無いか、あっても fromMonth で必ず success / fail する)。
- もしくは reconcile 専用の `fetchJournalsSingleDay(date)` メソッドを fetcher に追加。

---

## Minor

### MI-01: SF-21 fallback 時の `softDeleteActiveTokens` 後の状態が **JPA 1st-level cache に反映されない可能性**
**ファイル**: `backend/.../domain/service/finance/mf/MfOauthService.java:251-281`

`persistToken` は順序:
1. `findFirst...` で既存 token を読む (managed entity)
2. `softDeleteActiveTokens` (native UPDATE) で del_flg='1'
3. `tokenRepository.save(new TMfOauthToken)` で新行 insert

step 2 の native UPDATE は JPA 1st-level cache をスキップするため、step 1 で取得した managed entity の `delFlg` は古い値のまま (= '0') 残る。`tokenRepository.save(token)` では新規 insert なので影響しないが、**step 1 で取った既存 entity を以降で flush すると意図しない update が走る** リスクが残る。

実際には refresh token を String で抜き出すだけなので動作は正しい。Minor。

### MI-02: SF-06 `verifyAndConsume` で 不一致 userNo の場合に **state を即削除 = 正規ユーザーの DoS**
**ファイル**: `backend/.../domain/service/finance/mf/MfOauthStateStore.java:74-93`

`deleteAndReturnByState` は state を削除してから userNo を比較する。攻撃者が他ユーザーの state を入手した場合、そのユーザーの認可フローを 1 回だけ妨害できる (state が再利用不能になる)。state は random 32 バイトなので推測困難だが、ログ漏洩・URL コピペ等で漏れた場合の攻撃成立条件は緩い。

**正規挙動として許容できる** (state を提示できる時点で何らかの意図がある) が、`AND user_no = :userNo` を WHERE 句に追加すれば DoS も無くせる。

### MI-03: `MfHttpClientConfig` の `SimpleClientHttpRequestFactory` は **接続プールが無い**
**ファイル**: `backend/.../domain/service/finance/mf/MfHttpClientConfig.java:31`

`SimpleClientHttpRequestFactory` は HttpURLConnection の薄ラッパで、**KeepAlive / connection pooling が無い**。MF API への呼び出しは TCP/TLS handshake を毎回行う。低頻度なら問題ないが、ヘルスチェック (60 秒間隔) + 仕訳突合 + 残高突合 + ledger drill-down で同時に複数飛ぶシーンでは latency が嵩む。

`BCartHttpClientConfig` は OkHttpClient (pool あり)。同等にすべき。

### MI-04: `MfDebugApiClient` には `executeWithRetry` が無い
**ファイル**: `backend/.../domain/service/finance/mf/MfDebugApiClient.java:39-58`

dev/test 限定の診断用なので 429 retry は不要との判断は妥当。ただし、診断中に MF が 5xx を返すと素のスタックトレースが client に飛ぶ (MfIntegrationDebugController の catch は HttpClientErrorException だけ)。Minor。

### MI-05: BroadcastChannel の StrictMode 二重実行ガードが **callback 側のみ**
**ファイル**: `frontend/components/pages/finance/mf-integration.tsx:72-90`, `mf-integration-callback.tsx:22-26`

callback ページは `calledRef.current` で StrictMode 二重マウントの POST 重複を防いでいる。しかし parent 側 (mf-integration.tsx:72-90) の BroadcastChannel listener には同様のガードが無い。StrictMode で `useEffect` が 2 回走る場合、cleanup が走るので listener は 1 つになり OK だが、`onmessage` ハンドラが古い `queryClient` クロージャを抱えて、再マウント直後の極短時間に 2 回 invalidate される可能性がある。実害なし。Minor。

---

## SF-XX 別の確認結果

| SF-XX | 適切に適用されたか | 備考 |
|---|---|---|
| SF-01 | OK | `MfHttpClientConfig` で connect 5s / read 30s 設定済 (MI-03 接続プール改善余地あり) |
| SF-02 | OK | `TokenSnapshot.toString` / `CallbackContext.toString` でマスキング |
| SF-03 | OK | `mf-integration-callback.tsx:22 calledRef` で StrictMode ガード |
| SF-04 | OK | `MfApiClient` 例外 log + Controller 側で汎用メッセージ |
| SF-05 | OK | BroadcastChannel + window.open(noopener,noreferrer) |
| SF-06 | OK | DELETE...RETURNING で atomic 消費 (WebApplicationTests PASS で起動確認済) — DoS 観点 MI-02 のみ |
| SF-07 | OK | basicAuthHeader で URL encode → base64 |
| SF-08 | OK | listJournals/listTaxes/listAccounts/getTrialBalanceBs/postToken 全てに `executeWithRetry` 適用。ただし token endpoint は 4xx → MfReAuthRequiredException で即時 throw となるため retry 対象は 429 のみ (RFC 6749 準拠で正解) |
| SF-09 | 一部見直し | reconcile を fetcher 経由に。ただし MA-04 (1 日分 fetch でも fiscal year fallback の広範 fetch が走る) |
| SF-10 | OK | `urlEncode` で null は `Objects.requireNonNull` |
| SF-11 | OK | `UriComponentsBuilder` 利用 |
| SF-12 | OK | `LoginUserUtil.getLoginUserInfo()` に統一 |
| SF-13 | OK | `MfIntegrationDebugController` 分離 + `@Profile({"dev","test"})` で prod 非ロード |
| SF-14 | OK | `MfDebugApiClient` 分離 + `@Profile({"dev","test"})` |
| SF-15 | 一部見直し | MA-01: 副作用が広い (Finance パッケージ全体)。生 message 露出の情報漏洩リスク |
| SF-16 | OK | `MfTokenStatus` レコード化 (本レビュー範囲外) |
| SF-17 | OK | `useEffect` 依存配列を id/clientId に絞り無限ループ回避 |
| SF-18 | OK | `MfReAuthRequiredException` runtime 例外化 (本レビュー範囲外) |
| SF-19 | OK | frontend `judgeHealth` で 0 固定値を除外、`negativeClosingCount` のみで判定 |
| SF-20 | OK | `handleCallback` 3 段分割 (`consumeStateAndLoadClient` read tx → HTTP tx 外 → `persistTokenInNewTx` REQUIRES_NEW)。`@Lazy self` で同一クラス内呼び出しの propagation 確保 |
| SF-21 | OK | refresh_token null fallback で既存 `getRefreshTokenEnc()` (暗号化済 String) を直接再利用、再暗号化なし。読み取り順序 (softDeleteActiveTokens の前) も正しい |
| SF-22 | 大きな問題 | MA-02: backend のみ実装、frontend 側 type / UI 未対応。MA-03: ping が refresh token 副作用を起こす |

### 削除確認
- 旧 `MfApiClient.getRaw` への参照ゼロ ([Grep 確認](#) — `getRaw` 残存箇所はすべて `MfDebugApiClient.getRaw` 経由のみ)
- 旧 `MfJournalFetcher.callListJournalsWithRetry` への参照ゼロ
- 旧 `MfIntegrationController` 内の `/debug/*` endpoint は `MfIntegrationDebugController` に完全移動

---

## トランザクション・並行性の検証

### `handleCallback` (SF-20) の正しさ
3 段分割 + `@Lazy self` 注入:
```
self.consumeStateAndLoadClient(state, userNo)  // tx#1 read+write (state DELETE)
mfApiClient.exchangeCodeForToken(...)           // HTTP, no tx
self.persistTokenInNewTx(...)                   // tx#2 REQUIRES_NEW
```
- HTTP 呼び出しが tx 外なので DB connection 占有なし (B-W6 パターンに統一) ✓
- state は tx#1 で消費されているため、HTTP 失敗時に state が再利用できない (再認可必須) — 設計通り ✓
- tx#1 と tx#2 は独立しているため tx#2 失敗時に state は消えたまま → ユーザーは再認可フローからやり直し。挙動は妥当だが UX 改善余地 (Minor)

### `getValidAccessToken` (SF-08+SF-20) の並行 refresh ガード
- snapshot 読み取り (`loadActiveTokenSnapshot` `:196-209`) は readOnly tx
- refresh HTTP は tx 外
- `persistRefreshedToken` (`:215-223`) は REQUIRES_NEW + `pg_advisory_xact_lock(REFRESH_LOCK_KEY)` で直列化

⚠️ **微妙な点**: 2 つのスレッドが同時に snapshot を取る → 両方とも `isFresh=false` → 両方が refresh HTTP を起動 → 両方が advisory_lock 取得 → 1 つが先に persist → 2 つ目が後勝ち。
- MF token endpoint が refresh_token を rotation していない場合は同じ access_token が返るため衝突なし
- rotation する設定の場合、2 つ目の HTTP で「使われた refresh_token は無効」エラーになり 400 → MfReAuthRequiredException で **正規ユーザーが追い出される**

advisory lock は **persist 段階だけ** 直列化しているため、HTTP 段階で競合が発生する余地が残っている。Minor (MF の rotation 設定次第)。

### `MfOauthStateStore.verifyAndConsume` (SF-06) の atomicity
- 単一 SQL `DELETE...RETURNING` で TOCTOU race を排除 ✓
- WebApplicationTests PASS で `@Modifying + Optional<Object[]>` の repository registration が成功することを確認 ✓

---

## セキュリティ観点の評価

| 項目 | 状況 |
|---|---|
| client_secret の log 漏洩 | OK (`SensitiveLogMasker.mask`、`TokenSnapshot.toString` マスキング) |
| access_token / refresh_token の log 漏洩 | OK (同上) |
| token URL endpoint への brute force | OK (429 retry はあるが 4xx は即 throw、無限再送なし) |
| state CSRF | OK (DB store + TTL 10min + atomic delete) |
| PKCE (S256) | OK (32-byte random verifier + SHA-256 challenge) |
| redirect_uri allowlist | OK (完全一致 + http/https scheme チェック) |
| `noopener,noreferrer` の window.open | OK (`mf-integration.tsx:109`) |
| dev 限定 endpoint の prod 流出 | OK (`@Profile({"dev","test"})` で Bean 自体存在しない) |
| ErrorResponse での内部情報露出 | **MA-01 で要対策** (FinanceExceptionHandler が IllegalStateException raw message を返却) |

---

## 推奨アクション

### 即修正
1. **MA-01**: `FinanceExceptionHandler.handleIllegalState` のメッセージを汎用化、または `expose-exception-detail` ガード追加。代案として MfIntegrationController 専用 advice に切り戻し。
2. **MA-02**: `frontend/types/mf-health.ts` に `apiReachable` 追加 + UI 表示、または backend ping を削除。

### 次ループ
3. **MA-03**: `MfHealthCheckService.pingMfApi` を refresh トリガしない実装に変更 (`getValidAccessToken` ではなく snapshot を使う)。
4. **MA-04**: `MfJournalReconcileService` 用に fetcher の「単日モード」を追加するか直叩きに戻す。
5. **MI-03**: `MfHttpClientConfig` の `SimpleClientHttpRequestFactory` を OkHttp / HttpComponents に置換 (接続プール)。

### 受容
- MI-01, MI-02, MI-04, MI-05: 実害が無い、もしくは設計判断の範囲内。
- SF-08 token endpoint の 4xx 即 throw 挙動は OAuth2 RFC 準拠で正しい。

---

## 結論

triage で意図された 22 件の SAFE-FIX は**おおむね適切に適用された**。Critical 級のロジックエラー / セキュリティ後退 / トランザクション破壊は検出されず、`./gradlew test` と `WebApplicationTests` も PASS する。

ただし以下 2 点は **本番投入前に方針確定が必要**:
- **MA-01** (情報漏洩): `FinanceExceptionHandler` の作用範囲が想定より広い
- **MA-02** (機能片肺): `apiReachable` の frontend 未対応で SF-22 が空回り

次ループでは Major 4 件の修正と、ユニットテスト不足 (MfOauthService / MfApiClient / MfOauthStateStore のいずれも test 0 本) の補強を推奨する。
