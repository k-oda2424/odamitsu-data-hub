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
 * 月間売上金額マテリアライズドViewのPKカラム設定
 *
 * @author k_oda
 * @since 2020/03/13
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class VSalesMonthlySummaryPK implements Serializable {
    @Id
    @Column(name = "shop_no")
    protected Integer shopNo;
    @Id
    @Column(name = "month")
    protected String month;
}
