package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 注文明細テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2018/11/26
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TOrderDetailPK implements Serializable {
    @Column(name = "order_no")
    private Integer orderNo;
    @Column(name = "order_detail_no")
    private Integer orderDetailNo;
}
