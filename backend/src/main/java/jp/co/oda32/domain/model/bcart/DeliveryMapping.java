package jp.co.oda32.domain.model.bcart;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;

import jakarta.persistence.*;

@Entity
@Table(name = "x_delivery_mapping")
@Getter
@Setter
public class DeliveryMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "x_delivery_mapping_id")
    private Integer id;

    @Column(name = "b_cart_customer_id", nullable = false)
    private Long bCartCustomerId;

    @Column(name = "delivery_name", nullable = false)
    private String deliveryName;

    @Column(name = "smile_delivery_code", nullable = false, unique = true)
    private String smileDeliveryCode;

    @Column(name = "b_cart_destination_code")
    private String bCartDestinationCode;

    @Column(name = "partner_code")
    private String partnerCode;

    @Column(name = "delivery_index")
    private String deliveryIndex;

    @Column(name = "recipient_name1")
    private String recipientName1;

    @Column(name = "recipient_name2")
    private String recipientName2;

    @Column(name = "zip")
    private String zip;

    @Column(name = "address1")
    private String address1;

    @Column(name = "address2")
    private String address2;

    @Column(name = "address3")
    private String address3;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "fax_number")
    private String faxNumber;

    @Column(name = "smile_csv_outputted", nullable = false)
    private Boolean smileCsvOutputted;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DeliveryMapping that = (DeliveryMapping) obj;
        return new EqualsBuilder()
                .append(this.bCartCustomerId, that.bCartCustomerId)
                .append(this.deliveryName, that.deliveryName)
                .append(this.zip, that.zip)
                .append(this.address1, that.address1)
                .append(this.address2, that.address2)
                .append(this.address3, that.address3)
                .append(this.partnerCode, that.partnerCode)
                .append(this.deliveryIndex, that.deliveryIndex)
                .append(this.recipientName1, that.recipientName1)
                .append(this.phoneNumber, that.phoneNumber)
                .append(this.smileDeliveryCode, that.smileDeliveryCode)
                .isEquals();
    }
}