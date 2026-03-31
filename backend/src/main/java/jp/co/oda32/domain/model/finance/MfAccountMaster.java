package jp.co.oda32.domain.model.finance;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mf_account_master")
@Data
public class MfAccountMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_name", nullable = false)
    private String reportName;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "financial_statement_item", nullable = false)
    private String financialStatementItem;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "sub_account_name")
    private String subAccountName;

    @Column(name = "tax_classification")
    private String taxClassification;

    @Column(name = "search_key")
    private String searchKey;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
