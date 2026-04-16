package jp.co.oda32.batch.finance;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
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
 * 買掛金額とSMILE支払額の照合結果のレポートを出力するTasklet
 * 検証ロジックはSmilePaymentVerifierとAccountsPayableSummaryTaskletに統合
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableVerificationReportTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final MPaymentSupplierService mPaymentSupplierService; // 仕入先名を取得するためのサービスを追加

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Value("#{jobParameters['forceExecution']}")
    private Boolean forceExecution;

    /**
     * レポート出力先ディレクトリ。未指定時はカレントディレクトリにフォールバック
     * （CWD 依存を避けるため、本番は application-*.yml で明示設定を推奨）。
     */
    @Value("${finance.report.output-dir:.}")
    private String reportOutputDir;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        try {
            // targetDate を LocalDate に変換
            LocalDate date = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 指定された日付の買掛金集計データを取得
            List<TAccountsPayableSummary> summaries = tAccountsPayableSummaryService.findByTransactionMonth(date);

            // 最初に異常データのチェックを実施 - 差額がnullの場合は警告を出す
            List<TAccountsPayableSummary> nullDifferenceSummaries = summaries.stream()
                    .filter(s -> s.getPaymentDifference() == null)
                    .collect(Collectors.toList());

            int fixedCount = 0;
            if (!nullDifferenceSummaries.isEmpty()) {
                // 差額がnullのデータが存在する場合、警告ログを出力
                log.warn("差額がnullのデータが{}件あります。買掛データが存在するのに請求情報（SMILE支払情報）が紐づかない可能性があります。",
                        nullDifferenceSummaries.size());

                // 仕入先ごとにグループ化
                Map<String, List<TAccountsPayableSummary>> groupedBySupplier = nullDifferenceSummaries.stream()
                        .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));

                // 各仕入先コードについて警告を出す
                for (String supplierCode : groupedBySupplier.keySet()) {
                    List<TAccountsPayableSummary> supplierSummaries = groupedBySupplier.get(supplierCode);
                    TAccountsPayableSummary firstSummary = supplierSummaries.get(0);

                    // 仕入先名を取得
                    String supplierName = "不明";
                    try {
                        // 支払先サービスから仕入先名を取得
                        MPaymentSupplier paymentSupplier = mPaymentSupplierService.getByPaymentSupplierCode(
                                firstSummary.getShopNo(), supplierCode);
                        if (paymentSupplier != null) {
                            supplierName = paymentSupplier.getPaymentSupplierName();
                        }
                    } catch (Exception e) {
                        log.warn("仕入先名の取得に失敗しました: {}", supplierCode, e);
                    }

                    log.warn("警告: 仕入先コード={}, 仕入先名={} の差額がnullです。買掛データに対応する請求情報（SMILE支払情報）を確認してください。",
                            supplierCode, supplierName);

                    // 詳細情報をログに出力
                    for (TAccountsPayableSummary summary : supplierSummaries) {
                        log.warn("  - ショップ番号={}, 税率={}%, 買掛金額(税込)={}円, 取引月={}",
                                summary.getShopNo(),
                                summary.getTaxRate(),
                                summary.getTaxIncludedAmountChange(),
                                summary.getTransactionMonth());
                    }

                    // 差額がnullのデータにはマネーフォワードエクスポート不可のフラグを設定
                    for (TAccountsPayableSummary summary : supplierSummaries) {
                        // 差額はnullのまま保持
                        // 検証結果は「不一致（0）」として設定
                        summary.setVerificationResult(0);

                        // マネーフォワードエクスポート不可のフラグを設定
                        summary.setMfExportEnabled(false);

                        tAccountsPayableSummaryService.save(summary);
                        fixedCount++;
                    }

                    log.info("仕入先コード={}, 仕入先名={} の差額はnullのまま保持し、マネーフォワードエクスポート不可としてマークしました。",
                            supplierCode, supplierName);
                }
            }

            // 検証結果がnullまたは0（不一致）だが、差額が5円未満の場合は「一致」に修正
            for (TAccountsPayableSummary summary : summaries) {
                if (summary.getPaymentDifference() != null &&
                        (summary.getVerificationResult() == null || summary.getVerificationResult() == 0) &&
                        summary.getPaymentDifference().abs().compareTo(jp.co.oda32.constant.FinanceConstants.PAYMENT_VERIFICATION_TOLERANCE) < 0) {
                    summary.setVerificationResult(1); // 一致
                    summary.setMfExportEnabled(true); // マネーフォワードエクスポート可能に設定
                    tAccountsPayableSummaryService.save(summary);
                    log.info("差額が5円未満のデータを修正: 仕入先コード={}, 差額={}円, 検証結果=「一致」に設定",
                            summary.getSupplierCode(), summary.getPaymentDifference());
                    fixedCount++;
                }
            }

            if (fixedCount > 0) {
                log.info("データの異常を{}件修正しました。最新の状態を再取得します。", fixedCount);
                // 修正後に最新の状態を取得
                summaries = tAccountsPayableSummaryService.findByTransactionMonth(date);
            }

            // 一致しているレコード数をカウント
            long matchedRecordsCount = summaries.stream()
                    .filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 1)
                    .count();

            log.info("検証結果が「一致」のレコード数: {}件", matchedRecordsCount);

            // 検証結果が「一致」のデータはマネーフォワードエクスポート可能に設定
            for (TAccountsPayableSummary summary : summaries) {
                if (summary.getVerificationResult() != null && summary.getVerificationResult() == 1) {
                    if (summary.getMfExportEnabled() == null || !summary.getMfExportEnabled()) {
                        summary.setMfExportEnabled(true);
                        tAccountsPayableSummaryService.save(summary);
                        log.debug("検証結果「一致」のデータをマネーフォワードエクスポート可能に設定: 仕入先コード={}",
                                summary.getSupplierCode());
                    }
                }
            }

            // 未検証のデータを抽出（検証結果がnullのデータ）
            List<TAccountsPayableSummary> unverifiedSummaries = summaries.stream()
                    .filter(s -> s.getVerificationResult() == null)
                    .collect(Collectors.toList());

            // 不一致データを抽出（検証結果が0のデータ、ただし差額がnullのデータは除外）
            List<TAccountsPayableSummary> unmatchedSummaries = summaries.stream()
                    .filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0)
                    .filter(s -> s.getPaymentDifference() != null) // nullの差額は既に処理済み
                    .collect(Collectors.toList());

            boolean hasVerificationIssues = false;

            // 未検証データがある場合の警告
            if (!unverifiedSummaries.isEmpty()) {
                hasVerificationIssues = true;
                log.warn("SMILE支払データとの照合が未実施の買掛金集計データが{}件あります。", unverifiedSummaries.size());

                for (TAccountsPayableSummary summary : unverifiedSummaries) {
                    log.warn("SMILE支払データとの照合未実施: 仕入先コード={}, ショップ番号={}, 税率={}%, 買掛金額={}円",
                            summary.getSupplierCode(),
                            summary.getShopNo(),
                            summary.getTaxRate(),
                            summary.getTaxIncludedAmountChange());
                }
            }

            // 不一致データがある場合の警告
            if (!unmatchedSummaries.isEmpty()) {
                hasVerificationIssues = true;
                log.warn("仕入先ごとのSMILE支払額と買掛金額合計の照合結果が不一致の買掛金集計データが{}件あります。", unmatchedSummaries.size());

                // 詳細なログ出力
                for (TAccountsPayableSummary summary : unmatchedSummaries) {
                    log.warn("不一致データ: 仕入先コード={}, 税率={}%, 買掛金額(税込)={}円, 差額={}円",
                            summary.getSupplierCode(),
                            summary.getTaxRate(),
                            summary.getTaxIncludedAmountChange(),
                            summary.getPaymentDifference());
                }

                // 仕入先ごとにグループ化して集計
                Map<String, List<TAccountsPayableSummary>> supplierGroupedSummaries = unmatchedSummaries.stream()
                        .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));

                // 仕入先ごとの合計差額を表示
                for (String supplierCode : supplierGroupedSummaries.keySet()) {
                    List<TAccountsPayableSummary> supplierSummaries = supplierGroupedSummaries.get(supplierCode);
                    BigDecimal totalDifference = supplierSummaries.stream()
                            .map(s -> s.getPaymentDifference() != null ? s.getPaymentDifference() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(0, java.math.RoundingMode.DOWN);

                    log.warn("仕入先コード={}, 合計差額: {}円", supplierCode, totalDifference);
                }
            }

            // 検証に問題があり、かつforceExecution=trueが設定されていない場合は処理を中止
            if (hasVerificationIssues && (forceExecution == null || !forceExecution)) {
                String errorMessage = "買掛金集計データの検証に問題があるため、処理を中止しました。";
                if (!unverifiedSummaries.isEmpty()) {
                    errorMessage = "SMILE支払データとの照合が未実施の買掛金集計データが存在するため、マネーフォワード連携を中止しました。";
                }
                if (!unmatchedSummaries.isEmpty()) {
                    errorMessage = "仕入先ごとのSMILE支払額と買掛金額合計の照合結果が不一致のデータが存在するため、マネーフォワード連携を中止しました。";
                }
                errorMessage += "forceExecution=trueを指定すると、不一致データが存在しても処理を続行できます。";

                throw new Exception(errorMessage);
            } else if (hasVerificationIssues) {
                log.warn("forceExecution=trueが指定されているため、検証に問題があるデータが存在しても処理を続行します。");
            } else {
                log.info("買掛金集計データのSMILE支払データとの照合チェックが完了しました。問題なくマネーフォワード連携を実行できます。");
            }

            // 検証結果があるデータのみをフィルタリング
            List<TAccountsPayableSummary> verifiedSummaries = summaries.stream()
                    .filter(summary -> summary.getVerificationResult() != null)
                    .collect(Collectors.toList());

            if (verifiedSummaries.isEmpty()) {
                log.info("検証結果のある買掛金集計データが見つかりませんでした。");
                return RepeatStatus.FINISHED;
            }

            // 仕入先ごとに集計
            Map<String, List<TAccountsPayableSummary>> summariesBySupplier = verifiedSummaries.stream()
                    .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));

            // レポートファイル名
            String fileName = "accounts_payable_verification_report_" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

            // レポート出力
            generateReport(summariesBySupplier, fileName);

            log.info("買掛金額検証レポートを出力しました: {}", fileName);

        } catch (Exception e) {
            log.error("買掛金額検証レポートの出力中にエラーが発生しました。", e);
            throw e;
        }

        return RepeatStatus.FINISHED;
    }

    /**
     * レポートを生成します。
     */
    private void generateReport(Map<String, List<TAccountsPayableSummary>> summariesBySupplier, String fileName) throws IOException {
        Path dir = Paths.get(reportOutputDir);
        Files.createDirectories(dir);
        Path outputPath = dir.resolve(fileName);
        log.info("レポート出力先: {}", outputPath.toAbsolutePath());
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            // ヘッダー行
            writer.write("仕入先コード,仕入先名,ショップ番号,税率,買掛金額（税込）,SMILE支払額,差額,検証結果\n");

            // 仕入先コード順にソート
            List<String> sortedSupplierCodes = summariesBySupplier.keySet().stream()
                    .sorted()
                    .collect(Collectors.toList());

            // 支払情報が存在しない仕入先コードを収集（差額が買掛金額と同じ値で負のもの）
            List<String> supplierCodesWithoutPayment = new ArrayList<>();

            for (String supplierCode : sortedSupplierCodes) {
                List<TAccountsPayableSummary> summaries = summariesBySupplier.get(supplierCode);

                // 支払情報が存在しないかチェック
                boolean hasNoPayment = summaries.stream()
                        .allMatch(s -> s.getPaymentDifference() != null &&
                                s.getPaymentDifference().compareTo(BigDecimal.ZERO) < 0 &&
                                s.getPaymentDifference().abs().compareTo(s.getTaxIncludedAmountChange()) == 0);

                if (hasNoPayment) {
                    supplierCodesWithoutPayment.add(supplierCode);
                }

                // 税率の高い順にソート
                summaries.sort(Comparator.comparing(TAccountsPayableSummary::getTaxRate).reversed());

                for (TAccountsPayableSummary summary : summaries) {
                    // 差額の小数点以下を切り捨て
                    BigDecimal paymentDifference = summary.getPaymentDifference();
                    if (paymentDifference != null) {
                        BigDecimal truncatedDifference = paymentDifference.setScale(0, java.math.RoundingMode.DOWN);
                        summary.setPaymentDifference(truncatedDifference);
                    } else {
                        // 差額はnullの場合は0を設定
                        summary.setPaymentDifference(BigDecimal.ZERO);
                    }

                    // 仕入先コード
                    writer.write(supplierCode + ",");

                    // 仕入先名（実際の実装ではサービスから取得）
                    String supplierName = "";
                    try {
                        MPaymentSupplier paymentSupplier = mPaymentSupplierService.getByPaymentSupplierCode(
                                summary.getShopNo(), supplierCode);
                        if (paymentSupplier != null) {
                            supplierName = paymentSupplier.getPaymentSupplierName();
                        }
                    } catch (Exception e) {
                        log.warn("仕入先名の取得に失敗しました: {}", supplierCode, e);
                    }
                    writer.write("\"" + supplierName + "\",");

                    // ショップ番号
                    writer.write(summary.getShopNo() + ",");

                    // 税率
                    writer.write(summary.getTaxRate() + "%,");

                    // 買掛金額（税込）
                    BigDecimal taxIncludedAmountChange = summary.getTaxIncludedAmountChange();
                    writer.write((taxIncludedAmountChange != null ?
                            taxIncludedAmountChange.setScale(0, java.math.RoundingMode.DOWN) :
                            BigDecimal.ZERO) + ",");

                    // SMILE支払額: SmilePaymentVerifier により taxIncludedAmountChange は既に SMILE 支払額の一部に
                    // 調整済み（最大行で残差吸収）なので、そのまま表示。合計で supplier の SMILE 支払額に一致する。
                    BigDecimal smilePaymentAmount = taxIncludedAmountChange != null ? taxIncludedAmountChange : BigDecimal.ZERO;
                    writer.write(smilePaymentAmount.setScale(0, java.math.RoundingMode.DOWN) + ",");

                    // 差額 - nullの場合は「0」として表示（警告は既に出力済み）
                    BigDecimal diff = summary.getPaymentDifference();
                    if (diff == null) {
                        // このケースは実行されないはずだが、念のための防御コード
                        log.warn("レポート生成時に差額=nullのデータを検出: 仕入先コード={}", supplierCode);
                        writer.write("0,");
                    } else {
                        writer.write(diff.toString() + ",");
                    }

                    // 検証結果
                    String result = summary.getVerificationResult() == 1 ? "一致" : "不一致";
                    writer.write(result + "\n");
                }

                // 仕入先ごとに合計行を出力
                BigDecimal totalAccountsPayable = summaries.stream()
                        .map(s -> s.getTaxIncludedAmountChange() != null ?
                                s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(0, java.math.RoundingMode.DOWN);

                // SMILE支払額の合計: taxIncludedAmountChange の和（SmilePaymentVerifier 調整後は supplier の SMILE 支払額に一致）
                BigDecimal totalSmilePayment = summaries.stream()
                        .map(s -> s.getTaxIncludedAmountChange() != null ?
                                s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(0, java.math.RoundingMode.DOWN);

                BigDecimal totalDifference = summaries.stream()
                        .map(s -> s.getPaymentDifference() != null ? s.getPaymentDifference() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(0, java.math.RoundingMode.DOWN);

                writer.write(supplierCode + ",");
                writer.write("合計,");
                writer.write(",");
                writer.write(",");
                writer.write(totalAccountsPayable + ",");
                writer.write(totalSmilePayment + ",");
                writer.write(totalDifference + ",");

                // 総合的な検証結果
                String overallResult = totalDifference.abs().compareTo(jp.co.oda32.constant.FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE) <= 0 ? "一致" : "不一致";
                writer.write(overallResult + "\n\n");
            }

            // 全体の集計
            BigDecimal grandTotalAccountsPayable = summariesBySupplier.values().stream()
                    .flatMap(List::stream)
                    .map(s -> s.getTaxIncludedAmountChange() != null ?
                            s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, java.math.RoundingMode.DOWN);

            // SMILE支払額の全体合計
            BigDecimal grandTotalSmilePayment = summariesBySupplier.values().stream()
                    .flatMap(List::stream)
                    .map(s -> {
                        BigDecimal taxIncluded = s.getTaxIncludedAmountChange() != null ?
                                s.getTaxIncludedAmountChange() : BigDecimal.ZERO;
                        BigDecimal diff = s.getPaymentDifference() != null ?
                                s.getPaymentDifference() : BigDecimal.ZERO;
                        return taxIncluded.add(diff);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, java.math.RoundingMode.DOWN);

            BigDecimal grandTotalDifference = summariesBySupplier.values().stream()
                    .flatMap(List::stream)
                    .map(s -> s.getPaymentDifference() != null ? s.getPaymentDifference() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, java.math.RoundingMode.DOWN);

            writer.write("全体合計,");
            writer.write(",");
            writer.write(",");
            writer.write(",");
            writer.write(grandTotalAccountsPayable + ",");
            writer.write(grandTotalSmilePayment + ",");
            writer.write(grandTotalDifference + ",");

            // 全体的な検証結果
            String finalResult = grandTotalDifference.abs().compareTo(jp.co.oda32.constant.FinanceConstants.PAYMENT_REPORT_MEDIUM_DIFFERENCE) <= 0 ? "一致" : "不一致";
            writer.write(finalResult + "\n");

            // 支払情報が存在しない仕入先コード一覧を追加
            if (!supplierCodesWithoutPayment.isEmpty()) {
                writer.write("\n\n支払情報が存在しない仕入先コード一覧\n");
                writer.write("仕入先コード,仕入先名,買掛金額（税込）,検証結果\n");

                for (String supplierCode : supplierCodesWithoutPayment) {
                    BigDecimal payableAmount = summariesBySupplier.get(supplierCode).stream()
                            .map(s -> s.getTaxIncludedAmountChange() != null ?
                                    s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(0, java.math.RoundingMode.DOWN);

                    // 仕入先名を取得
                    String supplierName = "";
                    try {
                        TAccountsPayableSummary firstSummary = summariesBySupplier.get(supplierCode).get(0);
                        MPaymentSupplier paymentSupplier = mPaymentSupplierService.getByPaymentSupplierCode(
                                firstSummary.getShopNo(), supplierCode);
                        if (paymentSupplier != null) {
                            supplierName = paymentSupplier.getPaymentSupplierName();
                        }
                    } catch (Exception e) {
                        log.warn("仕入先名の取得に失敗しました: {}", supplierCode, e);
                    }

                    writer.write(String.format("%s,\"%s\",%s,\"支払情報なし\"\n",
                            supplierCode,
                            supplierName,
                            payableAmount));
                }

                // 合計
                BigDecimal totalNoPaymentAmount = supplierCodesWithoutPayment.stream()
                        .map(code -> summariesBySupplier.get(code).stream()
                                .map(s -> s.getTaxIncludedAmountChange() != null ?
                                        s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(0, java.math.RoundingMode.DOWN);

                writer.write(String.format("合計,,\"%s\",\"全てマネーフォワードエクスポート対象外\"\n",
                        totalNoPaymentAmount));
            }
        }
    }
}
