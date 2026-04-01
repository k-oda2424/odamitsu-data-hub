package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class QuoteImportHeaderResponse {
    private Integer quoteImportId;
    private Integer shopNo;
    private String supplierName;
    private String supplierCode;
    private Integer supplierNo;
    private String fileName;
    private LocalDate quoteDate;
    private LocalDate effectiveDate;
    private String changeReason;
    private String priceType;
    private Integer totalCount;
    private Integer remainingCount;
    private LocalDateTime addDateTime;

    public static QuoteImportHeaderResponse from(TQuoteImportHeader h, int remainingCount) {
        return QuoteImportHeaderResponse.builder()
                .quoteImportId(h.getQuoteImportId())
                .shopNo(h.getShopNo())
                .supplierName(h.getSupplierName())
                .supplierCode(h.getSupplierCode())
                .supplierNo(h.getSupplierNo())
                .fileName(h.getFileName())
                .quoteDate(h.getQuoteDate())
                .effectiveDate(h.getEffectiveDate())
                .changeReason(h.getChangeReason())
                .priceType(h.getPriceType())
                .totalCount(h.getTotalCount())
                .remainingCount(remainingCount)
                .addDateTime(h.getAddDateTime() != null ? h.getAddDateTime().toLocalDateTime() : null)
                .build();
    }
}
