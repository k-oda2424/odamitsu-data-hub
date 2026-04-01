package jp.co.oda32.domain.model.purchase;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_quote_import_header")
public class TQuoteImportHeader {
    @Id
    @Column(name = "quote_import_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer quoteImportId;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "supplier_code")
    private String supplierCode;

    @Column(name = "supplier_no")
    private Integer supplierNo;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "quote_date")
    private LocalDate quoteDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "price_type")
    private String priceType;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "del_flg", nullable = false)
    private String delFlg;

    @Column(name = "add_date_time")
    private Timestamp addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
}
