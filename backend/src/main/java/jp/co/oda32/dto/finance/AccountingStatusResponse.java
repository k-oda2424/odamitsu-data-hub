package jp.co.oda32.dto.finance;

import java.math.BigDecimal;
import java.util.List;

/**
 * 経理ワークフロー画面 (AccountingStatusService) の戻り値 DTO。
 * <p>
 * 旧実装は {@code Map<String, Object>} を直接返していたため、フロントエンド側で
 * 個別 cast が必要 / 型補完が効かない / OpenAPI 自動生成の対象から漏れる、という問題があった (SF-H06)。
 * 本 record は読み取り専用ステータス取得用で、JSON 出力時はフィールド名そのまま (camelCase)。
 *
 * @since 2026-05-04 (SF-H06)
 */
public record AccountingStatusResponse(
        List<CashbookHistoryRow> cashbookHistory,
        String smilePurchaseLatestDate,
        String smilePaymentLatestDate,
        List<InvoiceLatestRow> invoiceLatest,
        String accountsPayableLatestMonth,
        List<BatchJobStatus> batchJobs
) {
    /** 現金出納帳取込履歴の最新 N 件。 */
    public record CashbookHistoryRow(
            String periodLabel,
            String fileName,
            String processedAt,
            Integer rowCount,
            BigDecimal totalIncome,
            BigDecimal totalPayment
    ) {}

    /** ショップ別最新締日 (請求データ)。 */
    public record InvoiceLatestRow(
            Integer shopNo,
            String closingDate,
            Long count
    ) {}

    /** バッチジョブ最新実行ステータス。 */
    public record BatchJobStatus(
            String jobName,
            String status,
            String exitCode,
            String startTime,
            String endTime
    ) {}
}
