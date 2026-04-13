package jp.co.oda32.domain.service.finance;

/**
 * MoneyForward税区分リゾルバ。
 * Python版CashBookToMoneyForwardConverterの税区分判定ロジックを抽象化。
 */
public final class MfTaxResolver {

    private MfTaxResolver() {}

    /**
     * resolverコードと摘要Dから税区分文字列を決定する。
     */
    public static String resolve(String resolverCode, String descriptionD) {
        if (resolverCode == null) return "";
        String d = descriptionD == null ? "" : descriptionD;
        return switch (resolverCode) {
            case "OUTSIDE" -> "対象外";
            case "OUTSIDE_PURCHASE_FULL" -> "対象外仕入";
            case "OUTSIDE_PURCHASE_SHORT" -> "対象外仕";
            case "SALES_10" -> "課税売上 10%";
            case "PURCHASE_10" -> "課税仕入 10%";
            case "PURCHASE_10_TRAVEL" -> "課仕 10%";
            case "SALES_AUTO" -> d.contains("軽8%") ? "課売 (軽)8%" : "課売 10%";
            case "PURCHASE_AUTO" -> d.contains("軽8%") ? "課税仕入 (軽)8%" : "課税仕入 10%";
            case "PURCHASE_AUTO_WIDE" -> (d.contains("軽8%") || d.contains("軽８％"))
                    ? "課税仕入 (軽)8%" : "課税仕入 10%";
            default -> throw new IllegalArgumentException("未知の税区分リゾルバ: " + resolverCode);
        };
    }
}
