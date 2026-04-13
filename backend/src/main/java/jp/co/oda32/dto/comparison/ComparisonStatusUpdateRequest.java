package jp.co.oda32.dto.comparison;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ComparisonStatusUpdateRequest {
    @NotBlank(message = "ステータスは必須です")
    @Pattern(regexp = "^(00|10|20|30|40|50|60|70|90|99)$", message = "不正なステータスです")
    private String comparisonStatus;
}
