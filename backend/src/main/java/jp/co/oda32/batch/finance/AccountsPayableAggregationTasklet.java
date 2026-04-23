package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.service.AccountsPayableSummaryCalculator;
import jp.co.oda32.batch.finance.service.PayableMonthlyAggregator;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.service.finance.mf.MfPaymentAggregator;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 20日締め買掛金額を集計し、TAccountsPayableSummaryテーブルに登録するTasklet。
 * <p>
 * Phase A (2026-04-22): opening_balance 繰越を追加。
 * Phase B' (2026-04-22): payment_settled 按分 + payment-only 行生成。closing = opening + change - payment_settled。
 * 共通ロジックは {@link PayableMonthlyAggregator} に集約。
 * <p>
 * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §4.1
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableAggregationTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final TAccountsPayableSummaryRepository tAccountsPayableSummaryRepository;
    private final AccountsPayableSummaryCalculator summaryCalculator;
    private final PayableMonthlyAggregator monthlyAggregator;
    private final MfPaymentAggregator mfPaymentAggregator;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            LocalDate startDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate periodStartDate = YearMonth.from(startDate).minusMonths(1).atDay(21);
            LocalDate periodEndDate = YearMonth.from(startDate).atDay(20);

            log.info("買掛金額の集計を開始します。期間: {} から {}", periodStartDate, periodEndDate);

            List<TAccountsPayableSummary> summaries = summaryCalculator.calculatePayableSummaries(periodStartDate, periodEndDate);
            List<TAccountsPayableSummary> existingList = tAccountsPayableSummaryService.findByTransactionMonth(periodEndDate);

            PayableMonthlyAggregator.PrevMonthData prev = monthlyAggregator.buildPrevMonthData(periodEndDate);

            // upsert: summaries → toSave (existingList とマッチすれば update)
            Map<String, TAccountsPayableSummary> existMap = new HashMap<>();
            for (TAccountsPayableSummary e : existingList) existMap.put(PayableMonthlyAggregator.rowKey(e), e);

            List<TAccountsPayableSummary> allCurrRows = new ArrayList<>();
            Set<String> savedRowKeys = new HashSet<>();

            for (TAccountsPayableSummary s : summaries) {
                String k = PayableMonthlyAggregator.rowKey(s);
                TAccountsPayableSummary row = existMap.get(k);
                if (row != null) {
                    row.setTaxIncludedAmountChange(s.getTaxIncludedAmountChange());
                    row.setTaxExcludedAmountChange(s.getTaxExcludedAmountChange());
                    row.setVerificationResult(null);
                    row.setPaymentDifference(null);
                    row.setIsPaymentOnly(false);
                } else {
                    s.setVerificationResult(null);
                    s.setPaymentDifference(null);
                    s.setIsPaymentOnly(false);
                    row = s;
                }
                allCurrRows.add(row);
                savedRowKeys.add(k);
            }

            // preservedManual (verifiedManually=true で集計対象から外れた行)
            List<TAccountsPayableSummary> preservedManual = new ArrayList<>();
            for (TAccountsPayableSummary e : existingList) {
                if (savedRowKeys.contains(PayableMonthlyAggregator.rowKey(e))) continue;
                if (!Boolean.TRUE.equals(e.getVerifiedManually())) continue;
                preservedManual.add(e);
                allCurrRows.add(e);
                savedRowKeys.add(PayableMonthlyAggregator.rowKey(e));
            }

            // opening + payment_settled を共通ロジックで適用
            monthlyAggregator.applyOpenings(allCurrRows, prev);
            monthlyAggregator.applyPaymentSettled(allCurrRows, prev);
            // 案 A (2026-04-23): MF 期首以降は MF debit で上書き
            monthlyAggregator.overrideWithMfDebit(allCurrRows,
                    mfPaymentAggregator.getMfDebitBySupplierForMonth(
                            FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, periodEndDate),
                    periodEndDate);

            // payment-only 行生成 (supplier 単位 change 合計を全 currRows から判定 = R2 fix)
            Map<String, TAccountsPayableSummary> currMap = new HashMap<>();
            for (TAccountsPayableSummary r : allCurrRows) currMap.put(PayableMonthlyAggregator.rowKey(r), r);
            List<TAccountsPayableSummary> paymentOnlyRows = monthlyAggregator.generatePaymentOnlyRows(
                    prev, allCurrRows, periodEndDate, currMap);
            for (TAccountsPayableSummary po : paymentOnlyRows) {
                String k = PayableMonthlyAggregator.rowKey(po);
                if (!savedRowKeys.contains(k)) {
                    allCurrRows.add(po);
                    savedRowKeys.add(k);
                }
                // currMap の既存 row を payment-only 上書きした場合は既に allCurrRows に含まれる
            }

            if (!allCurrRows.isEmpty()) {
                tAccountsPayableSummaryService.saveAll(allCurrRows);
                log.info("買掛金 summary 保存: 通常 {} 件, 手動保持 {} 件, payment-only {} 件",
                        summaries.size(), preservedManual.size(), paymentOnlyRows.size());
            } else {
                log.info("新しい買掛金額の集計結果はありません。");
            }

            // stale-delete: savedKeys にない かつ !verifiedManually
            // is_payment_only=true 行は savedKeys 経由で保護済 (今回 run で再生成対象でなければ削除で OK / R3)
            List<TAccountsPayableSummary> stale = existingList.stream()
                    .filter(e -> !savedRowKeys.contains(PayableMonthlyAggregator.rowKey(e)))
                    .filter(e -> !Boolean.TRUE.equals(e.getVerifiedManually()))
                    .toList();
            if (!stale.isEmpty()) {
                log.info("集計対象外となった残留行を削除: {} 件", stale.size());
                tAccountsPayableSummaryRepository.deleteAll(stale);
            }
        } catch (Exception e) {
            log.error("買掛金額の集計中にエラーが発生しました。", e);
            throw e;
        }

        return RepeatStatus.FINISHED;
    }
}
