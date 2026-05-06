package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 請求 (TInvoice) の入金日一括更新リクエスト DTO。
 *
 * <p>{@code paymentDate} は <strong>null 許容</strong> (SF-06)。
 * null 指定時は対象全行の入金日をクリアする。
 *
 * <p>SF-16: {@code invoiceIds} に {@code @Size(max = 2000)} で件数上限を設定。
 *
 * @since 2026-05-04 (SF-06 / SF-16 改修)
 */
@Data
public class BulkPaymentDateRequest {
    @NotNull(message = "請求IDは必須です")
    @NotEmpty(message = "請求IDを1件以上指定してください")
    @Size(max = 2000, message = "一括反映は2000件以下にしてください")
    private List<Integer> invoiceIds;

    /** 入金日 (null = 一括クリア)。 */
    private LocalDate paymentDate;
}
