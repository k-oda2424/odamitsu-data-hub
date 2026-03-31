package jp.co.oda32.domain.model.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * マネーフォワード補助科目マスタテーブルのEntityクラス
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "m_mf_sub_account", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"partner_code", "sub_account_name"})
})
public class MMfSubAccount {
    @Id
    @Column(name = "partner_no")
    private Long partnerNo;

    @Column(name = "sub_account_name", nullable = false)
    private String subAccountName;

    @Column(name = "partner_code")
    private String partnerCode;
}
