package jp.co.oda32.batch.finance;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.PurchaseJournalCsvService;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 買掛金を仕入に変換する仕訳帳をCSVファイルに出力するTasklet。
 * <p>CSV 生成ロジックは {@link PurchaseJournalCsvService} に移譲。
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableToPurchaseJournalTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final PurchaseJournalCsvService purchaseJournalCsvService;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Value("#{jobParameters['forceExport'] ?: 'false'}")
    private String forceExport;

    /** CSV 出力先ディレクトリ。未指定時はカレントディレクトリにフォールバック。 */
    @Value("${finance.report.output-dir:.}")
    private String reportOutputDir;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        LocalDate date = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<TAccountsPayableSummary> summaries = tAccountsPayableSummaryService.findByTransactionMonth(date);

        boolean isForceExport = Boolean.parseBoolean(forceExport);
        PurchaseJournalCsvService.FilterResult filtered = purchaseJournalCsvService.filter(summaries, isForceExport);
        if (isForceExport && filtered.nonExportableCount > 0) {
            log.warn("強制エクスポートモード: チェック未完了 {}件を含めてCSV出力します。", filtered.nonExportableCount);
        } else if (!isForceExport && filtered.nonExportableCount > 0) {
            log.info("MFエクスポート対象外のデータ {}件は除外されました。", filtered.nonExportableCount);
        }
        log.info("マネーフォワードCSV連携処理を実行します。対象データ件数: {}", filtered.exportable.size());

        String fileName = generateFileName(date, isForceExport);
        Path dir = Paths.get(reportOutputDir);
        Files.createDirectories(dir);
        Path outputPath = dir.resolve(fileName);
        log.info("CSV 出力先: {}", outputPath.toAbsolutePath());

        try (Writer writer = new FileWriter(outputPath.toFile())) {
            PurchaseJournalCsvService.Result result =
                    purchaseJournalCsvService.writeCsv(filtered.exportable, date, writer, null);
            log.info("CSV出力完了: {}件 / 合計 {}円", result.rowCount, result.totalAmount);
        }

        // CSV 出力済みマーカーを付けて保存
        purchaseJournalCsvService.markExported(filtered.exportable);
        for (TAccountsPayableSummary summary : filtered.exportable) {
            tAccountsPayableSummaryService.save(summary);
        }

        return RepeatStatus.FINISHED;
    }

    private String generateFileName(LocalDate date, boolean isForceExport) {
        String baseName = "accounts_payable_to_purchase_journal_"
                + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return isForceExport ? baseName + "_UNCHECKED.csv" : baseName + ".csv";
    }
}
