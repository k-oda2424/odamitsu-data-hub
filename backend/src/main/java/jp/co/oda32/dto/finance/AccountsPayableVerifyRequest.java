package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountsPayableVerifyRequest {
    @NotNull
    private BigDecimal verifiedAmount;

    @Size(max = 500)
    private String note;
}
