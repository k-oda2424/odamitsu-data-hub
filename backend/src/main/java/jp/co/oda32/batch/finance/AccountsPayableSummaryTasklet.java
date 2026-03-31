package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.VerificationResult;
import jp.co.oda32.batch.finance.service.AccountsPayableSummaryCalculator;
import jp.co.oda32.batch.finance.service.SmilePaymentVerifier;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 20日締め買掛金額を集計し、TAccountsPayableSummaryテーブルに登録するTasklet
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableSummaryTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final TSmilePaymentService tSmilePaymentService;
    private final AccountsPayableSummaryCalculator summaryCalculator;
    private final SmilePaymentVerifier smilePaymentVerifier;

    // バッチジョブの引数として渡された targetDate を取得
    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // targetDate を LocalDate に変換
            LocalDate startDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 期間を設定（前月21日から当月20日まで）
            LocalDate periodStartDate = YearMonth.from(startDate).minusMonths(1).atDay(21); // 前月21日
            LocalDate periodEndDate = YearMonth.from(startDate).atDay(20); // 当月20日

            // データ集計処理（買掛金額の合計を計算）
            List<TAccountsPayableSummary> summaries = summaryCalculator.calculatePayableSummaries(periodStartDate, periodEndDate);

            // 集計結果をTAccountsPayableSummaryテーブルに一括登録
            if (!summaries.isEmpty()) {
                // SMILE支払情報の取得
                List<TSmilePayment> smilePayments = tSmilePaymentService.findByYearMonth(
                        YearMonth.from(periodEndDate).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")));

                // SMILE支払情報との照合結果を取得（仕入先コードごとの総額比較）
                // 注意: verifyWithSmilePaymentメソッド内で、差額が5円以内の場合は
                // summariesオブジェクトのtaxIncludedAmountChangeがSMILE支払額に合わせて更新されます
                Map<String, VerificationResult> verificationResults = smilePaymentVerifier.verifyWithSmilePayment(summaries, smilePayments);

                // 一旦全ての調整されたsummariesを保存（トランザクション内で確実に更新するため）
                log.info("買掛金額の調整結果をデータベースに一括保存します。");
                this.tAccountsPayableSummaryService.saveAll(summaries);

                for (TAccountsPayableSummary summary : summaries) {
                    // 検証結果の取得
                    VerificationResult result = verificationResults.get(summary.getSupplierCode());

                    // 既存のレコードを検索
                    TAccountsPayableSummary exist = this.tAccountsPayableSummaryService.getByPK(
                            summary.getShopNo(),
                            summary.getSupplierNo(),
                            summary.getTransactionMonth(),
                            summary.getTaxRate()
                    );

                    if (exist != null) {
                        // 既存レコードの更新
                        // 注意: summaryオブジェクトのtaxIncludedAmountChangeは
                        // 差額が5円以内の場合はSMILE支払額に合わせて更新されている可能性があります
                        log.debug("既存レコード更新: 仕入先コード={}, 税率={}%, 元の金額={}円, 更新後金額={}円",
                                summary.getSupplierCode(), summary.getTaxRate(),
                                exist.getTaxIncludedAmountChange(), summary.getTaxIncludedAmountChange());

                        // 重要：調整後のtaxIncludedAmountChangeを確実に反映
                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());

                        // 検証結果がある場合は検証結果フラグと差額を設定
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                exist.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                exist.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            exist.setPaymentDifference(result.getDifference()); // 差額を設定
                        }

                        this.tAccountsPayableSummaryService.save(exist);
                    } else {
                        // 新規レコードの作成
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                summary.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                summary.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            summary.setPaymentDifference(result.getDifference()); // 差額を設定
                        }

                        this.tAccountsPayableSummaryService.save(summary);
                    }
                }
                log.info("買掛金額の集計結果をデータベースに保存しました。件数: {}", summaries.size());
            } else {
                log.info("新しい買掛金額の集計結果はありません。");
            }
        } catch (Exception e) {
            log.error("買掛金額の集計中にエラーが発生しました。", e);
            throw e; // バッチを失敗としてマーク
        }

        return RepeatStatus.FINISHED;
    }
}
