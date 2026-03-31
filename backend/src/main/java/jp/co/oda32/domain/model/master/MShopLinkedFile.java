package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * ショップ連携ファイルマスタEntity
 *
 * @author k_oda
 * @since 2021/07/19
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_shop_linked_file")
@ShopEntity
public class MShopLinkedFile implements IEntity {

    @Id
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;

    @Column(name = "smile_order_input_file_name")
    private String smileOrderInputFileName;
    @Column(name = "smile_purchase_file_name")
    private String smilePurchaseFileName;
    @Column(name = "smile_order_output_file_name")
    private String smileOrderOutputFileName;
    @Column(name = "b_cart_logistics_import_file_name")
    private String bCartLogisticsImportFileName;
    @Column(name = "smile_partner_output_file_name")
    private String smilePartnerOutputFileName;
    @Column(name = "smile_destination_output_file_name")
    private String smileDestinationOutputFileName;
    @Column(name = "smile_goods_import_file_name")
    private String smileGoodsImportFileName;
    @Column(name = "invoice_file_path")
    private String invoiceFilePath;

    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
    @Column(name = "del_flg")
    private String delFlg;

    @OneToOne
    @JoinColumn(name = "shop_no", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private MShop shop;
}
