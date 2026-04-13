package jp.co.oda32.dto.finance.cashbook;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfClientMappingRequest {
    @NotBlank private String alias;
    @NotBlank private String mfClientName;
}
