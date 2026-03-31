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
 * 見積商品ViewのPKカラム設定(実際はPKではない) Entityを作るうえで必要なため
 *
 * @author k_oda
 * @since 2022/10/28
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class VEstimateGoodsPK implements Serializable {
    @Id
    @Column(name = "shop_no")
    protected Integer shopNo;
    @Id
    @Column(name = "goods_no")
    protected Integer goodsNo;
}
