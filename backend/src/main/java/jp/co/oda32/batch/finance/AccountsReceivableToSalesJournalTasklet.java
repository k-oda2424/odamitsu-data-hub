package jp.co.oda32.batch.finance;

import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.service.finance.SalesJournalCsvService;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 売掛金を売上に変換する仕訳帳をCSVファイルに出力するTasklet。
 * <p>
 * 2026-04-17 改修: CSV 生成ロジックを {@link SalesJournalCsvService} に抽出。
 * 対象は {@code mf_export_enabled=true} のみ。
 *
 * @author k_oda
 * @since 2024/08/31
 * @modified 2025/04/30 - targetDate を yyyyMM に変更
 * @modified 2025/12/30 - fromDate/toDate 期間指定（20日締め対応）
 * @modified 2026/04/17 - CSV 生成ロジックを Service に抽出、mf_export_enabled フィルタ
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsReceivableToSalesJournalTasklet implements Tasklet {

    private final TAccountsReceivableSummaryService tAccountsReceivableSummaryService;
    private final SalesJournalCsvService salesJournalCsvService;

    @Value("#{jobParameters['initialTransactionNo']}")
    private Long initialTransactionNo;

    @Value("#{jobParameters['fromDate']}")
    private String fromDate;

    @Value("#{jobParameters['toDate']}")
    private String toDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("売掛金→売上仕訳CSV出力 Tasklet 開始: fromDate={}, toDate={}", fromDate, toDate);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate fromLocalDate = LocalDate.parse(fromDate, dateFormatter);
        LocalDate toLocalDate = LocalDate.parse(toDate, dateFormatter);
        if (fromLocalDate.isAfter(toLocalDate)) {
            throw new IllegalArgumentException("fromDate は toDate 以前を指定してください: fromDate=" + fromDate + ", toDate=" + toDate);
        }
        log.info("処理対象期間: {} ～ {}", fromLocalDate, toLocalDate);

        List<TAccountsReceivableSummary> summaries = tAccountsReceivableSummaryService
                .findByDateRangeAndMfExportEnabled(fromLocalDate, toLocalDate, true);
        log.info("MF出力対象データ件数: {}", summaries.size());

        String fileName = generateFileName(fromLocalDate, toLocalDate);
        log.info("出力ファイル: {}", fileName);

        if (summaries.isEmpty()) {
            log.info("対象データなし。ヘッダのみ空ファイルを出力します。");
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(fileName), SalesJournalCsvService.CP932)) {
                salesJournalCsvService.writeCsv(summaries, writer, initialTransactionNo);
            }
            return RepeatStatus.FINISHED;
        }

        boolean success = false;
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(fileName), SalesJournalCsvService.CP932)) {
            int written = salesJournalCsvService.writeCsv(summaries, writer, initialTransactionNo);
            log.info("CSV出力完了: {} 件", written);
            success = true;
        } catch (Exception e) {
            log.error("CSV出力中にエラー", e);
            if (!success) {
                try {
                    Files.deleteIfExists(Paths.get(fileName));
                    log.info("不完全なファイルを削除しました: {}", fileName);
                } catch (Exception ex) {
                    log.error("不完全なファイルの削除に失敗しました: {}", fileName, ex);
                }
            }
            throw e;
        }

        // CSV出力済みマーカー（tax_included_amount = tax_included_amount_change）
        salesJournalCsvService.markExported(summaries);
        for (TAccountsReceivableSummary summary : summaries) {
            tAccountsReceivableSummaryService.save(summary);
        }
        log.info("売掛金→売上仕訳CSV出力 Tasklet 正常終了");

        return RepeatStatus.FINISHED;
    }

    private String generateFileName(LocalDate from, LocalDate to) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
        if (from.equals(to)) {
            return "accounts_receivable_to_sales_journal_" + from.format(f) + ".csv";
        }
        return "accounts_receivable_to_sales_journal_" + from.format(f) + "_" + to.format(f) + ".csv";
    }
}
