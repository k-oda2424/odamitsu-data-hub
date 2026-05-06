package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MfEnumTranslationRequest {

    @NotBlank
    @Pattern(regexp = "FINANCIAL_STATEMENT|CATEGORY")
    private String enumKind;

    @NotBlank
    private String englishCode;

    @NotBlank
    private String japaneseName;
}
