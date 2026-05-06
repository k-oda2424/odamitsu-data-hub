package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import jp.co.oda32.domain.model.IEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_payment_mf_rule")
public class MPaymentMfRule implements IEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "payment_supplier_code")
    private String paymentSupplierCode;

    @Column(name = "rule_kind", nullable = false)
    private String ruleKind;

    @Column(name = "debit_account", nullable = false)
    private String debitAccount;

    @Column(name = "debit_sub_account")
    private String debitSubAccount;

    @Column(name = "debit_department")
    private String debitDepartment;

    @Column(name = "debit_tax_category", nullable = false)
    private String debitTaxCategory;

    @Column(name = "credit_account", nullable = false)
    @Builder.Default
    private String creditAccount = "資金複合";

    @Column(name = "credit_sub_account")
    private String creditSubAccount;

    @Column(name = "credit_department")
    private String creditDepartment;

    @Column(name = "credit_tax_category", nullable = false)
    @Builder.Default
    private String creditTaxCategory = "対象外";

    @Column(name = "summary_template", nullable = false)
    private String summaryTemplate;

    @Column(name = "tag")
    private String tag;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "del_flg", nullable = false, length = 1)
    @Builder.Default
    private String delFlg = "0";

    @Column(name = "add_date_time")
    private Timestamp addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    /**
     * このマスタは shop に依存しない (全社共通の MF 仕訳ルール) ため null を返す。
     * IEntity 規約上 getter が必須なので {@code null} で実装する (SF-C13)。
     */
    @Override
    public Integer getShopNo() {
        return null;
    }
}
