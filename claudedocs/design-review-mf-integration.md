# 設計レビュー: MF 連携基盤 (Cluster F)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-mf-integration-status.md`
レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

---

## サマリー

- 総指摘件数: **Critical 3 / Major 7 / Minor 8** (合計 18 件)
- 承認状態: **Needs Revision**
- 主要懸念:
  1. **Critical-1**: `app.crypto.salt` の dev fallback 値がリポジトリにハードコード (application-dev.yml:21)。同じ値が prod 採用された場合、暗号化済 client_secret / access_token / refresh_token が攻撃者により復号可能。`feedback_dev_config_fallbacks` で「触らない方針」とされているが、暗号化対象が高機微な OAuth 資産になった以上、ガード強化を再評価すべき。
  2. **Critical-2**: `MfApiClient.postToken()` が token endpoint のエラーレスポンスを生でログ出力 + 例外メッセージ伝搬 (`MfApiClient.java:251,254`)。MF 側 OAuth エラー応答は仕様により `error_description` 内に reflected `code` / `state` / 部分的な token を含むケースがあり、ログ漏洩リスク。
  3. **Critical-3**: `mf-integration.tsx:103` で `window.open(res.url, '_blank', 'noreferrer')` を `noopener` 抜きで開いている。`noreferrer` は付くが、`noopener` を明示的に外しているため、認可後 callback タブが `window.opener` 経由で親タブの `location` に書き込み可能 (tabnabbing)。MF 自体は信頼できるが、`authorize_url` を DB から動的に注入できる設計のため、admin 権限を持つユーザーが authorize_url を改竄できれば任意 URL の開放につながる。
- ヘルスチェック画面の anomaly 集計 (`verifyDiffCount`/`continuityBreakCount`/`monthGapCount`) は **0 固定で実装途中** (`MfHealthCheckService.java:103-105`)。frontend (`mf-health.tsx:145-148`) は「(予定)」表記で UX としてはハンドリングされているが、`judgeHealth()` (`mf-health.ts:60`) が anomaly 合計を `red`/`yellow` 判定の根拠にしているため、実態と乖離した「健全」表示が常時出る危険性あり。後述 Major-7 で詳述。

---

## Critical 指摘

### Critical-1: `app.crypto.salt` の dev fallback がリポジトリにハードコード

- **箇所**: `backend/src/main/resources/config/application-dev.yml:21`
  ```yaml
  crypto:
    key: ${APP_CRYPTO_KEY:dev-odamitsu-data-hub-crypto-key-2026}
    salt: ${APP_CRYPTO_SALT:3f6a2d8c9e1b4a7d5c0f8e2a9b6d1c3f}
  ```
- **問題**:
  1. salt 値そのものが git 履歴に commit されている。AES-256/GCM の機密性は salt の秘匿性に依存しないが、PBKDF2 出力 (= 実際の AES 鍵) はパスフレーズ + salt の組合せで一意決定するため、key が leak した瞬間に salt 既知で完全復号可能。
  2. dev fallback の存在自体が「.env を作らずに動く」ことを正当化し、prod 設定漏れに気付きにくい。`CryptoUtil.init` (`CryptoUtil.java:43-66`) は salt が空白なら fail-fast するが、env 設定があれば fallback は使われない仕組み (`${APP_CRYPTO_KEY:default}` は env 未設定時のみ default 採用) のため、本番で env を入れ忘れた場合「dev と同じ鍵で起動できてしまう」。
- **影響**: prod に dev fallback がそのまま流れ込んだ場合、攻撃者は git 履歴から salt + key を取得し、`m_mf_oauth_client.client_secret_enc` / `t_mf_oauth_token.access_token_enc` / `refresh_token_enc` を復号可能。MF OAuth client_secret 漏洩 → MF 上で他社振りすまし、access_token 漏洩 → 仕訳・買掛・売掛閲覧。
- **修正案**:
  1. `application-prod.yml` (line 47-48) は env 必須なので OK だが、`application.yml` (デフォルトプロファイル) で `app.crypto.key/salt` を **空文字に明示し fail-fast に到達** することを保証する E2E テストを追加。
  2. `MEMORY.md` の `feedback_dev_config_fallbacks` で dev fallback 据え置き方針があるが、**MF token + client_secret は機密性が高い**ため、(a) OAuth 関連カラムの `_enc` 用に専用 KMS / Vault 鍵を導入する、もしくは (b) fallback 値を「明らかにダミー」(`__SET_APP_CRYPTO_KEY_AND_SALT_VIA_ENV__`) に変えて、起動はするが暗号化を試みると例外 → 開発者が即気付く構成に変更する選択肢を再検討。
  3. 暗号鍵ローテーション設計が未定義 (`CryptoUtil` 単一鍵)。「鍵更新時は `client_secret_enc` / `*_token_enc` を DELETE → 再認可」と application-dev.yml 16-18 行目コメントには書いてあるが、運用手順書としてマージ前に `claudedocs/runbook-mf-key-rotation.md` を作成すべき。

### Critical-2: token エンドポイントの error body を生ログ出力

- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:251`
  ```java
  log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), body);
  ...
  throw new MfReAuthRequiredException(
          "MF トークン交換に失敗しました（再認証が必要）: " + body, e);
  ```
- **問題**:
  1. MF OAuth2 token endpoint は `error: invalid_grant` 等のエラー応答に `error_description` を含む。実装によっては reflected な `code` / partial token / client_id を含むことがあり、ログレベル `WARN` でアプリログに残る。
  2. body 全体を `MfReAuthRequiredException` のメッセージに連結し、`MfIntegrationController.callback()` (line 132) で `Map.of("message", e.getMessage())` として **API レスポンス body にそのまま返却**。フロント `mf-integration-callback.tsx:55` でメッセージを画面表示。MF が将来エラーレスポンスフォーマットを変更し token を含むようになった場合、即漏洩。
  3. `MfApiClient.java:104,135,161,198,229` の他の API 呼び出しでも同パターン (`body=...` をログ出力)。
- **影響**: 機密情報の意図しない leak。コードレビュー指針 `### セキュリティ` (`CLAUDE.md` グローバル) の "No hardcoded secrets" + 「機微情報のログ出力チェック」観点で違反。
- **修正案**:
  1. `body` をログに出す場合は **token-like / secret-like パターン (`/[a-zA-Z0-9_-]{20,}/`) を `***` に置換**するマスキングユーティリティを `CryptoUtil` 隣に新設 (`SensitiveLogMasker.mask(body)`)。
  2. ユーザー向けエラーメッセージは `body` を含めず汎用文言 (`"再認証が必要です。詳細は管理者ログを参照してください"`) にし、詳細は `log.warn` のみ。
  3. token 取得失敗時に limited な structured log (`status`, `error`, `error_description` のみ抽出) を出す方針へ移行。

### Critical-3: `noopener` を明示的に外して新タブ起動 (tabnabbing)

- **箇所**: `frontend/components/pages/finance/mf-integration.tsx:101-104`
  ```ts
  // 親タブから opener アクセスできるよう noopener は付けない（post-message 通知のため）。
  window.open(res.url, '_blank', 'noreferrer')
  ```
- **問題**:
  1. `noreferrer` は referrer header を抑制するが、HTML 仕様上 `noreferrer` を含むと **暗黙的に `noopener` も適用される** ため、`window.opener` は実は子タブ側から `null` になる。**つまりコメント「親タブから opener アクセスできるよう」は誤り** で、`mf-integration-callback.tsx:46-50` の `window.opener?.postMessage(...)` は実装上 **動かない可能性が高い**。実機で動いているなら、`noreferrer` の opener 抑制ロジックがブラウザ依存 (古い Edge 等) の可能性。
  2. 仮に `noreferrer` を外して `noopener` だけ付けないバリエーションに直すと、子タブ (MF 側) から `window.opener.location = "悪意 URL"` で親タブを書き換え可能 (リバース tabnabbing)。MF 公式は信頼できるが、`authorize_url` 自体が DB 設定 (`m_mf_oauth_client.authorize_url`) で admin 編集可能 (`MfIntegrationController.upsertClient`, line 68-83) のため、admin アカウント乗っ取り → 攻撃 URL 注入 → tabnabbing チェーンが成立する。
- **影響**: post-message 通知機能が実は動いていない (= 検証不足) + 設計意図と実装の乖離。修正方向次第ではセキュリティ低下。
- **修正案**:
  1. **正しい方法**: `BroadcastChannel` API (`new BroadcastChannel('mf-oauth')`) もしくは `localStorage` イベントで親⇔子タブ通信。`window.opener` 依存を削除し、`window.open(url, '_blank', 'noopener,noreferrer')` で両方付ける。
  2. `authorize_url` も `redirect_uri` 同様 ALLOWED list で完全一致検証する (`MfOauthService.ALLOWED_REDIRECT_URIS` のパターンを `authorize_url`/`token_url`/`api_base_url` にも展開)。MF 公式 URL 以外は弾く。
  3. 検証手順: 現状 `window.opener?.postMessage` が実際に発火しているか、Chrome DevTools で MF 認可後のタブで `window.opener` の値を確認 (おそらく `null`)。代替パスを実装後、E2E テストで通知到達を確認。

---

## Major 指摘

### Major-1: refresh_token rotation 後の旧 token 即時無効化処理がない

- **箇所**: `MfOauthService.persistRefreshedToken` → `persistToken` (`MfOauthService.java:177-230`)
- **問題**: refresh で新 access_token + refresh_token を取得後、旧 refresh_token を MF 側で revoke していない。MF は通常 refresh_token を rotate (新発行で旧失効) する仕様だが、rotate しないモード (refresh_token sliding) もあり、API 仕様未確認。`exchangeCodeForToken` のレスポンスに **同一 refresh_token がそのまま返る場合**、旧 refresh_token の有効期限が切れない限り使い回し可能で、過去 leak のリスクが残り続ける。
- **影響**: refresh_token 漏洩時の影響範囲拡大。
- **修正案**: `revoke(userNo)` (`MfOauthService.java:206-211`) は DB 論理削除のみ。設計書 §9 にも「MF 側 revoke はベストエフォート省略」とあるが、`POST /oauth/revoke` (MF 側) を呼び出して失効を伝える実装を追加。refresh 時も旧 refresh_token と新 refresh_token が異なる場合のみ旧側を MF に revoke。

### Major-2: state TTL 検証で `<` 比較に等号がない

- **箇所**: `MfOauthStateStore.verifyAndConsume` (`MfOauthStateStore.java:75`)
  ```java
  if (e.getExpiresAt().toInstant().isBefore(Instant.now())) {
      log.info("MF OAuth state 有効期限切れ");
      return Optional.empty();
  }
  ```
- **問題**: `isBefore` は厳密小なり比較で同タイムスタンプは expired 扱いにならない (= 1ms 単位で expired の境界が「expires 時刻 + 1ms 以降」になる)。設計書 §4.4 の「TTL 10 分」を厳格適用するなら `!Instant.now().isBefore(expiresAt)` の方が安全。
- **影響**: 軽微 (せいぜい 1 リクエストの境界事例) だが、テスト失敗時の挙動が分かりにくい。
- **修正案**: `if (!Instant.now().isBefore(e.getExpiresAt().toInstant()))` に統一。`MfOauthService.TokenSnapshot.isFresh` (`MfOauthService.java:261`) と整合。

### Major-3: callback の `userNo` 一致検証が「同一ブラウザ前提」

- **箇所**: `MfOauthStateStore.verifyAndConsume` (`MfOauthStateStore.java:80-84`)
- **問題**: `state` 発行時の `userNo` と callback 時の `userNo` を比較する設計。callback が **新タブで開かれ、JWT (Authorization header) を持って `POST /oauth/callback` を呼ぶ** ため、同じブラウザの localStorage / cookie に JWT が共有されている前提。
  1. JWT が cookie ではなく Authorization header の場合、認可フロー中に admin がログアウト → 別 admin がログインすると userNo 不一致で弾く動作になるが、ユーザーフレンドリーなエラー文言なし (`MfIntegrationController.java:127` で 403 のみ)。
  2. `userNo == null` (= 認証されていない callback) のケースを `null/null` 一致で通している (`MfOauthStateStore.java:80`)。`SecurityConfig.java:43-45` で `/api/v1/auth/login` 以外は authenticated 必須なので実際には起きないはずだが、設計上の防御層が薄い。
- **影響**: 多重 admin 運用時に分かりにくい挙動。
- **修正案**:
  1. `userNo == null` 同士の許容を削除し、null は常に reject。
  2. 403 レスポンスに「state 発行ユーザーと callback 実行ユーザーが異なります」の文言を追加。

### Major-4: `MfApiClient` が retry ロジックを持たず、`MfJournalFetcher` 側で重複実装

- **箇所**: `MfApiClient.java:115-143` (`listJournals`), `MfJournalFetcher.callListJournalsWithRetry` (`MfJournalFetcher.java:140-158`)
- **問題**: 設計書 §5 で「`MfApiClient` 実装は Spring 6 `RestClient` + `Retry`(Resilience4j) で 429/5xx リトライ」と明記されているが、現実装は **RestClient のみで retry/backoff なし**。`MfJournalFetcher` だけが 429 retry をやっており、`getRaw` / `listAccounts` / `listTaxes` / `getTrialBalanceBs` は retry なし。`exchangeCodeForToken` / `refreshToken` も同様。
- **影響**:
  1. `MfBalanceReconcileService` の trial_balance_bs 呼び出し (`MfBalanceReconcileService.java:59,69`) が 429 で即落ちる。
  2. MF API レート制限 (Operations per second) 違反時に失敗が多発し、ユーザー操作で再実行が必要。
  3. token refresh 自体は通常 1 リクエストで済むため重要度は低いが、複数 admin が同時操作すると失敗ケースあり (advisory lock で直列化はしているが、MF 側は別)。
- **修正案**:
  1. `MfApiClient` 内に共通 `executeWithRetry(Supplier<T>, String operation)` を追加し、429 + 5xx は指数バックオフ retry。設計書 §5 と整合。
  2. timeout (connect 5s / read 30s) も設計書記載だが、`restClientBuilder` の build にタイムアウト設定が無い。`RestClient.Builder` に `requestFactory(ClientHttpRequestFactory)` を Bean 化し、Apache HttpClient5 / SimpleClientHttpRequestFactory で接続/読取タイムアウトを設定。

### Major-5: `MfJournalCacheService` が TTL なしのインメモリで OOM リスクあり

- **箇所**: `MfJournalCacheService.java:21-35` および `cachesByShop` (line 43)
- **問題**: 設計コメント (line 22-26) に「TTL なし、サーバー再起動まで保持」と明記。`MEMORY.md` の「初回 12.98s、cache hit 75ms」記載通り効果は大きいが:
  1. 1739 journals × 24 ヶ月 × shop 数 = メモリ蓄積。`MfJournal` レコードは `branches[]` を含む比較的大きなオブジェクト (`MfJournal.java`)。
  2. shop ごとの cache map を `synchronized` で守る大きな単一 lock (`getOrFetch` メソッド全体が synchronized) のため、高並列時に他 shop の操作も待ち。
  3. 手動 `invalidateAll(shopNo)` のみで、自動失効 / size cap なし。MF 側で過去 journal が修正された場合に検知不能。
- **影響**: 中長期運用で OOM および古いデータでの判断ミス。
- **修正案**:
  1. Caffeine Cache で `expireAfterWrite=24h` + `maximumSize=10000 entries` を設定 (`MfJournalCacheService` 内部実装を Caffeine に置換)。
  2. `synchronized` を shop 単位の `ReentrantLock` に分割。
  3. invalidateAll を「shop × month range 指定」で部分破棄できるように API 拡張 (`/finance/mf-health/cache/invalidate?shopNo=&fromMonth=&toMonth=`)。
  4. 設計書 §5 / §10 にキャッシュ戦略の記述がそもそも無い。`design-mf-integration-status.md` に Phase 1.5 として追記が必要。

### Major-6: redirect_uri ホワイトリストに admin 編集パスがない一方、authorize_url/token_url/api_base_url は admin 編集可

- **箇所**: `MfOauthService.ALLOWED_REDIRECT_URIS` (line 47-50), `MfIntegrationController.upsertClient` (line 68-83)
- **問題**:
  1. redirect_uri は完全一致 ALLOW list でガード (B-4)、これは正解。
  2. しかし `authorize_url` / `token_url` / `api_base_url` は admin が画面から自由設定可能 (`MfOauthClientRequest.java:25-32`)。admin アカウント乗っ取り or 内部不正で「`authorize_url=https://attacker.example/oauth`」に変更 → 認可フローで admin の MF 認可情報が攻撃者に流れる可能性。
  3. token endpoint も同様 (token leak 経路)、api_base_url も同様 (将来書き込み権限取得時に偽 API へ機微データ流出)。
- **影響**: admin 権限濫用 / 内部不正時の被害拡大。
- **修正案**:
  1. authorize_url / token_url / api_base_url を application.yml の固定値に移し、admin 編集不可にする (clientId / clientSecret / redirectUri / scope のみ編集可能)。
  2. もしくは `*.moneyforward.com` ドメインに限定する正規表現バリデーションを `MfOauthService.upsertClient` に追加。
  3. 設計書 §13 にも「アプリポータル登録時の URL は固定」と書くべき。

### Major-7: ヘルスチェックの anomaly 集計が 0 固定で実質的に「健全」誤表示

- **箇所**:
  - Backend: `MfHealthCheckService.java:103-105`
    ```java
    .verifyDiffCount(0L)
    .continuityBreakCount(0L)
    .monthGapCount(0L)
    ```
  - Frontend: `mf-health.tsx:145-148`, `mf-health.ts:60` (`anomalyTotal` 計算)
- **問題**:
  1. backend が 0 固定。`AccountsPayableLedgerService.summarize` (`AccountsPayableLedgerService.java:293-326`) では supplier 単位で実装済みのため、 **shop 単位で集計するロジックを util 化** すれば実装可能 (MEMORY.md の `Next Session Pickup` でも `PayableAnomalyCounter` util 化として残課題化)。
  2. frontend `judgeHealth()` (`mf-health.ts:60`) が `anomalyTotal = negativeClosingCount + verifyDiffCount + continuityBreakCount + monthGapCount` で判定。0 固定の 3 値が常に 0 のため、negativeClosingCount のみが anomaly 判定の根拠になる。実際には verifyDiff / continuityBreak / monthGap が複数発生している可能性があるのに「健全」表示される。
  3. 「(予定)」ラベルで UI 上は分かるが、ユーザーがそれに気付かないと判断ミスにつながる (例: 「健全」と表示されているから精算手続を進める)。
- **影響**: ヘルスチェック画面の主機能が機能不全。実運用で UX を信頼できない。
- **修正案**:
  1. `PayableAnomalyCounter` util を新設し、`AccountsPayableLedgerService` の anomaly 検出ロジック (line 293-310) を抽出。`MfHealthCheckService` から shop 単位の全 supplier を回して合算。
  2. もしくは `t_accounts_payable_summary` に anomaly カラムを永続化する案も検討 (毎回スキャンせず済む)。
  3. それまでの間は frontend `judgeHealth()` を修正し、 0 固定 3 値を **判定から除外** する (UI 表示はしつつ、判定には使わない)。コメントで `// TODO: backend 実装後に judgement に組み込む` を明記。
  4. 設計書 `design-supplier-balances-health.md` §4.3 を更新し「Phase 1: negativeClosing のみ実装、Phase 2 で残り 3 種を追加」と Phase 分割を明記。

---

## Minor 指摘

### Minor-1: `MfReAuthRequiredException` の発生条件が広すぎ

- **箇所**: `MfApiClient.java:104, 135, 161, 198, 229` 全 401 → `MfReAuthRequiredException`
- **問題**: 401 = 一律「再認証必要」だが、access_token expired は refresh で済む (再ログイン不要)。`MfOauthService.getValidAccessToken` (line 141-155) で 5 分前 refresh するため通常起きないが、稀に MF 側で revoke された場合と区別がつかない。
- **修正案**: `MfReAuthRequiredException` に `cause: TOKEN_EXPIRED | TOKEN_REVOKED | REFRESH_TOKEN_EXPIRED | UNKNOWN` enum を持たせ、UI でメッセージを切り替える。

### Minor-2: `MfTokenStatus.reAuthRequired` が常に false

- **箇所**: `MfOauthService.getStatus` (`MfOauthService.java:187-203`)
- **問題**: `MfTokenStatus` (`MfTokenStatus.java`) に `reAuthRequired` フィールドがあるが、`getStatus()` 内では常に `false` を返している。refresh_token 期限を判定するロジックがない。MF の refresh_token 期限 (通常 30 日) を保存していない (`t_mf_oauth_token` schema にない) ため計算不能。
- **修正案**: refresh_token の発行日時から経過時間で「29 日以上経過なら reAuthRequired=true」と推測ロジックを追加。または MF API に refresh 試行を一度行い 401 なら true にする probe 機能。

### Minor-3: `MfApiClient.basicAuthHeader` の Base64 エンコード前 raw が `clientId:clientSecret` のまま (RFC 6749 適合性)

- **箇所**: `MfApiClient.java:260-263`
- **問題**: RFC 6749 §2.3.1 では client_id/client_secret を **`application/x-www-form-urlencoded` でエンコードしてから** Base64 化することを要求。現実装は raw 文字列をそのまま結合。MF の client_secret に `:` や `+` などが含まれる可能性は低いが、仕様準拠のため修正推奨。
- **修正案**: `URLEncoder.encode(clientId, UTF_8) + ":" + URLEncoder.encode(clientSecret, UTF_8)` に変更。

### Minor-4: `MfIntegrationController` の `@AuthenticationPrincipal LoginUser user` で `user==null` チェックが冗長

- **箇所**: `MfIntegrationController.java:71,80,98,100,116,122,141,142,160,161,177` 等
- **問題**: `@PreAuthorize("hasRole('ADMIN')")` (line 45) で必ず認証済みのため、`user == null` は到達不能。冗長な null check が散在。
- **修正案**: `LoginUserUtil.getLoginUserInfo()` のような共通 util に集約 (`FinanceController.java:275` で既に使用)。`MfIntegrationController` も同パターンに統一。

### Minor-5: `MfIntegrationController` に debug endpoint が 6 個混在 (構造設計上の問題)

- **箇所**: `MfIntegrationController.java:267-572` の `/debug/*` 群
- **問題**: 本番 API と debug API が同一 Controller。`isDevProfile()` でガードはあるが、line 数が膨大 (Controller 全体 628 行のうち 300 行以上が debug)。CLAUDE.md の「small files (200-400 lines)」原則違反。
- **修正案**: `MfIntegrationDebugController` に分離し、`@Profile({"dev","test"})` でクラス丸ごとガード。

### Minor-6: `MfOauthService.ALLOWED_REDIRECT_URIS` が hardcoded で環境ごとに変更不可

- **箇所**: `MfOauthService.java:47-50`
- **問題**: 本番 URL `https://odamitsu-data-hub.local/...` がコード上に固定。dev / staging / prod で URL が違う場合にコード変更が必要。`feedback_dev_config_fallbacks` で fallback 据え置き方針はあるが、URL 自体は秘密ではないので環境変数化推奨。
- **修正案**: `@Value("${app.mf.allowed-redirect-uris:http://localhost:3000/finance/mf-integration/callback}")` で list 受け取り。

### Minor-7: `MfApiClient.listJournals` の URL 組み立てに RestClient の uriBuilder 未使用

- **箇所**: `MfApiClient.java:117-121`
- **問題**: `+ "?start_date=" + urlEncode(...)` で文字列連結。`RestClient.uri(URI uri)` + `UriComponentsBuilder` を使うべき。
- **修正案**: `restClient().get().uri(uriBuilder -> uriBuilder.path("/api/v3/journals").queryParam("start_date", startDate)...)` に統一。

### Minor-8: `mf-integration-callback.tsx` で `useEffect` 依存配列に `sp` のみで `setPhase` 等が含まれない

- **箇所**: `frontend/components/pages/finance/mf-integration-callback.tsx:63`
- **問題**: React 19 + Next.js 16 環境では eslint-react-hooks が `setPhase`/`setMessage` を依存配列必須としないが、`sp` は `useSearchParams()` の戻り値で参照値が変わらない場合があり、`code/state` が同じでも reload 時に effect が走らない可能性。
- **修正案**: 一度だけ実行する callback としては `useEffect(() => { ... }, [])` + 内部で `useSearchParams` 取得 or React 19 の Action パターンで直叩き。

---

## セキュリティ観点まとめ (OAuth/トークン管理)

| 項目 | 現状 | 評価 |
|------|------|------|
| **Authorization Code Grant + PKCE S256** | `MfOauthStateStore.computeCodeChallenge` (line 99-107) で SHA-256 + base64url-no-padding 実装。`MfApiClient.exchangeCodeForToken` (line 58-68) で code_verifier 送信 | OK (RFC 7636 準拠) |
| **state CSRF 防止** | `MfOauthStateStore` で `randomUrlSafe(32)` (256bit) 発行、DB 永続化、TTL 10 分、userNo 一致検証 | OK (Major-2/3 を除けば堅牢) |
| **Client Secret 暗号化** | `CryptoUtil` (Encryptors.delux = PBKDF2 + AES-256/GCM) で `m_mf_oauth_client.client_secret_enc` 保存 | OK (Critical-1 の鍵管理を除けば良好) |
| **access_token / refresh_token 暗号化** | 同 CryptoUtil で `t_mf_oauth_token.access_token_enc` / `refresh_token_enc` 保存 | OK |
| **token logging masking** | `log.info("MF access_token を refresh します（expiresAt={}）", snap.expiresAt)` 等で **token 値は出していない** が `body=...` 全体出力 (Critical-2) | NG |
| **redirect_uri ホワイトリスト** | ALLOW list 完全一致 (`MfOauthService.ALLOWED_REDIRECT_URIS`) | OK (Minor-6 で env 化推奨) |
| **rate limit / 429 対策** | `MfJournalFetcher` のみ実装、それ以外は未対応 (Major-4) | NG |
| **token rotation 後の旧 token 失効** | DB 論理削除 + active 1 件 unique index (`V021`) のみ。MF 側 revoke 未実装 | 部分的 (Major-1) |
| **scope 最小権限** | `mfc/accounting/journal.read` 等読み取り中心。書き込み権限なし | OK |
| **CLIENT_SECRET_BASIC** | `Authorization: Basic base64(id:secret)` で認証。RFC 6749 §2.3.1 の URL エンコード未対応 (Minor-3) | 軽微 NG |
| **同時 refresh race** | PostgreSQL `pg_advisory_xact_lock(REFRESH_LOCK_KEY)` で直列化 (`MfOauthService.java:179-181`) | OK (シングル DB 前提なら堅牢) |
| **callback の opener 経由通信** | `noreferrer` 付き window.open + `window.opener.postMessage` で通知。実は動かない可能性 (Critical-3) | NG |
| **DB schema integrity** | `V021` で `t_mf_oauth_token` active 1 件 unique partial index、`t_mf_oauth_state` PK + expires index | OK |

---

## 設計書 vs 実装の乖離

### 1. 勘定科目マッピングのテーブル設計が変更された

- **設計書 §6 (line 188-216)**: `m_mf_account_mapping` テーブルに admin が画面で `journal_kind × side × mf_account_id` を登録する設計。
- **実装**: `V020__drop_mf_account_mapping.sql` で削除済み。代わりに `mf_account_master` (旧: 手動 CSV 取込のテーブル) を `MfAccountSyncService` で MF API から自動同期する方針に変更。マッピングは `MfJournalReconcileService.PURCHASE_DEBIT/PURCHASE_CREDIT` 等のハードコード定数 (`MfJournalReconcileService.java:60-78`) に集約。
- **影響**: 設計書が古い。マージ前に **設計書 §6 を「mf_account_master 自動同期に変更」と書き換える** 必要あり。

### 2. リトライ実装が設計書と異なる

- **設計書 §5 (line 142-146)**: 「Spring 6 RestClient + Retry(Resilience4j) で 429/502/503/504 で最大 3 回リトライ (指数バックオフ 1s, 2s, 4s)」
- **実装**: Resilience4j 未使用。`MfJournalFetcher.callListJournalsWithRetry` (line 140-158) のみ手動 retry (1s/2s/4s)、他の API は retry なし (Major-4 参照)。
- **影響**: 設計通り全 API に retry 適用すべき。

### 3. 「公式 PoC は同時 1 リクエスト」記述に対する実装

- **設計書 §9 (line 273)**: 「同時 1 リクエスト、429 で待機」
- **実装**: 同時 1 リクエスト制約は MF 側のレート制限であり、`MfJournalFetcher` がページ間 350ms sleep + 429 retry で対応 (`RATE_LIMIT_SLEEP_MS=350`)。一方、複数 admin が同時に画面操作した場合の同時リクエスト制御はなし。
- **影響**: 軽微。複数 admin が同時 reconcile すると 429 多発リスクあり。Phase 2 で application-level rate limiter (Bucket4j 等) 導入推奨。

### 4. キャッシュ層の設計が後付け

- **設計書**: `MfJournalCacheService` に関する記述なし (Phase 1 設計書発行は 2026-04-20、cache 実装は 2026-04-23)。
- **影響**: cache invalidation strategy / TTL / OOM 対策 / 整合性 (MF 側修正の検知) が設計書未記載のため、レビューが薄い。Major-5 参照。

### 5. ヘルスチェック画面が設計書範囲外

- **設計書 (mf-integration-status)**: ヘルスチェック画面の言及なし (`design-supplier-balances-health.md` 別ファイル)。
- **影響**: Cluster F として一体化レビューする際に整合性チェックが分散。`design-mf-integration-status.md` に「軸 E ヘルスチェックは別設計書 `design-supplier-balances-health.md` 参照」と相互参照を追加すべき。

### 6. Redirect URI 設計

- **設計書 §7 (line 234-240)**: `http://localhost:3000/finance/mf-integration/callback` 1 個のみ言及。
- **実装**: `MfOauthService.ALLOWED_REDIRECT_URIS` で `http://localhost:3000/...` + `https://odamitsu-data-hub.local/...` の 2 個許可。
- **影響**: 設計書 §7 を更新し本番 URL も明記すべき。

### 7. 残高突合 (`MfBalanceReconcileService`) は別設計書

- **実装**: `MfBalanceReconcileService` は `design-supplier-partner-ledger-balance.md` §5 由来。
- **影響**: Phase 1 設計書範囲外なのでレビュー外だが、Cluster F としては OAuth 共通基盤を使うため、ヘルスチェック同様 cross-reference が望ましい。

---

## レビューチェックリスト結果

### Spring Boot / バックエンド観点

- [x] `@PreAuthorize("hasRole('ADMIN')")` でクラスレベル権限制御 (`MfIntegrationController.java:45`)
- [x] `@Transactional` 適切に付与 (Service 層、`MfOauthService` 内 readOnly / REQUIRES_NEW 区別)
- [x] DTO/Entity 分離 (`MfOauthClientRequest` / `MfOauthClientResponse` / `MMfOauthClient`)
- [x] `@Valid` + Jakarta Validation (`MfOauthClientRequest` の `@NotBlank` 等)
- [x] `@PrePersist` / `@PreUpdate` で監査フィールド自動補完 (`MMfOauthClient.java:70-78`)
- [x] Repository は JpaRepository 継承 + custom query を `@Query` で記述
- [ ] **Resilience4j 未導入** (Major-4)
- [ ] **RestClient タイムアウト未設定** (Major-4)
- [ ] **Controller 行数超過** (`MfIntegrationController` 628 行、CLAUDE.md 「max 800」 OK だが debug 分離推奨, Minor-5)
- [x] `LoginUserUtil` 共通化済 (FinanceController) — MfIntegrationController も統一余地あり (Minor-4)

### OAuth2 / セキュリティ観点

- [x] PKCE S256 実装 (RFC 7636 準拠)
- [x] state DB 永続化 + TTL + userNo 検証 (B-3, Major-2/3 で軽微指摘)
- [x] redirect_uri ホワイトリスト完全一致 (B-4)
- [x] CLIENT_SECRET_BASIC (RFC 6749 §2.3.1) — Minor-3 軽微
- [x] AES-256/GCM 暗号化 (CryptoUtil)
- [ ] **暗号鍵管理** (Critical-1)
- [ ] **token 関連エラーボディの masking** (Critical-2)
- [ ] **window.open noopener/tabnabbing** (Critical-3)
- [ ] **MF 側 revoke 未呼出** (Major-1)
- [ ] **authorize_url/token_url admin 編集可** (Major-6)
- [x] active token unique partial index (V021)
- [x] state TTL sweep 機能 (`MfOauthStateStore.sweep`)

### MF 連携固有観点

- [x] PKCE 必須 / state CSRF 対策実装済
- [x] 期首残 (`MfOpeningBalanceService`) で fiscal year 境界の特殊 journal を構造判定 (`isPayableOpeningJournal`)
- [x] Journal Cache 共通化 (`MfJournalCacheService`)
- [ ] **キャッシュ TTL / size cap 未設定** (Major-5)
- [x] 勘定科目マスタ自動同期 (`MfAccountSyncService`)
- [x] enum translation 機能で英→日翻訳辞書を管理 (`MfEnumTranslationService`)
- [x] scope 不足の 403 ハンドリング (`MfScopeInsufficientException`)
- [ ] **ヘルスチェック anomaly 集計 0 固定** (Major-7)
- [x] 配列 endpoint で per_page=1000 + pagination safeguard (max 50 pages, `MfJournalFetcher`)
- [x] fiscal year 境界 fallback (`buildStartDateCandidates`)

### フロントエンド観点

- [x] admin 限定表示 (`isAdmin = user?.shopNo === 0`)
- [x] TanStack Query で fetch + invalidate
- [x] `MF_DEFAULT_CONFIG` で初期値定数化 (`mf-integration.ts:194-200`)
- [x] post-message origin チェック (`mf-integration.tsx:72`)
- [ ] **`window.opener` post-message 動作確認** (Critical-3 で実機検証必要)
- [x] callback 後 `queryClient.invalidateQueries` で即時反映
- [x] secret 入力フィールドは `type="password"` + `autoComplete="new-password"`
- [x] 1 度保存後は secret を空にして UI 上から消す (`mf-integration.tsx:93`)

---

## 総合所見

OAuth2 + PKCE + state DB 永続化 + active 1 件保証 + 暗号化保存といった **基本設計は極めて堅牢**で、Critical-1 (鍵管理) を除けば商用導入できる水準。Critical-2 (ログ漏洩) と Critical-3 (window.open ハンドリング) も軽微な修正で対応可能。Major レベルでは **rate limit/retry の網羅 (Major-4)** と **ヘルスチェック anomaly 実装 (Major-7)** が運用品質に直結するため最優先で着手すべき。

設計書 (design-mf-integration-status.md) は Phase 1 の最初に書かれて以降アップデートされておらず、`m_mf_account_mapping` 廃止、cache 追加、ヘルスチェック画面追加、scope 拡張、Phase B/B''/B''' 対応などが設計書に反映されていない。Cluster F 全体を 1 本の生きた設計書としてマージするか、もしくは設計書を Phase ごとに分割して相互参照する運用ルールを `MEMORY.md` に追加するのが望ましい。

---

レビュアー: Opus サブエージェント
最終更新: 2026-05-04
