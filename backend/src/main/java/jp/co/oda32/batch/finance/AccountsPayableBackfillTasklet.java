package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.service.PayableMonthlyAggregator;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.mf.MfPaymentAggregator;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 買掛金累積残 opening / payment_settled の過去データ再集計 Tasklet。
 * <p>
 * Phase A: opening 繰越。Phase B': payment_settled 按分 + payment-only 行生成。
 * <p>
 * <b>Tx 設計 (レビュー R1 対応)</b>:
 * 月単位の tx を明示的に制御するため {@link TransactionTemplate} を使用。
 * `@Transactional(REQUIRES_NEW)` は Spring AOP の self-invocation 制約で無効化されるため不採用。
 * <p>
 * <b>起点強制 (R5)</b>:
 * {@code fromMonth != 2025-06-20} の場合は {@code allowPartialResume=true} 必須。
 * Phase A 旧式 closing が残る可能性があるため、admin が明示同意した場合のみ許可。
 * <p>
 * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §4.2
 */
@Component
@Log4j2
@StepScope
public class AccountsPayableBackfillTasklet implements Tasklet {

    private final TAccountsPayableSummaryService summaryService;
    private final PayableMonthlyAggregator monthlyAggregator;
    private final MfPaymentAggregator mfPaymentAggregator;
    private final TransactionTemplate monthTxTemplate;

    @Value("#{jobParameters['fromMonth']}")
    private String fromMonth;

    @Value("#{jobParameters['toMonth']}")
    private String toMonth;

    @Value("#{jobParameters['allowPartialResume'] ?: 'false'}")
    private String allowPartialResumeParam;

    private static final LocalDate EXPECTED_FROM_MONTH = LocalDate.of(2025, 6, 20);

    public AccountsPayableBackfillTasklet(
            TAccountsPayableSummaryService summaryService,
            PayableMonthlyAggregator monthlyAggregator,
            MfPaymentAggregator mfPaymentAggregator,
            PlatformTransactionManager txManager) {
        this.summaryService = summaryService;
        this.monthlyAggregator = monthlyAggregator;
        this.mfPaymentAggregator = mfPaymentAggregator;
        // 月単位 REQUIRES_NEW tx
        this.monthTxTemplate = new TransactionTemplate(txManager);
        this.monthTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate from = LocalDate.parse(fromMonth, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate to = LocalDate.parse(toMonth, DateTimeFormatter.ISO_LOCAL_DATE);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "fromMonth (" + fromMonth + ") が toMonth (" + toMonth + ") より後です");
        }
        boolean allowPartial = "true".equalsIgnoreCase(allowPartialResumeParam);
        if (!from.equals(EXPECTED_FROM_MONTH) && !allowPartial) {
            throw new IllegalArgumentException(
                    "fromMonth は " + EXPECTED_FROM_MONTH + " 固定です (Phase B' 整合性)。"
                            + "途中月から再開する場合は allowPartialResume=true を指定してください。");
        }
        if (allowPartial && !from.equals(EXPECTED_FROM_MONTH)) {
            log.warn("[backfill] 途中再開モード: fromMonth={}, 前月 closing が Phase A 旧式の可能性あり。"
                    + " 実データ検証後に再実行を推奨します。", fromMonth);
        }

        int processedMonths = 0;
        int totalUpdated = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate monthEnd = cursor;
            // 月単位 REQUIRES_NEW tx で独立 commit (R1 fix: @Transactional self-invocation 制約回避)
            Integer updated = monthTxTemplate.execute(status -> processOneMonth(monthEnd));
            totalUpdated += (updated != null ? updated : 0);
            processedMonths++;
            log.info("[backfill] {} — 行更新 {} 件", monthEnd, updated);
            YearMonth nextYm = YearMonth.from(monthEnd).plusMonths(1);
            cursor = nextYm.atDay(20);
        }
        log.info("[backfill] 完了: 処理月数={}, 更新行合計={}", processedMonths, totalUpdated);
        return RepeatStatus.FINISHED;
    }

    /** 1 ヶ月分の opening + payment_settled + payment-only 行を処理。TransactionTemplate で外側から tx 境界。 */
    int processOneMonth(LocalDate periodEndDate) {
        List<TAccountsPayableSummary> current = summaryService.findByTransactionMonth(periodEndDate);

        PayableMonthlyAggregator.PrevMonthData prev = monthlyAggregator.buildPrevMonthData(periodEndDate);

        // 既存行を currMap に格納 (payment-only 既存 row の上書き用)
        Map<String, TAccountsPayableSummary> currMap = new HashMap<>();
        for (TAccountsPayableSummary r : current) currMap.put(PayableMonthlyAggregator.rowKey(r), r);

        // opening + payment_settled 適用
        monthlyAggregator.applyOpenings(current, prev);
        monthlyAggregator.applyPaymentSettled(current, prev);
        // 案 A (2026-04-23): MF 期首以降は MF debit で上書き
        Map<Integer, BigDecimal> mfDebit = mfPaymentAggregator.getMfDebitBySupplierForMonth(
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, periodEndDate);
        monthlyAggregator.overrideWithMfDebit(current, mfDebit, periodEndDate);

        // payment-only 行生成 (支plier 単位 change 判定)
        List<TAccountsPayableSummary> generated = monthlyAggregator.generatePaymentOnlyRows(
                prev, current, periodEndDate, currMap);

        List<TAccountsPayableSummary> toSave = new ArrayList<>(current);
        for (TAccountsPayableSummary po : generated) {
            if (!toSave.contains(po)) toSave.add(po);
        }

        if (!toSave.isEmpty()) {
            summaryService.saveAll(toSave);
        }
        return toSave.size();
    }
}
