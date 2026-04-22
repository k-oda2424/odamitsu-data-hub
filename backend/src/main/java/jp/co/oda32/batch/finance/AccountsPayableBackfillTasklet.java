package jp.co.oda32.batch.finance;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 買掛金累積残 opening_balance の過去データ再集計 Tasklet。
 * <p>
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.4
 * <p>
 * 既存 {@link AccountsPayableAggregationTasklet} はループ呼び出しできないため
 * (Spring Batch 5.x は同一 JobParameters 再実行不可)、累積残繰越専用のバッチとして新設。
 * <p>
 * ポリシー:
 * <ul>
 *   <li>fromMonth → toMonth の各月を月単位 {@link Propagation#REQUIRES_NEW} で処理</li>
 *   <li>change 列 (taxIncludedAmountChange 等) は触らない — opening 列のみ更新</li>
 *   <li>verified_manually=true 行でも opening は上書き</li>
 *   <li>前月行が無い場合 opening=0 + WARN ログで継続 (新規事業年度 or 新規仕入先想定)</li>
 *   <li>失敗時は途中まで commit された状態で中断 → 再実行時は未処理月から再開可</li>
 * </ul>
 * <p>
 * 呼び出し:
 * <pre>
 *   --job.name=accountsPayableBackfill fromMonth=2025-06-20 toMonth=2026-03-20
 * </pre>
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableBackfillTasklet implements Tasklet {

    private final TAccountsPayableSummaryService summaryService;

    /** 開始月末日 (yyyy-MM-dd)。この月から順に opening 繰越を適用する。 */
    @Value("#{jobParameters['fromMonth']}")
    private String fromMonth;

    /** 終了月末日 (yyyy-MM-dd)。含む。 */
    @Value("#{jobParameters['toMonth']}")
    private String toMonth;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate from = LocalDate.parse(fromMonth, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate to = LocalDate.parse(toMonth, DateTimeFormatter.ISO_LOCAL_DATE);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "fromMonth (" + fromMonth + ") が toMonth (" + toMonth + ") より後です");
        }

        int processedMonths = 0;
        int totalUpdated = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            int updated = processOneMonth(cursor);
            totalUpdated += updated;
            processedMonths++;
            log.info("[backfill] {} — opening 更新 {} 件", cursor, updated);
            // 次月の 20 日に進める
            YearMonth nextYm = YearMonth.from(cursor).plusMonths(1);
            cursor = nextYm.atDay(20);
        }
        log.info("[backfill] 完了: 処理月数={}, 更新行合計={}", processedMonths, totalUpdated);
        return RepeatStatus.FINISHED;
    }

    /**
     * 1 ヶ月分の opening 繰越を独立 tx で処理する。
     * 月単位で commit することで、途中失敗時も成功済みの月は保存済みの状態で中断可能。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int processOneMonth(LocalDate periodEndDate) {
        List<TAccountsPayableSummary> current = summaryService.findByTransactionMonth(periodEndDate);
        if (current.isEmpty()) {
            log.info("[backfill] {} — 対象行なし、スキップ", periodEndDate);
            return 0;
        }

        Map<String, BigDecimal[]> prevClosingMap = buildPrevClosingMap(periodEndDate);
        int warnCount = 0;
        for (TAccountsPayableSummary row : current) {
            BigDecimal[] closing = prevClosingMap.get(buildPrevKey(
                    row.getShopNo(), row.getSupplierNo(), row.getTaxRate()));
            if (closing != null) {
                row.setOpeningBalanceTaxIncluded(closing[0]);
                row.setOpeningBalanceTaxExcluded(closing[1]);
            } else {
                row.setOpeningBalanceTaxIncluded(BigDecimal.ZERO);
                row.setOpeningBalanceTaxExcluded(BigDecimal.ZERO);
                warnCount++;
                log.warn("[backfill] 前月 closing なし — opening=0: shopNo={}, supplierNo={}, taxRate={}, month={}",
                        row.getShopNo(), row.getSupplierNo(), row.getTaxRate(), periodEndDate);
            }
        }
        summaryService.saveAll(current);
        if (warnCount > 0) {
            log.warn("[backfill] {} — 前月行無しの {} 件は opening=0 で継続", periodEndDate, warnCount);
        }
        return current.size();
    }

    private Map<String, BigDecimal[]> buildPrevClosingMap(LocalDate periodEndDate) {
        LocalDate prevMonthEnd = periodEndDate.minusMonths(1);
        List<TAccountsPayableSummary> prev = summaryService.findByTransactionMonth(prevMonthEnd);
        Map<String, BigDecimal[]> map = new HashMap<>();
        for (TAccountsPayableSummary p : prev) {
            boolean manual = Boolean.TRUE.equals(p.getVerifiedManually());
            BigDecimal effectiveChangeIncl = manual && p.getVerifiedAmount() != null
                    ? p.getVerifiedAmount()
                    : nz(p.getTaxIncludedAmountChange());
            BigDecimal effectiveChangeExcl = nz(p.getTaxExcludedAmountChange());
            BigDecimal closingIncl = nz(p.getOpeningBalanceTaxIncluded()).add(effectiveChangeIncl);
            BigDecimal closingExcl = nz(p.getOpeningBalanceTaxExcluded()).add(effectiveChangeExcl);
            map.put(buildPrevKey(p.getShopNo(), p.getSupplierNo(), p.getTaxRate()),
                    new BigDecimal[]{closingIncl, closingExcl});
        }
        return map;
    }

    private static String buildPrevKey(Integer shopNo, Integer supplierNo, BigDecimal taxRate) {
        return shopNo + "|" + supplierNo + "|" + (taxRate != null ? taxRate.toPlainString() : "null");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
