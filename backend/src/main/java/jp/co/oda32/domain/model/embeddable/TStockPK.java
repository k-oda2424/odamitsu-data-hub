package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 在庫テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2018/11/26
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TStockPK implements Serializable {
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "warehouse_no")
    private Integer warehouseNo;
}
