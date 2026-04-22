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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

            // 対象月の既存 summary を 1 回の SELECT で取得
            List<TAccountsPayableSummary> existingList =
                    this.tAccountsPayableSummaryService.findByTransactionMonth(periodEndDate);

            // 前月 closing Map を構築（opening 繰越用）。
            // 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.3
            Map<String, BigDecimal[]> prevClosingMap = buildPrevClosingMap(periodEndDate);

            // 集計結果をTAccountsPayableSummaryテーブルに登録（既存行は一括取得して Map 化 → N+1 回避）
            if (!summaries.isEmpty()) {
                // (shop_no, supplier_no, transaction_month, tax_rate) をキーに Map 化
                Map<String, TAccountsPayableSummary> existMap = existingList.stream()
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

                // opening を前月 closing から繰越 (手動確定行でも opening は常に上書き)
                applyOpeningBalances(toSave, prevClosingMap);

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

                // 残留する verified_manually=true 行も opening を繰越 (change 列は触らない)
                List<TAccountsPayableSummary> preservedManual = existingList.stream()
                        .filter(e -> !savedKeys.contains(buildKey(e.getShopNo(), e.getSupplierNo(),
                                e.getTransactionMonth(), e.getTaxRate())))
                        .filter(e -> Boolean.TRUE.equals(e.getVerifiedManually()))
                        .toList();
                if (!preservedManual.isEmpty()) {
                    applyOpeningBalances(preservedManual, prevClosingMap);
                    this.tAccountsPayableSummaryService.saveAll(preservedManual);
                    log.info("手動確定行の opening を再繰越: {} 件", preservedManual.size());
                }
            } else {
                log.info("新しい買掛金額の集計結果はありません。");
                // summaries 空でも既存行 (特に verified_manually) の opening は繰越する必要がある
                if (!existingList.isEmpty()) {
                    applyOpeningBalances(existingList, prevClosingMap);
                    this.tAccountsPayableSummaryService.saveAll(existingList);
                    log.info("既存 summary の opening のみ再繰越: {} 件", existingList.size());
                }
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

    /** 前月 closing の lookup キー (transaction_month を含めない — 月単位でつなぐため) */
    private static String buildPrevKey(Integer shopNo, Integer supplierNo, BigDecimal taxRate) {
        return shopNo + "|" + supplierNo + "|" + (taxRate != null ? taxRate.toPlainString() : "null");
    }

    /**
     * 前月 (periodEndDate の 1 ヶ月前) の summary を読み込み、closing を Map 化する。
     * closing = opening + effectiveChange。
     * effectiveChange は verified_manually=true なら verifiedAmount 優先、それ以外は taxIncludedAmountChange。
     */
    private Map<String, BigDecimal[]> buildPrevClosingMap(LocalDate periodEndDate) {
        LocalDate prevMonthEnd = periodEndDate.minusMonths(1);
        List<TAccountsPayableSummary> prev =
                tAccountsPayableSummaryService.findByTransactionMonth(prevMonthEnd);
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

    /**
     * 指定行の opening_balance を前月 closing から set する。
     * 前月行が無い場合は opening=0 で継続し WARN ログを出す（新規事業年度 or 新規仕入先想定）。
     */
    private void applyOpeningBalances(List<TAccountsPayableSummary> rows,
                                       Map<String, BigDecimal[]> prevClosingMap) {
        for (TAccountsPayableSummary row : rows) {
            BigDecimal[] closing = prevClosingMap.get(
                    buildPrevKey(row.getShopNo(), row.getSupplierNo(), row.getTaxRate()));
            if (closing != null) {
                row.setOpeningBalanceTaxIncluded(closing[0]);
                row.setOpeningBalanceTaxExcluded(closing[1]);
            } else {
                // 前月 closing なし: 期首 or 新規仕入先。opening=0 で継続。
                row.setOpeningBalanceTaxIncluded(BigDecimal.ZERO);
                row.setOpeningBalanceTaxExcluded(BigDecimal.ZERO);
                log.warn("前月 closing なし — opening=0 で継続: shopNo={}, supplierNo={}, taxRate={}, month={}",
                        row.getShopNo(), row.getSupplierNo(), row.getTaxRate(), row.getTransactionMonth());
            }
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
