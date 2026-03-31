package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 仕入明細テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2019/06/02
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TPurchaseDetailPK implements Serializable {
    @Column(name = "purchase_no")
    private Integer purchaseNo;
    @Column(name = "purchase_detail_no")
    private Integer purchaseDetailNo;
}
