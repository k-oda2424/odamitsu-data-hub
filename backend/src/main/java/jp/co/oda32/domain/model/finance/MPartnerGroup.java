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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "m_partner_group_member",
            joinColumns = @JoinColumn(name = "partner_group_id")
    )
    @Column(name = "partner_code")
    @Builder.Default
    private List<String> partnerCodes = new ArrayList<>();
}
