package jp.co.oda32.constant;

public final class Constants {
    // 手打ち商品コードの固定値
    public static final String FIXED_PRODUCT_CODE = "99999999";
    // 手打ち得意先コードの固定値
    public static final String FIXED_PARTNER_CODE = "99999999";
    // 送料の商品コードの固定値
    public static final String SHIPPING_FEE_PRODUCT_CODE = "00000015";
    // コンストラクタをプライベートにしてインスタンス化を防止
    private Constants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
