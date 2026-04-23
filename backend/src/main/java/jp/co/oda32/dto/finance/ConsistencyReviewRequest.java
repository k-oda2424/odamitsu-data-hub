package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 整合性レポート 差分確認 Request DTO。
 *
 * @since 2026-04-23
 */
@Data
public class ConsistencyReviewRequest {

    @NotNull
    private Integer shopNo;

    /** mfOnly | selfOnly | amountMismatch */
    @NotBlank
    private String entryType;

    /** selfOnly/amountMismatch: supplier_no 文字列, mfOnly: guessedSupplierNo or sub_account_name */
    @NotBlank
    private String entryKey;

    @NotNull
    private LocalDate transactionMonth;

    /** IGNORE | MF_APPLY */
    @NotBlank
    private String actionType;

    private BigDecimal selfSnapshot;
    private BigDecimal mfSnapshot;

    @Size(max = 500)
    private String note;
}
