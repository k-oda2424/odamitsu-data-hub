package jp.co.oda32.dto.bcart;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
public class BCartProductResponse {
    private Integer id;
    private String mainNo;
    private String name;
    private String catchCopy;
    private Integer categoryId;
    private String categoryName;
    private String subCategoryId;
    private String description;
    private String size;
    private String sozai;
    private String caution;
    private String tag;
    private String metaTitle;
    private String metaKeywords;
    private String metaDescription;
    private String image;
    private String prependText;
    private String appendText;
    private String middleText;
    private String rvPrependText;
    private String rvAppendText;
    private String rvMiddleText;
    private String flag;
    private Integer priority;
    private Date updatedAt;
    private int setCount;
    private List<ProductSetSummary> sets;

    @Data
    @Builder
    public static class ProductSetSummary {
        private Long id;
        private String productNo;
        private String janCode;
        private String name;
        private BigDecimal unitPrice;
        private BigDecimal purchasePrice;
        private BigDecimal shippingSize;
        private Integer stock;
        private String setFlag;
        @JsonProperty("bCartPriceReflected")
        private boolean bCartPriceReflected;
    }

    public static BCartProductResponse from(BCartProducts entity) {
        return from(entity, null);
    }

    public static BCartProductResponse from(BCartProducts entity, String categoryName) {
        BCartProductResponseBuilder builder = BCartProductResponse.builder()
                .id(entity.getId())
                .mainNo(entity.getMainNo())
                .name(entity.getName())
                .catchCopy(entity.getCatchCopy())
                .categoryId(entity.getCategoryId())
                .categoryName(categoryName)
                .subCategoryId(entity.getSubCategoryId())
                .description(entity.getDescription())
                .size(entity.getSize())
                .sozai(entity.getSozai())
                .caution(entity.getCaution())
                .tag(entity.getTag())
                .metaTitle(entity.getMetaTitle())
                .metaKeywords(entity.getMetaKeywords())
                .metaDescription(entity.getMetaDescription())
                .image(entity.getImage())
                .prependText(entity.getPrependText())
                .appendText(entity.getAppendText())
                .middleText(entity.getMiddleText())
                .rvPrependText(entity.getRvPrependText())
                .rvAppendText(entity.getRvAppendText())
                .rvMiddleText(entity.getRvMiddleText())
                .flag(entity.getFlag())
                .priority(entity.getPriority())
                .updatedAt(entity.getUpdatedAt());

        List<BCartProductSets> sets = entity.getBCartProductSets();
        if (sets != null) {
            builder.setCount(sets.size());
            builder.sets(sets.stream().map(s -> ProductSetSummary.builder()
                    .id(s.getId())
                    .productNo(s.getProductNo())
                    .janCode(s.getJanCode())
                    .name(s.getName())
                    .unitPrice(s.getUnitPrice())
                    .purchasePrice(s.getPurchasePrice())
                    .shippingSize(s.getShippingSize())
                    .stock(s.getStock())
                    .setFlag(s.getSetFlag())
                    .bCartPriceReflected(s.isBCartPriceReflected())
                    .build()).toList());
        } else {
            builder.setCount(0);
        }

        return builder.build();
    }
}
