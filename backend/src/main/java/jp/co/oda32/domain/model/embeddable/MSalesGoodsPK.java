package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 販売商品テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2018/07/20
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class MSalesGoodsPK implements Serializable {
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
}
