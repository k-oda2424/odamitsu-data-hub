package jp.co.oda32.domain.model.bcart;

import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@Setter
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "b_cart_member_other_addresses")
public class BCartMemberOtherAddresses {

    @Id
    @Column(name = "id", nullable = false)
    private Long id; // 別配送先ID

    @Column(name = "customer_id", nullable = false)
    private Long customerId; // 会員ID

    @Column(name = "destination_code", length = 255, nullable = false)
    private String destinationCode; // 配送コード

    @Column(name = "comp_name", length = 255, nullable = false)
    private String compName; // 配送先会社名

    @Column(name = "department", length = 255, nullable = false)
    private String department; // 配送先部署名

    @Column(name = "name", length = 255, nullable = false)
    private String name; // 配送先名

    @Column(name = "zip", length = 255, nullable = false)
    private String zip; // 配送先郵便番号

    @Column(name = "pref", length = 255, nullable = false)
    private String pref; // 配送先都道府県

    @Column(name = "address1", length = 50, nullable = false)
    private String address1; // 配送先市区町村

    @Column(name = "address2", length = 50, nullable = false)
    private String address2; // 配送先町域・番地

    @Column(name = "address3", length = 50, nullable = false)
    private String address3; // 配送先ビル・建物名

    @Column(name = "tel", length = 255, nullable = false)
    private String tel; // 配送先電話番号
}
