package jp.co.oda32.domain.model.purchase;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_quote_import_detail")
public class TQuoteImportDetail {
    @Id
    @Column(name = "quote_import_detail_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer quoteImportDetailId;

    @Column(name = "quote_import_id", nullable = false)
    private Integer quoteImportId;

    @Column(name = "row_no")
    private Integer rowNo;

    @Column(name = "jan_code")
    private String janCode;

    @Column(name = "quote_goods_name", nullable = false)
    private String quoteGoodsName;

    @Column(name = "quote_goods_code")
    private String quoteGoodsCode;

    @Column(name = "specification")
    private String specification;

    @Column(name = "quantity_per_case")
    private Integer quantityPerCase;

    @Column(name = "old_price")
    private BigDecimal oldPrice;

    @Column(name = "new_price")
    private BigDecimal newPrice;

    @Column(name = "old_box_price")
    private BigDecimal oldBoxPrice;

    @Column(name = "new_box_price")
    private BigDecimal newBoxPrice;

    @Column(name = "add_date_time")
    private Timestamp addDateTime;

    @Column(name = "status")
    private String status;

    @Column(name = "matched_goods_code")
    private String matchedGoodsCode;

    @Column(name = "matched_goods_no")
    private Integer matchedGoodsNo;

    @Column(name = "processed_at")
    private Timestamp processedAt;
}
