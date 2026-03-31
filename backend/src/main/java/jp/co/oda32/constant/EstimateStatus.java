package jp.co.oda32.constant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 見積ステータス
 *
 * @author k_oda
 * @since 2022/10/24
 */
public enum EstimateStatus {
    CREATE("00", "作成"),
    NOTIFIED("10", "提出済"),
    MODIFIED("20", "修正"),
    M_NOTIFIED("30", "修正後提出済"),
    OTHER_PARTNER_NOTIFIED("40", "他同グループ提出済"),
    DELETE("50", "削除"),
    EACH_TIME("60", "都度見積のため不要"),
    PRICE_REFLECT("70", "価格反映済"),
    BID("90", "入札関係のため不要"),
    NOT_DEAL("99", "取引なし");

    private String code;
    private String value;

    EstimateStatus(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static List<String> getBeforeNotifiedStatusCodeList() {
        return Arrays.asList(CREATE.getCode(), MODIFIED.getCode());
    }

    public static List<String> getNotifiedStatusCodeList() {
        return Arrays.asList(NOTIFIED.getCode(), M_NOTIFIED.getCode(), OTHER_PARTNER_NOTIFIED.getCode());
    }

    public static Map<String, String> toMap() {
        return Arrays.stream(EstimateStatus.class.getEnumConstants())
                .collect(Collectors.toMap(EstimateStatus::getCode, EstimateStatus::getValue, (a, b) -> b, TreeMap::new));
    }

    public static EstimateStatus purse(String key) {
        for (EstimateStatus returnStatus : values()) {
            if (returnStatus.getCode().equals(key)) {
                return returnStatus;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }

    public String getCode() {
        return this.code;
    }
}
