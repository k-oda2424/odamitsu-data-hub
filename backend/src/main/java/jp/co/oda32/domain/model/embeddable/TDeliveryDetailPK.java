package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 出荷明細テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2018/11/26
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TDeliveryDetailPK implements Serializable {
    @Column(name = "delivery_no")
    private Integer deliveryNo;
    @Column(name = "delivery_detail_no")
    private Integer deliveryDetailNo;
}
