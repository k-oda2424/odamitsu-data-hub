package jp.co.oda32.dto.master;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartnerCreateRequest {
    @NotBlank(message = "得意先コードは必須です")
    private String partnerCode;

    @NotBlank(message = "得意先名は必須です")
    private String partnerName;

    @NotNull(message = "店舗Noは必須です")
    private Integer shopNo;

    private String abbreviatedPartnerName;
    private String note;
}
