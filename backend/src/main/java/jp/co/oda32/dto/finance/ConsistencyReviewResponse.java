package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 整合性レポート 差分確認 Response DTO。
 *
 * @since 2026-04-23
 */
@Data
@Builder
public class ConsistencyReviewResponse {
    private Integer shopNo;
    private String entryType;
    private String entryKey;
    private LocalDate transactionMonth;
    /** IGNORE | MF_APPLY */
    private String actionType;
    /** UI 表示用過去形: IGNORED | MF_APPLIED */
    private String reviewStatus;
    private BigDecimal selfSnapshot;
    private BigDecimal mfSnapshot;
    private Integer reviewedBy;
    private String reviewedByName;
    private Instant reviewedAt;
    private String note;
    /** MF_APPLY の場合 verified_amount を書き換えたか */
    private Boolean verifiedAmountUpdated;
}
