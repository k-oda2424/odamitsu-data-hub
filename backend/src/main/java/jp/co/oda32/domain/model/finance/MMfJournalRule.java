package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_mf_journal_rule")
public class MMfJournalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "description_c", nullable = false)
    private String descriptionC;

    @Column(name = "description_d_keyword")
    private String descriptionDKeyword;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "amount_source", nullable = false)
    private String amountSource;

    @Column(name = "debit_account", nullable = false)
    private String debitAccount;

    @Column(name = "debit_sub_account")
    @Builder.Default
    private String debitSubAccount = "";

    @Column(name = "debit_department")
    @Builder.Default
    private String debitDepartment = "";

    @Column(name = "debit_tax_resolver", nullable = false)
    private String debitTaxResolver;

    @Column(name = "credit_account", nullable = false)
    private String creditAccount;

    @Column(name = "credit_sub_account")
    @Builder.Default
    private String creditSubAccount = "";

    @Column(name = "credit_sub_account_template")
    @Builder.Default
    private String creditSubAccountTemplate = "";

    @Column(name = "credit_department")
    @Builder.Default
    private String creditDepartment = "";

    @Column(name = "credit_tax_resolver", nullable = false)
    private String creditTaxResolver;

    @Column(name = "summary_template", nullable = false)
    @Builder.Default
    private String summaryTemplate = "{d}";

    @Column(name = "requires_client_mapping", nullable = false)
    @Builder.Default
    private Boolean requiresClientMapping = false;

    @Column(name = "del_flg", nullable = false, length = 1)
    @Builder.Default
    private String delFlg = "0";

    @Column(name = "add_date_time")
    private LocalDateTime addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private LocalDateTime modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
}
