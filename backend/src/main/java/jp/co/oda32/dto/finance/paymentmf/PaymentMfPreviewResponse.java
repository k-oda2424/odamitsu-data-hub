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

    /**
     * PAYABLE ルールが sourceName マッチはしたが {@code payment_supplier_code} が未設定のため、
     * 検証済みCSV出力時に CSV から除外される行の送り先リスト。
     * 一括検証前に「支払先コード自動補完」でマスタを整備するとこのリストが空になる。
     */
    private List<String> rulesMissingSupplierCode;

    /** 振込金額整合性チェック結果 (一致/不一致 + 差額)。 */
    private AmountReconciliation amountReconciliation;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AmountReconciliation {
        // --- チェック1: Excel 合計行の列間整合 (C - F - H = E) ---
        /** 合計行 C列（請求額合計）。 */
        private long summaryInvoiceTotal;
        /** 合計行 F列（振込手数料値引 = 送料相手合計）。 */
        private long summaryFee;
        /** 合計行 H列（早払収益 = 早払い合計）。 */
        private long summaryEarly;
        /** 合計行 E列（振込金額合計）。 */
        private long summaryTransferAmount;
        /** 期待振込金額 = C - F - H。 */
        private long expectedTransferAmount;
        /** チェック1 差額 = E - (C - F - H)。0 なら整合。 */
        private long excelDifference;
        /** チェック1 OK = excelDifference == 0 */
        private boolean excelMatched;

        // --- チェック2: 明細行 読取り整合 (sum明細 = C合計行) ---
        /** 合計行前の明細行 請求額合計（PAYABLE + EXPENSE のみ）。 */
        private long preTotalInvoiceSum;
        /** チェック2 差額 = preTotalInvoiceSum - summaryInvoiceTotal。0 なら全明細正しく読取れている。 */
        private long readDifference;
        /** チェック2 OK = readDifference == 0 */
        private boolean readMatched;

        /** DIRECT_PURCHASE / 別振込 の請求額合計（合計行後セクション。参考）。 */
        private long directPurchaseTotal;
    }
}
