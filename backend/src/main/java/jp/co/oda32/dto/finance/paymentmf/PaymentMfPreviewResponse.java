package jp.co.oda32.dto.finance.paymentmf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMfPreviewResponse {
    private String uploadId;
    private String fileName;
    private LocalDate transferDate;
    private LocalDate transactionMonth;

    private int totalRows;
    private long totalAmount;
    private int matchedCount;
    private int diffCount;
    private int unmatchedCount;
    private int errorCount;

    private List<PaymentMfPreviewRow> rows;
    private List<String> unregisteredSources;
}
