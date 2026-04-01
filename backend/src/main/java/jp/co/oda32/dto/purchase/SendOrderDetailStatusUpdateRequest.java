package jp.co.oda32.dto.purchase;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SendOrderDetailStatusUpdateRequest {
    @NotBlank(message = "ステータスは必須です")
    private String sendOrderDetailStatus;
    private LocalDate arrivePlanDate;
    private LocalDate arrivedDate;
    private BigDecimal arrivedNum;
}
