package jp.co.oda32.util;

import jp.co.oda32.constant.Flag;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

import static jp.co.oda32.util.KatakanaHalfToFullConverter.zenkakuKatakanaToHankakuKatakana;

/**
 * 文字列操作ユーティルクラス
 *
 * @author k_oda
 * @since 2017/08/01
 */
public final class StringUtil {

    private StringUtil() {}

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        return obj1.equals(obj2);
    }

    /**
     * 引数の中に空が含まれているか
     *
     * @param objects 引数
     * @return 引数に空を含む場合:true
     */
    public static boolean containEmpty(Object... objects) {
        return Arrays.stream(objects)
                .anyMatch(ObjectUtils::isEmpty);
    }

    /**
     * 引数が全て空であるか
     *
     * @param objects 引数
     * @return 引数が全て空の場合:true
     */
    public static boolean isAllEmpty(Object... objects) {
        return Arrays.stream(objects)
                .allMatch(ObjectUtils::isEmpty);
    }

    /**
     * 文字列のflgがOnであるかを返します
     *
     * @param flg 文字列のフラグ
     * @return 引数が"1"の場合true
     */
    public static boolean isOnFlg(String flg) {
        return !isEmpty(flg) && Flag.YES.getValue().equals(flg);
    }

    /**
     * 文字列を全角と半角を考慮して引数の文字数に制限します。（smile用)
     *
     * @param input             文字数制限したい文字列
     * @param maxHalfWidthChars 最大文字数
     * @return 最大文字数で制限した文字列
     */
    public static String limitHalfWidthAndFullWidth(String input, int maxHalfWidthChars) {
        if (input == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int charWidth = isFullWidthChar(c) ? 2 : 1;
            if (isHalfWidthKatakana(c)) {
                charWidth = 1;
            }
            if (count + charWidth <= maxHalfWidthChars) {
                sb.append(c);
                count += charWidth;
            } else {
                break;
            }
        }

        return sb.toString();
    }

    /**
     * 引数の文字列を全て半角文字に変換します。（カタカナを除く）
     *
     * @param input 半角にしたい文字列
     * @return 半角文字列
     */
    public static String convertToHalfWidth(String input) {
        if (input == null) {
            return null;
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);

        StringBuilder sb = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (isFullWidthSymbol(c)) {
                sb.append(convertFullWidthSymbolToHalfWidth(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isFullWidthSymbol(char c) {
        return c == 0x3000; // 全角スペース
    }

    private static char convertFullWidthSymbolToHalfWidth(char c) {
        if (c == 0x3000) {
            return 0x0020; // 全角スペースを半角スペースに
        }
        return c;
    }

    /**
     * 引数の文字列を全て半角文字に変換します。全角カタカナも半角に変換します。
     *
     * @param input 半角にしたい文字列
     * @return 半角文字列
     */
    public static String convertToHalfWidthIncludingKatakana(String input) {
        String halfWidth = convertToHalfWidth(input);
        halfWidth = halfWidth.replace("\u30FC", "-"); // 全角の "ー" を半角の "-" に変換
        return zenkakuKatakanaToHankakuKatakana(halfWidth);
    }

    /**
     * 文字が全角かどうかを判定します。
     *
     * @param c 判定したい文字
     * @return 全角ならtrue、半角ならfalse
     */
    private static boolean isFullWidthChar(char c) {
        if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }

        return (0x3000 <= c && c <= 0x30FF) || (0xFF00 <= c && c <= 0xFFEF) || (0x4E00 <= c && c <= 0x9FAF);
    }

    /**
     * 文字が半角カタカナかどうかを判定します。
     *
     * @param c 判定したい文字
     * @return 半角カタカナならtrue、それ以外ならfalse
     */
    private static boolean isHalfWidthKatakana(char c) {
        return (0xFF61 <= c && c <= 0xFF9F);
    }

    /**
     * 指定した文字列リストを入力文字列から取り除きます
     *
     * @param input         入力文字列
     * @param wordsToRemove 指定した文字列リスト
     * @return 指定した文字列リストを入力文字列から取り除いた文字列
     */
    public static String removeWords(String input, List<String> wordsToRemove) {
        if (input == null || wordsToRemove == null) {
            return input;
        }

        String result = input;
        for (String word : wordsToRemove) {
            result = result.replace(word, "");
        }

        return result;
    }

    /**
     * 全角スペース、半角スペースを除去します。
     *
     * @param input 文字列
     * @return 全角スペース、半角スペースを除去した文字列
     */
    public static String removeSpaces(String input) {
        if (input == null) {
            return null;
        }

        String result = input.trim(); // 半角スペースを除去
        result = result.replace("\u3000", ""); // 全角スペースを除去
        return result;
    }
}
