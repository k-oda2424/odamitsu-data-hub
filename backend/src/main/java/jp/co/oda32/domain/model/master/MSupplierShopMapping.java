package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * 仕入先ショップマッピングマスタEntity
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_supplier_shop_mapping")
public class MSupplierShopMapping implements IEntity {
    @Id
    @Column(name = "mapping_id")
    @SequenceGenerator(name = "m_supplier_shop_mapping_mapping_id_seq_gen", sequenceName = "m_supplier_shop_mapping_mapping_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_supplier_shop_mapping_mapping_id_seq_gen")
    private Integer mappingId;

    @Column(name = "source_shop_no")
    private Integer sourceShopNo;

    @Column(name = "source_supplier_code")
    private String sourceSupplierCode;

    @Column(name = "target_shop_no")
    private Integer targetShopNo;

    @Column(name = "target_supplier_code")
    private String targetSupplierCode;

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

    /**
     * IEntityインターフェース用のgetShopNo()メソッドの実装
     * ソースショップ番号をメインのショップ番号として返します
     *
     * @return ソースショップ番号
     */
    @Override
    public Integer getShopNo() {
        return sourceShopNo;
    }
}
