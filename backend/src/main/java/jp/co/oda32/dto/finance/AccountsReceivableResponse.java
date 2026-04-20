package jp.co.oda32.dto.finance;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.master.MPartner;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 売掛金一覧画面の一行DTO。
 * 買掛側 {@link AccountsPayableResponse} と対称。
 */
@Data
@Builder
public class AccountsReceivableResponse {
    private Integer shopNo;
    private Integer partnerNo;
    private String partnerCode;
    private String partnerName;
    private LocalDate transactionMonth;
    private BigDecimal taxRate;

    /**
     * 大竹市ゴミ袋フラグ。
     * <p>{@code @JsonProperty("isOtakeGarbageBag")} は Lombok 生成の {@code isOtakeGarbageBag()} getter を
     * Jackson がデフォルトで {@code "otakeGarbageBag"} にマッピングしてしまう問題への対策。
     * フロントエンドは {@code isOtakeGarbageBag} として読むため明示指定する。
     */
    @JsonProperty("isOtakeGarbageBag")
    private boolean isOtakeGarbageBag;

    private Integer cutoffDate;
    private Integer orderNo;

    private BigDecimal taxIncludedAmount;
    private BigDecimal taxExcludedAmount;
    private BigDecimal taxIncludedAmountChange;
    private BigDecimal taxExcludedAmountChange;

    private BigDecimal invoiceAmount;
    private BigDecimal verificationDifference;
    private Integer invoiceNo;

    private Integer verificationResult;
    private Boolean mfExportEnabled;
    private Boolean verifiedManually;
    private String verificationNote;

    public static AccountsReceivableResponse from(TAccountsReceivableSummary ar, MPartner partner) {
        return AccountsReceivableResponse.builder()
                .shopNo(ar.getShopNo())
                .partnerNo(ar.getPartnerNo())
                .partnerCode(ar.getPartnerCode())
                .partnerName(partner != null ? partner.getPartnerName() : null)
                .transactionMonth(ar.getTransactionMonth())
                .taxRate(ar.getTaxRate())
                .isOtakeGarbageBag(ar.isOtakeGarbageBag())
                .cutoffDate(ar.getCutoffDate())
                .orderNo(ar.getOrderNo())
                .taxIncludedAmount(ar.getTaxIncludedAmount())
                .taxExcludedAmount(ar.getTaxExcludedAmount())
                .taxIncludedAmountChange(ar.getTaxIncludedAmountChange())
                .taxExcludedAmountChange(ar.getTaxExcludedAmountChange())
                .invoiceAmount(ar.getInvoiceAmount())
                .verificationDifference(ar.getVerificationDifference())
                .invoiceNo(ar.getInvoiceNo())
                .verificationResult(ar.getVerificationResult())
                .mfExportEnabled(ar.getMfExportEnabled())
                .verifiedManually(ar.getVerifiedManually())
                .verificationNote(ar.getVerificationNote())
                .build();
    }

    public static AccountsReceivableResponse from(TAccountsReceivableSummary ar) {
        return from(ar, null);
    }
}
