package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.service.AccountsPayableSummaryCalculator;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final TAccountsPayableSummaryRepository tAccountsPayableSummaryRepository;
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

            // 集計結果をTAccountsPayableSummaryテーブルに登録（既存行は一括取得して Map 化 → N+1 回避）
            if (!summaries.isEmpty()) {
                // 対象月の既存 summary を 1 回の SELECT で取得
                List<TAccountsPayableSummary> existingList =
                        this.tAccountsPayableSummaryService.findByTransactionMonth(periodEndDate);
                // (shop_no, supplier_no, transaction_month, tax_rate) をキーに Map 化
                java.util.Map<String, TAccountsPayableSummary> existMap = existingList.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                e -> buildKey(e.getShopNo(), e.getSupplierNo(), e.getTransactionMonth(), e.getTaxRate()),
                                e -> e,
                                (a, b) -> a));

                List<TAccountsPayableSummary> toSave = new java.util.ArrayList<>();
                for (TAccountsPayableSummary summary : summaries) {
                    String key = buildKey(summary.getShopNo(), summary.getSupplierNo(), summary.getTransactionMonth(), summary.getTaxRate());
                    TAccountsPayableSummary exist = existMap.get(key);

                    if (exist != null) {
                        log.debug("既存レコード更新: 仕入先コード={}, 税率={}%, 元の金額={}円, 更新後金額={}円",
                                summary.getSupplierCode(), summary.getTaxRate(),
                                exist.getTaxIncludedAmountChange(), summary.getTaxIncludedAmountChange());
                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());
                        exist.setVerificationResult(null);
                        exist.setPaymentDifference(null);
                        toSave.add(exist);
                    } else {
                        summary.setVerificationResult(null);
                        summary.setPaymentDifference(null);
                        toSave.add(summary);
                    }
                }
                // 一括保存（batch insert/update）
                this.tAccountsPayableSummaryService.saveAll(toSave);
                log.info("買掛金額の集計結果をデータベースに保存しました。件数: {}", summaries.size());

                // 前回集計にあって今回の対象から外れた行を削除する
                // (例: 仕入明細が物理削除された supplier×tax_rate)。
                // ただし verified_manually=true の行は手動確定済みのため触らない
                // （手動で保持したい値が誤って消えないよう防御）。
                Set<String> savedKeys = new HashSet<>();
                for (TAccountsPayableSummary s : toSave) {
                    savedKeys.add(buildKey(s.getShopNo(), s.getSupplierNo(),
                            s.getTransactionMonth(), s.getTaxRate()));
                }
                List<TAccountsPayableSummary> stale = existingList.stream()
                        .filter(e -> !savedKeys.contains(buildKey(e.getShopNo(), e.getSupplierNo(),
                                e.getTransactionMonth(), e.getTaxRate())))
                        .filter(e -> !Boolean.TRUE.equals(e.getVerifiedManually()))
                        .toList();
                if (!stale.isEmpty()) {
                    log.info("集計対象外となった残留行を削除: {} 件", stale.size());
                    this.tAccountsPayableSummaryRepository.deleteAll(stale);
                }
            } else {
                log.info("新しい買掛金額の集計結果はありません。");
            }
        } catch (Exception e) {
            log.error("買掛金額の集計中にエラーが発生しました。", e);
            throw e; // バッチを失敗としてマーク
        }

        return RepeatStatus.FINISHED;
    }

    /** 4 キー (shop_no, supplier_no, transaction_month, tax_rate) を 1 文字列キーに結合 */
    private static String buildKey(Integer shopNo, Integer supplierNo, LocalDate transactionMonth, java.math.BigDecimal taxRate) {
        return shopNo + "|" + supplierNo + "|" + transactionMonth + "|" + (taxRate != null ? taxRate.toPlainString() : "null");
    }
}
