package jp.co.oda32.domain.model.bcart;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Entity
@Table(name = "x_customer_mapping")
@Getter
@Setter
/**
 * b_cartとsmileの得意先をマッピングするテーブル
 * @author k_oda
 * @since 2023/04/10
 */
public class CustomerMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "x_customer_mapping_id")
    private Integer id;

    @Column(name = "b_cart_customer_id", nullable = false)
    private Long bCartCustomerId;

    @Column(name = "smile_customer_code", nullable = false, unique = true)
    private String smileCustomerCode;
}
