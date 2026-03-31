package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 返品明細のPKカラム設定
 *
 * @author k_oda
 * @since 2017/09/25
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class ReturnDetailPK implements Serializable {
    @Column(name = "return_no")
    private Integer returnNo;
    @Column(name = "return_detail_no")
    private Integer returnDetailNo;
}
