package jp.co.oda32.domain.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * m_supplier_opening_balance の複合主キー。
 *
 * @since 2026-04-24
 */
@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class MSupplierOpeningBalancePK implements Serializable {

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(name = "supplier_no", nullable = false)
    private Integer supplierNo;
}
