package jp.co.oda32.dto.bcart;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BCartProductDescriptionUpdateRequest {
    @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String catchCopy;
    @Size(max = 65535)
    private String description;
    @Size(max = 65535)
    private String prependText;
    @Size(max = 65535)
    private String appendText;
    @Size(max = 65535)
    private String middleText;
    @Size(max = 65535)
    private String rvPrependText;
    @Size(max = 65535)
    private String rvAppendText;
    @Size(max = 65535)
    private String rvMiddleText;
    @Size(max = 255)
    private String metaTitle;
    @Size(max = 255)
    private String metaKeywords;
    @Size(max = 255)
    private String metaDescription;
}
