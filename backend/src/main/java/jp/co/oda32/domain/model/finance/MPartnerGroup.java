package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_partner_group")
public class MPartnerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partner_group_id")
    private Integer partnerGroupId;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    /**
     * 論理削除フラグ ('0'=有効, '1'=削除)。
     * SF-20: 物理削除を論理削除化するため V031 で追加。
     */
    @Column(name = "del_flg", nullable = false, length = 1)
    @Builder.Default
    private String delFlg = "0";

    /**
     * 所属パートナーコード。
     * SF-04 + SF-19: dedup と LAZY 化対応。
     * 取得時は {@link jp.co.oda32.domain.repository.finance.MPartnerGroupRepository}
     * の JOIN FETCH 経由で N+1 を回避すること。
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "m_partner_group_member",
            joinColumns = @JoinColumn(name = "partner_group_id")
    )
    @Column(name = "partner_code")
    @Builder.Default
    private List<String> partnerCodes = new ArrayList<>();
}
