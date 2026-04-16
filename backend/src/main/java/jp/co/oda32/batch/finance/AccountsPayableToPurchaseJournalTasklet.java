package jp.co.oda32.batch.finance;

import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.MfAccountMasterService;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.util.BigDecimalUtil;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 買掛金を仕入に変換する仕訳帳をCSVファイルに出力するTasklet
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
    private final MfAccountMasterService mfAccountMasterService;

    // 取引番号の初期値（常に1から開始）
    private final Long initialTransactionNo = 1L;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    // チェック未完了のデータも出力するかどうかのフラグ（デフォルトはfalse）
    @Value("#{jobParameters['forceExport'] ?: 'false'}")
    private String forceExport;

    /**
     * CSV 出力先ディレクトリ。未指定時はカレントディレクトリにフォールバック。
     */
    @Value("${finance.report.output-dir:.}")
    private String reportOutputDir;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 指定された日付のデータを取得
        LocalDate date = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<TAccountsPayableSummary> summaries = tAccountsPayableSummaryService.findByTransactionMonth(date);

        List<TAccountsPayableSummary> exportableSummaries;

        // forceExportフラグがtrueの場合は、チェック状態に関わらず全データを対象とする
        boolean isForceExport = Boolean.parseBoolean(forceExport);

        if (isForceExport) {
            log.warn("強制エクスポートモードが有効です。チェック未完了のデータも含めてCSV出力します。");
            exportableSummaries = summaries.stream()
                    .filter(summary -> !BigDecimalUtil.isZero(summary.getTaxIncludedAmountChange()))
                    .collect(Collectors.toList());

            // チェック未完了のデータをカウント
            long uncheckedCount = summaries.stream()
                    .filter(summary -> summary.getMfExportEnabled() == null || !summary.getMfExportEnabled())
                    .filter(summary -> !BigDecimalUtil.isZero(summary.getTaxIncludedAmountChange()))
                    .count();

            if (uncheckedCount > 0) {
                log.warn("チェック未完了のデータが{}件含まれています。", uncheckedCount);
            }
        } else {
            // 通常モード：マネーフォワードエクスポート可能なデータのみを抽出
            exportableSummaries = summaries.stream()
                    .filter(summary -> summary.getMfExportEnabled() != null && summary.getMfExportEnabled())
                    .filter(summary -> !BigDecimalUtil.isZero(summary.getTaxIncludedAmountChange()))
                    .collect(Collectors.toList());

            // エクスポート不可のデータをカウント
            long nonExportableCount = summaries.stream()
                    .filter(summary -> summary.getMfExportEnabled() == null || !summary.getMfExportEnabled())
                    .filter(summary -> !BigDecimalUtil.isZero(summary.getTaxIncludedAmountChange()))
                    .count();

            if (nonExportableCount > 0) {
                log.info("マネーフォワードエクスポート対象外のデータが{}件あります。これらは連携処理から除外されます。", nonExportableCount);
            }
        }

        log.info("マネーフォワードCSV連携処理を実行します。対象データ件数: {}", exportableSummaries.size());

        // 買掛金に該当するMfAccountMasterのデータを検索キーごとにMapに格納
        Map<String, MfAccountMaster> accountMasterMap = mfAccountMasterService.findByFinancialStatementItemAndAccountName("買掛金", "買掛金")
                .stream()
                .filter(mfAccountMaster -> mfAccountMaster.getSearchKey() != null)
                .collect(Collectors.toMap(MfAccountMaster::getSearchKey, accountMaster -> accountMaster));

        // ファイル名を作成
        String fileName = generateFileName(date, isForceExport);

        // CSVファイルに出力
        writeToFile(exportableSummaries, accountMasterMap, fileName);

        return RepeatStatus.FINISHED;
    }

    /**
     * ファイル名を生成します。
     *
     * @param date ファイル名に使用する日付
     * @param isForceExport 強制エクスポートモードかどうか
     * @return 生成されたファイル名
     */
    private String generateFileName(LocalDate date, boolean isForceExport) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String baseName = "accounts_payable_to_purchase_journal_" + date.format(formatter);
        if (isForceExport) {
            return baseName + "_UNCHECKED.csv";
        } else {
            return baseName + ".csv";
        }
    }

    /**
     * データをCSVファイルに書き込みます。
     *
     * @param exportableSummaries エクスポート対象のサマリーデータ（既に mfExportEnabled 等で絞り込み済み）
     * @param accountMasterMap    検索キーごとのMfAccountMasterのマップ
     * @param fileName            出力するファイル名
     */
    private void writeToFile(List<TAccountsPayableSummary> exportableSummaries, Map<String, MfAccountMaster> accountMasterMap, String fileName) throws Exception {
        // CSV出力用にsupplierNoとtaxRateごとに金額を合計する
        Map<AggregationKey, SummedAmounts> aggregatedData = exportableSummaries.stream()
                .collect(Collectors.groupingBy(
                        summary -> new AggregationKey(summary.getShopNo(), summary.getSupplierNo(), summary.getSupplierCode(), summary.getTaxRate()),
                        Collectors.mapping(
                                summary -> {
                                    // nullチェックを追加し、null値の場合はBigDecimal.ZEROを使用
                                    BigDecimal taxIncludedAmount = summary.getTaxIncludedAmountChange() != null ?
                                            summary.getTaxIncludedAmountChange() : BigDecimal.ZERO;
                                    BigDecimal taxExcludedAmount = summary.getTaxExcludedAmountChange() != null ?
                                            summary.getTaxExcludedAmountChange() : BigDecimal.ZERO;
                                    return new SummedAmounts(taxIncludedAmount, taxExcludedAmount);
                                },
                                Collectors.reducing(new SummedAmounts(BigDecimal.ZERO, BigDecimal.ZERO), SummedAmounts::combine)
                        )
                ));

        // 日付フォーマッタを定義
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        // targetDate をフォーマット
        LocalDate transactionDate = LocalDate.parse(targetDate, inputFormatter);
        String formattedTransactionDate = transactionDate.format(outputFormatter);

        Path dir = Paths.get(reportOutputDir);
        Files.createDirectories(dir);
        Path outputPath = dir.resolve(fileName);
        log.info("CSV 出力先: {}", outputPath.toAbsolutePath());
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            // ヘッダーを書き込む
            writer.write(MFJournalCsv.CSV_HEADER + "\n");

            // 集計結果をリストに変換してソート
            List<Map.Entry<AggregationKey, SummedAmounts>> aggregatedList = new ArrayList<>(aggregatedData.entrySet());
            aggregatedList.sort(Comparator
                    .comparing((Map.Entry<AggregationKey, SummedAmounts> e) -> e.getKey().getSupplierCode())
                    .thenComparing(e -> e.getKey().getTaxRate(), Comparator.reverseOrder())); // 消費税率を高い順にソート

            long currentTransactionNo = initialTransactionNo; // 起動引数で設定された初期取引番号を使用

            for (Map.Entry<AggregationKey, SummedAmounts> entry : aggregatedList) {
                AggregationKey key = entry.getKey();
                SummedAmounts amounts = entry.getValue();

                // 小数点以下をチェックし、エラーを発生させる
                validateAmount(amounts.getTaxIncludedAmount());
                validateAmount(amounts.getTaxExcludedAmount());

                String searchKey = key.getSupplierCode();
                MfAccountMaster accountMaster = accountMasterMap.get(searchKey);

                // 仕入先コードが「030302」の場合、部門を「クリーンラボ」に設定し、それ以外は「物販事業部」
                String debitDepartment = key.getSupplierCode().equals("030302") ? "クリーンラボ" : "物販事業部";

                if (accountMaster != null) {
                    MFJournalCsv csvRecord = MFJournalCsv.builder()
                            .transactionNo(String.valueOf(currentTransactionNo++)) // 連番の取引番号を設定
                            .transactionDate(formattedTransactionDate) // フォーマット済みの取引日付を設定
                            .debitAccount("仕入高") // 買掛金の借方勘定科目
                            .debitSubAccount("")
                            .debitDepartment(debitDepartment) // 買掛金を処理する部門
                            .debitPartner("")
                            .debitTaxCategory(getTaxType(key.getTaxRate()))
                            .debitInvoice("")
                            .debitAmount(formatAmount(amounts.getTaxIncludedAmount())) // 小数点を省く処理
                            .creditAccount("買掛金") // 貸方勘定科目
                            .creditSubAccount(accountMaster.getSubAccountName())
                            .creditDepartment("")
                            .creditPartner("")
                            .creditTaxCategory("対象外")
                            .creditInvoice("")
                            .creditAmount(formatAmount(amounts.getTaxIncludedAmount())) // 小数点を省く処理
                            .summary(accountMaster.getSearchKey() + ": " + accountMaster.getSubAccountName())
                            .tag("")
                            .memo("")
                            .build();

                    writer.write(formatCsvRecord(csvRecord));
                }
            }

            // エクスポート対象のサマリー行のみを個別に更新（exportableSummaries で絞り込み済み）
            for (TAccountsPayableSummary summary : exportableSummaries) {
                // nullチェックを追加し、null値の場合はBigDecimal.ZEROを使用
                BigDecimal taxIncludedAmount = summary.getTaxIncludedAmountChange() != null ?
                        summary.getTaxIncludedAmountChange() : BigDecimal.ZERO;
                BigDecimal taxExcludedAmount = summary.getTaxExcludedAmountChange() != null ?
                        summary.getTaxExcludedAmountChange() : BigDecimal.ZERO;

                // tmpカラムから連携金額カラムへコピーして更新
                summary.setTaxIncludedAmount(taxIncludedAmount);
                summary.setTaxExcludedAmount(taxExcludedAmount);
                this.tAccountsPayableSummaryService.save(summary);
            }

        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
            throw new IOException("CSVファイルの書き込みに失敗しました。");
        }
    }

    /**
     * CSVレコードをフォーマットします。
     *
     * @param record 書き込むCSVレコード
     * @return フォーマットされたCSVレコードの文字列
     */
    private String formatCsvRecord(MFJournalCsv record) {
        return String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                record.getTransactionNo(),
                record.getTransactionDate(),
                record.getDebitAccount(),
                record.getDebitSubAccount(),
                record.getDebitDepartment(),
                record.getDebitPartner(),
                record.getDebitTaxCategory(),
                record.getDebitInvoice(),
                record.getDebitAmount(),
                record.getCreditAccount(),
                record.getCreditSubAccount(),
                record.getCreditDepartment(),
                record.getCreditPartner(),
                record.getCreditTaxCategory(),
                record.getCreditInvoice(),
                record.getCreditAmount(),
                record.getSummary(),
                record.getTag(),
                record.getMemo()
        );
    }

    /**
     * 消費税率に応じた税区分を取得します。
     *
     * @param taxRate 消費税率
     * @return 税区分
     */
    private String getTaxType(BigDecimal taxRate) {
        if (taxRate.compareTo(BigDecimal.valueOf(10)) == 0) {
            return "課税仕入 10%";
        } else if (taxRate.compareTo(BigDecimal.valueOf(8)) == 0) {
            return "課仕 (軽)8%";
        } else if (taxRate.compareTo(BigDecimal.ZERO) == 0) {
            return "非課税";
        } else {
            return "";
        }
    }

    /**
     * 金額が整数かどうかをチェックします。
     *
     * @param amount チェックする金額
     * @throws Exception 小数点以下がある場合はエラーを投げます
     */
    private void validateAmount(BigDecimal amount) throws Exception {
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new Exception("金額に小数点以下が含まれています: " + amount);
        }
    }

    /**
     * 金額をフォーマットし、小数点以下が `.00` の場合は省きます。
     *
     * @param amount フォーマットする金額
     * @return フォーマットされた金額
     */
    private String formatAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.DOWN).toPlainString(); // 小数点以下を省く
    }

    /**
     * 集計のキーとなるクラス
     */
    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    private static class AggregationKey {
        private final Integer shopNo;
        private final Integer supplierNo;
        private final String supplierCode;
        private final BigDecimal taxRate;
    }

    /**
     * 合計金額を保持するクラス
     */
    @Getter
    @AllArgsConstructor
    private static class SummedAmounts {
        private final BigDecimal taxIncludedAmount;
        private final BigDecimal taxExcludedAmount;

        /**
         * 2つのSummedAmountsを合計します。
         *
         * @param a1 最初の金額
         * @param a2 2番目の金額
         * @return 合計されたSummedAmounts
         */
        public static SummedAmounts combine(SummedAmounts a1, SummedAmounts a2) {
            // nullチェックを追加し、null値の場合はBigDecimal.ZEROを使用
            BigDecimal taxIncluded1 = a1.taxIncludedAmount != null ? a1.taxIncludedAmount : BigDecimal.ZERO;
            BigDecimal taxIncluded2 = a2.taxIncludedAmount != null ? a2.taxIncludedAmount : BigDecimal.ZERO;
            BigDecimal taxExcluded1 = a1.taxExcludedAmount != null ? a1.taxExcludedAmount : BigDecimal.ZERO;
            BigDecimal taxExcluded2 = a2.taxExcludedAmount != null ? a2.taxExcludedAmount : BigDecimal.ZERO;

            return new SummedAmounts(
                    taxIncluded1.add(taxIncluded2),
                    taxExcluded1.add(taxExcluded2)
            );
        }
    }
}
