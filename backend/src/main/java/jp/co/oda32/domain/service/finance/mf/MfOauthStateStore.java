package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.TMfOauthState;
import jp.co.oda32.domain.repository.finance.TMfOauthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * MF OAuth 認可フローの CSRF 防止 state + PKCE code_verifier を DB 永続化する (B-3 / B-4)。
 * <p>
 * 旧実装は ConcurrentHashMap でインメモリ保管していたが、マルチ JVM / 再起動で state が
 * 消失する問題があった。{@code t_mf_oauth_state} テーブルで永続化し、TTL 10 分で sweep。
 *
 * <p>PKCE (S256): {@link #issue(Integer)} で code_verifier を生成して DB に保存、
 * {@link #computeCodeChallenge(String)} で SHA-256(verifier) の base64url を計算し、
 * authorize URL の {@code code_challenge} に乗せる。callback 時に
 * {@link #verifyAndConsume(String, Integer)} で取り出した verifier を token endpoint に送る。
 *
 * @since 2026-04-20 (rewritten 2026-04-21)
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class MfOauthStateStore {

    private static final long TTL_SECONDS = 600L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TMfOauthStateRepository stateRepository;

    /** 新しい state + code_verifier を発行して DB 保存する。期限切れの古いエントリは都度掃除。 */
    @Transactional
    public Issued issue(Integer userNo) {
        sweep();
        String state = randomUrlSafe(32);
        String codeVerifier = randomUrlSafe(64); // RFC 7636: 43-128 chars
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(TTL_SECONDS));
        Timestamp now = Timestamp.from(Instant.now());
        stateRepository.save(TMfOauthState.builder()
                .state(state)
                .userNo(userNo)
                .codeVerifier(codeVerifier)
                .expiresAt(expiresAt)
                .addDateTime(now)
                .build());
        return new Issued(state, codeVerifier, computeCodeChallenge(codeVerifier));
    }

    /**
     * state を検証して消費する（以降同じ state は使えない）。
     *
     * @param state  callback で受け取った state
     * @param userNo 呼び出し元のログインユーザ番号。発行時と一致する必要がある。
     * @return 有効かつユーザ一致なら code_verifier を返す。不正なら empty。
     */
    @Transactional
    public Optional<String> verifyAndConsume(String state, Integer userNo) {
        if (state == null) return Optional.empty();
        Optional<TMfOauthState> opt = stateRepository.findById(state);
        if (opt.isEmpty()) return Optional.empty();
        TMfOauthState e = opt.get();
        // 使い捨てなので即削除
        stateRepository.deleteById(state);
        if (e.getExpiresAt().toInstant().isBefore(Instant.now())) {
            log.info("MF OAuth state 有効期限切れ");
            return Optional.empty();
        }
        // userNo が両側 null なら一致、片方だけ null なら不一致
        if (e.getUserNo() == null) {
            if (userNo != null) return Optional.empty();
        } else if (!e.getUserNo().equals(userNo)) {
            return Optional.empty();
        }
        return Optional.of(e.getCodeVerifier());
    }

    private void sweep() {
        stateRepository.deleteExpired(Timestamp.from(Instant.now()));
    }

    private static String randomUrlSafe(int byteLen) {
        byte[] bytes = new byte[byteLen];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** PKCE S256: SHA-256(verifier) を base64url (no padding) でエンコード。 */
    public static String computeCodeChallenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }

    /** issue() 戻り値: state + verifier + challenge。 */
    public record Issued(String state, String codeVerifier, String codeChallenge) {}
}
