package jp.co.oda32.constant;

import lombok.Getter;

/**
 * 支払タイプを表す列挙型
 *
 * @author k
 * @since 2024/04/29
 */
@Getter
public enum PaymentType {

    /**
     * 月末締め (cutoffDate = 0 または null)
     */
    MONTH_END(0, "月末締め"),

    /**
     * 15日締め (cutoffDate = 15)
     */
    DAY_15(15, "15日締め"),

    /**
     * 20日締め (cutoffDate = 20)
     */
    DAY_20(20, "20日締め"),

    /**
     * 都度現金払い (cutoffDate = -1)
     */
    CASH_ON_DELIVERY(-1, "都度現金払い");

    /**
     * 締め日コード
     */
    private final int cutoffCode;

    /**
     * 説明
     */
    private final String description;

    /**
     * コンストラクタ
     *
     * @param cutoffCode  締め日コード
     * @param description 説明
     */
    PaymentType(int cutoffCode, String description) {
        this.cutoffCode = cutoffCode;
        this.description = description;
    }

    /**
     * 締め日コードからPaymentTypeを取得します
     *
     * @param cutoffCode 締め日コード
     * @return PaymentType
     */
    public static PaymentType fromCutoffCode(Integer cutoffCode) {
        if (cutoffCode == null) {
            return MONTH_END;
        }

        for (PaymentType type : values()) {
            if (type.getCutoffCode() == cutoffCode) {
                return type;
            }
        }

        // 特殊な値でなければ、値に応じて判断
        if (cutoffCode == 15) {
            return DAY_15;
        } else if (cutoffCode == 20) {
            return DAY_20;
        } else if (cutoffCode >= 28 || cutoffCode == 0) {
            return MONTH_END;
        }

        // その他の場合はデフォルトとして月末締めを返す
        return MONTH_END;
    }

    /**
     * 都度現金払いかどうかを判定します
     *
     * @return 都度現金払いならtrue
     */
    public boolean isCashOnDelivery() {
        return this == CASH_ON_DELIVERY;
    }
}
