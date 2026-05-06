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
     * <p>SF-06: native {@code DELETE ... RETURNING} で 1 SQL atomic 消費。
     * read → delete 2-step だった旧実装の TOCTOU race (同 state を 2 回消費可能) を排除。
     *
     * <p>SF-06: ユーザ一致は厳格化。発行時 userNo が null だった行を userNo=null callback が
     * 受け取れる旧仕様 (テスト/匿名想定) は廃止し、常に reject する。
     *
     * @param state  callback で受け取った state
     * @param userNo 呼び出し元のログインユーザ番号。発行時と一致する必要がある。
     * @return 有効かつユーザ一致なら code_verifier を返す。不正なら empty。
     */
    @Transactional
    public Optional<String> verifyAndConsume(String state, Integer userNo) {
        if (state == null) return Optional.empty();
        Optional<Object[]> rowOpt = stateRepository.deleteAndReturnByState(state);
        if (rowOpt.isEmpty()) return Optional.empty();
        Object[] row = rowOpt.get();
        Integer rowUserNo = row[0] != null ? ((Number) row[0]).intValue() : null;
        String codeVerifier = (String) row[1];
        java.sql.Timestamp expiresAt = (java.sql.Timestamp) row[2];

        // TTL: now < expiresAt を要件にする (= !now.isBefore(expiresAt) なら期限切れ)
        if (expiresAt == null || !Instant.now().isBefore(expiresAt.toInstant())) {
            log.info("MF OAuth state 有効期限切れ");
            return Optional.empty();
        }
        // userNo の null 同士許容は撤去 (SF-06): 必ず厳格一致を要求
        if (rowUserNo == null || !rowUserNo.equals(userNo)) {
            return Optional.empty();
        }
        return Optional.of(codeVerifier);
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
