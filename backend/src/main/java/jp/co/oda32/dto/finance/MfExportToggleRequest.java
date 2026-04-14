package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MfExportToggleRequest {
    @NotNull
    private Boolean enabled;
}
