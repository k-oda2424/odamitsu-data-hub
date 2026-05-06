package jp.co.oda32.domain.model.finance;

import java.util.Arrays;

/**
 * 売掛金集計バッチで指定する締め日タイプ。
 * <p>
 * 旧実装は Controller 側に文字列リテラル ({@code "all"} / {@code "15"} / {@code "20"} / {@code "month_end"}) と
 * Tasklet 側に {@code public static final String} 定数の二重管理になっていた。
 * SF-E10 で本 enum に集約し、Controller / Service / Tasklet から共通参照する。
 *
 * @since 2026-05-04 (SF-E10)
 */
public enum CutoffType {
    ALL("all"),
    DAY_15("15"),
    DAY_20("20"),
    MONTH_END("month_end");

    private final String code;

    CutoffType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * リクエスト文字列を enum に変換する。null/空文字は {@link #ALL} にフォールバック。
     *
     * @param code リクエスト文字列
     * @return 対応する {@link CutoffType}
     * @throws IllegalArgumentException 未知の code
     */
    public static CutoffType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ALL;
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "cutoffType は all / 15 / 20 / month_end のいずれかを指定してください: " + code));
    }
}
