package jp.co.oda32.dto.bcart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BCartCategoryUpdateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;
    private String rvDescription;
    private Integer parentCategoryId;
    @Size(max = 255)
    private String metaTitle;
    @Size(max = 500)
    private String metaKeywords;
    @Size(max = 500)
    private String metaDescription;
    @NotNull
    private Integer priority;
    @NotNull
    private Integer flag;
    @NotNull
    private Integer version;
}
