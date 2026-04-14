package jp.co.oda32.domain.model.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsPayableSummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め買掛金テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "t_accounts_payable_summary")
@IdClass(TAccountsPayableSummaryPK.class)
public class TAccountsPayableSummary {
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;

    @Id
    @Column(name = "supplier_no")
    private Integer supplierNo;

    @Column(name = "supplier_code")
    private String supplierCode;

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

    // 検証結果のフラグを追加（1: 一致、0: 不一致、null: 検証なし）
    @Column(name = "verification_result")
    private Integer verificationResult;

    // SMILE支払額との差額
    @Column(name = "payment_difference")
    private BigDecimal paymentDifference;

    // マネーフォワードエクスポート可否フラグを追加
    @Column(name = "mf_export_enabled")
    private Boolean mfExportEnabled; // デフォルトではエクスポート可能

    // 手入力保護: trueならSMILE再検証バッチで上書きされない
    @Column(name = "verified_manually")
    private Boolean verifiedManually;

    // 検証時の備考（請求書番号・確認経緯など）
    @Column(name = "verification_note")
    private String verificationNote;
}
