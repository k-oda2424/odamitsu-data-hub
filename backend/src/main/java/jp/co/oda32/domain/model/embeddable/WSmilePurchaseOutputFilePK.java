package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class WSmilePurchaseOutputFilePK implements Serializable {
    private Long shoriRenban;     // 処理連番
    private Integer gyou;         // 行
    private Integer shopNo;       // ショップ番号
    private Integer meisaikubun;  // 明細区分
}
