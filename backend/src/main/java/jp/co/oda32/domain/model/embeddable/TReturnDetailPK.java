package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 返品明細テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2018/11/26
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TReturnDetailPK implements Serializable {
    @Column(name = "return_no")
    private Integer returnNo;
    @Column(name = "return_detail_no")
    private Integer returnDetailNo;
}
