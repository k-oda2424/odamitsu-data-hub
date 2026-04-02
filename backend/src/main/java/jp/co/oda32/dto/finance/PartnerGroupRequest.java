package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PartnerGroupRequest {
    @NotBlank(message = "グループ名は必須です")
    private String groupName;

    @NotNull(message = "店舗番号は必須です")
    private Integer shopNo;

    @NotNull(message = "得意先コードは必須です")
    @Size(min = 1, message = "得意先コードを1件以上指定してください")
    private List<String> partnerCodes;
}
