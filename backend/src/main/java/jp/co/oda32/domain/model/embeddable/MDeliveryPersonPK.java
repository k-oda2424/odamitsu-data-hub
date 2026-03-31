package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * 配送担当者マスタのPKカラム設定
 *
 * @author k_oda
 * @since 2024/05/04
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class MDeliveryPersonPK implements Serializable {
    @Column(name = "partner_code")
    private String partnerCode;
    @Column(name = "delivery_code")
    private String deliveryCode;
}
