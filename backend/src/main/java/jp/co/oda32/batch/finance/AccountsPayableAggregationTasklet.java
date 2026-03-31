package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.service.AccountsPayableSummaryCalculator;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 20日締め買掛金額を集計し、TAccountsPayableSummaryテーブルに登録するTasklet
 * （集計処理のみ、チェック処理は別タスクレットで実行）
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableAggregationTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final AccountsPayableSummaryCalculator summaryCalculator;

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

            log.info("買掛金額の集計を開始します。期間: {} から {}", periodStartDate, periodEndDate);

            // データ集計処理（買掛金額の合計を計算）
            List<TAccountsPayableSummary> summaries = summaryCalculator.calculatePayableSummaries(periodStartDate, periodEndDate);

            // 集計結果をTAccountsPayableSummaryテーブルに登録
            if (!summaries.isEmpty()) {
                for (TAccountsPayableSummary summary : summaries) {
                    // 既存のレコードを検索
                    TAccountsPayableSummary exist = this.tAccountsPayableSummaryService.getByPK(
                            summary.getShopNo(),
                            summary.getSupplierNo(),
                            summary.getTransactionMonth(),
                            summary.getTaxRate()
                    );

                    if (exist != null) {
                        // 既存レコードの更新
                        log.debug("既存レコード更新: 仕入先コード={}, 税率={}%, 元の金額={}円, 更新後金額={}円",
                                summary.getSupplierCode(), summary.getTaxRate(),
                                exist.getTaxIncludedAmountChange(), summary.getTaxIncludedAmountChange());

                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());
                        // 検証結果フラグは未設定（null）のままにする
                        exist.setVerificationResult(null);
                        exist.setPaymentDifference(null);

                        this.tAccountsPayableSummaryService.save(exist);
                    } else {
                        // 新規レコードの作成
                        // 検証結果フラグは未設定（null）のままにする
                        summary.setVerificationResult(null);
                        summary.setPaymentDifference(null);

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
