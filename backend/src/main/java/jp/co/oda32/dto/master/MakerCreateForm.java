package jp.co.oda32.dto.master;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * メーカー登録DTO
 *
 * @author k_oda
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakerCreateForm {
    private Integer makerNo;
    @NotBlank
    private String makerName;
}
