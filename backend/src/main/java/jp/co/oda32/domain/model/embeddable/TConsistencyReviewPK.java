package jp.co.oda32.domain.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 整合性レポート差分確認履歴の複合主キー。
 *
 * @since 2026-04-23
 */
@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class TConsistencyReviewPK implements Serializable {

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    @Column(name = "entry_key", nullable = false, length = 255)
    private String entryKey;

    @Column(name = "transaction_month", nullable = false)
    private LocalDate transactionMonth;
}
