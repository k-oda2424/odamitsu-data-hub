package jp.co.oda32.batch.finance.model;

import lombok.Builder;
import lombok.Data;

/**
 * {@code InvoiceVerifier#verify} の集計結果。
 * <p>
 * 個別行の更新内容は対象の {@code TAccountsReceivableSummary} オブジェクトに反映済みで、
 * 本DTOは件数集計のみを保持する。保存（save）は呼び出し側で行う。
 */
@Data
@Builder
public class InvoiceVerificationSummary {
    private int matchedCount;
    private int mismatchCount;
    private int notFoundCount;
    private int skippedManualCount;
    private int josamaOverwriteCount;
    private int quarterlySpecialCount;
}
