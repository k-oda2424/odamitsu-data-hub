package jp.co.oda32.dto.bcart;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.oda32.domain.model.bcart.BCartCategories;
import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
@Builder
public class BCartCategoryResponse {
    private Integer id;
    private String name;
    private String description;
    private String rvDescription;
    private Integer parentCategoryId;
    private String parentCategoryName;
    private String headerImage;
    private String bannerImage;
    private String menuImage;
    private String metaTitle;
    private String metaKeywords;
    private String metaDescription;
    private Integer priority;
    private Integer flag;
    @JsonProperty("bCartReflected")
    private boolean bCartReflected;
    private Integer version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<BCartCategoryResponse> children;
    private Long productCount;

    public static BCartCategoryResponse from(BCartCategories entity) {
        return BCartCategoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .rvDescription(entity.getRvDescription())
                .parentCategoryId(entity.getParentCategoryId())
                .headerImage(entity.getHeaderImage())
                .bannerImage(entity.getBannerImage())
                .menuImage(entity.getMenuImage())
                .metaTitle(entity.getMetaTitle())
                .metaKeywords(entity.getMetaKeywords())
                .metaDescription(entity.getMetaDescription())
                .priority(entity.getPriority())
                .flag(entity.getFlag())
                .bCartReflected(entity.isBCartReflected())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
