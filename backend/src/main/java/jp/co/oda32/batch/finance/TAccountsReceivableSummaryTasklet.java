package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.batch.finance.service.InvoiceVerifier;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.PaymentType;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.finance.CutoffType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 売掛金額を集計し、TAccountsReceivableSummaryテーブルに登録するTasklet
 * <ul>
 *   <li>集計元: {@code t_order_detail}（SMILE受注連携）</li>
 *   <li>集計キー: 店舗 × invoice_partner_code(= 請求先) × 税率 × ゴミ袋フラグ × (都度現金時のみ注文番号)</li>
 *   <li>検証: {@link InvoiceVerifier} に委譲（請求書 t_invoice と突合）</li>
 *   <li>検証NGも保存（画面で手動確定できるように）</li>
 *   <li>verified_manually=true 行は再集計でも上書きしない</li>
 *   <li>締め日タイプ指定 (all/15/20/month_end) で対象グループを絞れる</li>
 * </ul>
 *
 * @author k_oda
 * @modified 2026/04/17 - 検証ロジックを InvoiceVerifier に抽出、締め日タイプ指定、検証NG保存、手動確定保護 を追加
 *                       （設計書 design-accounts-receivable-mf.md）
 * @since 2024/08/31
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class TAccountsReceivableSummaryTasklet implements Tasklet {

    // 大竹市のゴミ袋のgoods_code一覧
    private static final Set<String> OTAKE_GARBAGE_BAG_GOODS_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "00100001", "00100003", "00100005",
                    "00100101", "00100103", "00100105"
            ))
    );

    // 締め日タイプ ({@link CutoffType}) はジョブパラメータ "cutoffType" として渡される。
    // SF-E10 で文字列定数 CUTOFF_TYPE_* を {@link CutoffType} enum に集約。

    private final TAccountsReceivableSummaryService tAccountsReceivableSummaryService;
    private final TOrderDetailService tOrderDetailService;
    private final MPartnerService mPartnerService;
    private final InvoiceVerifier invoiceVerifier;

    // --- 処理全体で共有するキャッシュデータ ---
    private Map<Integer, String> partnerInvoiceCodeMap; // partner_no -> invoice_partner_code
    private Map<ShopInvoiceKey, Integer> invoicePartnerCodeToPartnerNoMap; // (shopNo, invoice_partner_code) -> partnerNo (請求先)
    private Map<Integer, Integer> partnerCutoffDateMap; // partner_no -> cutoff_date

    // --- 各締め日タイプごとの得意先リスト (partner_no) ---
    private List<Integer> monthEndPartnerNos;
    private List<Integer> cutoff20PartnerNos;
    private List<Integer> cutoff15PartnerNos;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate; // 例: "20260420"

    @Value("#{jobParameters['cutoffType']}")
    private String cutoffType; // "all" | "15" | "20" | "month_end"（null/空は "all" 扱い）

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // SF-E10: cutoffType を enum に変換 (null/空は ALL にフォールバック)。
        CutoffType effectiveCutoffType = CutoffType.fromCode(cutoffType);
        log.info("売掛金集計バッチ処理を開始します。 targetDate={}, cutoffType={}", targetDate, effectiveCutoffType.getCode());

        // SF-E12: 二重例外ハンドリング (try/catch + setExitStatus + throw new RuntimeException) を撤去。
        // Spring Batch framework が tasklet 内例外を捕捉して自動的に ExitStatus.FAILED をセットする。
        initializeCache();
        LocalDate targetDateAsDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        log.info("処理対象年月: {}", YearMonth.from(targetDateAsDate));

        preloadPartnerData();

        boolean all = effectiveCutoffType == CutoffType.ALL;
        if (all || effectiveCutoffType == CutoffType.MONTH_END) {
            processMonthEndCutoffPartners(targetDateAsDate);
        }
        if (all || effectiveCutoffType == CutoffType.DAY_20) {
            process20thCutoffPartners(targetDateAsDate);
        }
        if (all || effectiveCutoffType == CutoffType.DAY_15) {
            process15thCutoffPartners(targetDateAsDate);
        }

        log.info("売掛金集計バッチ処理が正常に完了しました。");
        return RepeatStatus.FINISHED;
    }

    private void initializeCache() {
        partnerInvoiceCodeMap = new HashMap<>();
        invoicePartnerCodeToPartnerNoMap = new HashMap<>();
        partnerCutoffDateMap = new HashMap<>();
        monthEndPartnerNos = new ArrayList<>();
        cutoff20PartnerNos = new ArrayList<>();
        cutoff15PartnerNos = new ArrayList<>();
    }

    private void preloadPartnerData() {
        log.info("得意先情報のプリロードを開始します。");
        List<MPartner> allPartners = mPartnerService.findAll();
        log.info("得意先マスタから {} 件の有効な得意先を読み込みました。", allPartners.size());

        int cashOnDeliveryCount = 0;
        for (MPartner partner : allPartners) {
            Integer partnerNo = partner.getPartnerNo();
            String partnerCode = partner.getPartnerCode();
            Integer shopNo = partner.getShopNo();
            Integer cutoffDate = partner.getCutoffDate() == null ? 0 : partner.getCutoffDate();
            String invoicePartnerCode = partner.getInvoicePartnerCode();
            String effectiveInvoiceCode = (invoicePartnerCode == null || invoicePartnerCode.isEmpty()) ? partnerCode : invoicePartnerCode;

            partnerInvoiceCodeMap.put(partnerNo, effectiveInvoiceCode);
            ShopInvoiceKey shopInvoiceKey = new ShopInvoiceKey(shopNo, effectiveInvoiceCode);
            invoicePartnerCodeToPartnerNoMap.put(shopInvoiceKey, partnerNo);
            partnerCutoffDateMap.put(partnerNo, cutoffDate);

            PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDate);
            switch (paymentType) {
                case DAY_20:
                    cutoff20PartnerNos.add(partnerNo);
                    break;
                case DAY_15:
                    cutoff15PartnerNos.add(partnerNo);
                    break;
                case CASH_ON_DELIVERY:
                    monthEndPartnerNos.add(partnerNo);
                    cashOnDeliveryCount++;
                    break;
                case MONTH_END:
                default:
                    monthEndPartnerNos.add(partnerNo);
                    break;
            }
        }

        monthEndPartnerNos = monthEndPartnerNos.stream().distinct().collect(Collectors.toList());
        cutoff20PartnerNos = cutoff20PartnerNos.stream().distinct().collect(Collectors.toList());
        cutoff15PartnerNos = cutoff15PartnerNos.stream().distinct().collect(Collectors.toList());

        log.info("得意先情報のプリロード完了。月末締め:{}件, 20日締め:{}件, 15日締め:{}件, 都度現金払い:{}件",
                monthEndPartnerNos.size() - cashOnDeliveryCount,
                cutoff20PartnerNos.size(), cutoff15PartnerNos.size(), cashOnDeliveryCount);
    }

    private void processMonthEndCutoffPartners(LocalDate targetDateAsDate) {
        if (monthEndPartnerNos.isEmpty()) {
            log.info("月末締めグループ（都度現金払い含む）の対象得意先が存在しません。");
            return;
        }
        YearMonth currentMonth = YearMonth.from(targetDateAsDate);
        LocalDate periodStartDate = currentMonth.atDay(1);
        LocalDate periodEndDate = currentMonth.atEndOfMonth();
        log.info("--- 月末締めグループ（都度現金払い含む）の売掛金集計開始 ---");
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, monthEndPartnerNos);
        verifyAndSave(summaries, "月末締めグループ", periodEndDate);
    }

    private void process20thCutoffPartners(LocalDate targetDateAsDate) {
        if (cutoff20PartnerNos.isEmpty()) {
            log.info("20日締めの対象得意先が存在しません。");
            return;
        }
        LocalDate periodStartDate = YearMonth.from(targetDateAsDate).minusMonths(1).atDay(21);
        LocalDate periodEndDate = YearMonth.from(targetDateAsDate).atDay(20);
        log.info("--- 20日締めの売掛金集計開始 ---");
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, cutoff20PartnerNos);
        verifyAndSave(summaries, "20日締め", periodEndDate);
    }

    private void process15thCutoffPartners(LocalDate targetDateAsDate) {
        if (cutoff15PartnerNos.isEmpty()) {
            log.info("15日締めの対象得意先が存在しません。");
            return;
        }
        LocalDate periodStartDate = YearMonth.from(targetDateAsDate).minusMonths(1).atDay(16);
        LocalDate periodEndDate = YearMonth.from(targetDateAsDate).atDay(15);
        log.info("--- 15日締めの売掛金集計開始 ---");
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, cutoff15PartnerNos);
        verifyAndSave(summaries, "15日締め", periodEndDate);
    }

    /**
     * 集計結果を検証し、検証NGを含む全件を保存する。
     * <ul>
     *   <li>既存DB行と計算結果をマージ（PKが一致するDB行があればそれに計算値を反映）</li>
     *   <li>verified_manually=true の既存行はスキップ（保護）</li>
     *   <li>{@link InvoiceVerifier} に検証を委譲</li>
     *   <li>全件保存（検証NG含む、画面で手動確定できるよう）</li>
     * </ul>
     */
    private void verifyAndSave(List<TAccountsReceivableSummary> calculated,
                               String cutoffType, LocalDate targetPeriodEndDate) {
        if (calculated == null || calculated.isEmpty()) {
            log.info("{} の処理対象となる集計結果はありませんでした。", cutoffType);
            return;
        }
        log.info("{} の集計件数: {}", cutoffType, calculated.size());

        // 既存DB行とマージ。verified_manually=true はスキップ。
        List<TAccountsReceivableSummary> toVerify = new ArrayList<>();
        int skippedManualCount = 0;
        for (TAccountsReceivableSummary calc : calculated) {
            TAccountsReceivableSummary existing = tAccountsReceivableSummaryService.getByPK(
                    calc.getShopNo(), calc.getPartnerNo(), calc.getTransactionMonth(),
                    calc.getTaxRate(), calc.isOtakeGarbageBag()
            );
            if (existing != null && Boolean.TRUE.equals(existing.getVerifiedManually())) {
                skippedManualCount++;
                log.debug("手動確定済みのため上書きスキップ: shopNo={}, partnerNo={}, month={}, taxRate={}%",
                        existing.getShopNo(), existing.getPartnerNo(),
                        existing.getTransactionMonth(), existing.getTaxRate());
                continue;
            }
            if (existing != null) {
                // 既存行の集計フィールドだけ上書き。検証系は verify() で更新される。
                existing.setPartnerCode(calc.getPartnerCode());
                existing.setTaxIncludedAmountChange(calc.getTaxIncludedAmountChange());
                existing.setTaxExcludedAmountChange(calc.getTaxExcludedAmountChange());
                existing.setCutoffDate(calc.getCutoffDate());
                existing.setOrderNo(calc.getOrderNo());
                toVerify.add(existing);
            } else {
                toVerify.add(calc);
            }
        }

        // 検証ロジックは InvoiceVerifier に委譲
        InvoiceVerificationSummary result = invoiceVerifier.verify(toVerify, targetPeriodEndDate);
        log.info("{} の検証結果: 一致={}, 不一致={}, 請求書なし={}, 上様上書き={}, 四半期特殊={}, 手動スキップ(tasklet側)={}",
                cutoffType, result.getMatchedCount(), result.getMismatchCount(),
                result.getNotFoundCount(), result.getJosamaOverwriteCount(),
                result.getQuarterlySpecialCount(), skippedManualCount);

        // 全件保存（検証NG含む）
        int saved = 0;
        for (TAccountsReceivableSummary summary : toVerify) {
            try {
                tAccountsReceivableSummaryService.save(summary);
                saved++;
            } catch (Exception e) {
                log.error("売掛金集計の保存中にエラー: shopNo={}, partnerNo={}, partnerCode={}, month={}",
                        summary.getShopNo(), summary.getPartnerNo(),
                        summary.getPartnerCode(), summary.getTransactionMonth(), e);
            }
        }
        log.info("{} の保存件数: {}", cutoffType, saved);
    }

    private List<TAccountsReceivableSummary> calculateReceivableSummaries(
            LocalDate startDate, LocalDate endDate, List<Integer> targetPartnerNos) {
        log.info("売掛金集計計算を開始します。期間: {} ～ {}, 対象得意先数: {}", startDate, endDate, targetPartnerNos.size());
        if (targetPartnerNos.isEmpty()) {
            return new ArrayList<>();
        }
        List<TOrderDetail> orderDetails = tOrderDetailService.findByPartnerNosAndDateRange(
                targetPartnerNos, startDate, endDate, Flag.NO);
        log.info("期間内の注文詳細を取得しました。件数: {}", orderDetails.size());
        if (orderDetails.isEmpty()) {
            return new ArrayList<>();
        }

        Map<SummaryKey, List<TOrderDetail>> detailsBySummaryKey = new HashMap<>();
        Map<SummaryKey, PartnerBillingInfo> keyToBillingInfoMap = new HashMap<>();
        int skippedDetailCount = 0;

        for (TOrderDetail detail : orderDetails) {
            Integer shopNo = detail.getShopNo();
            Integer orderPartnerNo = detail.getTOrder().getPartnerNo();
            String invoicePartnerCode = this.partnerInvoiceCodeMap.get(orderPartnerNo);
            if (invoicePartnerCode == null) {
                log.error("注文詳細(ID:{}, OrderNo:{})の得意先番号 {} に対応する請求コードが見つかりません。スキップ。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), orderPartnerNo);
                skippedDetailCount++;
                continue;
            }
            ShopInvoiceKey shopInvoiceKey = new ShopInvoiceKey(shopNo, invoicePartnerCode);
            Integer billingPartnerNo = this.invoicePartnerCodeToPartnerNoMap.get(shopInvoiceKey);
            if (billingPartnerNo == null) {
                log.error("注文詳細(ID:{}, OrderNo:{})の店舗:{}, 請求コード:{} に対応する請求先得意先番号が見つかりません。スキップ。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), shopNo, invoicePartnerCode);
                skippedDetailCount++;
                continue;
            }
            Integer billingCutoffDate = this.partnerCutoffDateMap.get(billingPartnerNo);
            if (billingCutoffDate == null) {
                log.error("請求先得意先番号 {} の締め日情報が見つかりません。スキップ。", billingPartnerNo);
                skippedDetailCount++;
                continue;
            }

            PaymentType paymentType = PaymentType.fromCutoffCode(billingCutoffDate);
            boolean isCashOnDelivery = paymentType == PaymentType.CASH_ON_DELIVERY;
            boolean isLongPartnerCode = invoicePartnerCode.length() >= 7;
            int summaryPartnerNo;
            Integer summaryOrderNo = null;
            String summaryPartnerCode;
            Integer effectiveCutoffDate = billingCutoffDate;

            if (isCashOnDelivery) {
                // 都度現金払いは「月初〜月末の合計 vs 月次請求書」で突合するため、
                // 注文単位ではなく月次集約する (order_no を SummaryKey に含めない)。
                // DB PK = (shop, partner, month, tax_rate, is_otake) なので、order_no を
                // SummaryKey に入れると複数行を作ろうとしても PK 衝突で最後の1件しか残らない。
                summaryPartnerNo = billingPartnerNo;
                summaryOrderNo = null;
                summaryPartnerCode = invoicePartnerCode;
            } else if (isLongPartnerCode) {
                // 得意先コードが7桁以上なら「上様」(999999) + 都度現金払い
                summaryPartnerNo = -999999;
                summaryPartnerCode = "999999";
                summaryOrderNo = detail.getOrderNo();
                effectiveCutoffDate = PaymentType.CASH_ON_DELIVERY.getCutoffCode();
                if (summaryOrderNo == null) {
                    skippedDetailCount++;
                    continue;
                }
            } else {
                summaryPartnerNo = billingPartnerNo;
                summaryPartnerCode = invoicePartnerCode;
            }

            SummaryKey key = new SummaryKey(
                    shopNo, summaryPartnerNo,
                    detail.getTaxRate() != null ? detail.getTaxRate() : BigDecimal.ZERO,
                    OTAKE_GARBAGE_BAG_GOODS_CODES.contains(detail.getGoodsCode()),
                    summaryOrderNo
            );
            final String finalPartnerCode = summaryPartnerCode;
            final Integer finalCutoffDate = effectiveCutoffDate;
            keyToBillingInfoMap.computeIfAbsent(key, k -> new PartnerBillingInfo(finalPartnerCode, finalCutoffDate));
            detailsBySummaryKey.computeIfAbsent(key, k -> new ArrayList<>()).add(detail);
        }

        if (skippedDetailCount > 0) {
            log.warn("請求情報の解決に失敗し、{} 件の注文詳細がスキップされました。", skippedDetailCount);
        }
        log.info("注文詳細のグループ化完了。グループ数: {}", detailsBySummaryKey.size());

        List<TAccountsReceivableSummary> summaries = new ArrayList<>();
        for (Map.Entry<SummaryKey, List<TOrderDetail>> entry : detailsBySummaryKey.entrySet()) {
            SummaryKey key = entry.getKey();
            List<TOrderDetail> detailsInGroup = entry.getValue();
            PartnerBillingInfo billingInfo = keyToBillingInfoMap.get(key);
            if (billingInfo == null) continue;

            BigDecimal totalAmountExcludingTax = BigDecimal.ZERO;
            BigDecimal totalOriginalAmount = BigDecimal.ZERO;

            for (TOrderDetail detail : detailsInGroup) {
                totalAmountExcludingTax = totalAmountExcludingTax.add(calculateAmountExcludingTax(detail));
                BigDecimal totalAmount = detail.getTotalAmount();
                if (totalAmount != null) {
                    totalOriginalAmount = totalOriginalAmount.add(totalAmount);
                }
            }

            BigDecimal calculatedTaxAmount = BigDecimal.ZERO;
            BigDecimal taxRate = key.getTaxRate();
            if (taxRate.compareTo(BigDecimal.ZERO) > 0) {
                calculatedTaxAmount = totalAmountExcludingTax
                        .multiply(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN))
                        .setScale(0, RoundingMode.DOWN);
            }

            boolean allTaxableInclude = detailsInGroup.stream()
                    .allMatch(d -> TaxType.TAXABLE_INCLUDE.equals(TaxType.purse(d.getTaxType())));

            BigDecimal totalAmountIncludingTax = allTaxableInclude
                    ? totalOriginalAmount
                    : totalAmountExcludingTax.add(calculatedTaxAmount);

            BigDecimal taxExcludedAmountTruncated = totalAmountExcludingTax.setScale(0, RoundingMode.DOWN);
            BigDecimal taxAmount = allTaxableInclude
                    ? totalOriginalAmount.subtract(totalAmountExcludingTax)
                    : calculatedTaxAmount;
            BigDecimal taxAmountTruncated = taxAmount.setScale(0, RoundingMode.DOWN);

            BigDecimal originalTaxIncludedAmount = allTaxableInclude ? totalOriginalAmount : totalAmountIncludingTax;
            BigDecimal originalTaxIncludedAmountTruncated = originalTaxIncludedAmount.setScale(0, RoundingMode.DOWN);
            BigDecimal taxIncludedAmountTruncated = taxExcludedAmountTruncated.add(taxAmountTruncated);

            BigDecimal diff = originalTaxIncludedAmountTruncated.subtract(taxIncludedAmountTruncated);
            if (diff.abs().compareTo(BigDecimal.ONE) == 0) {
                log.warn("消費税計算に1円の誤差を検出したため請求書金額に合わせます: partnerNo={}, 元={}, 計算={}",
                        key.getPartnerNo(), originalTaxIncludedAmountTruncated, taxIncludedAmountTruncated);
                taxIncludedAmountTruncated = originalTaxIncludedAmountTruncated;
            }

            TAccountsReceivableSummary summary = TAccountsReceivableSummary.builder()
                    .shopNo(key.getShopNo())
                    .partnerNo(key.getPartnerNo())
                    .partnerCode(billingInfo.getPartnerCode())
                    .transactionMonth(endDate)
                    .taxRate(key.getTaxRate())
                    .taxIncludedAmountChange(taxIncludedAmountTruncated)
                    .taxExcludedAmountChange(taxExcludedAmountTruncated)
                    .isOtakeGarbageBag(key.isOtakeGarbageBag())
                    .cutoffDate(billingInfo.getCutoffDate())
                    .orderNo(key.getOrderNo())
                    .mfExportEnabled(false) // 検証前は false
                    .verifiedManually(false)
                    .build();
            summaries.add(summary);
        }

        log.info("売掛金集計計算完了。生成されたSummary件数: {}", summaries.size());
        return summaries;
    }

    private BigDecimal calculateAmountExcludingTax(TOrderDetail detail) {
        BigDecimal totalAmount = detail.getTotalAmount();
        TaxType taxType = TaxType.purse(detail.getTaxType());
        BigDecimal taxRate = detail.getTaxRate();
        if (totalAmount == null) return BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;

        switch (Objects.requireNonNull(taxType, "TaxType が決定できません: " + detail.getTaxType())) {
            case TAX_EXCLUDE:
                return totalAmount;
            case TAXABLE_INCLUDE:
                if (taxRate.compareTo(BigDecimal.ZERO) == 0) return totalAmount;
                BigDecimal divider = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN));
                return totalAmount.divide(divider, 10, RoundingMode.DOWN);
            case TAX_FREE:
                return totalAmount;
            default:
                log.error("未知の税区分: OrderDetail ID={}, TaxType={}", detail.getOrderDetailNo(), taxType);
                return totalAmount;
        }
    }

    // --- Helper Classes ---
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    private static class SummaryKey {
        private final Integer shopNo;
        private final Integer partnerNo;
        private final BigDecimal taxRate;
        private final boolean isOtakeGarbageBag;
        private final Integer orderNo;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    private static class PartnerBillingInfo {
        private final String partnerCode;
        private final Integer cutoffDate;
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    private static class ShopInvoiceKey {
        private final Integer shopNo;
        private final String invoicePartnerCode;
    }
}
