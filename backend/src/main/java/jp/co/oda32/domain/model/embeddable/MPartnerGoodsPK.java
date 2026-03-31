package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 得意先商品テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class MPartnerGoodsPK implements Serializable {
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "destination_no")
    private Integer destinationNo;
}
