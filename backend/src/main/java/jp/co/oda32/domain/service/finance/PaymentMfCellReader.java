package jp.co.oda32.domain.service.finance;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.time.LocalDate;

/**
 * Excel セルを型安全に読み出すユーティリティ。
 * <p>ステートレスな純粋関数のみ提供する（Bean 化しない）。
 * テストから直接呼べるよう package-private で公開。
 */
final class PaymentMfCellReader {

    private PaymentMfCellReader() {}

    /** 改行・復帰文字を {@code '_'} で置換する（CSV や DB への混入防止）。 */
    static String sanitize(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\r\\n]", "_");
    }

    /** 全角空白・前後空白除去・連続空白1個化で、比較用に正規化する。 */
    static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u3000', ' ').strip().replaceAll("\\s+", " ");
    }

    /**
     * セル値を文字列として読み出す。NUMERIC は整数ならそのまま整数、
     * そうでなければ double 文字列化。FORMULA は文字列→数値の順でフォールバック。
     */
    static String readStringCell(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d))
                    yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double d2 = cell.getNumericCellValue();
                        if (d2 == Math.floor(d2)) yield String.valueOf((long) d2);
                        yield String.valueOf(d2);
                    } catch (Exception e2) { yield null; }
                }
            }
            default -> null;
        };
    }

    /**
     * セル値を Long として読み出す。文字列の "1,234" などはカンマ除去してパース。
     * 非数値・空欄は null を返す。
     * <p>SF-C12: NaN/Infinity/long 範囲外の値は null を返してオーバーフロー被害を防ぐ。
     */
    static Long readLongCell(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> safeToLong(cell.getNumericCellValue());
                case STRING -> {
                    String s = cell.getStringCellValue().trim();
                    if (s.isEmpty()) yield null;
                    try { yield Long.parseLong(s.replace(",", "")); }
                    catch (NumberFormatException e) { yield null; }
                }
                case FORMULA -> {
                    try { yield safeToLong(cell.getNumericCellValue()); }
                    catch (Exception e) { yield null; }
                }
                default -> null;
            };
        } catch (Exception e) { return null; }
    }

    /**
     * double → long 変換時の NaN/Infinity/オーバーフローガード (SF-C12)。
     * Excel の壊れたセルや異常値で長整数オーバーフローが起きないようにする。
     */
    private static Long safeToLong(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return null;
        if (d > Long.MAX_VALUE || d < Long.MIN_VALUE) return null;
        return Math.round(d);
    }

    /**
     * セル値を LocalDate として読み出す。日付書式セル → シリアル値 → null の順でフォールバック。
     */
    static LocalDate readDateCell(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                double d = cell.getNumericCellValue();
                // Excelの日付シリアル値範囲で date formatted が効かないケースのフォールバック
                if (d > 40000 && d < 100000) {
                    return DateUtil.getLocalDateTime(d).toLocalDate();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
