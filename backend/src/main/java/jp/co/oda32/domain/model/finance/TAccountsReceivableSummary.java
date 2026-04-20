package jp.co.oda32.domain.model.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsReceivableSummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め売掛金テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "t_accounts_receivable_summary")
@IdClass(TAccountsReceivableSummaryPK.class)
public class TAccountsReceivableSummary {
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;
    @Id
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "partner_code")
    private String partnerCode;
    @Id
    @Column(name = "transaction_month")
    private LocalDate transactionMonth;
    @Id
    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(name = "tax_included_amount")
    private BigDecimal taxIncludedAmount;

    @Column(name = "tax_excluded_amount")
    private BigDecimal taxExcludedAmount;

    @Column(name = "tax_included_amount_change")
    private BigDecimal taxIncludedAmountChange;

    @Column(name = "tax_excluded_amount_change")
    private BigDecimal taxExcludedAmountChange;

    @Id
    @Column(name = "is_otake_garbage_bag")
    private boolean isOtakeGarbageBag;

    @Column(name = "cutoff_date")
    private Integer cutoffDate;

    @Column(name = "order_no")
    private Integer orderNo;  // 都度現金払い用の注文番号

    // 検証結果のフラグ（1: 一致、0: 不一致、null: 検証なし）
    @Column(name = "verification_result")
    private Integer verificationResult;

    // マネーフォワードエクスポート可否フラグ
    @Builder.Default
    @Column(name = "mf_export_enabled", nullable = false)
    @ColumnDefault("false")
    private Boolean mfExportEnabled = false;

    // 手動確定フラグ: trueなら再集計・再検証バッチで上書きされない
    @Builder.Default
    @Column(name = "verified_manually", nullable = false)
    @ColumnDefault("false")
    private Boolean verifiedManually = false;

    // 検証時の備考（手動確定時の理由など）
    @Column(name = "verification_note")
    private String verificationNote;

    // 突合した請求書金額（税込, t_invoice.net_sales_including_tax）
    @Column(name = "invoice_amount")
    private BigDecimal invoiceAmount;

    // 差額（invoice_amount - tax_included_amount_change）
    @Column(name = "verification_difference")
    private BigDecimal verificationDifference;

    // 突合した請求書ID（t_invoice.invoice_id, 監査用）
    @Column(name = "invoice_no")
    private Integer invoiceNo;
}
