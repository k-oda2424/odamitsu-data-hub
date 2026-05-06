package jp.co.oda32.util;

/**
 * ログ出力に含まれる可能性のある機密値 (token, secret, base64 hash 等) をマスキングするユーティリティ。
 * <p>
 * 単純な heuristic として、URL-safe base64 / hex / JWT などで現れる
 * {@code [a-zA-Z0-9_-]} 系列が 20 文字以上連続している場合を機密値とみなして
 * {@code ***} に置換する。
 * <p>
 * 主に MF token endpoint のエラーボディ (access_token / refresh_token / id_token を含むことがある)
 * を warn ログに残すケースで利用する (SF-04)。
 *
 * @since 2026-05-04 (SF-04)
 */
public final class SensitiveLogMasker {

    private SensitiveLogMasker() {}

    /**
     * 文字列中の長い英数字+_- 文字列を {@code ***} に置換する。
     * @param body マスキング対象 (null 可)
     * @return マスキング後の文字列。null in → null out。
     */
    public static String mask(String body) {
        if (body == null) return null;
        return body.replaceAll("[a-zA-Z0-9_-]{20,}", "***");
    }
}
