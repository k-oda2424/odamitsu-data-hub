package jp.co.oda32.dto.master;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MakerCreateRequest {
    @NotBlank(message = "メーカー名は必須です")
    private String makerName;
}
