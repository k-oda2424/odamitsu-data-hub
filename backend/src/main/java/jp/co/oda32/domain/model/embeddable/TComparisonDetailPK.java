package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TComparisonDetailPK implements Serializable {
    @Column(name = "comparison_no")
    private Integer comparisonNo;
    @Column(name = "group_no")
    private Integer groupNo;
    @Column(name = "detail_no")
    private Integer detailNo;
}
