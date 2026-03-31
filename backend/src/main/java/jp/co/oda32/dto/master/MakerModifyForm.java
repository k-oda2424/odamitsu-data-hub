package jp.co.oda32.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * メーカー更新DTO
 *
 * @author k_oda
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakerModifyForm {
    private Integer makerNo;
    private String makerName;
    private String action;
}
