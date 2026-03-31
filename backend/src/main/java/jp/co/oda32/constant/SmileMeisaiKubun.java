package jp.co.oda32.constant;

/**
 * Smileの明細区分
 *
 * @author k_oda
 * @since 2024/05/08
 */
public enum SmileMeisaiKubun {
    // 0：売上行、1：消費税行
    ORDERED("0"),
    TAX("1");

    private String value;

    SmileMeisaiKubun(String value) {
        this.value = value;
    }

    public static SmileMeisaiKubun purse(String key) {
        for (SmileMeisaiKubun smileMeisaiKubun : values()) {
            if (smileMeisaiKubun.getValue().equals(key)) {
                return smileMeisaiKubun;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }
}
