package jp.co.oda32.dto.finance.paymentmf;

import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMfHistoryResponse {
    private Integer id;
    private Integer shopNo;
    private LocalDate transferDate;
    private String sourceFilename;
    private String csvFilename;
    private Integer rowCount;
    private Long totalAmount;
    private Integer matchedCount;
    private Integer diffCount;
    private Integer unmatchedCount;
    private LocalDateTime addDateTime;

    public static PaymentMfHistoryResponse from(TPaymentMfImportHistory h) {
        return PaymentMfHistoryResponse.builder()
                .id(h.getId())
                .shopNo(h.getShopNo())
                .transferDate(h.getTransferDate())
                .sourceFilename(h.getSourceFilename())
                .csvFilename(h.getCsvFilename())
                .rowCount(h.getRowCount())
                .totalAmount(h.getTotalAmount())
                .matchedCount(h.getMatchedCount())
                .diffCount(h.getDiffCount())
                .unmatchedCount(h.getUnmatchedCount())
                .addDateTime(h.getAddDateTime())
                .build();
    }
}
