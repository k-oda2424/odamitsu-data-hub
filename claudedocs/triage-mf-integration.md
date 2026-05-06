# Triage: MF 連携基盤 (Cluster F) 修正対応

triage 日: 2026-05-04
対象指摘総数: 54 件 (設計 18 + コード 21 + Codex 15)
出典: design-review-mf-integration.md / code-review-mf-integration.md / codex-adversarial-mf-integration.md
ブランチ: `refactor/code-review-fixes`

## サマリー

| 分類 | 件数 |
|---|---|
| SAFE-FIX | 22 件 |
| DESIGN-DECISION | 19 件 |
| DEFER | 12 件 |
| ALREADY-RESOLVED | 1 件 |

優先度概観:
- **Critical 即適用 SAFE-FIX**: 4 件 (SF-01 RestClient timeout / SF-02 token DTO toString / SF-03 callback StrictMode / SF-04 token error body マスキング)
- **セキュリティ系 SAFE-FIX**: 7 件 (SF-01 / SF-02 / SF-04 / SF-05 noopener / SF-06 state atomic / SF-07 RFC6749 URL encode / SF-15 例外ハンドラ)
- **DESIGN-DECISION** 中心の懸念: dev salt 据置 (`feedback_dev_config_fallbacks`) / Vault/KMS 移行 (Codex 5) / BFF callback 化 (Codex 9) / マルチテナント (Codex 4) / tenant binding (Codex 1) / 監査基盤 (Codex 12)
- **DEFER**: scheduled probe (Codex 11) / mf_sync_job 化 (Codex 8) / contract test (Codex 10) / 個人情報統制 (Codex 13) / event sourcing 系

---

## SAFE-FIX (即適用)

### SF-01: `MfHttpClientConfig` 新設で `RestClient` connect/read timeout 設定 (DoS 防止)
- **元レビュー**: code-review C-impl-1 / design-review Major-4 (timeout 部分)
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfHttpClientConfig.java`
  - 改修: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:34, 36-37`
- **修正内容**: `SimpleClientHttpRequestFactory` で connect=5s / read=30s を設定した `@Bean(name="mfRestClient") RestClient` を追加。`MfApiClient` で `@Qualifier("mfRestClient")` の `RestClient` を field injection に切替え (現行の `restClientBuilder.build()` 都度呼びは廃止)。`BCartHttpClientConfig` を参考に既存パターン踏襲
- **想定影響範囲**: 新規 1 ファイル + 1 ファイル改修
- **テスト確認**: `./gradlew compileJava` + Spring Boot 起動 + `/api/v1/finance/mf-integration/oauth/status` 200 応答
- **依存関係**: なし (SF-08 retry 共通化と同一エージェントで連続適用が望ましい)
- **担当推奨**: 1 サブエージェント (グループ A "HTTP 基盤")

### SF-02: `MfTokenResponse` / `MfOauthService.TokenSnapshot` の `toString()` マスキング override
- **元レビュー**: code-review C-impl-2
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenResponse.java:8`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:255` 付近 (TokenSnapshot record)
- **修正内容**: 両 record に `@Override public String toString()` を追加し `accessToken`, `refreshToken`, `plainSecret`, `plainRefresh` を `***` に置換 (`tokenType`/`expiresIn`/`scope` のみ表示)
- **想定影響範囲**: 2 ファイル
- **テスト確認**: `./gradlew compileJava` + ユニットテスト (toString に "eyJ" や hex が含まれないこと)
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ B "ログ漏洩対策")

### SF-03: `mf-integration-callback.tsx` の `useEffect` を `useRef` で once-only 実行 (StrictMode 二重消費修正)
- **元レビュー**: code-review M-impl-8 / design-review Minor-8
- **対象ファイル**: `frontend/components/pages/finance/mf-integration-callback.tsx:22-63`
- **修正内容**:
  ```tsx
  const calledRef = useRef(false)
  useEffect(() => {
    if (calledRef.current) return
    calledRef.current = true
    ...
  }, [])
  ```
  依存配列を `[]` に変更し、`useSearchParams` は effect 内で `sp.get(...)` を 1 度だけ参照
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + dev mode で MF 認可 → callback 完了するまで E2E 実機確認
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ E "フロント")

### SF-04: token endpoint エラーボディの masking ユーティリティ + ユーザー向けメッセージ汎用化
- **元レビュー**: design-review Critical-2
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/util/SensitiveLogMasker.java`
  - 改修: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:104, 135, 161, 198, 229, 251, 254`
  - 改修: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:132` (callback エラー文言)
- **修正内容**:
  1. `SensitiveLogMasker.mask(String body)` で `[a-zA-Z0-9_-]{20,}` を `***` に置換
  2. 全 `log.warn("...body={}", body)` を `SensitiveLogMasker.mask(body)` 経由に
  3. `MfReAuthRequiredException` のメッセージは「再認証が必要です。詳細は管理者ログを参照してください」に汎用化、`body` を含めない
  4. callback API レスポンスの `Map.of("message", e.getMessage())` も同汎用文言を返却
- **想定影響範囲**: 1 ファイル新規 + 2 ファイル改修
- **テスト確認**: `./gradlew compileJava` + ユニットテスト (`SensitiveLogMasker` の置換テスト)
- **依存関係**: SF-02 と同一エージェントが望ましい (両方ログ漏洩対策)
- **担当推奨**: SF-02 と同一サブエージェント (グループ B)

### SF-05: `window.open` を `noopener,noreferrer` に変更 + `BroadcastChannel` で親⇔子通信
- **元レビュー**: design-review Critical-3
- **対象ファイル**:
  - `frontend/components/pages/finance/mf-integration.tsx:101-104` (親側)
  - `frontend/components/pages/finance/mf-integration-callback.tsx:46-50` (子側)
- **修正内容**:
  1. 親: `window.open(res.url, '_blank', 'noopener,noreferrer')` に変更、`window.opener` 依存コメント削除
  2. 親: `useEffect` で `new BroadcastChannel('mf-oauth')` を購読し `connected`/`failed` メッセージを受信
  3. 子: `window.opener?.postMessage(...)` を `new BroadcastChannel('mf-oauth').postMessage({type:'connected'})` に置換
  4. 親側の `message` event listener (`mf-integration.tsx:72`) も BroadcastChannel に統合
- **想定影響範囲**: 2 ファイル
- **テスト確認**: `npx tsc --noEmit` + 実機で認可 → 親タブで成功通知が届くこと
- **依存関係**: SF-03 と同じフロント callback 周りを触る → 同一エージェント
- **担当推奨**: SF-03 と同一サブエージェント (グループ E)

### SF-06: `MfOauthStateStore.verifyAndConsume` を atomic な `DELETE ... RETURNING` 化
- **元レビュー**: code-review M-impl-2 / design-review Major-2 (TTL 比較も併修正)
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TMfOauthStateRepository.java`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthStateStore.java:74-86`
- **修正内容**:
  1. Repository に native query `@Modifying @Query(value="DELETE FROM t_mf_oauth_state WHERE state=:state RETURNING user_no, code_verifier, expires_at", nativeQuery=true)` を追加 (Object[] 戻り or projection interface)
  2. `verifyAndConsume` を 1 SQL で取り出し → race 排除
  3. ついでに `isBefore(Instant.now())` を `!Instant.now().isBefore(expiresAt)` に変更し境界揃え (Major-2)
  4. `userNo == null` 同士の許容を撤去 (Major-3 部分対応): `null` は常に reject
- **想定影響範囲**: 2 ファイル
- **テスト確認**: `./gradlew compileJava` + 既存 state ライフサイクルが壊れないことの実機確認 (認可 → callback)
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ C "OAuth state/token 整合性")

### SF-07: `MfApiClient.basicAuthHeader` の RFC 6749 §2.3.1 準拠 URL encode
- **元レビュー**: design-review Minor-3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:260-263`
- **修正内容**: `clientId + ":" + clientSecret` の連結を `URLEncoder.encode(clientId, UTF_8) + ":" + URLEncoder.encode(clientSecret, UTF_8)` に変更
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 既存認可フロー疎通 (現 secret に特殊文字がない前提では挙動同一)
- **依存関係**: なし
- **担当推奨**: SF-06 と同一サブエージェント (グループ C)

### SF-08: `MfApiClient.executeWithRetry` 共通化で 429/5xx リトライを全 API に適用
- **元レビュー**: design-review Major-4 (retry 部分) / `MfJournalReconcileService` の m-impl-6 (pagination) と連動
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java` 全体
- **修正内容**:
  1. private `<T> T executeWithRetry(Supplier<T> op, String operation)` を実装 (1s/2s/4s 指数バックオフ、429+5xx のみ retry、最大 3 回)
  2. `listJournals`, `listAccounts`, `listTaxes`, `getTrialBalanceBs`, `getRaw` の RestClient 呼び出しを `executeWithRetry(...)` 経由に統一
  3. `MfJournalFetcher.callListJournalsWithRetry` (line 140-158) は二重 retry にならないよう削除し API client 側に集約
  4. token endpoint (`exchangeCodeForToken`/`refreshToken`) も同 retry 適用 (4xx は retry しない)
- **想定影響範囲**: 2 ファイル (`MfApiClient` + `MfJournalFetcher`)
- **テスト確認**: `./gradlew compileJava` + journals 取得疎通確認
- **依存関係**: SF-01 (timeout) と同一エージェント推奨
- **担当推奨**: SF-01 と同一サブエージェント (グループ A)

### SF-09: `MfJournalReconcileService.reconcile` を `MfJournalFetcher` 経由に置換 (pagination 一貫適用)
- **元レビュー**: code-review m-impl-6
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalReconcileService.java:56, 96`
- **修正内容**: `mfApiClient.listJournals(...).items()` 直叩きを `mfJournalFetcher.fetchJournalsForPeriod(shopNo, date, date)` に置換 (cache + pagination + retry が自動適用される)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 既存 reconcile 実機確認 (1 日分でも items 件数同一)
- **依存関係**: SF-08 と同じ MF API 周りを触るので同一エージェント
- **担当推奨**: SF-08 と同一サブエージェント (グループ A)

### SF-10: `MfApiClient.urlEncode(null)` を fail-fast 化
- **元レビュー**: code-review m-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:265-267`
- **修正内容**: `Objects.requireNonNull(s, "url-encoded 引数が null")` を `urlEncode` 先頭に追加 (silent failure 排除)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-08 と同一サブエージェント (グループ A)

### SF-11: `MfApiClient.buildAuthorizeUrl` / `listJournals` を `UriComponentsBuilder` で組み立て
- **元レビュー**: code-review m-impl-2 / design-review Minor-7
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:40-53, 115-143`
- **修正内容**: `StringBuilder` + `?`/`&` 判定を `UriComponentsBuilder.fromHttpUrl(authorizeUrl).queryParam(...).build().toUriString()` に置換、journals は `.uri(uriBuilder -> uriBuilder.path(...).queryParam(...))` に統一
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 認可 URL の query 順序差異がないことを確認
- **依存関係**: なし
- **担当推奨**: SF-08 と同一サブエージェント (グループ A)

### SF-12: `MfIntegrationController` の `user==null` 冗長 check を `LoginUserUtil` に統一
- **元レビュー**: design-review Minor-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:71, 80, 98, 100, 116, 122, 141, 142, 160, 161, 177` 等
- **修正内容**: `@AuthenticationPrincipal LoginUser user` の null check を削除し、`LoginUserUtil.getLoginUserInfo()` を使用 (`FinanceController.java:275` 既存パターンと統一)
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava` + 既存 endpoint 疎通
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ D "Controller 整理")

### SF-13: `MfIntegrationController` の debug endpoint 群を `MfIntegrationDebugController` に分離
- **元レビュー**: design-review Minor-5 / code-review m-impl-5
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationDebugController.java`
  - 改修: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:267-572` の `/debug/*` 群を新ファイルに移動
- **修正内容**: 新 Controller に `@Profile({"dev","test"})` + `@RequestMapping("/api/v1/finance/mf-integration/debug")` を付け、本番では Bean 自体が登録されない構造に変更。`isDevProfile()` 個別ガードは削除可能 (Profile 側で担保)
- **想定影響範囲**: 1 新規 + 1 改修 (合計約 300 行の移動)
- **テスト確認**: `./gradlew compileJava` + dev profile で debug endpoint 200, prod profile (起動シミュ) で 404
- **依存関係**: SF-12 と同一エージェント (両方 Controller リファクタ)
- **担当推奨**: SF-12 と同一サブエージェント (グループ D)

### SF-14: `MfApiClient.getRaw` を `MfDebugApiClient` に分離
- **元レビュー**: code-review m-impl-5
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfDebugApiClient.java` (`@Profile({"dev","test"})`)
  - 改修: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:89-108` から削除
- **修正内容**: `getRaw` メソッドを `MfDebugApiClient` に切り出し、`MfIntegrationDebugController` から注入
- **想定影響範囲**: 2 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: SF-13 と同一エージェント
- **担当推奨**: SF-13 と同一サブエージェント (グループ D)

### SF-15: `MfScopeInsufficientException` を全 endpoint で 403 にマップ (`@RestControllerAdvice`)
- **元レビュー**: code-review m-impl-11
- **対象ファイル**:
  - 新規: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationExceptionHandler.java`
  - 改修: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:204-211, 240-248, 252-262` の個別 catch ブロックを撤去
- **修正内容**: `@RestControllerAdvice(assignableTypes = {MfIntegrationController.class, FinanceController.class})` で `MfReAuthRequiredException` (401) / `MfScopeInsufficientException` (403 + `requiredScope` body) / `IllegalStateException` (422) を集約
- **想定影響範囲**: 1 新規 + 1 改修
- **テスト確認**: `./gradlew compileJava` + scope 不足 endpoint で 403 + scope 名が返ること
- **依存関係**: なし (SF-12 / SF-13 と同一エージェント推奨)
- **担当推奨**: SF-12 と同一サブエージェント (グループ D)

### SF-16: `mf-integration.ts` の `MF_DEFAULT_CONFIG.scope` を配列定義 + `.join(' ')`
- **元レビュー**: code-review m-impl-8
- **対象ファイル**: `frontend/types/mf-integration.ts:194-200`
- **修正内容**:
  ```ts
  export const MF_REQUIRED_SCOPES = [
    'mfc/accounting/journal.read',
    'mfc/accounting/accounts.read',
    'mfc/accounting/offices.read',
    'mfc/accounting/taxes.read',
    'mfc/accounting/report.read',
  ] as const
  export const MF_DEFAULT_SCOPE = MF_REQUIRED_SCOPES.join(' ')
  ```
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ E "フロント")

### SF-17: `mf-integration.tsx` の `useEffect` 依存配列を key field のみに
- **元レビュー**: code-review m-impl-9
- **対象ファイル**: `frontend/components/pages/finance/mf-integration.tsx:54-67`
- **修正内容**: `[clientQuery.data]` → `[clientQuery.data?.id, clientQuery.data?.clientId]` に変更
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: SF-16 と同一サブエージェント (グループ E)

### SF-18: `mf-health.tsx` の anomaly タブから `unverifiedCount` 重複表示を削除
- **元レビュー**: code-review m-impl-10
- **対象ファイル**: `frontend/components/pages/finance/mf-health.tsx:131-148`
- **修正内容**: anomaly カード側の「未検証 (当月)」行を削除し summary 側のみに統一。判定文言は維持
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし (SF-19 anomaly judgement と整合)
- **担当推奨**: SF-16 と同一サブエージェント (グループ E)

### SF-19: `mf-health.ts:60` の `judgeHealth` で 0 固定 3 値を判定から除外
- **元レビュー**: design-review Major-7 (frontend 暫定対応)
- **対象ファイル**: `frontend/types/mf-health.ts:60` 付近
- **修正内容**: `anomalyTotal = negativeClosingCount` に変更し、TODO コメントで `verifyDiff/continuityBreak/monthGap` は backend 実装後に組み込む旨を明示
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: SF-18 と同一エージェント
- **担当推奨**: SF-18 と同一サブエージェント (グループ E)

### SF-20: `MfOauthService.handleCallback` の HTTP 呼び出しを tx 外に分離
- **元レビュー**: code-review M-impl-1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:121-139` 付近
- **修正内容**: `handleCallback` を 3 段に分割:
  1. read-only tx で `client` + `verifier` 取得 (state はこの時点で消費)
  2. tx 外で `mfApiClient.exchangeCodeForToken(...)` 呼び出し
  3. `REQUIRES_NEW` で `persistToken` を呼ぶ private method を別 Bean 化 (`getValidAccessToken` の B-W6 パターンと統一)
- **想定影響範囲**: 1 ファイル (場合により別 `@Service` 1 つ追加)
- **テスト確認**: `./gradlew compileJava` + 認可フロー疎通
- **依存関係**: SF-06 (state atomic 化) と整合する形で実装
- **担当推奨**: SF-06 と同一サブエージェント (グループ C)

### SF-21: `MfOauthService.persistToken` で refresh_token null fallback (rotate 無効時の旧値維持)
- **元レビュー**: code-review M-impl-4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:213-230`, `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:244-247`
- **修正内容**:
  1. `MfApiClient.postToken` の null check から `refreshToken` を外す (access_token のみ必須)
  2. `persistToken` で `res.refreshToken() != null` なら新規暗号化保存、null なら既存 active token の `refresh_token_enc` を再利用
- **想定影響範囲**: 2 ファイル
- **テスト確認**: `./gradlew compileJava` + 既存認可フロー疎通 (rotate ON 環境では同一挙動)
- **依存関係**: なし
- **担当推奨**: SF-20 と同一サブエージェント (グループ C)

### SF-22: `MfHealthCheckService` に MF 軽量 ping を追加 + cache age 警告 (frontend)
- **元レビュー**: code-review M-impl-6
- **対象ファイル**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java:55-67`
  - `frontend/types/mf-health.ts` (cache age 警告条件)
- **修正内容**:
  1. backend: `MfApiClient.listAccounts(client, accessToken, 1, 1)` を try-catch で呼び `apiReachable: boolean` を `MfOauthStatus` に追加
  2. frontend: cache `oldestFetchedAt` が 24h 以上前なら yellow に落とすロジックを `judgeHealth` に追加
- **想定影響範囲**: 2 ファイル
- **テスト確認**: `./gradlew compileJava` + `npx tsc --noEmit` + ヘルスチェック画面 200 表示
- **依存関係**: SF-19 と整合する形で frontend 改修
- **担当推奨**: SF-19 と同一サブエージェント (グループ E)

---

## DESIGN-DECISION (要ユーザー判断)

### DD-01: `app.crypto.salt` の dev fallback 据え置きをどこまで例外扱いするか
- **元レビュー**: design-review Critical-1
- **論点**: `MEMORY.md feedback_dev_config_fallbacks` で「触らない方針」だが、暗号化対象が高機微 OAuth 資産 (client_secret/access_token/refresh_token) になった以上、ガード強化を再評価すべきと指摘
- **選択肢**:
  - A: 現状維持 (dev fallback 据置)。`runbook-mf-key-rotation.md` を作成して鍵更新手順だけ整備
  - B: dev fallback を「明らかにダミー」(`__SET_APP_CRYPTO_KEY_AND_SALT_VIA_ENV__`) に変更し、起動はするが暗号化を試みると例外で開発者が即気付く構成に
  - C: OAuth 関連 `_enc` 用に専用 KMS / Vault 鍵を導入 (Codex 5 と統合)
- **影響範囲**: A は doc のみ / B は `application-dev.yml` + ローカル開発フロー全員に影響 / C は大規模設計変更
- **推奨**: **A + runbook 作成** を最低限。Codex 5 と束ねて C を中期検討

### DD-02: `m_mf_oauth_client.client_secret` を Vault/KMS に外出し (Codex 5)
- **元レビュー**: codex 5
- **論点**: アプリ侵害時に DB + crypto key の両方が同一権限で取れるため、暗号化は遅延策にすぎない
- **選択肢**:
  - A: Spring Cloud Config + Vault で secret 管理、DB には version のみ
  - B: 現状維持 + DB 暗号化 (DD-01 と統合)
  - C: 中間案: client_secret のみ env var に、DB には保存しない
- **影響範囲**: A は大規模インフラ変更 / B/C は局所
- **推奨**: **B 維持 + Phase 2 で A 検討**。MEMORY.md `feedback_dev_config_fallbacks` 文脈から見て本 sprint では DEFER 寄り

### DD-03: `client_secret` rotation 移行期間の対応 (Codex 6)
- **元レビュー**: codex 6
- **論点**: `upsertClient()` が in-place 更新で旧 secret を保持しないため、MF アプリポータル側 secret 切替時に refresh が割れる
- **選択肢**:
  - A: `client_secret_version` カラム + 旧 secret 短期併存 (rotation 手順設計)
  - B: 現状維持 (ダウンタイム許容で運用回避)
- **影響範囲**: A はスキーマ変更 + Service / B は運用手順のみ
- **推奨**: **B (運用手順で吸収)**。本格化は Phase 2

### DD-04: MF tenant binding を必須化するか (Codex 1)
- **元レビュー**: codex 1
- **論点**: `mfc/admin/tenant.read` を取得し `mf_tenant_id/name` を保存しないと、別会社 MF への誤接続が静かに業務データ不整合になる
- **選択肢**:
  - A: scope に `mfc/admin/tenant.read` 追加 + `m_mf_oauth_client` に `mf_tenant_id`, `mf_tenant_name` 追加 + callback 直後に取得 + refresh 前後で一致検証
  - B: 現状維持 (運用で吸収)
- **影響範囲**: A はスキーマ変更 + scope 変更 (= 再認可必須) + tenant 取得 API 呼び出し
- **推奨**: **A 強く推奨** (Codex 最重要 3 件の 1 つ)。本 sprint で対応する場合は SAFE-FIX 化可能

### DD-05: refresh token 540 日寿命の予兆管理 (Codex 2 / design Minor-2 / code m-impl-3)
- **元レビュー**: codex 2 / design Minor-2 / code m-impl-3
- **論点**: refresh_token の `issued_at` カラム不在のため、`reAuthRequired` が常に false で 540 日満了に気付けない
- **選択肢**:
  - A: `t_mf_oauth_token` に `refresh_token_issued_at`, `last_refresh_succeeded_at`, `refresh_failure_count`, `reauth_deadline_at` を追加 + 60/30/14/7 日前段階アラート
  - B: 暫定: `add_date_time` を refresh_token_issued_at として使い、29 日経過で `reAuthRequired=true` (近似)
  - C: 現状維持
- **影響範囲**: A はスキーマ + alert 基盤 / B は Service だけ / C は運用課題
- **推奨**: **B → A** の順。Phase 1 で B、Phase 2 で A (DEF-01 alert 基盤)

### DD-06: refresh 後の 2 相 commit (永続化失敗時の再利用不可 token 喪失対策, Codex 3)
- **元レビュー**: codex 3
- **論点**: HTTP refresh 成功 → DB commit 失敗で旧 refresh_token が再利用不可になり業務停止
- **選択肢**:
  - A: `t_mf_oauth_token_attempt` ステージング表に先行保存 → commit 後 active 化
  - B: refresh 成功後の DB 保存失敗を Critical アラートにし、即再認可手順誘導
- **影響範囲**: A は中規模 / B は alert 基盤 (DEF-01 と統合)
- **推奨**: **B (alert)** + 設計書に「rotation 強制環境のリスク明文化」

### DD-07: マルチテナント設計 (shop / MF 契約) を今着手するか (Codex 4)
- **元レビュー**: codex 4
- **論点**: 現状 `findFirstByDelFlgOrderByIdDesc()` で 1 client 全社共通。将来店舗別法人やグループ会社追加時に詰む
- **選択肢**:
  - A: `tenant_key` / `company_scope` カラム追加 + 全 MF API 呼び出しに明示的接続 ID
  - B: 現状維持 + 「全社単一前提」を DB 制約 + 設計書に明文化
- **影響範囲**: A は全 service 改修 / B は doc のみ
- **推奨**: **B (doc 明文化)** で Phase 1 OK。A は Phase 2

### DD-08: MF tenant.read scope 変更を破壊的変更として管理 (Codex 7)
- **元レビュー**: codex 7
- **論点**: scope 設定変更と再認可が別操作なため、設定だけ変わって token は旧 scope のまま、機能毎 403 で初検知
- **選択肢**:
  - A: 必須 scope を typed list で定義 + status に `missingScopes`/`extraScopes` 返却 + 設定変更時 connected を yellow/red に降格
  - B: 現状維持
- **影響範囲**: A は SF-16 (配列化) と統合可、Status DTO 拡張 + UI バッジ追加
- **推奨**: **A** を SF-16 完了後の追加タスクとして検討

### DD-09: `MfApiClient` 同期呼び出しをジョブ化 (mf_sync_job) するか (Codex 8)
- **元レビュー**: codex 8
- **論点**: 仕訳突合・勘定科目同期・残高突合が画面操作から同期実行で再開点なし
- **選択肢**:
  - A: `mf_sync_job` テーブル + queue worker + UI を `202 Accepted + polling` に
  - B: 現状維持 (手動再実行で対応)
- **影響範囲**: A は新規ジョブ基盤 + UI 大改造 / B はゼロ
- **推奨**: **B (DEFER)**。MEMORY.md `Next Session Pickup` に追加検討

### DD-10: callback を BFF/backend で完結させるか (Codex 9)
- **元レビュー**: codex 9
- **論点**: 現状 Next.js ページが callback を受けて admin JWT 付きで backend に POST。BFF パターンで CSRF/監査境界を明確化すべき
- **選択肢**:
  - A: redirect_uri を `/api/v1/finance/mf-integration/oauth/callback` (backend 直) に変更 + state は cookie/session
  - B: 現状維持 (state DB 永続化 + JWT で代替)
- **影響範囲**: A は OAuth フロー全面再設計 / B はゼロ
- **推奨**: **B** Phase 1 維持。A は Cluster F refactor で別タスク化

### DD-11: MF API 仕様変更検知 (contract test, Codex 10)
- **元レビュー**: codex 10
- **論点**: API 列削除/型変更/意味変更時の静かな突合誤判定リスク。CI 上の contract fixture / schema drift 検知なし
- **選択肢**:
  - A: journals/accounts/taxes/trial_balance の代表 JSON fixture 固定 + DTO deserialize contract test を CI に追加
  - B: 現状維持 (debug raw endpoint で手動確認)
- **影響範囲**: A は test infra 拡張 / B は運用属人化
- **推奨**: **A** を Phase 2 で実装 (DEFER)

### DD-12: `MfHealthCheckService` の anomaly 集計 0 固定の扱い (design Major-7, B 部分)
- **元レビュー**: design-review Major-7 / MEMORY.md Next Session Pickup
- **論点**: `verifyDiffCount/continuityBreakCount/monthGapCount` が 0 固定で「健全」誤表示
- **選択肢**:
  - A: `PayableAnomalyCounter` util を実装し `MfHealthCheckService` から shop 単位集計
  - B: `t_accounts_payable_summary` に anomaly カラム永続化 (Phase D 統合)
  - C: 現状維持 + frontend で 0 固定 3 値を判定除外 (SF-19 で部分対応済)
- **影響範囲**: A は新 util + service 統合 / B はスキーマ変更 / C は SF-19 のみ
- **推奨**: **A** を本 sprint 後半で実装。SF-19 は暫定 fix

### DD-13: `authorize_url`/`token_url`/`api_base_url` を admin 編集不可にするか (design Major-6 / Codex 15)
- **元レビュー**: design-review Major-6 / codex 15
- **論点**: 現状 admin が DB 経由で MF endpoint URL を変更可能 → 内部不正で攻撃 URL 注入リスク
- **選択肢**:
  - A: application.yml の固定値に移し admin 編集不可、画面は client_id/secret/scope のみ
  - B: `*.moneyforward.com` 正規表現バリデーションを `MfOauthService.upsertClient` に追加
  - C: 現状維持
- **影響範囲**: A は config + UI / B は Service バリデーションのみ
- **推奨**: **B** が SAFE 寄り。A は環境設定見直しが必要なため DESIGN-DECISION

### DD-14: redirect_uri ALLOW list の env 化 (design Minor-6)
- **元レビュー**: design-review Minor-6
- **論点**: `ALLOWED_REDIRECT_URIS` が hardcoded で dev/staging/prod の URL 切替に対応不可
- **選択肢**:
  - A: `@Value("${app.mf.allowed-redirect-uris:...}")` で list 受け取り
  - B: 現状維持 (`feedback_dev_config_fallbacks` の方針上)
- **影響範囲**: A は Service + 全環境 application.yml
- **推奨**: **A** が望ましいが `feedback_dev_config_fallbacks` 方針に照らし要確認

### DD-15: `MfReAuthRequiredException` の cause 細分化 (design Minor-1)
- **元レビュー**: design-review Minor-1
- **論点**: 全 401 が「再認証必要」扱いで token expired と revoke を区別不能
- **選択肢**:
  - A: `cause: TOKEN_EXPIRED | TOKEN_REVOKED | REFRESH_TOKEN_EXPIRED | UNKNOWN` enum 追加 + UI 切替
  - B: 現状維持
- **影響範囲**: A は exception + Service + UI
- **推奨**: **A** を DD-05 (refresh 寿命管理) と統合

### DD-16: `MMfOauthClient` を `@Builder(toBuilder=true)` で immutable copy 化 (code m-impl-7)
- **元レビュー**: code m-impl-7
- **論点**: `upsertClient` が setter mutation で CLAUDE.md immutability 原則違反
- **選択肢**:
  - A: `@Builder(toBuilder=true)` 導入 + `existing.toBuilder().clientId(...).build()` パターン
  - B: 現状維持 (JPA managed entity は mutation で動作 OK)
- **影響範囲**: A は Entity + Service / B はゼロ
- **推奨**: **A** だが JPA managed state との整合性検証が必要

### DD-17: `mf_account_master` を物理削除から論理削除に変更 (code m-impl-4)
- **元レビュー**: code m-impl-4
- **論点**: 物理削除で audit log 残らず、誤実行時の復旧が手動
- **選択肢**:
  - A: `del_flg` カラム追加 + 論理削除 (B-CART パターンと整合)
  - B: `mf_account_master_archive` テーブルにコピー
  - C: 現状維持 (運用注意)
- **影響範囲**: A はスキーマ + Service / B は新テーブル / C はゼロ
- **推奨**: **A** だが mf_account_master の参照経路 (`MfJournalReconcileService` 等) 全洗い出しが必要

### DD-18: callback 監査用 `last_authorized_at` 追加 (code M-impl-3)
- **元レビュー**: code M-impl-3
- **論点**: refresh と新規認可を区別不能 + audit 欠落
- **選択肢**:
  - A: `m_mf_oauth_client` に `last_authorized_at` カラム追加 + callback で更新
  - B: `t_mf_oauth_token` に `obtain_kind` enum (`AUTHORIZATION_CODE`/`REFRESH_TOKEN`) 追加
- **影響範囲**: A/B いずれもスキーマ変更
- **推奨**: **A** がシンプル。DD-05 と一緒にスキーマ拡張するのが望ましい

### DD-19: `MfOauthStateStore.verifyAndConsume` の 403 メッセージ詳細化 (design Major-3 / code M-impl-7)
- **元レビュー**: design-review Major-3 / code M-impl-7
- **論点**: state 発行ユーザーと callback userNo が異なる時の 403 メッセージが汎用すぎ、多重 admin 運用時の UX 混乱
- **選択肢**:
  - A: 「state 発行ユーザーと callback 実行ユーザーが異なります。発行した admin で完了してください」と明示
  - B: 現状維持
- **影響範囲**: A は Service + Controller (1 文言追加)
- **推奨**: **A** だが UX 文言は運用責任者承認後に SAFE-FIX 化

---

## DEFER (将来課題)

### DEF-01: scheduled health probe + push 通知基盤 (Codex 11)
- **元レビュー**: codex 11
- **理由**: alert 基盤 (Slack/メール通知) は別 sprint。MEMORY.md `Next Session Pickup` 候補

### DEF-02: `mf_integration_audit_log` 監査基盤 (Codex 12)
- **元レビュー**: codex 12 / Cluster D 軸 F (`design-audit-trail-accounts-payable.md`) と統合可
- **理由**: 監査基盤は cluster 横断課題。design doc ドラフト済、実装は別 sprint

### DEF-03: MF データの個人情報分類 + 保持期限定義 (Codex 13)
- **元レビュー**: codex 13
- **理由**: GDPR/個人情報保護法対応はガバナンス領域。debug raw endpoint の本番無効化のみ SF-13 (Profile 分離) で部分カバー済

### DEF-04: degraded mode (provider_status) 表示 (Codex 14)
- **元レビュー**: codex 14
- **理由**: MF 一時停止と認証切れの切り分け表示は運用 UX 向上。SF-22 (apply ping) 後に検討

### DEF-05: `MfApiClient` をジョブ化 (Codex 8 = DD-09 実装)
- **元レビュー**: codex 8
- **理由**: DD-09 で B 採択時の追加対応

### DEF-06: 設計書 `design-mf-integration-status.md` 更新 (m_mf_account_mapping 廃止 / cache 追加 / scope 拡張 / Phase B'/B'' 反映)
- **元レビュー**: design-review §「設計書 vs 実装の乖離」全体
- **理由**: doc-update タスク (実装影響なし)。SF 完了後に一括更新

### DEF-07: `MfJournalCacheService` を Caffeine 化 (TTL + size cap + shop 単位 lock)
- **元レビュー**: design-review Major-5 / code M-impl-5
- **理由**: OOM リスク中長期。中規模リファクタ (Caffeine 導入)。SAFE-FIX 化には依存パッケージ追加が必要

### DEF-08: refresh_token rotation 後の MF 側 `POST /oauth/revoke` 呼び出し
- **元レビュー**: design-review Major-1
- **理由**: MF 側 revoke 実装。設計書 §9 で「ベストエフォート省略」と明記済。Phase 2

### DEF-09: `MfBalanceReconcileService` の Cluster F 統合 (cross-reference)
- **元レビュー**: design-review §設計書 vs 実装 §7
- **理由**: doc-update タスク

### DEF-10: 複数 admin 同時操作の application-level rate limiter (Bucket4j)
- **元レビュー**: design-review §設計書 vs 実装 §3
- **理由**: 軽微。現行 `pg_advisory_xact_lock` でほぼ吸収済

### DEF-11: `MfApiClient` debug raw 系メソッドの contract test (DD-11 = Codex 10 実装)
- **元レビュー**: codex 10 = DD-11
- **理由**: DD-11 で A 採択時の追加対応

### DEF-12: `runbook-mf-key-rotation.md` 作成
- **元レビュー**: design-review Critical-1 §修正案 3
- **理由**: doc-update タスク。鍵ローテーション手順整備。DD-01 と統合

---

## ALREADY-RESOLVED

### AR-01: state CSRF / PKCE S256 / active 1 件 unique index (V021) / advisory lock 直列化
- **解消経緯**: 設計レビュー §セキュリティ観点まとめで「OK」評価済。OAuth2 基本機構として既に堅牢に実装済 (`MfOauthStateStore.computeCodeChallenge`, `MfApiClient.exchangeCodeForToken`, V021 partial index, `pg_advisory_xact_lock(REFRESH_LOCK_KEY)`)。本 triage で取り扱う必要なし

---

## 適用順序提案

SAFE-FIX を以下の順序で適用すると依存関係が綺麗:

1. **SF-01 → SF-08 → SF-09 → SF-10 → SF-11** (HTTP 基盤 + retry/pagination 統一) — グループ A、後続 SF が依存
2. **SF-02 → SF-04** (ログ漏洩対策) — グループ B、独立
3. **SF-06 → SF-07 → SF-20 → SF-21** (OAuth state/token 整合性) — グループ C、内部で順序あり
4. **SF-12 → SF-13 → SF-14 → SF-15** (Controller/Service 整理) — グループ D、SF-13 が SF-14 に依存
5. **SF-03 → SF-05 → SF-16 → SF-17 → SF-18 → SF-19 → SF-22** (フロント) — グループ E、独立

SF-22 だけは backend (`MfHealthCheckService`) も触るのでグループ A or E どちらかに含める。本 triage では E 配置 (frontend judgement と一体化のため)

## 並列実行プラン

| グループ | 担当タスク | サブエージェント | 依存 |
|---|---|---|---|
| **A: HTTP 基盤** | SF-01 / SF-08 / SF-09 / SF-10 / SF-11 | 1 | なし (先行推奨) |
| **B: ログ漏洩対策** | SF-02 / SF-04 | 1 | なし |
| **C: OAuth state/token 整合性** | SF-06 / SF-07 / SF-20 / SF-21 | 1 | なし (内部順序あり) |
| **D: Controller/Service 整理** | SF-12 / SF-13 / SF-14 / SF-15 | 1 | なし (内部 SF-13 → SF-14) |
| **E: フロント + ヘルス** | SF-03 / SF-05 / SF-16 / SF-17 / SF-18 / SF-19 / SF-22 | 1 | A 完了後に SF-22 着手 (`MfHealthCheckService` の MF ping 追加) |

並列グループ数: **5**

## 推定総工数

| グループ | 推定 | 内訳 |
|---|---|---|
| A | 4 時間 | SF-01 (1h) + SF-08 (1.5h retry 共通化) + SF-09 (30min) + SF-10/SF-11 (1h) |
| B | 1.5 時間 | SF-02 (30min) + SF-04 (masker + 文言修正 1h) |
| C | 4 時間 | SF-06 (1.5h native query) + SF-07 (15min) + SF-20 (2h tx 分割) + SF-21 (15min) |
| D | 4 時間 | SF-12 (30min) + SF-13/SF-14 (debug 分離 2.5h) + SF-15 (advice 1h) |
| E | 5 時間 | SF-03 (30min) + SF-05 (BroadcastChannel 1.5h) + SF-16/17 (30min) + SF-18/19 (30min) + SF-22 (2h backend ping + frontend) |

**並列実行時の wallclock**: 最遅グループ E の 5 時間 (グループ A〜E が並列で走る前提)
**直列実行時の累積**: 18.5 時間 (1 サブエージェント逐次)

DESIGN-DECISION 19 件 + DEFER 12 件は別途ユーザー判断・別 Sprint で消化。

## セキュリティ系 SAFE-FIX 集計

本 triage で SAFE-FIX 化したセキュリティ系修正:
1. **SF-01**: RestClient timeout 設定 (DoS 防止)
2. **SF-02**: token DTO `toString()` masking (record 漏洩源遮断)
3. **SF-04**: token error body マスキング + ユーザーメッセージ汎用化 (token leak 経路遮断)
4. **SF-05**: `noopener,noreferrer` + BroadcastChannel (tabnabbing 対策)
5. **SF-06**: state DELETE atomic 化 + TTL 比較統一 (state race + 境界事例)
6. **SF-07**: RFC 6749 §2.3.1 準拠 URL encode (BASIC auth 仕様準拠)
7. **SF-15**: `MfScopeInsufficientException` 全 endpoint 403 マップ (scope 不足の正しいハンドリング)

合計 **7 件**。

## 出力ファイルパス

`C:\project\odamitsu-data-hub\claudedocs\triage-mf-integration.md`
