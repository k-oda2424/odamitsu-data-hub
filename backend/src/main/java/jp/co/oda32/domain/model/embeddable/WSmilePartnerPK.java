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
 * @since 2024/06/11
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class WSmilePartnerPK implements Serializable {
    private String 得意先コード;
    @Column(name = "shop_no")
    private Integer shopNo;
}
