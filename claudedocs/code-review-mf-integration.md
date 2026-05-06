# コードレビュー: MF 連携基盤 (Cluster F)

レビュー日: 2026-05-04
ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント
対象スコープ:
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/**`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java`
- `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java`
- `backend/src/main/java/jp/co/oda32/dto/finance/MfOauthClient*.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/{MMfOauthClient,TMfOauthToken}.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/{MMfOauthClientRepository,TMfOauthTokenRepository,MfAccountMasterRepository}.java`
- `frontend/components/pages/finance/mf-integration{,-callback}.tsx` / `mf-health.tsx` / `Mf*Tab.tsx`
- `frontend/types/{mf-integration,mf-health}.ts`

## 前提

設計レビュー (`claudedocs/design-review-mf-integration.md`) で報告済の指摘 (Critical 3 / Major 7 / Minor 8) は本レビューでは **再掲しない**。本レビューは設計レビューでカバーされなかったコード固有の問題、特に
- 暗号鍵 / token のライフサイクル詳細
- HTTP クライアント設定 (timeout/retry)
- DB 永続化トランザクション境界
- フロント側のセキュリティ詳細
- Controller / Service の保守性

に絞った。

## サマリー

- 総指摘件数: **Blocker 0 / Critical 2 / Major 8 / Minor 11** (合計 21 件)
- 承認状態: **Needs Revision**
- セキュリティ系の新発見 (設計レビュー未指摘):
  1. **C-impl-1**: `RestClient.Builder` の DI を **Bean 化せずデフォルト共有** で取得しており、connect/read timeout が **無限** になる可能性。MF 側 hang で全 admin スレッドが thread pool ごと枯渇する Denial-of-Service 性のリスク。
  2. **C-impl-2**: `MfApiClient.exchangeCodeForToken` / `refreshToken` のレスポンス body を `MfTokenResponse` (record) に直接バインドした際、**MF が将来追加するフィールド** (`id_token`, `refresh_token_expires_in` 等) は捨てられる。それ自体は OK だが `toString()` が record 自動生成で `accessToken` 値そのものを含むため、Lombok `@Slf4j` 等の意図しない `log.debug("res={}", res)` 一行で **生 access_token がログ漏洩** する。現状は出していないが防御層が無い。

詳細は下記。

---

## Critical

### C-impl-1: `RestClient` の connect/read timeout が未設定 (DoS リスク)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:34`
  ```java
  private final RestClient.Builder restClientBuilder;
  ...
  public RestClient restClient() {
      return restClientBuilder.build();
  }
  ```
- **問題**:
  - Spring Boot のデフォルト `RestClient.Builder` は `JdkClientHttpRequestFactory` 系で、`requestFactory()` を明示しない限り **read timeout も connect timeout も無限**。
  - Project 内の `backend/src/main/java/jp/co/oda32/config/` 配下を全 Grep してもカスタム `ClientHttpRequestFactory` の Bean 定義は無し (`Glob backend/src/main/java/jp/co/oda32/config/*.java` 結果に http 設定なし)。
  - 設計レビュー Major-4 で「timeout 未設定」は記載されているが、リトライと混在して扱われていたためコード固有の DoS 性 (= MF API hang で `MfOauthService.getValidAccessToken` 経由のすべての ledger / reconcile / health check 操作が永久ブロック) を再強調する。
- **影響**:
  - MF 側 LB が SYN-ACK を返さないケースや slow loris で、Tomcat default の 200 worker thread が全てブロック → ヘルスチェック含むすべての `/api/**` リクエストが 503 化。
  - `RestClient` は `Mono` ではなく **同期 blocking** なので InterruptedException で抜けることもできず、process kill しか復旧手段なし。
- **修正案**:
  ```java
  @Configuration
  class MfHttpClientConfig {
      @Bean(name = "mfRestClient")
      RestClient mfRestClient() {
          var factory = new SimpleClientHttpRequestFactory();
          factory.setConnectTimeout(Duration.ofSeconds(5));
          factory.setReadTimeout(Duration.ofSeconds(30));
          return RestClient.builder().requestFactory(factory).build();
      }
  }
  ```
  かつ `MfApiClient` で `@Qualifier("mfRestClient") RestClient client` を field injection。共有 `RestClient.Builder` から builder を都度 `build()` する現行実装も、毎回 factory 構築のコストはあるが thread-safe ではあるので timeout 設定さえあれば問題はない。
- **追加注意**: `BCartHttpClientConfig` (`backend/src/main/java/jp/co/oda32/batch/bcart/config/BCartHttpClientConfig.java`) は B-CART 用に既存しているため、それを参考に MF 用も追加するのが既存パターンと整合。

---

### C-impl-2: `MfTokenResponse` の `toString()` が `access_token` / `refresh_token` を漏洩

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenResponse.java:8`
  ```java
  public record MfTokenResponse(
          @JsonProperty("access_token") String accessToken,
          @JsonProperty("refresh_token") String refreshToken,
          ...
  ) {}
  ```
- **問題**:
  - Java `record` は `toString()` を自動生成し全フィールドを含む (`MfTokenResponse[accessToken=eyJhbGc..., refreshToken=...]`)。
  - 今回コード上 `log.warn("...{}", token)` 等の暴露経路は無いが、**例外スタックトレース** (Spring AOP の方が `arg` を toString して error log に出すケースあり) や `@RestController` の global exception handler で argument が dump されるケース、デバッグ時の `System.out.println(res)` で偶発的に流出する。
  - 設計レビュー Critical-2 (body 全体ログ) と独立した「DTO 自身が漏洩源」になる構造的問題。
- **影響**:
  - 一度漏洩した access_token は最大 1 時間有効。refresh_token は 30 日相当 (MF 仕様)。攻撃者に渡れば MF 仕訳閲覧。
- **修正案**:
  - record で `toString` を override:
    ```java
    public record MfTokenResponse(...) {
        @Override public String toString() {
            return "MfTokenResponse[tokenType=" + tokenType
                + ", expiresIn=" + expiresIn
                + ", scope=" + scope
                + ", accessToken=***, refreshToken=***]";
        }
    }
    ```
  - 同様の対処を `MfOauthService.TokenSnapshot` (`MfOauthService.java:255`) にも適用。`plainSecret`, `accessToken`, `plainRefresh` がフィールドにあり同じリスク。
  - グローバルに `@JsonIgnoreProperties` ではなく `Object#toString` を完全に潰すこと。

---

## Major

### M-impl-1: `MfOauthService.handleCallback` の `cryptoUtil.decrypt(client_secret)` が DB tx 内で実行される

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:121`
  ```java
  @Transactional
  public void handleCallback(String code, String state, Integer userNo) {
      Optional<String> verifierOpt = stateStore.verifyAndConsume(state, userNo);
      ...
      String plainSecret = cryptoUtil.decrypt(client.getClientSecretEnc());
      MfTokenResponse token = mfApiClient.exchangeCodeForToken(client, plainSecret, code, verifierOpt.get());
      persistToken(client, token, userNo);
  }
  ```
- **問題**:
  - `getValidAccessToken()` (line 141) では「HTTP 呼び出しは tx 外、永続化は REQUIRES_NEW」というパターン (= B-W6) を採用しているが、`handleCallback` だけが **HTTP 呼び出し (= MF token endpoint への 5〜数十秒) を `@Transactional` 内で行う**。
  - MF token endpoint レイテンシ + slow MF response 中、DB connection が pool から占有されたまま。同時に複数 admin が認可フローを試行すると HikariCP のコネクションプールを枯渇させる。
- **影響**:
  - 高頻度操作ではないので即障害ではないが、設計上の一貫性を欠く。`getValidAccessToken` で工夫した B-W6 ガードが callback だけ抜けている。
- **修正案**:
  - `handleCallback` も同じパターンに分割:
    1. read-only tx で client + verifier 読み取り (state は使い捨てなのでこの時点で消費)
    2. tx 外で `exchangeCodeForToken`
    3. REQUIRES_NEW で `persistToken`
  - もしくはコメントで「callback は admin が手動で 1 回しか呼ばないため tx 内 HTTP 許容」と明示する (運用前提 + テスト確認)。

### M-impl-2: `MfOauthStateStore.verifyAndConsume` が delete を「条件チェック前」に実行 (race + 攻撃時 enumeration)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthStateStore.java:74-86`
  ```java
  TMfOauthState e = opt.get();
  // 使い捨てなので即削除
  stateRepository.deleteById(state);
  if (e.getExpiresAt().toInstant().isBefore(Instant.now())) {
      log.info("MF OAuth state 有効期限切れ");
      return Optional.empty();
  }
  if (e.getUserNo() == null) {
      if (userNo != null) return Optional.empty();
  } else if (!e.getUserNo().equals(userNo)) {
      return Optional.empty();
  }
  ```
- **問題**:
  1. **意図的問題**: 期限切れでも、ユーザ不一致でも、レコードはまず削除される。これは「同じ state を 2 回使えないようにする」ためで設計通り。ただし攻撃者が無効 state をブルートフォースで送ると、たまたま誰かの有効 state が偶然衝突した場合に **正規ユーザーの認可フローを巻き込んで破壊** できる (DoS)。state は 256bit ランダムなので衝突確率は実用上ゼロだが、設計コメントが無い。
  2. **race**: `findById → deleteById` が別 SQL なので、極稀に同 state を 2 リクエスト同時処理した場合に両方が「取り出せた」と判定し callback が 2 回走る (片方は token 上書きされる)。トークン上書きは uniq partial index で active 1 件保証なのでデータ破損はないが、verifier の 2 重消費自体は仕様違反 (RFC 7636 §4.6)。
  3. PostgreSQL の `DELETE ... RETURNING *` を使えば 1 SQL で atomic に取り出せる。
- **影響**: 軽微 (実害はほぼ無いが、ベストプラクティス的には削除と検証を atomic にすべき)。
- **修正案**:
  - JPQL で `@Modifying @Query("DELETE FROM TMfOauthState s WHERE s.state=:s") Optional<TMfOauthState>` は JPA 単体だと returning 不可なので、native `DELETE FROM t_mf_oauth_state WHERE state = ? RETURNING user_no, code_verifier, expires_at` で 1 SQL atomic 化。

### M-impl-3: callback 後の `m_mf_oauth_client.modifyDateTime` が更新されず監査が欠落

- **場所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java:77`
  ```java
  @PreUpdate
  void preUpdate() {
      modifyDateTime = Timestamp.from(java.time.Instant.now());
  }
  ```
  および `MfOauthService.persistToken` (line 213-230) → `tokenRepository.save(token)` のみで `clientRepository.save(client)` は呼ばない。
- **問題**: token は新規 insert で `t_mf_oauth_token.add_date_time` に履歴が残るが、`m_mf_oauth_client` 自体は更新されない。「最終認可成功時刻」を保持するカラム (例: `last_authorized_at`) が無いため、運用 admin が画面で「いつ認可した token か」を厳密に追えるのは `t_mf_oauth_token` の最新 `add_date_time` のみ。
- **影響**:
  - `MfTokenStatus.lastRefreshedAt` (`MfOauthService.java:199-201`) は `t.modifyDateTime ?? t.addDateTime` で代替しているが、refresh と新規認可を区別不能。
- **修正案**:
  - `MMfOauthClient` に `last_authorized_at` カラム追加 + `handleCallback` の最後で client.lastAuthorizedAt = now() で save。
  - もしくは `t_mf_oauth_token` に `obtain_kind` enum (`AUTHORIZATION_CODE` / `REFRESH_TOKEN`) を追加。

### M-impl-4: `MfApiClient.postToken` のレスポンス検証が緩く `NullPointerException` リスク

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:244-247`
  ```java
  if (res == null || res.accessToken() == null || res.refreshToken() == null) {
      throw new IllegalStateException("MF token エンドポイントから不正なレスポンス");
  }
  ```
- **問題**:
  - `expiresIn` が null の場合 (MF が省略した場合) `MfOauthService.persistToken` (line 216) で `res.expiresIn() != null ? res.expiresIn() : 3600L` と fallback しているので OK。
  - しかし `tokenType` が null の場合は `MfTokenResponse` 自身でなく `persistToken` (line 222) で `res.tokenType() != null ? res.tokenType() : "Bearer"` と fallback するので OK。
  - 一方 `scope` は MF が省略した場合 `null` のまま `t_mf_oauth_token.scope` に保存 (column nullable=true なので OK)、後続で `MfTokenStatus.scope` に流す時も null OK。
  - **ただし** `refresh_token` が省略 (= MF が rotate しない sliding mode) の場合、`postToken` が `IllegalStateException` を投げて再認可必要扱いになる。MF 仕様上 refresh_token rotation を ON/OFF 切替できる場合、sliding mode の admin は永久に認可できない。
- **影響**: MF 側設定次第では機能不全。
- **修正案**:
  - refresh_token が null の場合は **既存の DB 上 refresh_token を維持** する (= rotate なし)、access_token のみ更新するパスを追加。`MfOauthService.persistToken` に「refresh が null なら旧値を再利用」を実装。

### M-impl-5: `MfJournalCacheService` が shop 単位の cache を **全プロセス共通の `synchronized` メソッド**で守る

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalCacheService.java:56`
  ```java
  public synchronized CachedResult getOrFetch(Integer shopNo, ...) {
  ```
- **問題**:
  - 設計レビュー Major-5 で「synchronized 単一 lock で他 shop も待ち」と既出だが、**コード固有の細部**として `getStats()` (line 91) は **non-synchronized** で `cachesByShop.entrySet()` を回す。`MonthlyCache.fetchedAtByMonth` は ConcurrentHashMap なので iteration は安全だが、`getOrFetch` 中の `storeFetched` (line 137-141) で
    ```java
    cache.journalsByMonth.put(month, byMonth.getOrDefault(month, List.of()));
    cache.fetchedMonths.add(month);
    cache.fetchedAtByMonth.put(month, now);
    ```
    の 3 行は連続 mutation だが**個別の atomic** で、`getStats` 中に途中状態 (months だけ更新、fetchedAt 未更新) を読む可能性がある。実害は「最古/最新時刻が一瞬ずれる」程度なので軽微だが、テスト時の flake 原因になりうる。
- **影響**: 軽微 (UI 表示のみ、永続化や判断ロジックには使われない)。
- **修正案**:
  - `getStats()` も同じ `synchronized` か、各 ShopStats 構築を `cache` 単位で synchronized ブロックに入れる。
  - または `MonthlyCache` を不変な snapshot record に置き換え、更新時は CAS で全体差し替えするパターン (Caffeine の AsyncCache に置換すれば全部解決)。

### M-impl-6: `MfHealthCheckService` が `mfOauthService.getStatus()` を呼び **かつ** `journalCache.getStats()` を呼ぶが両者の整合性チェックなし

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java:55-67`
- **問題**:
  - `getStatus()` で「OAuth 接続中」を返しても、journalCache が空なら過去 fetch がなく、本当に MF と疎通できているかは不明。逆に cache が古いまま OAuth が disconnect された場合、画面では「OAuth: 未接続 / cache: 12 月分保持」と一見矛盾した状態が表示される (実際にはキャッシュが残っているだけ)。
  - 設計レビュー Major-7 (anomaly 0 固定) と並ぶ「health 画面の信頼性」問題。`judgeHealth` (`frontend/types/mf-health.ts:60`) は OAuth disconnect を red 判定するが、cache 古さは無視するため、MF 側が修正された journal は検知できない。
- **影響**: 中 (UX の信頼性に直結)。
- **修正案**:
  - `MfHealthCheckService.check()` の最後で軽量 ping (`/api/v3/offices` 等で 200 を確認) を追加し、`MfOauthStatus` に `apiReachable` フィールドを追加。失敗時は yellow 判定。
  - cache の `oldestFetchedAt` が 24h 以上前なら frontend で yellow 判定に組み込む。

### M-impl-7: `MfIntegrationController.callback` が `@PreAuthorize("hasRole('ADMIN')")` クラスレベルで保護されているが、callback URL 自体は MF からブラウザリダイレクト経由

- **場所**: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:45,113`
- **問題**:
  - クラスレベル `@PreAuthorize("hasRole('ADMIN')")` は **API 呼び出し時** の認可。ブラウザの redirect から `/finance/mf-integration/callback` は frontend ページであり、そこから JS で `POST /api/v1/finance/mf-integration/oauth/callback` を呼ぶ際に admin の JWT が Authorization header に乗る。
  - frontend の `mf-integration-callback.tsx:39-40` が `api.post('/finance/mf-integration/oauth/callback', { code, state })` を呼ぶ際、`api-client.ts:18` で `localStorage.getItem('token')` を Authorization に乗せる。
  - **問題点**: callback URL が MF 経由なので、攻撃者が **被害者管理者を別の認可リクエストにリダイレクト** させて被害者の admin token で他社の MF アカウントを bind することが可能 (= state 検証で防げているはず)。
  - state 検証 (`MfOauthStateStore.verifyAndConsume`) で「state 発行ユーザーと callback 時 userNo 一致」を見ているため、攻撃者の admin の state を被害者 admin が踏むケースは弾けるが、その逆 (被害者 state を攻撃者が踏む) も `userNo` 比較で弾く。OK。
  - ただし「state 発行ユーザーが既にログアウト → 別 admin がログイン → 同じブラウザで callback」の場合、設計レビュー Major-3 で指摘の通り 403 が返るが、**メッセージが汎用すぎて運用混乱**。
- **影響**: 軽微 + UX 不良。
- **修正案**:
  - 設計レビュー Major-3 と連動して、403 レスポンスに「state は別 admin で発行されています。発行した admin で callback を完了してください」と明示。

### M-impl-8: フロント `mf-integration-callback.tsx` の `useEffect` が StrictMode で 2 回実行 → state 二重消費

- **場所**: `frontend/components/pages/finance/mf-integration-callback.tsx:22-63`
  ```ts
  useEffect(() => {
      const code = sp.get('code')
      const state = sp.get('state')
      ...
      api.post(...).then(...)
  }, [sp])
  ```
- **問題**:
  - React 19 + Next.js 16 dev mode の `<StrictMode>` (`app/layout.tsx` で有効化されている前提) では `useEffect` が 2 回実行される。1 回目で `verifyAndConsume` が state を消費 (DELETE) し、2 回目で `findById` が空になり 403。dev 環境での DX を著しく損なう。
  - 設計レビュー Minor-8 で「依存配列 sp のみ」と指摘済みだが、**StrictMode 二重実行**自体への対策が無い。
- **影響**: dev 環境での認可フロー検証が常に 403 になり、開発者に「実装バグでは?」という誤解を生む。
- **修正案**:
  ```tsx
  const calledRef = useRef(false)
  useEffect(() => {
      if (calledRef.current) return
      calledRef.current = true
      ...
  }, [])
  ```
  もしくは React 19 の `Action` を使い、useEffect 自体を排除する。

---

## Minor

### m-impl-1: `MfApiClient.urlEncode` が `null` を空文字に置換 (silent failure)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:265-267`
  ```java
  private static String urlEncode(String s) {
      return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
  }
  ```
- **問題**: 必須パラメータ (state, code_challenge, redirect_uri 等) が null だと空文字で encode され、MF 側で 400 エラーになる。原因特定が難しい。
- **修正案**: `Objects.requireNonNull(s, "url-encoded 引数が null")` で fail-fast、または `Optional` を返すバージョンを別メソッド化。

### m-impl-2: `MfApiClient.buildAuthorizeUrl` が String concatenation で組み立て、URL injection の余地

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:40-53`
  ```java
  StringBuilder sb = new StringBuilder(client.getAuthorizeUrl());
  sb.append(client.getAuthorizeUrl().contains("?") ? "&" : "?");
  ```
- **問題**:
  - `client.authorizeUrl` 自体に `?fragment` や `#fragment` が含まれている場合の挙動が未テスト。
  - 設計レビュー Major-6 で admin 編集可能と指摘済 → ALLOW list 化されれば軽微化。
- **修正案**: `UriComponentsBuilder.fromHttpUrl(authorizeUrl).queryParam(...).build().toUriString()` を使用。MF 側はクエリ順で動作仕様化されていないので OK。

### m-impl-3: `TMfOauthToken` に refresh_token 発行日時カラムが無い

- **場所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java:43`
- **問題**: `expires_at` は access_token の有効期限のみ。refresh_token の発行日時は `add_date_time` から推測するしかなく、設計レビュー Minor-2 (`reAuthRequired` が常に false) と連動して機能不全。
- **修正案**: `refresh_token_issued_at TIMESTAMP NOT NULL` カラム追加 + `getStatus()` で `Instant.now() > issuedAt + 29日` なら true。

### m-impl-4: `MfAccountSyncService.apply` の DELETE が物理削除 (audit log 残らない)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfAccountSyncService.java:62-64`
  ```java
  if (!plan.toDelete.isEmpty()) {
      mfAccountMasterRepository.deleteAllInBatch(plan.toDelete);
  }
  ```
- **問題**:
  - `mf_account_master` を物理削除すると、後で「あの sub_account_name は何だった?」を遡及できない。MF 側が誤って削除して再同期した場合の復旧が手動対応に。
  - frontend `MfAccountSyncTab.tsx:101` で「物理削除」と注意喚起しているが、admin が誤実行すると取り返せない。
- **修正案**:
  - `mf_account_master` に `del_flg` を追加して論理削除に変更 (B-CART 配下と整合)、もしくは削除前に `mf_account_master_archive` テーブルにコピー。

### m-impl-5: `MfApiClient.getRaw` の戻り型が `JsonNode` で型安全性ゼロ (debug only)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:89-108`
- **問題**: dev 限定 endpoint (`MfIntegrationController.debug*`) でしか使われていない。本番では `isDevProfile()` ガードがあるため OK だが、Service 層に debug-only メソッドが混在しているのは責務分離違反。
- **修正案**: `MfApiClient` から `getRaw` を削除し、`MfIntegrationController.debugTrialBalanceRaw` 等を新設の `MfDebugApiClient` に分離。設計レビュー Minor-5 (Controller debug 分離) と連動。

### m-impl-6: `MfJournalReconcileService.reconcile` が単一 page (per_page=10000) で取得し pagination 未対応

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalReconcileService.java:56,96`
  ```java
  private static final int PER_PAGE = 10000;
  ...
  List<MfJournal> mfJournals = mfApiClient.listJournals(client, accessToken, date, date, 1, PER_PAGE).items();
  ```
- **問題**:
  - 1 日分なので通常 10000 件以下で済むが、月末締め日に大量取込が走ると超える可能性あり。`MfJournalFetcher` のような pagination loop が無いため、page 2 以降が落ちて静かにデータ抜け。
  - MF 側 `per_page` の上限仕様が記載されていないが、設計書 §5 で「max 1000」想定なら 10000 指定は仕様違反でレスポンス 400 の可能性。
- **修正案**:
  - `MfJournalFetcher.fetchJournalsForPeriod` を再利用 (期間 1 日でも問題なし、cache も自動で乗る)。

### m-impl-7: `MfOauthService.upsertClient` が Lombok `@Data` の MMfOauthClient に対し setter で mutate (immutability 原則違反)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:84-95`
  ```java
  existing.setClientId(requested.getClientId());
  if (plainClientSecret != null && !plainClientSecret.isBlank()) {
      existing.setClientSecretEnc(cryptoUtil.encrypt(plainClientSecret));
  }
  ...
  ```
- **問題**:
  - CLAUDE.md 「Immutability: always create new objects, never mutate」原則違反。
  - JPA Entity は managed state なので setter mutation で動作はするが、`requested` と `existing` の両 instance を持って手動 copy する現在の書き方はバグ温床 (今後フィールド追加時に copy 漏れ)。
- **修正案**: `existing.toBuilder().clientId(...).clientSecretEnc(...).modifyDateTime(...).build()` で immutable copy → save。Lombok `@Builder(toBuilder = true)` を `MMfOauthClient` に追加。

### m-impl-8: フロント `MF_DEFAULT_CONFIG.scope` がハードコードされた space-separated 文字列

- **場所**: `frontend/types/mf-integration.ts:196`
  ```ts
  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/offices.read mfc/accounting/taxes.read mfc/accounting/report.read',
  ```
- **問題**: scope を追加・削除する際に文字列編集ミスでスペース重複や typo が起きやすい。typo すると MF 側で「無効 scope」エラーになる。
- **修正案**: `const SCOPES = ['mfc/accounting/journal.read', ...] as const; export const MF_DEFAULT_SCOPE = SCOPES.join(' ')` で配列管理 + Set で重複検知。

### m-impl-9: フロント `mf-integration.tsx` の `useEffect` (line 54-67) が `clientQuery.data` 全体を依存に

- **場所**: `frontend/components/pages/finance/mf-integration.tsx:67`
  ```tsx
  }, [clientQuery.data])
  ```
- **問題**:
  - TanStack Query は同じデータでも new object reference を返すことがあり、`clientQuery.data` 参照変化で effect が無限走り (form リセット → query refetch ループ) する可能性。
  - 現状 `setForm((prev) => ...)` で merge しているため値が変わらないと React は re-render しないはずだが、refetchInterval があると毎分 effect が走って `setForm` を呼ぶ → 何も変わらないが React 19 の StrictMode で警告が出る場合あり。
- **修正案**: `[clientQuery.data?.id, clientQuery.data?.clientId]` 等、key field のみ依存配列に。

### m-impl-10: フロント `mf-health.tsx:144` の `unverifiedCount` が summary 由来 + anomaly 由来の **2 重カウント**

- **場所**: `frontend/components/pages/finance/mf-health.tsx:131-144`
  ```tsx
  <Row label="未検証">{data.summary.unverifiedCount}</Row>
  ...
  <Row label="未検証 (当月)">{data.anomalies.unverifiedCount}</Row>
  ```
- **問題**:
  - 同じ「未検証件数」が summary タブと anomaly タブの両方に表示される。`MfHealthCheckService.buildAnomalyStats` (`MfHealthCheckService.java:94-96`) で `summary.unverifiedCount` と同じ計算をしている。設計レビュー Major-7 で 0 固定の他 3 種別と並列に「未検証」のみ実装されている形だが、**重複表示**で UX が冗長。
- **修正案**: anomaly 側の「未検証 (当月)」は summary と同値なので、anomaly カードからは削除し summary 側でハイライト表示に統一。

### m-impl-11: `MfIntegrationController` が `MfBalanceReconcileService` 以外で `MfScopeInsufficientException` を 403 にマップしていない

- **場所**: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:204-211, 240-248, 252-262`
- **問題**:
  - `/reconcile` (`reconcile()` line 198) と `/account-sync/preview` `/apply` のハンドラは `MfReAuthRequiredException` (401) と `IllegalStateException` (422) しか catch しない。
  - 内部で `MfApiClient.listJournals` (line 137) や `listAccounts` は 403 → `MfScopeInsufficientException` を投げるが、Controller がこれをキャッチしないため Spring の global exception handler (おそらく 500) に流れる。
- **影響**: scope 不足時のユーザー誘導が `/balance-reconcile` だけ綺麗で、他は 500 エラー画面。
- **修正案**: `@ExceptionHandler(MfScopeInsufficientException.class)` を Controller に追加して全エンドポイントで 403 + `requiredScope` を返却。

---

## OAuth / トークン管理 セキュリティチェックリスト (コード固有)

設計レビューと重複しない項目に絞った再評価。

| 項目 | 結果 | コメント |
|---|---|---|
| HTTP timeout 設定 | NG (C-impl-1) | RestClient.Builder のデフォルト = 無限 |
| HTTP retry 全 API 適用 | NG (Major-4 既出) | journals のみ |
| token DTO の toString マスキング | NG (C-impl-2) | record auto toString が漏洩源 |
| TokenSnapshot record の toString マスキング | NG (C-impl-2) | 同上、`MfOauthService.java:255` |
| state DELETE と verify の atomic 化 | NG (M-impl-2) | findById → deleteById 別 SQL |
| handleCallback の HTTP tx 外実行 | NG (M-impl-1) | `getValidAccessToken` パターンと不整合 |
| refresh_token 発行日時保持 | NG (m-impl-3) | re-auth 警告ロジックに必要 |
| client 側 refresh_token rotation 不要対応 | NG (M-impl-4) | sliding mode で永久に再認可になるバグ |
| state 同時 callback race | 軽微 NG (M-impl-2) | uniq partial index で永続データは保護されるが、verifier が 2 重消費される |
| audit (last_authorized_at) | NG (M-impl-3) | refresh と新規認可を区別不能 |
| account_sync 物理削除 | 軽微 NG (m-impl-4) | audit 残らない |
| react StrictMode callback 二重実行 | NG (M-impl-8) | dev のみだが体験不良 |
| journals pagination 一貫適用 | NG (m-impl-6) | reconcile で page 2 以降未取得 |

---

## 設計レビューと重複指摘の再確認 (コード上の状態)

| 設計レビュー指摘 | 該当コード | コード上の対応状況 |
|---|---|---|
| Critical-1 (salt fallback) | `application-dev.yml:21` | 未対応 (運用ポリシーで据え置き) |
| Critical-2 (token error body log) | `MfApiClient.java:251` 等 | 未対応、本レビュー C-impl-2 と独立した別経路あり |
| Critical-3 (window.open noreferrer) | `mf-integration.tsx:103` | 未対応、本レビュー M-impl-8 と関連 (StrictMode 別問題) |
| Major-1 (refresh_token rotation revoke) | `MfOauthService.java:206-211` | 未対応 |
| Major-2 (state TTL `<` 比較) | `MfOauthStateStore.java:75` | 未対応、本レビュー M-impl-2 で atomic 化提案 |
| Major-3 (callback userNo) | `MfOauthStateStore.java:80-84` | 未対応、本レビュー M-impl-7 で UX 改善提案 |
| Major-4 (retry 全 API 適用) | `MfApiClient.java:115-143` 等 | 未対応、本レビュー C-impl-1 で timeout もセットで指摘 |
| Major-5 (cache TTL/OOM) | `MfJournalCacheService.java:21-43` | 未対応、本レビュー M-impl-5 で `getStats` 整合性追記 |
| Major-6 (admin 編集可 URL) | `MfOauthService.upsertClient` line 79 | 未対応 |
| Major-7 (anomaly 0 固定) | `MfHealthCheckService.java:103-105` | 未対応、本レビュー m-impl-10 で表示重複追記 |

---

## 対応表 (本レビュー指摘のみ)

| ID | 判定 | 推奨対応 |
|---|---|---|
| C-impl-1 | Must Fix | `MfHttpClientConfig` Bean 追加、`SimpleClientHttpRequestFactory` で connect 5s / read 30s |
| C-impl-2 | Must Fix | `MfTokenResponse` / `TokenSnapshot` の `toString()` を override |
| M-impl-1 | Should Fix | `handleCallback` を read-only tx → tx 外 HTTP → REQUIRES_NEW persist に分割 |
| M-impl-2 | Should Fix | `DELETE ... RETURNING` で state を atomic 取り出し |
| M-impl-3 | Should Fix | `m_mf_oauth_client.last_authorized_at` カラム追加 |
| M-impl-4 | Should Fix | refresh_token null fallback (旧値維持) を `persistToken` に追加 |
| M-impl-5 | Could Fix | `getStats()` を `synchronized` 化、または Caffeine 置換 |
| M-impl-6 | Should Fix | health check に MF 軽量 ping 追加、cache age 警告 |
| M-impl-7 | Could Fix | 403 エラーメッセージ詳細化 |
| M-impl-8 | Must Fix (DX) | callback `useEffect` を `useRef` で once-only 実行 |
| m-impl-1 | Could Fix | urlEncode null fail-fast |
| m-impl-2 | Could Fix | `UriComponentsBuilder` 移行 |
| m-impl-3 | Should Fix | refresh_token_issued_at カラム追加 |
| m-impl-4 | Could Fix | `mf_account_master` 論理削除化 |
| m-impl-5 | Could Fix | debug client 分離 |
| m-impl-6 | Should Fix | `MfJournalFetcher` 再利用 |
| m-impl-7 | Could Fix | `@Builder(toBuilder=true)` で immutable copy |
| m-impl-8 | Could Fix | scope 配列管理 |
| m-impl-9 | Could Fix | useEffect 依存配列を id のみに |
| m-impl-10 | Could Fix | anomaly タブから unverified 削除 |
| m-impl-11 | Should Fix | `MfScopeInsufficientException` を全エンドポイントで 403 |

**Approval status**: Needs Revision → C-impl-1, C-impl-2, M-impl-8, m-impl-6 を最優先で対応すれば商用導入可能水準に到達。

---

## 総評

設計レビューで OAuth 基盤としての堅牢性が確認されており、コード実装も大筋で設計通り (PKCE / state DB 永続化 / 暗号化 / advisory lock 等)。本レビューでは **設計書では見えにくい実装層のリスク** を中心に指摘した:

- **Critical 2 件はいずれも「漏洩経路の追加」**: ログ経由 (設計レビュー C-2) と DTO toString 経由 (本 C-impl-2) は別物で両対応必要。RestClient timeout 未設定 (C-impl-1) は OAuth と無関係に DoS リスク。
- **Major 8 件のうち M-impl-1/2/3/4 は MF 側挙動依存の堅牢性問題** (sliding mode、state race、audit、HTTP tx 占有)。運用開始前に対応推奨。
- **M-impl-8 は dev 体験を著しく損なう** ため StrictMode 対応必須。
- 設計書 (`design-mf-integration-status.md`) の更新も必要 (m_mf_account_mapping 廃止、cache 追加、ヘルスチェック画面、scope 拡張、Phase B/B''/B''' 対応) — 設計レビュー §「設計書 vs 実装の乖離」参照。

レビュアー: Opus サブエージェント
最終更新: 2026-05-04
