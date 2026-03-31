package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Id;
import java.io.Serializable;

/**
 * 特値見積商品ViewのPKカラム設定(実際はPKではない) Entityを作るうえで必要なため
 *
 * @author k_oda
 * @since 2022/12/21
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class VEstimateGoodsSpecialPK implements Serializable {
    @Id
    @Column(name = "shop_no")
    protected Integer shopNo;
    @Id
    @Column(name = "goods_no")
    protected Integer goodsNo;
    @Id
    @Column(name = "partner_no")
    protected Integer partnerNo;
    @Id
    @Column(name = "destination_no")
    protected Integer destinationNo;
}
