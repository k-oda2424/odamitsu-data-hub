package jp.co.oda32.constant;

import lombok.Getter;

/**
 * B-CART 受注対応状況
 * status はそのまま B-CART API に送信される文字列値。
 * "カスタム1" 等は B-CART 管理画面で表示名を変更可能。
 */
@Getter
public enum BCartOrderStatus {
    NEW_ORDER("新規注文"),
    PROCESSING("カスタム1"),
    CANCELED("カスタム2"),
    COMPLETED("完了");

    private final String status;

    BCartOrderStatus(String status) {
        this.status = status;
    }
}
