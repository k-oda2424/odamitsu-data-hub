package jp.co.oda32.batch.finance.service;

import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.constant.PaymentType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * SMILE請求書 (t_invoice) と売掛金集計 (TAccountsReceivableSummary) を突合する検証サービス。
 * <p>
 * 買掛側 {@link SmilePaymentVerifier} と対称。検証ロジックは
 * 元々 {@code TAccountsReceivableSummaryTasklet} に埋め込まれていたものを抽出した。
 * <ul>
 *   <li>差額0円 or 許容誤差内: 一致（請求書金額に按分）</li>
 *   <li>許容誤差超: 不一致、mf_export_enabled=false</li>
 *   <li>請求書なし: 不一致扱い、mf_export_enabled=false</li>
 *   <li>verified_manually=true: スキップ（再検証で上書きしない）</li>
 *   <li>「上様」(partnerCode "999999" or ≥7桁): 請求書金額で常に上書き</li>
 *   <li>イズミ(得意先000231): 当月15日締め + 前月が四半期特殊月(2/5/8/11)なら前月末締めの請求書も合算</li>
 *   <li>クリーンラボ(得意先301491): 検索時の店舗番号を1に強制</li>
 * </ul>
 *
 * @author k_oda
 * @since 2026/04/17 (抽出元: {@code TAccountsReceivableSummaryTasklet})
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class InvoiceVerifier {

    private static final String QUARTERLY_BILLING_PARTNER_CODE = "000231";
    private static final String CLEAN_LAB_PARTNER_CODE = "301491";
    private static final String JOSAMA_PARTNER_CODE = "999999";

    private final TInvoiceService tInvoiceService;

    @Value("${batch.accounts-receivable.invoice-amount-tolerance:3}")
    private BigDecimal invoiceAmountTolerance;

    /**
     * 売掛金集計リストと SMILE 請求書を突合し、
     * {@code TAccountsReceivableSummary} の検証関連フィールドを更新する。
     * <p>
     * 更新対象フィールド: {@code verificationResult}, {@code mfExportEnabled},
     * {@code invoiceAmount}, {@code verificationDifference}, {@code invoiceNo},
     * 按分により {@code taxIncludedAmountChange}, {@code taxExcludedAmountChange}。
     * <p>
     * 本メソッドは save を呼ばない。呼び出し側で persist すること。
     *
     * <p><b>締め日突合キーの決定ロジック:</b>
     * 各 {@code summary.transactionMonth} がそのままその行の「締め日 (期間終了日)」を表すため、
     * 請求書検索は個別の {@code summary.transactionMonth} を基に行う。
     * {@code fallbackPeriodEndDate} は {@code transactionMonth} が null の救済用のみで、
     * 画面一括検証のように AR 行が複数月にまたがる場合でも各行が正しい月の請求書と突合される。
     *
     * @param summaries             対象の売掛金集計リスト（inplace 更新される）
     * @param fallbackPeriodEndDate {@code transactionMonth} が null の行のフォールバック期間終了日
     * @return 集計件数の結果DTO
     */
    public InvoiceVerificationSummary verify(
            List<TAccountsReceivableSummary> summaries,
            LocalDate fallbackPeriodEndDate) {

        int matchedCount = 0;
        int mismatchCount = 0;
        int notFoundCount = 0;
        int skippedManualCount = 0;
        int josamaOverwriteCount = 0;
        int quarterlySpecialCount = 0;

        // 検証対象から手動確定行を除外
        List<TAccountsReceivableSummary> targetSummaries = new ArrayList<>();
        for (TAccountsReceivableSummary summary : summaries) {
            if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                skippedManualCount++;
                log.debug("手動確定済みのため検証をスキップ: shopNo={}, partnerNo={}, transactionMonth={}, taxRate={}%",
                        summary.getShopNo(), summary.getPartnerNo(),
                        summary.getTransactionMonth(), summary.getTaxRate());
                continue;
            }
            targetSummaries.add(summary);
        }

        // 請求書検索キーでグループ化（各 summary の transactionMonth を個別に参照するため、
        // 複数月にまたがる AR リストでも行ごとに正しい請求書月と突合される）
        Map<InvoiceValidationKey, List<TAccountsReceivableSummary>> byKey = new LinkedHashMap<>();
        for (TAccountsReceivableSummary summary : targetSummaries) {
            InvoiceValidationKey key = buildInvoiceKey(summary, fallbackPeriodEndDate);
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(summary);
        }

        for (Map.Entry<InvoiceValidationKey, List<TAccountsReceivableSummary>> entry : byKey.entrySet()) {
            InvoiceValidationKey key = entry.getKey();
            List<TAccountsReceivableSummary> group = entry.getValue();

            // グループの集計税込金額
            BigDecimal totalTaxIncluded = group.stream()
                    .map(s -> s.getTaxIncludedAmountChange() != null ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, RoundingMode.DOWN);

            // 請求書検索
            Optional<TInvoice> invoiceOpt;
            boolean isQuarterlySpecial = QUARTERLY_BILLING_PARTNER_CODE.equals(key.getPartnerCode());

            if (isQuarterlySpecial) {
                // 四半期特殊も group の transactionMonth を基準にする（group は同一締め日キーで集約済みのため
                // 先頭行の transactionMonth で代表させて問題ない）
                LocalDate groupPeriodEnd = group.get(0).getTransactionMonth() != null
                        ? group.get(0).getTransactionMonth()
                        : fallbackPeriodEndDate;
                invoiceOpt = findQuarterlyInvoice(key.getShopNo(), key.getPartnerCode(), groupPeriodEnd);
                if (invoiceOpt.isPresent()) quarterlySpecialCount++;
            } else {
                invoiceOpt = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(
                        key.getShopNo(), key.getPartnerCode(), key.getClosingDateStr());
            }

            boolean isJosama = JOSAMA_PARTNER_CODE.equals(key.getPartnerCode());

            if (invoiceOpt.isEmpty()) {
                // 請求書なし
                notFoundCount++;
                applyNotFound(group);
                continue;
            }

            TInvoice invoice = invoiceOpt.get();
            BigDecimal invoiceAmount = invoice.getNetSalesIncludingTax() != null
                    ? invoice.getNetSalesIncludingTax().setScale(0, RoundingMode.DOWN)
                    : BigDecimal.ZERO;
            Integer invoiceId = invoice.getInvoiceId();
            BigDecimal diff = invoiceAmount.subtract(totalTaxIncluded);

            if (isJosama) {
                // 上様: 請求書金額で常に上書き
                josamaOverwriteCount++;
                if (totalTaxIncluded.compareTo(BigDecimal.ZERO) != 0) {
                    allocateProportionallyWithRemainder(group, invoiceAmount, totalTaxIncluded);
                } else {
                    log.error("上様分の集計金額が0のため按分不可: shopNo={}, partnerCode={}",
                            key.getShopNo(), key.getPartnerCode());
                }
                applyMatched(group, invoiceAmount, diff, invoiceId);
                matchedCount++;
                continue;
            }

            if (diff.compareTo(BigDecimal.ZERO) == 0) {
                // 完全一致
                applyMatched(group, invoiceAmount, diff, invoiceId);
                matchedCount++;
            } else if (diff.abs().compareTo(invoiceAmountTolerance) <= 0) {
                // 許容誤差内: 請求書金額に按分
                allocateProportionallyWithRemainder(group, invoiceAmount, totalTaxIncluded);
                applyMatched(group, invoiceAmount, diff, invoiceId);
                matchedCount++;
                log.warn("請求書との差額が{}円ありますが許容範囲内として一致扱いに調整: shopNo={}, partnerCode={}, 集計={}, 請求書={}",
                        diff.abs(), key.getShopNo(), key.getPartnerCode(), totalTaxIncluded, invoiceAmount);
            } else {
                // 不一致
                applyMismatch(group, invoiceAmount, diff, invoiceId);
                mismatchCount++;
                log.error("請求書金額不一致: shopNo={}, partnerCode={}, 集計={}, 請求書={}, 差額={}",
                        key.getShopNo(), key.getPartnerCode(), totalTaxIncluded, invoiceAmount, diff);
            }
        }

        log.info("検証完了: 一致={}, 不一致={}, 請求書なし={}, 手動スキップ={}, 上様上書き={}, 四半期特殊={}",
                matchedCount, mismatchCount, notFoundCount,
                skippedManualCount, josamaOverwriteCount, quarterlySpecialCount);

        return InvoiceVerificationSummary.builder()
                .matchedCount(matchedCount)
                .mismatchCount(mismatchCount)
                .notFoundCount(notFoundCount)
                .skippedManualCount(skippedManualCount)
                .josamaOverwriteCount(josamaOverwriteCount)
                .quarterlySpecialCount(quarterlySpecialCount)
                .build();
    }

    /**
     * 請求書検索キーを構築。
     * <ul>
     *   <li>締め日文字列は <b>各 summary 自身の {@code transactionMonth}</b> を基準に組み立てる
     *       （画面一括検証のように AR 行が複数月にまたがっても、行ごとに正しい月の請求書と突合される）。</li>
     *   <li>{@code transactionMonth} が null の場合のみ {@code fallbackPeriodEndDate} を使用。</li>
     *   <li>クリーンラボ 301491 は店舗番号 1 に強制。</li>
     * </ul>
     */
    private InvoiceValidationKey buildInvoiceKey(
            TAccountsReceivableSummary summary, LocalDate fallbackPeriodEndDate) {
        Integer shopNo = summary.getShopNo();
        String partnerCode = summary.getPartnerCode();
        if (CLEAN_LAB_PARTNER_CODE.equals(partnerCode)) {
            shopNo = 1;
        }
        LocalDate periodEnd = summary.getTransactionMonth() != null
                ? summary.getTransactionMonth()
                : fallbackPeriodEndDate;
        String closingDateStr = formatClosingDateForSearch(periodEnd, summary.getCutoffDate());
        return new InvoiceValidationKey(shopNo, partnerCode, closingDateStr);
    }

    /**
     * 請求書検索に使用する締め日文字列を生成する。
     * 都度現金払い・月末締め → "YYYY/MM/末"、特定日締め → "YYYY/MM/DD"。
     */
    private String formatClosingDateForSearch(LocalDate targetPeriodEndDate, Integer cutoffDate) {
        PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDate);
        if (paymentType == PaymentType.CASH_ON_DELIVERY || paymentType == PaymentType.MONTH_END) {
            return String.format("%d/%02d/末", targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue());
        } else {
            return String.format("%d/%02d/%02d",
                    targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue(), cutoffDate);
        }
    }

    /**
     * イズミ(000231)の四半期特殊処理。
     * 当月15日締め + 前月が四半期特殊月(2/5/8/11)なら前月末締めも合算。
     */
    private Optional<TInvoice> findQuarterlyInvoice(
            Integer shopNo, String partnerCode, LocalDate targetPeriodEndDate) {
        List<String> closingDates = getSpecialPartnerClosingDates(targetPeriodEndDate);
        log.info("四半期特殊処理 partnerCode={}, 対象締め日={}", partnerCode, closingDates);

        List<TInvoice> combined = new ArrayList<>();
        for (String closingDateStr : closingDates) {
            tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, closingDateStr)
                    .ifPresent(combined::add);
        }

        if (combined.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (TInvoice inv : combined) {
            if (inv.getNetSalesIncludingTax() != null) {
                totalAmount = totalAmount.add(inv.getNetSalesIncludingTax());
            }
        }

        // 代表の請求書IDは最初のものを使用（監査用）
        TInvoice representative = combined.get(0);
        TInvoice virtual = new TInvoice();
        virtual.setInvoiceId(representative.getInvoiceId());
        virtual.setShopNo(shopNo);
        virtual.setPartnerCode(partnerCode);
        virtual.setPartnerName(representative.getPartnerName());
        virtual.setClosingDate(combined.get(combined.size() - 1).getClosingDate());
        virtual.setNetSalesIncludingTax(totalAmount);
        return Optional.of(virtual);
    }

    /**
     * イズミの検索対象締め日リスト。当月15日締め + 前月が2/5/8/11月なら前月末締めも追加。
     */
    private List<String> getSpecialPartnerClosingDates(LocalDate targetPeriodEndDate) {
        List<String> closingDates = new ArrayList<>();
        int month = targetPeriodEndDate.getMonthValue();
        int year = targetPeriodEndDate.getYear();
        closingDates.add(String.format("%d/%02d/15", year, month));
        int previousMonth = month - 1;
        int previousYear = year;
        if (previousMonth == 0) {
            previousMonth = 12;
            previousYear--;
        }
        if (previousMonth == 2 || previousMonth == 5 || previousMonth == 8 || previousMonth == 11) {
            closingDates.add(String.format("%d/%02d/末", previousYear, previousMonth));
        }
        return closingDates;
    }

    /**
     * 集計サマリー群を請求書金額に合わせて按分する。
     * 単純に比率を掛けて DOWN で丸めると合計が請求書金額に届かないため、
     * 最大金額の行で残差を吸収する。税抜も同様に按分。
     */
    private void allocateProportionallyWithRemainder(
            List<TAccountsReceivableSummary> summaries,
            BigDecimal targetIncTotal,
            BigDecimal originalIncTotal) {
        if (summaries == null || summaries.isEmpty()) return;
        if (originalIncTotal == null || originalIncTotal.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal ratio = targetIncTotal.divide(originalIncTotal, 10, RoundingMode.HALF_UP);

        TAccountsReceivableSummary largest = summaries.stream()
                .filter(s -> s.getTaxIncludedAmountChange() != null)
                .max(Comparator.comparing(TAccountsReceivableSummary::getTaxIncludedAmountChange))
                .orElse(null);

        BigDecimal incAllocated = BigDecimal.ZERO;
        BigDecimal excAllocated = BigDecimal.ZERO;
        BigDecimal targetExcTotal = summaries.stream()
                .filter(s -> s.getTaxExcludedAmountChange() != null)
                .map(TAccountsReceivableSummary::getTaxExcludedAmountChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(ratio)
                .setScale(0, RoundingMode.DOWN);

        for (TAccountsReceivableSummary s : summaries) {
            if (s == largest) continue;
            if (s.getTaxIncludedAmountChange() != null) {
                BigDecimal adjIncluded = s.getTaxIncludedAmountChange().multiply(ratio).setScale(0, RoundingMode.DOWN);
                s.setTaxIncludedAmountChange(adjIncluded);
                incAllocated = incAllocated.add(adjIncluded);
            }
            if (s.getTaxExcludedAmountChange() != null) {
                BigDecimal adjExcluded = s.getTaxExcludedAmountChange().multiply(ratio).setScale(0, RoundingMode.DOWN);
                s.setTaxExcludedAmountChange(adjExcluded);
                excAllocated = excAllocated.add(adjExcluded);
            }
        }

        if (largest != null) {
            largest.setTaxIncludedAmountChange(targetIncTotal.subtract(incAllocated));
            if (largest.getTaxExcludedAmountChange() != null) {
                largest.setTaxExcludedAmountChange(targetExcTotal.subtract(excAllocated));
            }
        }
    }

    private void applyMatched(List<TAccountsReceivableSummary> group,
                              BigDecimal invoiceAmount, BigDecimal diff, Integer invoiceId) {
        // 一致時は AR が請求書合算に按分済みなので、行の invoice_amount は行の taxIncludedAmountChange と一致する。
        // UI 表示で「請求書金額(税込)」列が行ごとに税率別で出るようにするため、行単位で設定する。
        Map<TAccountsReceivableSummary, BigDecimal> allocated = allocateInvoiceByArRatio(group, invoiceAmount);
        for (TAccountsReceivableSummary s : group) {
            s.setVerificationResult(1);
            s.setMfExportEnabled(Boolean.TRUE);
            s.setInvoiceAmount(allocated.get(s));
            // 行ごとの差額 = 行の按分請求書額 - 行の税込売掛 (一致時は 0)
            BigDecimal rowAr = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO;
            s.setVerificationDifference(allocated.get(s).subtract(rowAr));
            s.setInvoiceNo(invoiceId);
            // 一致時のみ、按分後の金額を確定値としても反映（CSV出力はこちらを使用）
            s.setTaxIncludedAmount(s.getTaxIncludedAmountChange());
            s.setTaxExcludedAmount(s.getTaxExcludedAmountChange());
        }
    }

    private void applyMismatch(List<TAccountsReceivableSummary> group,
                               BigDecimal invoiceAmount, BigDecimal diff, Integer invoiceId) {
        // 不一致時も行単位に按分して表示する（ユーザーが税率別に原因を追えるように）。
        Map<TAccountsReceivableSummary, BigDecimal> allocated = allocateInvoiceByArRatio(group, invoiceAmount);
        for (TAccountsReceivableSummary s : group) {
            s.setVerificationResult(0);
            s.setMfExportEnabled(Boolean.FALSE);
            s.setInvoiceAmount(allocated.get(s));
            BigDecimal rowAr = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO;
            s.setVerificationDifference(allocated.get(s).subtract(rowAr));
            s.setInvoiceNo(invoiceId);
            // 不一致時は確定金額を更新しない（手動確定が来るまで待つ）
        }
    }

    /**
     * 請求書合計額 ({@code invoiceAmount}) を各行の {@code taxIncludedAmountChange} 比率で按分する。
     * 端数は最大行で吸収して SUM が合計と一致することを保証する。
     * <p>一致時は group の taxIncludedAmountChange が既に按分済みのため、各行の按分額 = taxIncludedAmountChange になる。
     */
    private Map<TAccountsReceivableSummary, BigDecimal> allocateInvoiceByArRatio(
            List<TAccountsReceivableSummary> group, BigDecimal invoiceAmount) {
        Map<TAccountsReceivableSummary, BigDecimal> out = new java.util.LinkedHashMap<>();
        BigDecimal arTotal = group.stream()
                .map(s -> s.getTaxIncludedAmountChange() != null ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (arTotal.compareTo(BigDecimal.ZERO) == 0) {
            // AR 合計が 0 なら均等割。行数 0 の可能性もゼロなので guard。
            int n = Math.max(group.size(), 1);
            BigDecimal equal = invoiceAmount.divide(BigDecimal.valueOf(n), 0, RoundingMode.DOWN);
            BigDecimal allocated = BigDecimal.ZERO;
            TAccountsReceivableSummary last = group.isEmpty() ? null : group.get(group.size() - 1);
            for (TAccountsReceivableSummary s : group) {
                if (s == last) {
                    out.put(s, invoiceAmount.subtract(allocated));
                } else {
                    out.put(s, equal);
                    allocated = allocated.add(equal);
                }
            }
            return out;
        }

        // 按分（税込 AR 比率）。残差は最大行で吸収。
        TAccountsReceivableSummary largest = group.stream()
                .max(Comparator.comparing(s -> s.getTaxIncludedAmountChange() != null
                        ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO))
                .orElse(null);
        BigDecimal allocated = BigDecimal.ZERO;
        for (TAccountsReceivableSummary s : group) {
            if (s == largest) continue;
            BigDecimal rowAr = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO;
            BigDecimal share = rowAr.multiply(invoiceAmount)
                    .divide(arTotal, 0, RoundingMode.DOWN);
            out.put(s, share);
            allocated = allocated.add(share);
        }
        if (largest != null) {
            out.put(largest, invoiceAmount.subtract(allocated));
        }
        return out;
    }

    private void applyNotFound(List<TAccountsReceivableSummary> group) {
        for (TAccountsReceivableSummary s : group) {
            s.setVerificationResult(0);
            s.setMfExportEnabled(Boolean.FALSE);
            s.setInvoiceAmount(null);
            s.setVerificationDifference(null);
            s.setInvoiceNo(null);
        }
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    private static class InvoiceValidationKey {
        private final Integer shopNo;
        private final String partnerCode;
        private final String closingDateStr;
    }
}
