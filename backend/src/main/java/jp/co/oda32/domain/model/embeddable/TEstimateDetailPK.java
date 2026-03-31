package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 見積明細テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2022/10/24
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TEstimateDetailPK implements Serializable {
    @Column(name = "estimate_no")
    private Integer estimateNo;
    @Column(name = "estimate_detail_no")
    private Integer estimateDetailNo;
}
