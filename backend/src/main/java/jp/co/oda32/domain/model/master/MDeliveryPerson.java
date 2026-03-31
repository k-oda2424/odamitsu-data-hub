package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.embeddable.MDeliveryPersonPK;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "m_delivery_person")
@IdClass(MDeliveryPersonPK.class)
public class MDeliveryPerson {
    @Id
    @Column(name = "partner_code")
    private String partnerCode;
    @Id
    @Column(name = "delivery_code")
    private String deliveryCode;

    // m_asanaのuser_idと一緒
    @Column(name = "user_id")
    private BigDecimal userId;
}
