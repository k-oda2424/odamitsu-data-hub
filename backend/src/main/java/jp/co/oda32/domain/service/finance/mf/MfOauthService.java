package jp.co.oda32.domain.service.finance.mf;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.TMfOauthToken;
import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
import jp.co.oda32.util.CryptoUtil;
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
    private final CryptoUtil cryptoUtil;
    private final MfOauthStateStore stateStore;

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

    /** MF からの callback を処理し、トークンを DB 保存して接続状態にする。state 検証 + PKCE 込み。 */
    @Transactional
    public void handleCallback(String code, String state, Integer userNo) {
        Optional<String> verifierOpt = stateStore.verifyAndConsume(state, userNo);
        if (verifierOpt.isEmpty()) {
            throw new IllegalArgumentException("MF OAuth state 検証失敗 (期限切れ or 改ざん)");
        }
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String plainSecret = cryptoUtil.decrypt(client.getClientSecretEnc());
        MfTokenResponse token = mfApiClient.exchangeCodeForToken(client, plainSecret, code, verifierOpt.get());
        persistToken(client, token, userNo);
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
        if (snap.isFresh(REFRESH_MARGIN_SECONDS)) {
            return snap.accessToken;
        }

        // 2) refresh 必要。tx 外で HTTP を呼ぶ。
        log.info("MF access_token を refresh します（expiresAt={}）", snap.expiresAt);
        MfTokenResponse refreshed = mfApiClient.refreshToken(snap.client, snap.plainSecret, snap.plainRefresh);

        // 3) 永続化のみ REQUIRES_NEW で直列化
        self.persistRefreshedToken(snap.client.getId(), refreshed);
        return refreshed.accessToken();
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

    public MfTokenStatus getStatus() {
        Optional<MMfOauthClient> client = findActiveClient();
        if (client.isEmpty()) {
            return new MfTokenStatus(false, false, null, null, null, false);
        }
        Optional<TMfOauthToken> token = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.get().getId(), "0");
        if (token.isEmpty()) {
            return new MfTokenStatus(true, false, null, null, null, false);
        }
        TMfOauthToken t = token.get();
        Instant exp = t.getExpiresAt().toInstant();
        Instant lastRefreshed = t.getModifyDateTime() != null
                ? t.getModifyDateTime().toInstant()
                : t.getAddDateTime().toInstant();
        return new MfTokenStatus(true, true, exp, t.getScope(), lastRefreshed, false);
    }

    /** 接続切断。DB トークンを論理削除する（MF 側 revoke は現時点でベストエフォート省略）。 */
    @Transactional
    public void revoke(Integer userNo) {
        MMfOauthClient client = findActiveClient().orElse(null);
        if (client == null) return;
        tokenRepository.softDeleteActiveTokens(client.getId(), Timestamp.from(Instant.now()), userNo);
    }

    private void persistToken(MMfOauthClient client, MfTokenResponse res, Integer userNo) {
        Timestamp now = Timestamp.from(Instant.now());
        tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
        long expiresInSec = res.expiresIn() != null ? res.expiresIn() : 3600L;
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(expiresInSec, ChronoUnit.SECONDS));
        TMfOauthToken token = TMfOauthToken.builder()
                .clientId(client.getId())
                .accessTokenEnc(cryptoUtil.encrypt(res.accessToken()))
                .refreshTokenEnc(cryptoUtil.encrypt(res.refreshToken()))
                .tokenType(res.tokenType() != null ? res.tokenType() : "Bearer")
                .expiresAt(expiresAt)
                .scope(res.scope())
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

    /** {@link #loadActiveTokenSnapshot()} 戻り値。refresh 判定や HTTP 呼び出しで使う。 */
    public record TokenSnapshot(
            MMfOauthClient client,
            String plainSecret,
            String accessToken,
            String plainRefresh,
            Instant expiresAt) {
        public boolean isFresh(long marginSeconds) {
            return Instant.now().isBefore(expiresAt.minusSeconds(marginSeconds));
        }
    }
}
