package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 買掛金の複合主キー
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TAccountsPayableSummaryPK implements Serializable {
    private Integer shopNo;
    private Integer supplierNo;
    private LocalDate transactionMonth;
    private BigDecimal taxRate;
}
