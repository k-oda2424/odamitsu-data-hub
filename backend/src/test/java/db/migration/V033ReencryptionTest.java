package db.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V033 再暗号化 migration のユニットテスト。
 * <p>
 * H2 in-memory DB に t_mf_oauth_token / m_mf_oauth_client 相当のテーブルを作成し、
 * 旧鍵で暗号化したダミー値を投入して V033 のヘルパー (`reencrypt` / `reencryptMfTokens`) を
 * 直接呼び出し、両カラムが新鍵で再暗号化されることを検証する。
 *
 * <h2>Codex P1 fix の主要回帰テスト</h2>
 * 旧実装は access_token / refresh_token を別 reencrypt() 呼び出しで処理 → 1 回目で version=2 に
 * マーク → 2 回目で WHERE version=1 が 0 件ヒット → refresh_token_enc が旧鍵のまま残るバグ。
 * 本テストの {@link #reencryptMfTokens_両カラム同時更新_versionMarker} がこの回帰を防ぐ。
 */
class V033ReencryptionTest {

    private static final String OLD_KEY = "old-key-for-test-aaaaaaaaaaaaaaaa";
    private static final String OLD_SALT = "0123456789abcdef0123456789abcdef";
    private static final String NEW_KEY = "new-key-for-test-bbbbbbbbbbbbbbbb";
    private static final String NEW_SALT = "fedcba9876543210fedcba9876543210";

    private TextEncryptor oldCipher;
    private TextEncryptor newCipher;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        oldCipher = Encryptors.delux(OLD_KEY, OLD_SALT);
        newCipher = Encryptors.delux(NEW_KEY, NEW_SALT);

        // テストごとに独立した H2 DB を用意 (`MODE=PostgreSQL` で構文を本番に近づける)
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:v033_test_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Statement st = conn.createStatement()) {
            // 本番テーブル (V032 まで適用済) の構造を簡易再現。
            st.execute("CREATE TABLE t_mf_oauth_token ("
                    + "id INT PRIMARY KEY, "
                    + "access_token_enc VARCHAR(2000), "
                    + "refresh_token_enc VARCHAR(2000)"
                    + ")");
            st.execute("CREATE TABLE m_mf_oauth_client ("
                    + "id INT PRIMARY KEY, "
                    + "client_secret_enc VARCHAR(2000)"
                    + ")");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    /**
     * Codex P1 主要回帰テスト: t_mf_oauth_token の access + refresh が 1 つの UPDATE で
     * 同時に再暗号化され、両方とも新鍵で復号できる状態になること。version=2 マーカーが
     * セットされること。
     */
    @Test
    @DisplayName("reencryptMfTokens: access + refresh を 1 update で同時再暗号化 + version=2 マーク")
    void reencryptMfTokens_両カラム同時更新_versionMarker() throws Exception {
        // version 列を追加 (V037 適用後の世界をシミュレート)
        addVersionColumn("t_mf_oauth_token");

        String origAccess = "access-token-plaintext-001";
        String origRefresh = "refresh-token-plaintext-001";
        insertToken(1, oldCipher.encrypt(origAccess), oldCipher.encrypt(origRefresh), 1);

        int updated = V033__reencrypt_mf_oauth_secrets.reencryptMfTokens(
                conn, oldCipher, newCipher, true);

        assertEquals(1, updated, "1 行 update されるはず");

        // version 列が 2 になり、両カラムが新鍵で復号できる
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT access_token_enc, refresh_token_enc, oauth_encryption_version"
                        + " FROM t_mf_oauth_token WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            String newAccessEnc = rs.getString(1);
            String newRefreshEnc = rs.getString(2);
            int version = rs.getInt(3);

            assertEquals(2, version, "version は 2 にマークされるはず");
            assertEquals(origAccess, newCipher.decrypt(newAccessEnc),
                    "access_token_enc は新鍵で復号できるはず");
            assertEquals(origRefresh, newCipher.decrypt(newRefreshEnc),
                    "refresh_token_enc も新鍵で復号できるはず (= P1 バグの回帰防止)");
        }
    }

    /**
     * version=2 行は対象外: 既に再暗号化済の row に対して再走しても何も起きない (idempotent)。
     */
    @Test
    @DisplayName("reencryptMfTokens: version=2 の row は skip (idempotent)")
    void reencryptMfTokens_既新鍵化済はskip() throws Exception {
        addVersionColumn("t_mf_oauth_token");

        // version=2 (既に新鍵化済) の row
        String alreadyNewAccess = newCipher.encrypt("payload-A");
        String alreadyNewRefresh = newCipher.encrypt("payload-R");
        insertToken(10, alreadyNewAccess, alreadyNewRefresh, 2);

        int updated = V033__reencrypt_mf_oauth_secrets.reencryptMfTokens(
                conn, oldCipher, newCipher, true);

        assertEquals(0, updated, "version=2 行は対象外なので 0 件");

        // 値はそのまま (上書きされていない)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT access_token_enc, refresh_token_enc FROM t_mf_oauth_token WHERE id = 10");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(alreadyNewAccess, rs.getString(1));
            assertEquals(alreadyNewRefresh, rs.getString(2));
        }
    }

    /**
     * decrypt 失敗時 (= 新鍵で暗号化済の row を旧鍵で decrypt 試行した場合) は当該カラムを温存し、
     * 別の正常カラムの再暗号化は続行する。version 列なし環境の部分 commit 再実行を想定。
     */
    @Test
    @DisplayName("reencryptMfTokens: decrypt 失敗カラムは温存、もう片方は再暗号化継続")
    void reencryptMfTokens_decrypt失敗時はskip温存() throws Exception {
        // version 列なし環境 (V037 未適用) を模擬
        // 1 行目: access は旧鍵、refresh は新鍵 (= 既に再暗号化済) ← 部分 commit 後の再実行ケース
        String origAccess = "halfway-access";
        String alreadyNewRefresh = newCipher.encrypt("already-new-refresh");
        insertTokenNoVersion(20, oldCipher.encrypt(origAccess), alreadyNewRefresh);

        int updated = V033__reencrypt_mf_oauth_secrets.reencryptMfTokens(
                conn, oldCipher, newCipher, false);

        assertEquals(1, updated, "1 行 update が走る");

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT access_token_enc, refresh_token_enc FROM t_mf_oauth_token WHERE id = 20");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            String accessAfter = rs.getString(1);
            String refreshAfter = rs.getString(2);

            // access は新鍵で復号できる (= 再暗号化された)
            assertEquals(origAccess, newCipher.decrypt(accessAfter));
            // refresh は元の新鍵 enc がそのまま温存されている (= 二重暗号化されない)
            assertEquals(alreadyNewRefresh, refreshAfter,
                    "decrypt 失敗カラムは値を変えず温存");
        }
    }

    /**
     * 単一カラム reencrypt() — m_mf_oauth_client.client_secret_enc 用。version 列で絞り込み +
     * UPDATE で version=2 マーク。
     */
    @Test
    @DisplayName("reencrypt: m_mf_oauth_client.client_secret_enc は単一カラム再暗号化")
    void reencrypt_singleColumn_clientSecret() throws Exception {
        addVersionColumn("m_mf_oauth_client");

        String origSecret = "super-secret-client-credential";
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO m_mf_oauth_client (id, client_secret_enc, oauth_encryption_version)"
                        + " VALUES (?, ?, ?)")) {
            ps.setInt(1, 100);
            ps.setString(2, oldCipher.encrypt(origSecret));
            ps.setInt(3, 1);
            ps.executeUpdate();
        }

        int updated = V033__reencrypt_mf_oauth_secrets.reencrypt(
                conn, "m_mf_oauth_client", "id", "client_secret_enc",
                oldCipher, newCipher, true);

        assertEquals(1, updated);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT client_secret_enc, oauth_encryption_version FROM m_mf_oauth_client WHERE id = 100");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            String newEnc = rs.getString(1);
            assertNotEquals(oldCipher.encrypt(origSecret), newEnc, "新鍵で再暗号化されているはず");
            assertEquals(origSecret, newCipher.decrypt(newEnc), "新鍵で復号できる");
            assertEquals(2, rs.getInt(2), "version=2 マーク");
        }
    }

    /**
     * 元から両カラムとも NULL の row (= MF 連携未設定 row) は壊さず version マーカーだけ進める。
     */
    @Test
    @DisplayName("reencryptMfTokens: 両カラム NULL の row は値変えずに version=2 にマーク")
    void reencryptMfTokens_両NULL行はマーカーのみ() throws Exception {
        addVersionColumn("t_mf_oauth_token");
        insertToken(30, null, null, 1);

        int updated = V033__reencrypt_mf_oauth_secrets.reencryptMfTokens(
                conn, oldCipher, newCipher, true);

        assertEquals(1, updated);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT access_token_enc, refresh_token_enc, oauth_encryption_version"
                        + " FROM t_mf_oauth_token WHERE id = 30");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertNull(rs.getString(1));
            assertNull(rs.getString(2));
            assertEquals(2, rs.getInt(3));
        }
    }

    /**
     * version 列が存在しない場合 (= V037 未適用環境) でも reencrypt は機能する。
     */
    @Test
    @DisplayName("reencryptMfTokens: version 列なし環境でも全行再暗号化")
    void reencryptMfTokens_version列なし() throws Exception {
        // version 列追加せず実行
        String origAccess = "no-ver-access";
        String origRefresh = "no-ver-refresh";
        insertTokenNoVersion(40, oldCipher.encrypt(origAccess), oldCipher.encrypt(origRefresh));

        int updated = V033__reencrypt_mf_oauth_secrets.reencryptMfTokens(
                conn, oldCipher, newCipher, false);

        assertEquals(1, updated);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT access_token_enc, refresh_token_enc FROM t_mf_oauth_token WHERE id = 40");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(origAccess, newCipher.decrypt(rs.getString(1)));
            assertEquals(origRefresh, newCipher.decrypt(rs.getString(2)));
        }
    }

    // -------------------- helpers --------------------

    private void addVersionColumn(String table) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table
                    + " ADD COLUMN oauth_encryption_version SMALLINT NOT NULL DEFAULT 1");
        }
    }

    private void insertToken(int id, String accessEnc, String refreshEnc, int version) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO t_mf_oauth_token (id, access_token_enc, refresh_token_enc,"
                        + " oauth_encryption_version) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, id);
            if (accessEnc == null) ps.setNull(2, java.sql.Types.VARCHAR);
            else ps.setString(2, accessEnc);
            if (refreshEnc == null) ps.setNull(3, java.sql.Types.VARCHAR);
            else ps.setString(3, refreshEnc);
            ps.setInt(4, version);
            ps.executeUpdate();
        }
    }

    private void insertTokenNoVersion(int id, String accessEnc, String refreshEnc) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO t_mf_oauth_token (id, access_token_enc, refresh_token_enc)"
                        + " VALUES (?, ?, ?)")) {
            ps.setInt(1, id);
            if (accessEnc == null) ps.setNull(2, java.sql.Types.VARCHAR);
            else ps.setString(2, accessEnc);
            if (refreshEnc == null) ps.setNull(3, java.sql.Types.VARCHAR);
            else ps.setString(3, refreshEnc);
            ps.executeUpdate();
        }
    }
}
