package jp.co.oda32.dto.finance.paymentmf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 検証済みCSV出力ダイアログのプレビュー用 DTO。
 * ダウンロード前の件数確認と、片方 Excel が未取込の警告表示に使う。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VerifiedExportPreviewResponse {
    private LocalDate transactionMonth;
    private int payableCount;
    private long payableTotalAmount;
    private List<AuxBreakdownItem> auxBreakdown;
    private List<String> warnings;
    private List<String> skippedSuppliers;  // PAYABLE でルール未登録 (CSV から除外される)

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuxBreakdownItem {
        private LocalDate transferDate;
        private String ruleKind;          // EXPENSE / SUMMARY / DIRECT_PURCHASE
        private int count;
        private long totalAmount;
    }
}
