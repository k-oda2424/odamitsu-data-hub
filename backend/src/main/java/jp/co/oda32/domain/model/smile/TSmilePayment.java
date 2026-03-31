package jp.co.oda32.domain.model.smile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SMILE支払情報テーブルのEntityクラス
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_smile_payment")
@IdClass(TSmilePayment.TSmilePaymentId.class)
public class TSmilePayment {

    @Id
    @Column(name = "processing_serial_number")
    private Long processingSerialNumber; // 処理連番

    @Id
    @Column(name = "line_no")
    private Integer lineNo; // 行

    @Column(name = "voucher_date")
    private LocalDate voucherDate; // 伝票日付

    @Column(name = "yearmonth")
    private String yearMonth; // 年月度

    @Column(name = "voucher_no")
    private String voucherNo; // 伝票№

    @Column(name = "supplier_code")
    private String supplierCode; // 仕入先ｺｰﾄﾞ

    @Column(name = "supplier_name1")
    private String supplierName1; // 仕入先名１

    @Column(name = "supplier_name2")
    private String supplierName2; // 仕入先名２

    @Column(name = "supplier_name_abbr")
    private String supplierNameAbbr; // 仕入先名略称

    @Column(name = "office_code")
    private String officeCode; // 営業所ｺｰﾄﾞ

    @Column(name = "office_name")
    private String officeName; // 営業所名

    @Column(name = "department_code")
    private String departmentCode; // 部門ｺｰﾄﾞ

    @Column(name = "department_name")
    private String departmentName; // 部門名

    @Column(name = "area_code")
    private String areaCode; // 地区ｺｰﾄﾞ

    @Column(name = "area_name")
    private String areaName; // 地区名

    @Column(name = "industry_code")
    private String industryCode; // 業種ｺｰﾄﾞ

    @Column(name = "industry_name")
    private String industryName; // 業種名

    @Column(name = "supplier_class4_code")
    private String supplierClass4Code; // 仕入先分類４ｺｰﾄﾞ

    @Column(name = "supplier_class4_name")
    private String supplierClass4Name; // 仕入先分類４名

    @Column(name = "supplier_class5_code")
    private String supplierClass5Code; // 仕入先分類５ｺｰﾄﾞ

    @Column(name = "supplier_class5_name")
    private String supplierClass5Name; // 仕入先分類５名

    @Column(name = "supplier_class6_code")
    private String supplierClass6Code; // 仕入先分類６ｺｰﾄﾞ

    @Column(name = "supplier_class6_name")
    private String supplierClass6Name; // 仕入先分類６名

    @Column(name = "supplier_class7_code")
    private String supplierClass7Code; // 仕入先分類７ｺｰﾄﾞ

    @Column(name = "supplier_class7_name")
    private String supplierClass7Name; // 仕入先分類７名

    @Column(name = "supplier_class8_code")
    private String supplierClass8Code; // 仕入先分類８ｺｰﾄﾞ

    @Column(name = "supplier_class8_name")
    private String supplierClass8Name; // 仕入先分類８名

    @Column(name = "supplier_class9_code")
    private String supplierClass9Code; // 仕入先分類９ｺｰﾄﾞ

    @Column(name = "supplier_class9_name")
    private String supplierClass9Name; // 仕入先分類９名

    @Column(name = "transaction_type")
    private String transactionType; // 取引区分

    @Column(name = "transaction_type_name")
    private String transactionTypeName; // 取引区分名

    @Column(name = "transaction_type_attribute")
    private String transactionTypeAttribute; // 取引区分属性

    @Column(name = "transaction_type_attribute_name")
    private String transactionTypeAttributeName; // 取引区分属性名

    @Column(name = "payment_amount")
    private BigDecimal paymentAmount; // 支払額

    @Column(name = "settlement_due_date")
    private LocalDate settlementDueDate; // 決済予定日

    @Column(name = "note_code")
    private String noteCode; // 備考ｺｰﾄﾞ

    @Column(name = "note")
    private String note; // 備考

    @Column(name = "login_id")
    private String loginId; // ﾛｸﾞｲﾝID

    @Column(name = "login_name")
    private String loginName; // ﾛｸﾞｲﾝ名

    @Column(name = "operation_date")
    private LocalDate operationDate; // 操作日付

    @Column(name = "data_occurrence_type")
    private String dataOccurrenceType; // ﾃﾞｰﾀ発生区分

    @Column(name = "counter_processing_serial_number")
    private Long counterProcessingSerialNumber; // 相手処理連番

    @Column(name = "checkmark_type")
    private String checkmarkType; // ﾁｪｯｸﾏｰｸ区分

    @Column(name = "checkmark_type_name")
    private String checkmarkTypeName; // ﾁｪｯｸﾏｰｸ区分名

    @Column(name = "import_date")
    private LocalDate importDate; // 取込日

    /**
     * 複合主キークラス
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TSmilePaymentId implements Serializable {
        private Long processingSerialNumber;
        private Integer lineNo;
    }
}
