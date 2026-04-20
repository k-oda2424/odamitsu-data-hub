package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 売掛金の手動確定リクエスト。買掛側 {@link AccountsPayableVerifyRequest} と対称。
 */
@Data
public class AccountsReceivableVerifyRequest {
    @NotNull
    private BigDecimal taxIncludedAmount;

    @NotNull
    private BigDecimal taxExcludedAmount;

    @Size(max = 500)
    private String note;

    /** 手動確定時に MF 出力を有効にするか（default true） */
    private Boolean mfExportEnabled = Boolean.TRUE;
}
