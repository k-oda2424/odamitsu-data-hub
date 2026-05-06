package jp.co.oda32.dto.finance;

import lombok.Data;

import java.time.LocalDate;

/**
 * 請求 (TInvoice) の入金日更新リクエスト DTO。
 *
 * <p>{@code paymentDate} は <strong>null 許容</strong>。
 * <ul>
 *   <li>非 null: 当該日付で入金日を更新</li>
 *   <li>null: 入金日をクリア (DB は payment_date=NULL)</li>
 * </ul>
 *
 * <p>SF-06 (Critical, code C-N2) で {@code @NotNull} を撤去。
 * 旧仕様では空文字を送ると 400 が返り「入金日のクリア」操作が UI から不能だった。
 *
 * @since 2026-05-04 (SF-06 改修)
 */
@Data
public class PaymentDateUpdateRequest {
    private LocalDate paymentDate;
}
