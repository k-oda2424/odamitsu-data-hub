package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * P1-05 案 C.3: MF OAuth 関連 _enc カラムを <b>OAuth 専用鍵</b> で再暗号化する Java migration。
 * <p>
 * 旧鍵 ({@code APP_CRYPTO_KEY} / {@code APP_CRYPTO_SALT}) で暗号化されている下記 3 カラムを
 * 新鍵 ({@code APP_CRYPTO_OAUTH_KEY} / {@code APP_CRYPTO_OAUTH_SALT}) で再暗号化する。
 * <ul>
 *   <li>{@code m_mf_oauth_client.client_secret_enc}</li>
 *   <li>{@code t_mf_oauth_token.access_token_enc}</li>
 *   <li>{@code t_mf_oauth_token.refresh_token_enc}</li>
 * </ul>
 *
 * <p>動作 (idempotent 化済 / C1 fix + Codex P1 fix):
 * <ol>
 *   <li>環境変数から旧鍵 (未設定なら application-dev.yml の dev fallback) と新鍵を取得</li>
 *   <li>新鍵 (oauth-key/salt) が未設定なら fail-fast (本 migration をスキップさせない)</li>
 *   <li>{@code oauth_encryption_version} 列が存在すれば、{@code version=1} 行のみ対象に絞る
 *       (V037 適用後の挙動)。version 列が無ければ全行対象 (V037 適用前の旧環境)</li>
 *   <li>1 行 SELECT → 旧鍵 decrypt → 新鍵 encrypt → version=2 と共に UPDATE → 即 commit</li>
 *   <li>途中で失敗しても、それまで update 済みの行は version=2 で永続化されているため
 *       次回再実行時に skip される (idempotent)</li>
 * </ol>
 *
 * <p><b>Codex P1 fix (2026-05-06)</b>: {@code t_mf_oauth_token} は access_token_enc と
 * refresh_token_enc の 2 カラムを持つため、旧実装は同テーブルを 2 回 reencrypt 呼び出ししていた。
 * しかし 1 回目の呼び出しが {@code version=1 → 2} にマークしてしまうため、2 回目の呼び出し
 * (= refresh_token_enc 用) は WHERE {@code version=1} で 0 件になり、refresh_token_enc が
 * 旧鍵のまま残る致命バグがあった。修正: {@link #reencryptMfTokens} で両カラムを 1 つの UPDATE で
 * アトミックに再暗号化する。
 *
 * <p><b>Codex P1 fix #2 (2026-05-06)</b>: 旧実装は decrypt 失敗で即 fail-fast していたが、
 * version 列なし環境で部分 commit 後に再実行されると「既に新鍵化済の行」を旧鍵で decrypt 試行 →
 * 失敗 → migration 全体停止になる。修正: decrypt 失敗時は WARN ログ出して skip し、
 * (新鍵で暗号化済 = 既に再暗号化完了済) と仮定して次行へ進む。
 *
 * <p>autoCommit OFF + 全件まとめて commit していた旧実装は、Flyway 履歴記録前にプロセス停止すると
 * 「暗号文は新鍵化済み・migration 履歴は未適用」のミスマッチ状態になり復旧困難だった。
 * 本実装は行単位 commit + version マーカーで部分再開を可能にする。
 *
 * <p>セットアップ手順 / トラブルシューティングは {@code claudedocs/runbook-mf-oauth-keys.md} を参照。
 *
 * @since 2026/05/04
 */
public class V033__reencrypt_mf_oauth_secrets extends BaseJavaMigration {

    /**
     * application-dev.yml に書かれている dev fallback と完全一致させる。
     * 環境変数 APP_CRYPTO_KEY/SALT が未設定の dev 環境でも、Spring 起動時に CryptoUtil が
     * これらの値を使って暗号化済みなので、本 migration もこの値で旧データを復号する。
     */
    private static final String DEV_FALLBACK_KEY = "dev-odamitsu-data-hub-crypto-key-2026";
    private static final String DEV_FALLBACK_SALT = "3f6a2d8c9e1b4a7d5c0f8e2a9b6d1c3f";

    private static final String VERSION_COLUMN = "oauth_encryption_version";

    @Override
    public void migrate(Context context) throws Exception {
        String oldKey = envOrDefault("APP_CRYPTO_KEY", DEV_FALLBACK_KEY);
        String oldSalt = envOrDefault("APP_CRYPTO_SALT", DEV_FALLBACK_SALT);
        String newKey = System.getenv("APP_CRYPTO_OAUTH_KEY");
        String newSalt = System.getenv("APP_CRYPTO_OAUTH_SALT");

        if (newKey == null || newKey.isBlank() || newSalt == null || newSalt.isBlank()) {
            throw new IllegalStateException(
                    "[V033] APP_CRYPTO_OAUTH_KEY と APP_CRYPTO_OAUTH_SALT は必須です。"
                            + " backend/scripts/gen-oauth-key.ps1 で鍵を生成し、env var を設定してください。"
                            + " 詳細は claudedocs/runbook-mf-oauth-keys.md (Step 1-2) を参照。");
        }

        TextEncryptor oldCipher = Encryptors.delux(oldKey, oldSalt);
        TextEncryptor newCipher = Encryptors.delux(newKey, newSalt);

        Connection conn = context.getConnection();
        // 行単位 commit (idempotent 再開) を成立させるため autoCommit を ON 化。
        // Flyway は本 migration 完了後に独自トランザクションで履歴を記録する。
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(true);
        try {
            boolean clientHasVersion = columnExists(conn, "m_mf_oauth_client", VERSION_COLUMN);
            boolean tokenHasVersion = columnExists(conn, "t_mf_oauth_token", VERSION_COLUMN);

            int clientCount = reencrypt(conn, "m_mf_oauth_client", "id",
                    "client_secret_enc", oldCipher, newCipher, clientHasVersion);
            // Codex P1 fix: t_mf_oauth_token は access + refresh を 1 UPDATE で同時更新する。
            // 旧実装は reencrypt() を 2 回呼んでいたが、1 回目で version=2 にマーク → 2 回目で
            // WHERE version=1 がヒット 0 件になり refresh_token_enc が旧鍵のまま残るバグがあった。
            int tokenCount = reencryptMfTokens(conn, oldCipher, newCipher, tokenHasVersion);
            System.out.printf(
                    "[V033] OAuth secrets re-encrypted: client_secret=%d, t_mf_oauth_token rows=%d"
                            + " (clientHasVersion=%b, tokenHasVersion=%b)%n",
                    clientCount, tokenCount, clientHasVersion, tokenHasVersion);
        } catch (Exception e) {
            // 行単位 commit 済みのため rollback では戻らない。
            // 例外をそのまま伝播 → Flyway が migration 失敗として記録 → 次回再起動時に
            // version=2 マーク済み行は自動 skip され、未処理行から再開できる。
            throw new IllegalStateException(
                    "[V033] 再暗号化に失敗しました。version=2 マーク済み行は新鍵化完了。"
                            + " 残り行は次回再実行で自動再開されます。"
                            + " 旧鍵 (APP_CRYPTO_KEY/SALT) が変わっている可能性があるので env を要確認。"
                            + " 詳細は claudedocs/runbook-mf-oauth-keys.md の鍵紛失復旧手順を参照。"
                            + " 元例外: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * 単一カラム再暗号化ヘルパー (m_mf_oauth_client.client_secret_enc 用)。
     * <p>
     * {@code hasVersionColumn=true} のときは {@code oauth_encryption_version=1} の行のみ処理し、
     * UPDATE で同時に {@code version=2} へ進める。version 列が無い旧環境では NULL 行以外を全件処理する
     * (V037 適用前のため version 概念が未導入)。
     * <p>
     * autoCommit ON 前提: 1 行 UPDATE = 即 commit。途中で例外発生してもそれまでの行は確定済み。
     * <p>
     * <b>decrypt 失敗時 (Codex P1 fix #2)</b>: WARN ログ出して skip。version 列なし環境で部分 commit
     * 後に再実行された場合、新鍵で暗号化済の行は旧鍵 decrypt できず例外になるため、それを「既に
     * 再暗号化完了済」と判定して次行へ進む。
     *
     * @return 再暗号化した行数 (skip した行は含まない)
     */
    static int reencrypt(Connection conn, String table, String pkColumn, String column,
                         TextEncryptor oldCipher, TextEncryptor newCipher,
                         boolean hasVersionColumn) throws Exception {
        String whereVersion = hasVersionColumn
                ? " AND " + VERSION_COLUMN + " = 1"
                : "";
        String selectSql = "SELECT " + pkColumn + ", " + column + " FROM " + table
                + " WHERE " + column + " IS NOT NULL" + whereVersion;
        String updateSql = hasVersionColumn
                ? "UPDATE " + table + " SET " + column + " = ?, " + VERSION_COLUMN + " = 2"
                        + " WHERE " + pkColumn + " = ? AND " + VERSION_COLUMN + " = 1"
                : "UPDATE " + table + " SET " + column + " = ? WHERE " + pkColumn + " = ?";
        int count = 0;
        int skipped = 0;
        try (Statement select = conn.createStatement();
             ResultSet rs = select.executeQuery(selectSql);
             PreparedStatement update = conn.prepareStatement(updateSql)) {
            while (rs.next()) {
                int pk = rs.getInt(1);
                String oldEnc = rs.getString(2);
                String plain;
                try {
                    plain = oldCipher.decrypt(oldEnc);
                } catch (Exception e) {
                    // Codex P1 fix #2: 旧鍵で decrypt 失敗 = 既に新鍵化済の可能性 (version 列なし環境の
                    // 部分 commit 再実行時等)。fail-fast せずに skip + WARN ログ。
                    System.err.printf(
                            "[V033] WARN: %s.%s (%s=%d) は旧鍵で復号できず skip (新鍵化済の可能性): %s%n",
                            table, column, pkColumn, pk, e.getMessage());
                    skipped++;
                    continue;
                }
                String newEnc = newCipher.encrypt(plain);
                update.setString(1, newEnc);
                update.setInt(2, pk);
                // 行単位 commit (autoCommit=true) → executeBatch ではなく即時 executeUpdate
                update.executeUpdate();
                count++;
            }
        }
        if (skipped > 0) {
            System.err.printf("[V033] %s.%s: %d 行を decrypt 失敗で skip しました (新鍵化済と仮定)%n",
                    table, column, skipped);
        }
        return count;
    }

    /**
     * t_mf_oauth_token 専用ヘルパー: access_token_enc + refresh_token_enc を 1 つの UPDATE で
     * 同時に再暗号化する。
     * <p>
     * <b>Codex P1 fix の根幹</b>: 旧実装は {@link #reencrypt} を access_token / refresh_token に対して
     * 順番に 2 回呼んでいたが、1 回目で {@code version=2} にマーク → 2 回目では {@code WHERE version=1}
     * がヒット 0 件になり refresh_token_enc が旧鍵のまま残ってしまっていた。
     * 本メソッドは 1 SELECT で両カラムを取得 → 1 UPDATE で両カラムをアトミックに更新する。
     * <p>
     * decrypt 失敗時の skip ロジックは {@link #reencrypt} と同じ (両カラム独立に try/catch、
     * 両方 skip した行のみ "完全 skip" 扱い)。
     *
     * @return 1 行でも実際に UPDATE が走った行数
     */
    static int reencryptMfTokens(Connection conn, TextEncryptor oldCipher, TextEncryptor newCipher,
                                 boolean hasVersionColumn) throws Exception {
        String whereVersion = hasVersionColumn
                ? " WHERE " + VERSION_COLUMN + " = 1"
                : "";
        // access_token_enc / refresh_token_enc とも NULL 許容 (= MF 連携未設定の row が存在し得る) なので
        // どちらか NOT NULL の行を全部拾う。両方 NULL の行は処理対象外。
        String selectSql = "SELECT id, access_token_enc, refresh_token_enc FROM t_mf_oauth_token"
                + whereVersion;
        String updateSql = hasVersionColumn
                ? "UPDATE t_mf_oauth_token SET access_token_enc = ?, refresh_token_enc = ?,"
                        + " " + VERSION_COLUMN + " = 2"
                        + " WHERE id = ? AND " + VERSION_COLUMN + " = 1"
                : "UPDATE t_mf_oauth_token SET access_token_enc = ?, refresh_token_enc = ? WHERE id = ?";
        int count = 0;
        int skipped = 0;
        try (Statement select = conn.createStatement();
             ResultSet rs = select.executeQuery(selectSql);
             PreparedStatement update = conn.prepareStatement(updateSql)) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String oldAccess = rs.getString(2);
                String oldRefresh = rs.getString(3);

                // 両カラム独立に decrypt 試行 (片方だけ NULL の row もあり得る前提)
                String newAccess = recipherOrSkip(oldAccess, oldCipher, newCipher,
                        "t_mf_oauth_token.access_token_enc", id);
                String newRefresh = recipherOrSkip(oldRefresh, oldCipher, newCipher,
                        "t_mf_oauth_token.refresh_token_enc", id);

                // 両カラムとも NULL or skip → row 単位で skip (DB は更新しない)。
                // version=2 にマークしないので次回再実行で再評価される。
                if (newAccess == null && newRefresh == null
                        && oldAccess == null && oldRefresh == null) {
                    // 両方とも元から NULL → version マーカーは進めるが値は変えない
                    if (hasVersionColumn) {
                        update.setNull(1, java.sql.Types.VARCHAR);
                        update.setNull(2, java.sql.Types.VARCHAR);
                        update.setInt(3, id);
                        update.executeUpdate();
                        count++;
                    }
                    continue;
                }

                // skip した側は既存値を「そのまま温存」: 元 NULL なら NULL のまま、元 enc なら元 enc のまま。
                // recipherOrSkip は decrypt 失敗時に oldEnc をそのまま返すので、上書きしても値は変わらない。
                update.setString(1, newAccess);
                update.setString(2, newRefresh);
                update.setInt(3, id);
                update.executeUpdate();
                count++;
            }
        }
        if (skipped > 0) {
            System.err.printf("[V033] t_mf_oauth_token: %d カラムを skip しました (新鍵化済と仮定)%n",
                    skipped);
        }
        return count;
    }

    /**
     * 値が null なら null、decrypt 成功なら新鍵 encrypt、decrypt 失敗なら元値をそのまま返す
     * (= 既に新鍵化済の可能性が高いので壊さず温存)。
     */
    private static String recipherOrSkip(String oldEnc, TextEncryptor oldCipher,
                                         TextEncryptor newCipher, String fieldLabel, int pk) {
        if (oldEnc == null) {
            return null;
        }
        try {
            String plain = oldCipher.decrypt(oldEnc);
            return newCipher.encrypt(plain);
        } catch (Exception e) {
            System.err.printf(
                    "[V033] WARN: %s (id=%d) は旧鍵で復号できず温存 (新鍵化済の可能性): %s%n",
                    fieldLabel, pk, e.getMessage());
            return oldEnc;
        }
    }

    /** 指定テーブルに指定カラムが存在するか判定 (大文字小文字無視)。 */
    static boolean columnExists(Connection conn, String table, String column) throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        }
        // PostgreSQL identifier は通常 lower-case 格納。明示的に lower でも試す。
        try (ResultSet rs = md.getColumns(null, null, table.toLowerCase(), column.toLowerCase())) {
            if (rs.next()) return true;
        }
        // H2 (test 用) は識別子を upper-case で格納する。テスト用に upper も試行。
        try (ResultSet rs = md.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
