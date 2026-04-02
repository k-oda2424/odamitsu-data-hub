package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class BulkPaymentDateRequest {
    @NotNull(message = "請求IDは必須です")
    @NotEmpty(message = "請求IDを1件以上指定してください")
    private List<Integer> invoiceIds;

    @NotNull(message = "入金日は必須です")
    private LocalDate paymentDate;
}
