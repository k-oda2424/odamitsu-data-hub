package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.VerificationResult;
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
 * 買掛金額とSMILE支払情報との照合を行うTasklet
 * （チェック処理のみ、集計処理は別タスクレットで実行済み）
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableVerificationTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final TSmilePaymentService tSmilePaymentService;
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
            LocalDate periodEndDate = YearMonth.from(startDate).atDay(20); // 当月20日

            log.info("買掛金額とSMILE支払情報の照合を開始します。対象月: {}", periodEndDate);

            // 既存の買掛金集計データを取得
            List<TAccountsPayableSummary> summaries = tAccountsPayableSummaryService.findByTransactionMonth(periodEndDate);

            if (!summaries.isEmpty()) {
                // SMILE支払情報の取得（翌月の支払情報を取得）
                List<TSmilePayment> smilePayments = tSmilePaymentService.findByYearMonth(
                        YearMonth.from(periodEndDate).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")));

                log.info("照合対象: 買掛金集計データ {}件, SMILE支払情報 {}件", summaries.size(), smilePayments.size());

                // SMILE支払情報との照合結果を取得（仕入先コードごとの総額比較）
                // 注意: verifyWithSmilePaymentメソッド内で、差額が5円以内の場合は
                // summariesオブジェクトのtaxIncludedAmountChangeがSMILE支払額に合わせて更新されます
                Map<String, VerificationResult> verificationResults = smilePaymentVerifier.verifyWithSmilePayment(summaries, smilePayments);

                // 一旦全ての調整されたsummariesを保存（トランザクション内で確実に更新するため）
                log.info("買掛金額の調整結果をデータベースに一括保存します。");
                this.tAccountsPayableSummaryService.saveAll(summaries);

                // 検証結果を既存レコードに反映
                int updatedCount = 0;
                for (TAccountsPayableSummary summary : summaries) {
                    // 検証結果の取得
                    VerificationResult result = verificationResults.get(summary.getSupplierCode());

                    // 既存のレコードを検索（確実に最新のデータを取得）
                    TAccountsPayableSummary exist = this.tAccountsPayableSummaryService.getByPK(
                            summary.getShopNo(),
                            summary.getSupplierNo(),
                            summary.getTransactionMonth(),
                            summary.getTaxRate()
                    );

                    if (exist != null) {
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
                        } else {
                            // 検証結果がない場合（SMILE支払情報がない場合）
                            exist.setVerificationResult(0); // 不一致
                            exist.setPaymentDifference(null);
                            log.warn("SMILE支払情報が存在しないため「不一致」に設定: 仕入先コード={}", summary.getSupplierCode());
                        }

                        // 重要：調整後のtaxIncludedAmountChangeを確実に反映
                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());

                        this.tAccountsPayableSummaryService.save(exist);
                        updatedCount++;
                    }
                }
                log.info("買掛金額の検証結果をデータベースに反映しました。更新件数: {}", updatedCount);
            } else {
                log.info("照合対象の買掛金集計データが存在しません。");
            }
        } catch (Exception e) {
            log.error("買掛金額の照合中にエラーが発生しました。", e);
            throw e; // バッチを失敗としてマーク
        }

        return RepeatStatus.FINISHED;
    }
}
