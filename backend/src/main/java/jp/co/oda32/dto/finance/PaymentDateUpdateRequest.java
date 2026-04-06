package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PaymentDateUpdateRequest {
    @NotNull(message = "入金日は必須です")
    private LocalDate paymentDate;
}
