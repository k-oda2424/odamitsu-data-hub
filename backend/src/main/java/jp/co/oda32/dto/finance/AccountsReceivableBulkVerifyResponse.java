package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 売掛金一括検証のレスポンス。
 */
@Data
@Builder
public class AccountsReceivableBulkVerifyResponse {
    private int matchedCount;
    private int mismatchCount;
    private int notFoundCount;
    private int skippedManualCount;
    private int josamaOverwriteCount;
    private int quarterlySpecialCount;

    /** 請求書の closing_date に合わせて自動再集計した得意先件数。 */
    private int reconciledPartners;
    /** 再集計で削除された旧 AR 行の件数。 */
    private int reconciledDeletedRows;
    /** 再集計で新規挿入された AR 行の件数。 */
    private int reconciledInsertedRows;
    /** 手動確定済のため再集計をスキップした得意先件数。 */
    private int reconciledSkippedManualPartners;
    /** 再集計された得意先の一覧（UI で確認用、"partner_code (YYYY-MM: 月末)" 形式）。 */
    private List<String> reconciledDetails;
}
