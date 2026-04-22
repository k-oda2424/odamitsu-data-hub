package jp.co.oda32.domain.model.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsPayableSummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

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
    @Builder.Default
    @Column(name = "verified_manually", nullable = false)
    @ColumnDefault("false")
    private Boolean verifiedManually = false;

    // 検証時の備考（請求書番号・確認経緯など）
    @Column(name = "verification_note")
    private String verificationNote;

    /**
     * 検証時の請求額（振込明細や手入力で提示された税込金額）。
     * 買掛集計(`tax_included_amount_change`)との比較元となる値で、
     * MF出力スナップショット `tax_included_amount` とは別管理。
     * 再集計バッチでは上書きされない（手動/Excel検証の結果を保持するため）。
     */
    @Column(name = "verified_amount")
    private BigDecimal verifiedAmount;

    /**
     * MF CSV 出力時の送金日 (CSV 取引日列に使う)。
     * Excel 振込明細取込 (applyVerification) で、行が属するセクションの送金日を記録する。
     * 5日払いセクション hit → 当月 5日。NULL 時は transactionMonth (締め日) にフォールバック。
     */
    @Column(name = "mf_transfer_date")
    private LocalDate mfTransferDate;

    /**
     * 前月末時点の累積残 (税込・符号あり)。
     * closing_balance = opening + effectiveChange は Entity には持たず DTO 層で算出。
     * 手動確定行でも常にバッチで上書きされる (change 列は保護、opening 列は繰越が絶対条件のため)。
     * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.2
     */
    @Builder.Default
    @Column(name = "opening_balance_tax_included", nullable = false)
    @ColumnDefault("0")
    private BigDecimal openingBalanceTaxIncluded = BigDecimal.ZERO;

    /**
     * 前月末時点の累積残 (税抜・符号あり)。
     */
    @Builder.Default
    @Column(name = "opening_balance_tax_excluded", nullable = false)
    @ColumnDefault("0")
    private BigDecimal openingBalanceTaxExcluded = BigDecimal.ZERO;

    /**
     * 当月完了した支払額 (税込)。
     * supplier 単位支払を税率別 change 比で按分。
     * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §2.2
     * Phase B': closing = opening + change - payment_settled の算出要素。
     */
    @Builder.Default
    @Column(name = "payment_amount_settled_tax_included", nullable = false)
    @ColumnDefault("0")
    private BigDecimal paymentAmountSettledTaxIncluded = BigDecimal.ZERO;

    /**
     * 当月完了した支払額 (税抜)。change_excl 比で按分。
     */
    @Builder.Default
    @Column(name = "payment_amount_settled_tax_excluded", nullable = false)
    @ColumnDefault("0")
    private BigDecimal paymentAmountSettledTaxExcluded = BigDecimal.ZERO;

    /**
     * payment-only 行フラグ。
     * 当月 change=0 だが前月支払があった supplier のために生成された行で、
     * stale-delete 対象から除外するための目印。
     */
    @Builder.Default
    @Column(name = "is_payment_only", nullable = false)
    @ColumnDefault("false")
    private Boolean isPaymentOnly = false;
}
