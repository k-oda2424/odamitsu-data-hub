package jp.co.oda32.domain.service.finance.mf;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.TMfOauthToken;
import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
import jp.co.oda32.util.OauthCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * MF OAuth2 クライアント設定・トークンライフサイクル管理。
 * <p>
 * {@link MfApiClient} を使って MF と通信し、暗号化済みトークンを DB に永続化する。
 * Controller はこの Service を介して「接続状態」「認可 URL」「callback 処理」「失効」
 * を操作する。
 *
 * @since 2026/04/20
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfOauthService {

    /** access_token 有効期限の何秒前から自動 refresh するか。 */
    private static final long REFRESH_MARGIN_SECONDS = 300L;

    /**
     * redirect_uri ホワイトリスト (B-4)。
     * 登録時に完全一致でチェックし、任意 URL への誘導を防ぐ。運用上追加が必要なら環境変数化も検討。
     */
    private static final Set<String> ALLOWED_REDIRECT_URIS = Set.of(
            "http://localhost:3000/finance/mf-integration/callback",
            "https://odamitsu-data-hub.local/finance/mf-integration/callback"
    );

    /** advisory lock 用キー (refresh_token を同時 refresh しないための直列化)。 */
    private static final long REFRESH_LOCK_KEY = 0x4D46_5245_4641_4331L; // "MFREFAC1"

    private final MMfOauthClientRepository clientRepository;
    private final TMfOauthTokenRepository tokenRepository;
    private final MfApiClient mfApiClient;
    private final OauthCryptoUtil cryptoUtil;
    private final MfOauthStateStore stateStore;
    /** G1-M5: MF OAuth エンドポイント URL のホスト検証 (credential exfiltration 対策)。 */
    private final MfOauthHostAllowlist hostAllowlist;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 自己注入。{@code @Transactional(REQUIRES_NEW)} が同一クラス内呼び出しでも
     * Spring AOP proxy 経由になるように。{@code @Lazy} は循環依存回避。
     */
    @Autowired
    @Lazy
    private MfOauthService self;

    /** 登録済みの OAuth クライアントを返す。未設定なら空。 */
    public Optional<MMfOauthClient> findActiveClient() {
        return clientRepository.findFirstByDelFlgOrderByIdDesc("0");
    }

    /** 画面から Client ID / Client Secret 等を登録/更新する（洗い替え）。 */
    @Transactional
    public MMfOauthClient upsertClient(MMfOauthClient requested, String plainClientSecret, Integer userNo) {
        // redirect_uri はホワイトリスト完全一致 (B-4)。ADMIN でも任意 URL 設定は不可。
        validateRedirectUri(requested.getRedirectUri());
        // G1-M5: OAuth endpoint host allowlist 検証。
        // 攻撃者制御 URL に client_secret を Basic auth で流す経路を遮断する。
        hostAllowlist.validate(requested.getAuthorizeUrl(), "authorizeUrl");
        hostAllowlist.validate(requested.getTokenUrl(), "tokenUrl");
        hostAllowlist.validate(requested.getApiBaseUrl(), "apiBaseUrl");
        Timestamp now = Timestamp.from(Instant.now());
        MMfOauthClient existing = clientRepository.findFirstByDelFlgOrderByIdDesc("0").orElse(null);
        if (existing != null) {
            existing.setClientId(requested.getClientId());
            if (plainClientSecret != null && !plainClientSecret.isBlank()) {
                existing.setClientSecretEnc(cryptoUtil.encrypt(plainClientSecret));
            }
            existing.setRedirectUri(requested.getRedirectUri());
            existing.setScope(requested.getScope());
            existing.setAuthorizeUrl(requested.getAuthorizeUrl());
            existing.setTokenUrl(requested.getTokenUrl());
            existing.setApiBaseUrl(requested.getApiBaseUrl());
            existing.setModifyDateTime(now);
            existing.setModifyUserNo(userNo);
            return clientRepository.save(existing);
        }
        if (plainClientSecret == null || plainClientSecret.isBlank()) {
            throw new IllegalArgumentException("新規登録時は client_secret が必要です");
        }
        requested.setClientSecretEnc(cryptoUtil.encrypt(plainClientSecret));
        requested.setDelFlg("0");
        requested.setAddDateTime(now);
        requested.setAddUserNo(userNo);
        return clientRepository.save(requested);
    }

    /**
     * 認可 URL (MF ログインページ) を組み立てて返す。
     * 内部で state + PKCE code_verifier を発行し DB 永続化する。
     */
    public AuthorizeUrl buildAuthorizeUrl(Integer userNo) {
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        MfOauthStateStore.Issued issued = stateStore.issue(userNo);
        String url = mfApiClient.buildAuthorizeUrl(client, issued.state(), issued.codeChallenge());
        return new AuthorizeUrl(url, issued.state());
    }

    /**
     * MF からの callback を処理し、トークンを DB 保存して接続状態にする。state 検証 + PKCE 込み。
     *
     * <p>SF-20: HTTP 呼び出しを tx 外に分離する 3 段構成。
     * <ol>
     *   <li>read-only tx: state 消費 + client 取得 ({@link #consumeStateAndLoadClient})</li>
     *   <li>tx 外: MF token endpoint への HTTP (= 数秒〜数十秒) を呼ぶ</li>
     *   <li>REQUIRES_NEW tx: token 永続化 ({@link #persistTokenInNewTx})</li>
     * </ol>
     * これで MF API のレイテンシ中に DB connection を占有しない (B-W6 パターンに統一)。
     */
    @AuditLog(table = "t_mf_oauth_token", operation = "auth_callback",
            pkExpression = "{'userNo': #a2}",
            captureArgsAsAfter = true)
    public void handleCallback(String code, String state, Integer userNo) {
        // 1) state 消費 + client 読み取り (短い tx)
        CallbackContext ctx = self.consumeStateAndLoadClient(state, userNo);
        // 2) tx 外で HTTP (token 交換)
        MfTokenResponse token = mfApiClient.exchangeCodeForToken(
                ctx.client(), ctx.plainSecret(), code, ctx.codeVerifier());
        // 3) tx 外で HTTP (P1-01: tenant binding 検証)
        //    別会社 MF を誤認可した場合に、ここで MfTenantMismatchException を throw して停止する。
        //    既存 client.mfTenantId が NULL (旧データ互換) なら初回 binding として確定する。
        MfTenantResponse tenant = mfApiClient.getTenant(ctx.client(), token.accessToken());
        // 4) 永続化のみ別 tx (token + tenant binding)
        self.persistTokenAndTenantInNewTx(ctx.client().getId(), token, tenant, userNo);
    }

    /** SF-20: state 消費 + client 取得を 1 つの短い tx で行う。 */
    @Transactional
    public CallbackContext consumeStateAndLoadClient(String state, Integer userNo) {
        Optional<String> verifierOpt = stateStore.verifyAndConsume(state, userNo);
        if (verifierOpt.isEmpty()) {
            throw new IllegalArgumentException("MF OAuth state 検証失敗 (期限切れ or 改ざん)");
        }
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String plainSecret = cryptoUtil.decrypt(client.getClientSecretEnc());
        return new CallbackContext(client, plainSecret, verifierOpt.get());
    }

    /** SF-20: token 永続化のみ REQUIRES_NEW で実行 (HTTP の長い tx を避ける)。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistTokenInNewTx(Integer clientId, MfTokenResponse token, Integer userNo) {
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        persistToken(client, token, userNo);
    }

    /**
     * P1-01: token 永続化 + tenant binding を 1 つの tx でまとめて実行。
     * <p>
     * 既存 client に {@code mf_tenant_id} が保存されている場合は一致確認し、
     * 不一致なら {@link MfTenantMismatchException} を throw して tx を rollback する
     * (= token も保存しない)。これにより別会社 MF への誤認可で業務データを汚染しない。
     * <p>
     * 一致 or 初回認可 (mf_tenant_id IS NULL) は client の tenant 列を更新して save する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistTokenAndTenantInNewTx(
            Integer clientId, MfTokenResponse token, MfTenantResponse tenant, Integer userNo) {
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        bindOrVerifyTenant(client, tenant, userNo);
        persistToken(client, token, userNo);
    }

    /** {@link #consumeStateAndLoadClient} 戻り値。 */
    public record CallbackContext(MMfOauthClient client, String plainSecret, String codeVerifier) {
        @Override
        public String toString() {
            // SF-02 同様: plainSecret/codeVerifier はマスキング
            return "CallbackContext[clientId=" + (client != null ? client.getId() : null)
                    + ", plainSecret=***, codeVerifier=***]";
        }
    }

    /**
     * 有効な access_token を返す（期限切れなら refresh を試みる）。突合 Service が利用。
     * <p>HTTP refresh とDB永続化を同一 tx で行うと、MF API のレイテンシ中に DB コネクション占有する
     * ため (B-W6)、refresh の HTTP は tx 外で実行し、永続化のみ別 tx に切り出す。
     * <p>同時 refresh による token 上書き race を防ぐため、refresh 経路では PostgreSQL の
     * advisory transaction lock で直列化する。
     */
    public String getValidAccessToken() {
        // 1) 既存 token の読み取り (短い read-only tx)
        TokenSnapshot snap = self.loadActiveTokenSnapshot();

        // G1-M3: 未バインド client (P1-01 導入前の旧データ) は fresh パスでも強制 binding。
        // P1-01 は refresh 経路でだけ tenant 検証する設計だったため、access_token が fresh
        // な間は別会社 MF を誤認可していても業務 API が通ってしまう穴があった。
        // ここで /v2/tenant を呼んで mf_tenant_id を確定する。fetch 失敗時は
        // MfTenantBindingFailedException を throw して業務 API 全体を停止する (P1-01 と同じ厳格動作)。
        if (snap.client.getMfTenantId() == null && snap.isFresh(REFRESH_MARGIN_SECONDS)) {
            log.info("MF client.mf_tenant_id IS NULL: 強制 tenant binding を実行 clientId={}",
                    snap.client.getId());
            MfTenantResponse tenant;
            try {
                tenant = mfApiClient.getTenant(snap.client, snap.accessToken);
            } catch (MfTenantBindingFailedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new MfTenantBindingFailedException(
                        "未バインド client の tenant 取得に失敗しました clientId=" + snap.client.getId(), e);
            }
            // tx 内で binding 確定 (token は触らない、既に有効)
            self.bindTenantInNewTx(snap.client.getId(), tenant);
            // bind 完了。access_token 自体は変わっていないので snap の値をそのまま返却。
            return snap.accessToken;
        }

        if (snap.isFresh(REFRESH_MARGIN_SECONDS)) {
            return snap.accessToken;
        }

        // 2) refresh 必要。tx 外で HTTP を呼ぶ。
        log.info("MF access_token を refresh します（expiresAt={}）", snap.expiresAt);
        MfTokenResponse refreshed = mfApiClient.refreshToken(snap.client, snap.plainSecret, snap.plainRefresh);

        // 3) P1-01: refresh 後の tenant 一致検証 (別会社 client_secret に差し替えられた等を検知)。
        //    通常 access_token 利用時は呼ばず、refresh 経路でだけ確認することで負荷を抑える。
        //    旧データ (mf_tenant_id IS NULL) の場合は初回 binding として確定する。
        MfTenantResponse tenant = mfApiClient.getTenant(snap.client, refreshed.accessToken());

        // 4) 永続化のみ REQUIRES_NEW で直列化 (tenant binding も同 tx で確定)
        self.persistRefreshedTokenAndTenant(snap.client.getId(), refreshed, tenant);
        return refreshed.accessToken();
    }

    /**
     * G1-M3: token は変更せず tenant binding のみ確定する (未バインド旧 client の救済)。
     * <p>
     * fresh access_token を保持したまま、{@code /v2/tenant} 観測値を client に書き込む。
     * 既存 binding がある場合 (= 別 thread が先に binding 確定済) は
     * {@link #bindOrVerifyTenant} の一致検証ロジックで観測 tenant と突合し、
     * 不一致なら {@link MfTenantMismatchException} で rollback する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bindTenantInNewTx(Integer clientId, MfTenantResponse tenant) {
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        // userNo は system 由来 (= null) で記録。bindOrVerifyTenant 内で modify_user_no に直接 set するので
        // null 許容。手動 callback (handleCallback / persistTokenAndTenantInNewTx) は userNo を渡すため区別される。
        bindOrVerifyTenant(client, tenant, null);
    }

    /** access_token 取得のための既存 token スナップショット。短い read-only tx で取る。 */
    @Transactional(readOnly = true)
    public TokenSnapshot loadActiveTokenSnapshot() {
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new MfReAuthRequiredException("MF クライアント設定が未登録です"));
        TMfOauthToken token = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.getId(), "0")
                .orElseThrow(() -> new MfReAuthRequiredException("MF 接続がされていません。認可を実施してください。"));
        return new TokenSnapshot(
                client,
                cryptoUtil.decrypt(client.getClientSecretEnc()),
                cryptoUtil.decrypt(token.getAccessTokenEnc()),
                cryptoUtil.decrypt(token.getRefreshTokenEnc()),
                token.getExpiresAt().toInstant());
    }

    /**
     * refresh 済み token を新 tx で永続化。
     * advisory lock で同時 refresh の race を防ぐ。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistRefreshedToken(Integer clientId, MfTokenResponse refreshed) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", REFRESH_LOCK_KEY)
                .getSingleResult();
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        persistToken(client, refreshed, null);
    }

    /**
     * P1-01: refresh 後の token + tenant binding 検証を 1 つの tx でまとめて永続化。
     * tenant 不一致なら {@link MfTenantMismatchException} で rollback (= 古い token のまま残す)。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistRefreshedTokenAndTenant(
            Integer clientId, MfTokenResponse refreshed, MfTenantResponse tenant) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", REFRESH_LOCK_KEY)
                .getSingleResult();
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        bindOrVerifyTenant(client, tenant, null);
        persistToken(client, refreshed, null);
    }

    /** MF refresh_token の公称寿命 (P1-04 案 α: rotation 想定で現 active token から起算)。 */
    private static final long REFRESH_TOKEN_LIFETIME_DAYS = 540L;

    public MfTokenStatus getStatus() {
        Optional<MMfOauthClient> client = findActiveClient();
        if (client.isEmpty()) {
            // T6: 未設定時は scope 解析対象なし (= scopeOk=true で banner 非表示)
            return new MfTokenStatus(false, false, null, null, null, false,
                    null, null, null, null, null,
                    List.of(), List.of(), true, false);
        }
        MMfOauthClient c = client.get();
        Instant boundAt = c.getTenantBoundAt() != null ? c.getTenantBoundAt().toInstant() : null;
        // T6: client.scope (admin が画面で編集できる「次回認可で要求する scope」) を解析。
        // 現 active token の scope (t.getScope()) ではなく client 側を見るのは、
        // admin が必須 scope を削除した時点で (再認可前でも) 構成ドリフトを検知するため。
        MfScopeConstants.ScopeAnalysis scopeAnalysis = MfScopeConstants.analyze(c.getScope());
        boolean scopeOk = scopeAnalysis.missing().isEmpty();
        Optional<TMfOauthToken> token = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(c.getId(), "0");
        if (token.isEmpty()) {
            return new MfTokenStatus(true, false, null, null, null, false,
                    c.getMfTenantId(), c.getMfTenantName(), boundAt, null, null,
                    scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk, false);
        }
        TMfOauthToken t = token.get();
        Instant exp = t.getExpiresAt().toInstant();
        Instant lastRefreshed = t.getModifyDateTime() != null
                ? t.getModifyDateTime().toInstant()
                : t.getAddDateTime().toInstant();
        // G1-M4 (2026-05-06): add_date_time でなく refresh_token_issued_at を使用する。
        // rotation OFF (= MF レスポンスに refresh_token が無く旧 token を流用) の場合、
        // softDeleteActiveTokens + 新 row insert で add_date_time は毎回更新されるため
        // 540 日寿命の起点として誤検知 (残日数過大評価) していた。
        // 旧 row の値を継承する refresh_token_issued_at を使うことで rotation 設定に依らず正確になる。
        // 旧データ救済として NULL fallback で add_date_time を残す (V042 backfill で実質発生しない想定)。
        Instant refreshTokenIssuedAt = t.getRefreshTokenIssuedAt() != null
                ? t.getRefreshTokenIssuedAt().toInstant()
                : (t.getAddDateTime() != null ? t.getAddDateTime().toInstant() : null);
        Integer daysUntilReauth = null;
        boolean reAuthRequired = false;
        boolean reAuthExpired = false;
        if (refreshTokenIssuedAt != null) {
            long elapsedDays = ChronoUnit.DAYS.between(refreshTokenIssuedAt, Instant.now());
            long remaining = REFRESH_TOKEN_LIFETIME_DAYS - elapsedDays;
            if (remaining < 0) {
                // G1-M4: 540 日超過 = 期限切れ。再認可必須 (UI では最高 severity の destructive banner で表示)。
                reAuthExpired = true;
                reAuthRequired = true;
                // UI 表示用に 0 にクランプ (負値表示は不自然)。
                daysUntilReauth = 0;
            } else {
                // int 安全キャスト (540 日基準で int の範囲を逸脱しない)
                daysUntilReauth = (int) Math.max(Math.min(remaining, Integer.MAX_VALUE), Integer.MIN_VALUE);
                reAuthRequired = daysUntilReauth <= 0;
            }
        }
        return new MfTokenStatus(true, true, exp, t.getScope(), lastRefreshed, reAuthRequired,
                c.getMfTenantId(), c.getMfTenantName(), boundAt,
                refreshTokenIssuedAt, daysUntilReauth,
                scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk, reAuthExpired);
    }

    /**
     * 接続切断。DB トークンを論理削除し、tenant binding もクリアする
     * (P1-01: 別 MF tenant への再認可を許可するため)。MF 側 revoke は現時点でベストエフォート省略。
     */
    @Transactional
    @AuditLog(table = "t_mf_oauth_token", operation = "revoke",
            pkExpression = "{'userNo': #a0}",
            captureArgsAsAfter = true)
    public void revoke(Integer userNo) {
        MMfOauthClient client = findActiveClient().orElse(null);
        if (client == null) return;
        Timestamp now = Timestamp.from(Instant.now());
        tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
        // P1-01: tenant binding をクリアし、次回 callback で別 tenant に bind 可能とする。
        if (client.getMfTenantId() != null) {
            log.info("MF revoke: tenant binding をクリアします clientId={} previousTenantId={} previousTenantName={}",
                    client.getId(), client.getMfTenantId(), client.getMfTenantName());
            client.setMfTenantId(null);
            client.setMfTenantName(null);
            client.setTenantBoundAt(null);
            client.setModifyDateTime(now);
            client.setModifyUserNo(userNo);
            clientRepository.save(client);
        }
    }

    /**
     * P1-01: tenant binding の検証 + 必要なら確定。
     * <ul>
     *   <li>既存 {@code mf_tenant_id} が NULL → 初回 binding として確定 (旧データ互換)</li>
     *   <li>既存 {@code mf_tenant_id} が一致 → tenant_name のみ更新 (改名追従)</li>
     *   <li>既存 {@code mf_tenant_id} が不一致 → {@link MfTenantMismatchException} を throw</li>
     * </ul>
     */
    private void bindOrVerifyTenant(MMfOauthClient client, MfTenantResponse tenant, Integer userNo) {
        if (tenant == null || tenant.id() == null || tenant.id().isBlank()) {
            throw new IllegalStateException("MF tenant id を取得できませんでした");
        }
        String observedId = tenant.id();
        String observedName = tenant.name();
        Timestamp now = Timestamp.from(Instant.now());

        if (client.getMfTenantId() == null) {
            // 初回 binding (旧データ互換 or 初回認可)
            log.info("MF tenant binding 初回確定 clientId={} tenantId={} tenantName={}",
                    client.getId(), observedId, observedName);
            client.setMfTenantId(observedId);
            client.setMfTenantName(observedName);
            client.setTenantBoundAt(now);
            client.setModifyDateTime(now);
            client.setModifyUserNo(userNo);
            clientRepository.save(client);
            return;
        }

        if (!client.getMfTenantId().equals(observedId)) {
            // 不一致 = 別会社 MF を認可した可能性
            log.error("MF tenant 不一致を検知 clientId={} bound={} observed={}",
                    client.getId(), client.getMfTenantId(), observedId);
            throw new MfTenantMismatchException(client.getMfTenantId(), observedId,
                    "既存連携と異なる MF tenant です: 既存=" + client.getMfTenantId()
                            + " / 新規=" + observedId
                            + ". 旧連携を解除してから再認可してください");
        }

        // 一致: 名称差分があれば追従更新 (id 不変なので safe)
        if (observedName != null && !observedName.equals(client.getMfTenantName())) {
            log.info("MF tenant 名称を更新 clientId={} tenantId={} oldName={} newName={}",
                    client.getId(), observedId, client.getMfTenantName(), observedName);
            client.setMfTenantName(observedName);
            client.setModifyDateTime(now);
            client.setModifyUserNo(userNo);
            clientRepository.save(client);
        }
    }

    private void persistToken(MMfOauthClient client, MfTokenResponse res, Integer userNo) {
        Timestamp now = Timestamp.from(Instant.now());
        // SF-21: refresh_token は MF が返さないこともある (rotation OFF の場合)。
        // null fallback は **既存 active token の refresh_token_enc を再利用** する。
        // softDeleteActiveTokens 実行前に既存 token を読む必要があるため順序に注意。
        // G1-M4: refresh_token_issued_at の継承元としても旧 row を参照するので、ここで一括取得する。
        Optional<TMfOauthToken> previousToken = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.getId(), "0");
        String refreshTokenEnc;
        if (res.refreshToken() != null) {
            refreshTokenEnc = cryptoUtil.encrypt(res.refreshToken());
        } else {
            refreshTokenEnc = previousToken
                    .map(TMfOauthToken::getRefreshTokenEnc)
                    .orElseThrow(() -> new IllegalStateException(
                            "MF token レスポンスに refresh_token が無く、再利用可能な既存 token も存在しません"));
        }
        // G1-M4: refresh_token の真の発行日を判定。
        //  - rotation 動作 (新 refresh_token 受領) → 今を発行日とする
        //  - rotation OFF (旧 refresh_token 流用) → 旧 row の値を継承
        //  - 旧 row なし (= 初回認可) → now (rotation 有無に関わらず安全な fallback)
        Timestamp refreshTokenIssuedAt;
        if (res.refreshToken() != null) {
            refreshTokenIssuedAt = now;
        } else {
            refreshTokenIssuedAt = previousToken
                    .map(TMfOauthToken::getRefreshTokenIssuedAt)
                    .orElse(now);
        }
        tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
        long expiresInSec = res.expiresIn() != null ? res.expiresIn() : 3600L;
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(expiresInSec, ChronoUnit.SECONDS));
        TMfOauthToken token = TMfOauthToken.builder()
                .clientId(client.getId())
                .accessTokenEnc(cryptoUtil.encrypt(res.accessToken()))
                .refreshTokenEnc(refreshTokenEnc)
                .tokenType(res.tokenType() != null ? res.tokenType() : "Bearer")
                .expiresAt(expiresAt)
                .scope(res.scope())
                .refreshTokenIssuedAt(refreshTokenIssuedAt)
                .delFlg("0")
                .addDateTime(now)
                .addUserNo(userNo)
                .build();
        tokenRepository.save(token);
    }

    /** redirect_uri のホワイトリスト検証。完全一致でのみ受け付ける。 */
    private static void validateRedirectUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("redirect_uri が未入力です");
        }
        if (!ALLOWED_REDIRECT_URIS.contains(uri)) {
            throw new IllegalArgumentException(
                    "redirect_uri が許可リストにありません。許可済み: " + ALLOWED_REDIRECT_URIS);
        }
        try {
            URI u = URI.create(uri);
            if (u.getScheme() == null || (!u.getScheme().equals("http") && !u.getScheme().equals("https"))) {
                throw new IllegalArgumentException("redirect_uri の scheme は http/https のみ");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("redirect_uri が URI として不正: " + uri, e);
        }
    }

    /** {@link #buildAuthorizeUrl(Integer)} 戻り値。 */
    public record AuthorizeUrl(String url, String state) {}

    /**
     * {@link #loadActiveTokenSnapshot()} 戻り値。refresh 判定や HTTP 呼び出しで使う。
     * <p>SF-02: client_secret / access_token / refresh_token は機密値のため
     * {@link #toString()} で {@code ***} に置換 (ログ漏洩対策)。
     */
    public record TokenSnapshot(
            MMfOauthClient client,
            String plainSecret,
            String accessToken,
            String plainRefresh,
            Instant expiresAt) {
        public boolean isFresh(long marginSeconds) {
            return Instant.now().isBefore(expiresAt.minusSeconds(marginSeconds));
        }

        @Override
        public String toString() {
            return "TokenSnapshot[clientId=" + (client != null ? client.getId() : null)
                    + ", plainSecret=***, accessToken=***, plainRefresh=***, expiresAt=" + expiresAt + "]";
        }
    }
}
