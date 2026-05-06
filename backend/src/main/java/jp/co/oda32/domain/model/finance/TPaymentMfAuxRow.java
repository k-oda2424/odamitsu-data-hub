package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 買掛仕入MF 補助行 Entity。
 * <p>振込明細 Excel を {@code applyVerification} した際、PAYABLE(買掛金) 主行 <b>以外</b> の
 * 全行を永続化する:
 * <ul>
 *   <li>EXPENSE / SUMMARY 主行</li>
 *   <li>DIRECT_PURCHASE 主行</li>
 *   <li>PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET 副行 (C2 で追加)</li>
 *   <li>DIRECT_PURCHASE_FEE / DISCOUNT / EARLY / OFFSET 副行 (C2 で追加)</li>
 * </ul>
 * 検証済みCSV出力 ({@code exportVerifiedCsv}, DB-only 経路) で
 * {@code t_accounts_payable_summary}(PAYABLE 主行) と結合して完全な MF 仕訳 CSV を
 * Excel 再アップロードなしで再生成するための拠り所となる。
 *
 * <p>PK は {@code aux_row_id}（サロゲート）。論理的な一意性は
 * (shop_no, transaction_month, transfer_date) 単位で物理削除→再挿入で保持する。
 *
 * <p>設計書: {@code claudedocs/design-payment-mf-aux-rows.md},
 *           {@code claudedocs/design-payment-mf-import.md} §5.4 / §5.5.6
 * <p>関連 migration: V016 (新規作成), V038 (rule_kind CHECK 拡張)
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_payment_mf_aux_row")
public class TPaymentMfAuxRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aux_row_id")
    private Long auxRowId;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    /** 小田光締め日(前月20日)。CSV 取引日列にも使用。 */
    @Column(name = "transaction_month", nullable = false)
    private LocalDate transactionMonth;

    /** 出処 Excel の送金日 (5日 or 20日)。(transaction_month, transfer_date) で洗い替え。 */
    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    /**
     * 行種別。
     * EXPENSE / SUMMARY / DIRECT_PURCHASE 主行、
     * PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET 副行 (C2 で追加)、
     * DIRECT_PURCHASE_FEE / DIRECT_PURCHASE_DISCOUNT / DIRECT_PURCHASE_EARLY / DIRECT_PURCHASE_OFFSET 副行 (C2 で追加)。
     * PAYABLE 主行は本テーブルに保存しない (t_accounts_payable_summary 由来のため重複排除)。
     * V038 で chk_payment_mf_aux_rule_kind 制約拡張。
     */
    @Column(name = "rule_kind", nullable = false, length = 30)
    private String ruleKind;

    /** Excel 内の出現順。CSV 出力順序を保つため保存。 */
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "payment_supplier_code", length = 20)
    private String paymentSupplierCode;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "debit_account", nullable = false, length = 50)
    private String debitAccount;

    @Column(name = "debit_sub_account", length = 50)
    private String debitSubAccount;

    @Column(name = "debit_department", length = 50)
    private String debitDepartment;

    @Column(name = "debit_tax", nullable = false, length = 30)
    private String debitTax;

    @Column(name = "credit_account", nullable = false, length = 50)
    private String creditAccount;

    @Column(name = "credit_sub_account", length = 50)
    private String creditSubAccount;

    @Column(name = "credit_department", length = 50)
    private String creditDepartment;

    @Column(name = "credit_tax", nullable = false, length = 30)
    private String creditTax;

    @Column(name = "summary")
    private String summary;

    @Column(name = "tag", length = 50)
    private String tag;

    @Column(name = "source_filename")
    private String sourceFilename;

    @Column(name = "add_date_time", nullable = false)
    private LocalDateTime addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private LocalDateTime modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
}
