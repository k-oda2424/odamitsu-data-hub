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

    /**
     * P1-08 L1: 同一 SHA-256 ハッシュの Excel が過去に取込済の場合の警告。
     * null = 重複なし (初回取込)。
     */
    private DuplicateWarning duplicateWarning;

    /**
     * P1-08 L2: 同 (shop, transferDate) で applyVerification 実行済の場合の警告。
     * null = 未確定 (初回確定)。
     */
    private AppliedWarning appliedWarning;

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

        // --- P1-03 案 D チェック3: per-supplier 振込金額合計 == E(合計行 振込金額) ---
        /** per-supplier の振込金額合計 (= 請求額 - 送料相手 - 値引 - 早払 - 相殺 の合計)。 */
        private long perSupplierTransferSum;
        /** チェック3 差額 = perSupplierTransferSum - summaryTransferAmount。0 なら整合。 */
        private long perSupplierTransferDiff;
        /** チェック3 OK = perSupplierTransferDiff == 0 */
        private boolean perSupplierTransferMatched;
        /** per-supplier 送料相手合計 (参考: summaryFee と一致するべき)。 */
        private long perSupplierFeeSum;
        /** per-supplier 値引合計。 */
        private long perSupplierDiscountSum;
        /** per-supplier 早払合計 (参考: summaryEarly と一致するべき)。 */
        private long perSupplierEarlySum;
        /** per-supplier 相殺合計。 */
        private long perSupplierOffsetSum;
        /**
         * per-supplier の 1 円整合性 (請求 = 振込 + 控除合計) に違反した行のメッセージ一覧。
         * Excel 入力ミス検知用。空なら全行 OK。
         */
        @lombok.Builder.Default
        private List<String> perSupplierMismatches = java.util.Collections.emptyList();
    }
}
