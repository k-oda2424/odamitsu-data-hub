package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * コードマスタのPKカラム設定
 *
 * @author k_oda
 * @since 2017/05/25
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class CodePK implements Serializable {
    @Column(name = "code_no")
    private Integer codeNo;
    @Column(name = "code_detail_no")
    private Integer codeDetailNo;
}
