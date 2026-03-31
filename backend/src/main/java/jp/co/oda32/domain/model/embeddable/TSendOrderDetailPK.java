package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 発注明細テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2019/07/24
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TSendOrderDetailPK implements Serializable {
    @Column(name = "send_order_no")
    private Integer sendOrderNo;
    @Column(name = "send_order_detail_no")
    private Integer sendOrderDetailNo;
}
