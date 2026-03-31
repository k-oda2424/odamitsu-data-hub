package jp.co.oda32.domain.model.bcart.products;

import jp.co.oda32.domain.model.bcart.BCartOrderProduct;
import jp.co.oda32.domain.model.embeddable.BCartProductSetCustomPK;
import lombok.*;

import jakarta.persistence.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "b_cart_product_set_custom")
@IdClass(BCartProductSetCustomPK.class)
public class BCartProductSetCustom {

    @Id
    @Column(name = "id")
    private Long id;

    @Id
    @Column(name = "product_set_id")
    private Long productSetId;

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "value")
    private String value;

    @ManyToOne
    @JoinColumn(name = "id", insertable = false, updatable = false)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private BCartOrderProduct bCartOrderProduct;
}
