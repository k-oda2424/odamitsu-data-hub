package jp.co.oda32.util;

/**
 * 全角カタカナを半角カタカナに変換するクラス
 *
 * @author k_oda
 * @since 2023/04/18
 */
public class KatakanaHalfToFullConverter {
    private static final char[] ZENKAKU_KATAKANA = {'\u30A1', '\u30A2', '\u30A3', '\u30A4', '\u30A5',
            '\u30A6', '\u30A7', '\u30A8', '\u30A9', '\u30AA', '\u30AB', '\u30AC', '\u30AD', '\u30AE', '\u30AF', '\u30B0', '\u30B1', '\u30B2',
            '\u30B3', '\u30B4', '\u30B5', '\u30B6', '\u30B7', '\u30B8', '\u30B9', '\u30BA', '\u30BB', '\u30BC', '\u30BD', '\u30BE', '\u30BF',
            '\u30C0', '\u30C1', '\u30C2', '\u30C3', '\u30C4', '\u30C5', '\u30C6', '\u30C7', '\u30C8', '\u30C9', '\u30CA', '\u30CB', '\u30CC',
            '\u30CD', '\u30CE', '\u30CF', '\u30D0', '\u30D1', '\u30D2', '\u30D3', '\u30D4', '\u30D5', '\u30D6', '\u30D7', '\u30D8', '\u30D9',
            '\u30DA', '\u30DB', '\u30DC', '\u30DD', '\u30DE', '\u30DF', '\u30E0', '\u30E1', '\u30E2', '\u30E3', '\u30E4', '\u30E5', '\u30E6',
            '\u30E7', '\u30E8', '\u30E9', '\u30EA', '\u30EB', '\u30EC', '\u30ED', '\u30EE', '\u30EF', '\u30F0', '\u30F1', '\u30F2', '\u30F3',
            '\u30F4', '\u30F5', '\u30F6'};

    private static final String[] HANKAKU_KATAKANA = {"\uFF67", "\uFF71", "\uFF68", "\uFF72", "\uFF69",
            "\uFF73", "\uFF6A", "\uFF74", "\uFF6B", "\uFF75", "\uFF76", "\uFF76\uFF9E", "\uFF77", "\uFF77\uFF9E", "\uFF78", "\uFF78\uFF9E", "\uFF79",
            "\uFF79\uFF9E", "\uFF7A", "\uFF7A\uFF9E", "\uFF7B", "\uFF7B\uFF9E", "\uFF7C", "\uFF7C\uFF9E", "\uFF7D", "\uFF7D\uFF9E", "\uFF7E", "\uFF7E\uFF9E", "\uFF7F",
            "\uFF7F\uFF9E", "\uFF80", "\uFF80\uFF9E", "\uFF81", "\uFF81\uFF9E", "\uFF6F", "\uFF82", "\uFF82\uFF9E", "\uFF83", "\uFF83\uFF9E", "\uFF84", "\uFF84\uFF9E",
            "\uFF85", "\uFF86", "\uFF87", "\uFF88", "\uFF89", "\uFF8A", "\uFF8A\uFF9E", "\uFF8A\uFF9F", "\uFF8B", "\uFF8B\uFF9E", "\uFF8B\uFF9F", "\uFF8C",
            "\uFF8C\uFF9E", "\uFF8C\uFF9F", "\uFF8D", "\uFF8D\uFF9E", "\uFF8D\uFF9F", "\uFF8E", "\uFF8E\uFF9E", "\uFF8E\uFF9F", "\uFF8F", "\uFF90", "\uFF91", "\uFF92",
            "\uFF93", "\uFF6C", "\uFF94", "\uFF6D", "\uFF95", "\uFF6E", "\uFF96", "\uFF97", "\uFF98", "\uFF99", "\uFF9A", "\uFF9B", "\uFF9C",
            "\uFF9C", "\uFF72", "\uFF74", "\uFF66", "\uFF9D", "\uFF73\uFF9E", "\uFF76", "\uFF79"};

    private static final char ZENKAKU_KATAKANA_FIRST_CHAR = ZENKAKU_KATAKANA[0];

    private static final char ZENKAKU_KATAKANA_LAST_CHAR = ZENKAKU_KATAKANA[ZENKAKU_KATAKANA.length - 1];

    public static String zenkakuKatakanaToHankakuKatakana(char c) {
        if (c >= ZENKAKU_KATAKANA_FIRST_CHAR && c <= ZENKAKU_KATAKANA_LAST_CHAR) {
            return HANKAKU_KATAKANA[c - ZENKAKU_KATAKANA_FIRST_CHAR];
        } else {
            return String.valueOf(c);
        }
    }

    public static String zenkakuKatakanaToHankakuKatakana(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char originalChar = s.charAt(i);
            String convertedChar = zenkakuKatakanaToHankakuKatakana(originalChar);
            sb.append(convertedChar);
        }
        return sb.toString();
    }
}
