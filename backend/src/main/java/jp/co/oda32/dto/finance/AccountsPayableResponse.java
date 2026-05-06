package jp.co.oda32.dto.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.service.finance.PayableBalanceCalculator;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AccountsPayableResponse {
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierCode;
    private String supplierName;
    private LocalDate transactionMonth;
    private BigDecimal taxRate;
    private BigDecimal taxIncludedAmount;
    private BigDecimal taxExcludedAmount;
    private BigDecimal taxIncludedAmountChange;
    private BigDecimal taxExcludedAmountChange;
    private BigDecimal paymentDifference;
    private Integer verificationResult;
    private Boolean mfExportEnabled;
    private Boolean verifiedManually;
    private String verificationNote;
    /** 検証時の請求額（振込明細 or 手入力）。 */
    private BigDecimal verifiedAmount;
    /** V026: verified_amount に対応する税抜確定額 (振込明細 Excel 由来)。 */
    private BigDecimal verifiedAmountTaxExcluded;
    /** V026: 振込明細取込時の自動調整額 (= verified_amount − tax_included_amount_change)。0 なら調整なし。 */
    private BigDecimal autoAdjustedAmount;
    /** MF CSV 出力時の送金日 (支払予定日)。Excel 取込時に set される。 */
    private LocalDate mfTransferDate;
    /**
     * 検証経路の区分（UIバッジ表示用）。G2-M1/M10 (V040) で
     * {@code t_accounts_payable_summary.verification_source} 列由来に変更。
     * <ul>
     *   <li>{@code BULK}   — 振込明細Excel での一括検証。
     *       {@link FinanceConstants#VERIFICATION_SOURCE_BULK} 由来。</li>
     *   <li>{@code MANUAL} — 買掛金一覧の詳細ダイアログで手入力検証された行。
     *       {@link FinanceConstants#VERIFICATION_SOURCE_MANUAL} 由来。</li>
     *   <li>{@code MF_APPLY} — 整合性レポートで MF override された行。
     *       {@link FinanceConstants#VERIFICATION_SOURCE_MF_OVERRIDE} 由来。</li>
     *   <li>{@code null}   — verifiedManually=false（SMILE自動検証など）</li>
     * </ul>
     * <p>旧実装は {@code verification_note} 接頭辞文字列で BULK/MANUAL を推定していたが、
     * ユーザが偶然 "振込明細検証 " で始まる note を手入力すると誤判定されるため、
     * V040 で書込経路 enum 列に切替。
     */
    private String verificationSource;

    // ---- 累積残関連 (include=balance 時のみ set。それ以外は null) ----
    // 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.6
    //         claudedocs/design-phase-b-prime-payment-settled.md §5 (Phase B')

    /** 前月末時点の累積残 (税込・符号あり)。include=balance 指定時のみ。 */
    private BigDecimal openingBalanceTaxIncluded;

    /** 前月末時点の累積残 (税抜・符号あり)。include=balance 指定時のみ。 */
    private BigDecimal openingBalanceTaxExcluded;

    /** 当月完了した支払額 (税込、supplier 単位を change 比で按分)。Phase B'。include=balance 指定時のみ。 */
    private BigDecimal paymentSettledTaxIncluded;

    /** 当月完了した支払額 (税抜)。Phase B'。include=balance 指定時のみ。 */
    private BigDecimal paymentSettledTaxExcluded;

    /**
     * 当月末時点の累積残 (税込・符号あり)。Phase B': closing = opening + effectiveChange - payment_settled。
     * effectiveChange は verifiedManually=true なら verifiedAmount、それ以外は taxIncludedAmountChange。
     * include=balance 指定時のみ。
     */
    private BigDecimal closingBalanceTaxIncluded;

    /** 当月末時点の累積残 (税抜・符号あり)。include=balance 指定時のみ。 */
    private BigDecimal closingBalanceTaxExcluded;

    /** payment-only 行フラグ (当月仕入無し、前月支払あり supplier の支払計上行)。UI バッジ表示用。 */
    private Boolean isPaymentOnly;

    public static AccountsPayableResponse from(TAccountsPayableSummary ap, MPaymentSupplier ps) {
        return from(ap, ps, false);
    }

    public static AccountsPayableResponse from(TAccountsPayableSummary ap) {
        return from(ap, null, false);
    }

    /**
     * include=balance 時に opening/closing を含めた Response を生成する。
     * @param includeBalance true の時のみ 4 つの balance フィールドを set
     */
    public static AccountsPayableResponse from(TAccountsPayableSummary ap, MPaymentSupplier ps,
                                                 boolean includeBalance) {
        boolean isManuallyVerified = Boolean.TRUE.equals(ap.getVerifiedManually());
        // G2-M1/M10 (V040): note 接頭辞推定から source 列直接判定に切替。
        // BULK_VERIFICATION → "BULK" / MANUAL_VERIFICATION → "MANUAL" / MF_OVERRIDE → "MF_APPLY" / NULL → null
        String source = null;
        if (isManuallyVerified) {
            String src = ap.getVerificationSource();
            if (FinanceConstants.VERIFICATION_SOURCE_BULK.equals(src)) {
                source = "BULK";
            } else if (FinanceConstants.VERIFICATION_SOURCE_MANUAL.equals(src)) {
                source = "MANUAL";
            } else if (FinanceConstants.VERIFICATION_SOURCE_MF_OVERRIDE.equals(src)) {
                source = "MF_APPLY";
            } else {
                // verification_source が NULL のまま verified_manually=true な行は backfill 漏れ。
                // legacy fallback: note 接頭辞で MANUAL/BULK を推定 (運用初期の暫定挙動)。
                String note = ap.getVerificationNote();
                @SuppressWarnings("deprecation")
                String legacyPrefix = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX;
                source = (note != null && note.startsWith(legacyPrefix)) ? "BULK" : "MANUAL";
            }
        }
        AccountsPayableResponseBuilder b = AccountsPayableResponse.builder()
                .shopNo(ap.getShopNo())
                .supplierNo(ap.getSupplierNo())
                .supplierCode(ps != null ? ps.getPaymentSupplierCode() : ap.getSupplierCode())
                .supplierName(ps != null ? ps.getPaymentSupplierName() : null)
                .transactionMonth(ap.getTransactionMonth())
                .taxRate(ap.getTaxRate())
                .taxIncludedAmount(ap.getTaxIncludedAmount())
                .taxExcludedAmount(ap.getTaxExcludedAmount())
                .taxIncludedAmountChange(ap.getTaxIncludedAmountChange())
                .taxExcludedAmountChange(ap.getTaxExcludedAmountChange())
                .paymentDifference(ap.getPaymentDifference())
                .verificationResult(ap.getVerificationResult())
                .mfExportEnabled(ap.getMfExportEnabled())
                .verifiedManually(isManuallyVerified)
                .verificationNote(ap.getVerificationNote())
                .verifiedAmount(ap.getVerifiedAmount())
                .verifiedAmountTaxExcluded(ap.getVerifiedAmountTaxExcluded())
                .autoAdjustedAmount(ap.getAutoAdjustedAmount())
                .verificationSource(source)
                .mfTransferDate(ap.getMfTransferDate());

        if (includeBalance) {
            // Phase B': PayableBalanceCalculator で closing = opening + effectiveChange - payment_settled
            b.openingBalanceTaxIncluded(nz(ap.getOpeningBalanceTaxIncluded()))
                    .openingBalanceTaxExcluded(nz(ap.getOpeningBalanceTaxExcluded()))
                    .paymentSettledTaxIncluded(nz(ap.getPaymentAmountSettledTaxIncluded()))
                    .paymentSettledTaxExcluded(nz(ap.getPaymentAmountSettledTaxExcluded()))
                    .closingBalanceTaxIncluded(PayableBalanceCalculator.closingTaxIncluded(ap))
                    .closingBalanceTaxExcluded(PayableBalanceCalculator.closingTaxExcluded(ap))
                    .isPaymentOnly(Boolean.TRUE.equals(ap.getIsPaymentOnly()));
        }
        return b.build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
