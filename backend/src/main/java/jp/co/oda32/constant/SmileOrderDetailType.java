package jp.co.oda32.constant;

import lombok.Getter;

/**
 * Smile連携用「明細区分」定義
 *
 * @author k_oda
 * @since 2023/04/10
 */
@Getter
public enum SmileOrderDetailType {
    NORMAL(0, "通常"),
    CONSUMPTION_TAX(1, "消費税");

    private final int code;
    private final String description;

    SmileOrderDetailType(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
