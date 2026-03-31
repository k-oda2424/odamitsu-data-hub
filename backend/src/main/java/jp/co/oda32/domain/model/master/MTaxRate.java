package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * 消費税率マスタEntity
 *
 * @author k_oda
 * @since 2022/12/01
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_tax_rate")
public class MTaxRate implements IEntity {
    @Id
    @Column(name = "tax_rate_no")
    @SequenceGenerator(name = "m_tax_rate_tax_rate_no_seq_gen", sequenceName = "m_tax_rate_tax_rate_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_tax_rate_tax_rate_no_seq_gen")
    private Integer taxRateNo;
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    @Column(name = "reduced_tax_rate")
    private BigDecimal reducedTaxRate;
    @Column(name = "period_from")
    private LocalDate periodFrom;
    @Column(name = "period_to")
    private LocalDate periodTo;
    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    @Override
    public Integer getShopNo() {
        return null;
    }
}
