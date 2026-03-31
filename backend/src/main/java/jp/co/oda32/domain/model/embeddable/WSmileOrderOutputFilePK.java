package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * WSmileOrderOutputFileのPK
 *
 * @author k_oda
 * @since 2024/05/08
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class WSmileOrderOutputFilePK implements Serializable {
    private Long shoriRenban;  // 処理連番
    private Integer gyou;      // 行
    private Integer shopNo; // 旧松山事業所と伝票番号が重複するので、切り分けに使用
}
