package jp.co.oda32.constant;

import lombok.Getter;

/**
 * Smile連携用「取引区分」定義
 *
 * @author k_oda
 * @since 2023/04/10
 */
@Getter
public enum SmileTransactionType {
    NORMAL(1, "掛売上");

    private final int code;
    private final String description;

    SmileTransactionType(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
