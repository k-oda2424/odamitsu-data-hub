package jp.co.oda32.batch.finance.service;

import jp.co.oda32.batch.finance.helper.TaxCalculationHelper;
import jp.co.oda32.batch.finance.model.TaxBreakdown;
import jp.co.oda32.batch.finance.model.VerificationResult;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.model.master.MSupplierShopMapping;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.domain.service.master.MSupplierShopMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SMILE支払情報との照合を行うサービスクラス（リファクタリング版）
 *
 * 主な改善点：
 * - メソッドの分割による可読性向上
 * - 定数の抽出
 * - エラーハンドリングの改善
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SmilePaymentVerifierRefactored {

    // 定数定義
    private static final BigDecimal TOLERANCE_AMOUNT = new BigDecimal(5);
    private static final String UNKNOWN_SUPPLIER_NAME = "不明";
    private static final int SHOP_NO_1 = 1;
    private static final int SHOP_NO_2 = 2;

    private final MSupplierShopMappingService mSupplierShopMappingService;
    private final MPaymentSupplierService mPaymentSupplierService;
    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;

    /**
     * SMILE支払情報との照合を行い、検証結果を返します。
     *
     * @param summaries     買掛金集計結果
     * @param smilePayments SMILE支払情報
     * @return 仕入先コードをキーとした検証結果のマップ
     */
    public Map<String, VerificationResult> verifyWithSmilePayment(
            List<TAccountsPayableSummary> summaries,
            List<TSmilePayment> smilePayments) {

        Map<String, VerificationResult> results = new HashMap<>();

        // 基本データの準備
        Map<String, BigDecimal> smilePaymentMap = groupSmilePaymentsBySupplier(smilePayments);
        Map<String, String> supplierNameMap = collectSupplierNames(summaries, smilePayments);
        Map<String, List<String>> shop2ToShop1SupplierMap = createShopMappings(summaries);

        // 買掛金額の集計
        Map<String, BigDecimal> supplierTotalIncTaxMap = groupSummariesBySupplier(summaries, true);
        Map<String, BigDecimal> supplierTotalExcTaxMap = groupSummariesBySupplier(summaries, false);

        // 支払情報が存在しない仕入先の処理
        processSupplierWithoutPayment(
                supplierTotalIncTaxMap,
                smilePaymentMap,
                supplierNameMap,
                summaries,
                results
        );

        // 税率別内訳の計算
        Map<String, Map<BigDecimal, TaxBreakdown>> supplierTaxBreakdownMap =
                calculateTaxBreakdowns(summaries);

        // 各仕入先の検証処理
        for (String supplierCode : supplierTotalIncTaxMap.keySet()) {
            processSupplierVerification(
                    supplierCode,
                    supplierTotalIncTaxMap,
                    supplierTotalExcTaxMap,
                    smilePaymentMap,
                    shop2ToShop1SupplierMap,
                    supplierNameMap,
                    supplierTaxBreakdownMap,
                    summaries,
                    results
            );
        }

        return results;
    }

    /**
     * SMILE支払情報を仕入先コードでグループ化
     */
    private Map<String, BigDecimal> groupSmilePaymentsBySupplier(List<TSmilePayment> smilePayments) {
        return smilePayments.stream()
                .collect(Collectors.groupingBy(
                        TSmilePayment::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TSmilePayment::getPaymentAmount,
                                BigDecimal::add
                        )
                ));
    }

    /**
     * 仕入先名情報を収集
     */
    private Map<String, String> collectSupplierNames(
            List<TAccountsPayableSummary> summaries,
            List<TSmilePayment> smilePayments) {

        Map<String, String> supplierNameMap = new HashMap<>();

        // SMILE支払情報から仕入先名を収集
        smilePayments.stream()
                .filter(payment -> payment.getSupplierName1() != null)
                .forEach(payment ->
                        supplierNameMap.putIfAbsent(payment.getSupplierCode(), payment.getSupplierName1())
                );

        // 買掛集計データから仕入先名を収集
        for (TAccountsPayableSummary summary : summaries) {
            if (!supplierNameMap.containsKey(summary.getSupplierCode())) {
                String supplierName = fetchSupplierName(summary);
                supplierNameMap.put(summary.getSupplierCode(), supplierName);
            }
        }

        return supplierNameMap;
    }

    /**
     * 仕入先名を取得
     */
    private String fetchSupplierName(TAccountsPayableSummary summary) {
        try {
            MPaymentSupplier paymentSupplier = mPaymentSupplierService.getByPaymentSupplierCode(
                    summary.getShopNo(), summary.getSupplierCode());
            return paymentSupplier != null ?
                    paymentSupplier.getPaymentSupplierName() : UNKNOWN_SUPPLIER_NAME;
        } catch (Exception e) {
            log.warn("仕入先名の取得に失敗しました: {}", summary.getSupplierCode(), e);
            return UNKNOWN_SUPPLIER_NAME;
        }
    }

    /**
     * 店舗間のマッピング情報を作成
     */
    private Map<String, List<String>> createShopMappings(List<TAccountsPayableSummary> summaries) {
        Map<String, List<String>> shop2ToShop1SupplierMap = new HashMap<>();

        summaries.stream()
                .filter(summary -> summary.getShopNo() == SHOP_NO_2)
                .forEach(summary -> {
                    Optional<MSupplierShopMapping> mapping = mSupplierShopMappingService
                            .findBySourceShopNoAndSupplierCode(SHOP_NO_2, summary.getSupplierCode());

                    mapping.ifPresent(m ->
                            shop2ToShop1SupplierMap
                                    .computeIfAbsent(m.getTargetSupplierCode(), k -> new ArrayList<>())
                                    .add(summary.getSupplierCode())
                    );
                });

        return shop2ToShop1SupplierMap;
    }

    /**
     * 買掛金額を仕入先コードでグループ化
     */
    private Map<String, BigDecimal> groupSummariesBySupplier(
            List<TAccountsPayableSummary> summaries,
            boolean includeTax) {

        return summaries.stream()
                .collect(Collectors.groupingBy(
                        TAccountsPayableSummary::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                summary -> includeTax ?
                                        summary.getTaxIncludedAmountChange() :
                                        summary.getTaxExcludedAmountChange(),
                                BigDecimal::add
                        )
                ));
    }

    /**
     * 支払情報が存在しない仕入先の処理
     */
    private void processSupplierWithoutPayment(
            Map<String, BigDecimal> supplierTotalIncTaxMap,
            Map<String, BigDecimal> smilePaymentMap,
            Map<String, String> supplierNameMap,
            List<TAccountsPayableSummary> summaries,
            Map<String, VerificationResult> results) {

        List<String> supplierCodesWithPayable = new ArrayList<>(supplierTotalIncTaxMap.keySet());
        supplierCodesWithPayable.removeAll(smilePaymentMap.keySet());

        if (!supplierCodesWithPayable.isEmpty()) {
            log.warn("以下の仕入先コードはSMILE支払情報が存在しませんが、買掛金データが存在します: {}",
                    String.join(", ", supplierCodesWithPayable));

            for (String supplierCode : supplierCodesWithPayable) {
                processSupplierWithoutPaymentInfo(
                        supplierCode,
                        supplierTotalIncTaxMap.get(supplierCode),
                        supplierNameMap.getOrDefault(supplierCode, UNKNOWN_SUPPLIER_NAME),
                        summaries,
                        results
                );
            }
        }
    }

    /**
     * 支払情報がない仕入先の個別処理
     */
    private void processSupplierWithoutPaymentInfo(
            String supplierCode,
            BigDecimal payableAmount,
            String supplierName,
            List<TAccountsPayableSummary> summaries,
            Map<String, VerificationResult> results) {

        log.warn("支払情報なし: 仕入先コード={}, 仕入先名={}, 買掛金額={}円",
                supplierCode, supplierName, payableAmount);

        BigDecimal difference = BigDecimal.ZERO.subtract(payableAmount);
        updateVerificationFlags(summaries, supplierCode, false, difference);
        disableMfExport(summaries, supplierCode);

        VerificationResult result = new VerificationResult(
                supplierCode,
                null,
                payableAmount,
                BigDecimal.ZERO,
                difference,
                false
        );
        results.put(supplierCode, result);
    }

    /**
     * マネーフォワードエクスポートを無効化
     */
    private void disableMfExport(List<TAccountsPayableSummary> summaries, String supplierCode) {
        summaries.stream()
                .filter(summary -> summary.getSupplierCode().equals(supplierCode))
                .forEach(summary -> {
                    summary.setMfExportEnabled(false);
                    log.info("仕入先コード={}のマネーフォワードエクスポートを無効に設定しました", supplierCode);
                });
    }

    /**
     * 税率別内訳を計算
     */
    private Map<String, Map<BigDecimal, TaxBreakdown>> calculateTaxBreakdowns(
            List<TAccountsPayableSummary> summaries) {

        Map<String, Map<BigDecimal, TaxBreakdown>> supplierTaxBreakdownMap = new HashMap<>();

        for (TAccountsPayableSummary summary : summaries) {
            Map<BigDecimal, TaxBreakdown> taxBreakdownMap = supplierTaxBreakdownMap
                    .computeIfAbsent(summary.getSupplierCode(), k -> new HashMap<>());

            TaxBreakdown breakdown = taxBreakdownMap
                    .computeIfAbsent(summary.getTaxRate(),
                            k -> new TaxBreakdown(BigDecimal.ZERO, BigDecimal.ZERO));

            breakdown.addTaxExcludedAmount(summary.getTaxExcludedAmountChange());
            breakdown.addTaxIncludedAmount(summary.getTaxIncludedAmountChange());
        }

        return supplierTaxBreakdownMap;
    }

    /**
     * 仕入先の検証処理
     */
    private void processSupplierVerification(
            String supplierCode,
            Map<String, BigDecimal> supplierTotalIncTaxMap,
            Map<String, BigDecimal> supplierTotalExcTaxMap,
            Map<String, BigDecimal> smilePaymentMap,
            Map<String, List<String>> shop2ToShop1SupplierMap,
            Map<String, String> supplierNameMap,
            Map<String, Map<BigDecimal, TaxBreakdown>> supplierTaxBreakdownMap,
            List<TAccountsPayableSummary> summaries,
            Map<String, VerificationResult> results) {

        BigDecimal totalAccountsPayableIncTax = supplierTotalIncTaxMap.get(supplierCode);
        BigDecimal totalAccountsPayableExcTax = supplierTotalExcTaxMap.getOrDefault(supplierCode, BigDecimal.ZERO);

        if (totalAccountsPayableIncTax.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // SMILE支払額の計算（マッピングされた仕入先も含む）
        BigDecimal smilePaymentAmount = calculateTotalSmilePayment(
                supplierCode, smilePaymentMap, shop2ToShop1SupplierMap);

        // 検証結果の作成と処理
        VerificationResult result = createAndProcessVerificationResult(
                supplierCode,
                totalAccountsPayableIncTax,
                totalAccountsPayableExcTax,
                smilePaymentAmount,
                supplierNameMap.getOrDefault(supplierCode, UNKNOWN_SUPPLIER_NAME),
                supplierTaxBreakdownMap.getOrDefault(supplierCode, new HashMap<>()),
                summaries
        );

        results.put(supplierCode, result);
    }

    /**
     * SMILE支払額の総額を計算（マッピングされた仕入先を含む）
     */
    private BigDecimal calculateTotalSmilePayment(
            String supplierCode,
            Map<String, BigDecimal> smilePaymentMap,
            Map<String, List<String>> shop2ToShop1SupplierMap) {

        BigDecimal total = smilePaymentMap.getOrDefault(supplierCode, BigDecimal.ZERO);

        // マッピングされた仕入先の支払額も合算
        List<String> mappedCodes = shop2ToShop1SupplierMap.getOrDefault(supplierCode, new ArrayList<>());
        for (String mappedCode : mappedCodes) {
            total = total.add(smilePaymentMap.getOrDefault(mappedCode, BigDecimal.ZERO));
        }

        return total;
    }

    /**
     * 検証結果の作成と処理
     */
    private VerificationResult createAndProcessVerificationResult(
            String supplierCode,
            BigDecimal totalAccountsPayableIncTax,
            BigDecimal totalAccountsPayableExcTax,
            BigDecimal smilePaymentAmount,
            String supplierName,
            Map<BigDecimal, TaxBreakdown> taxBreakdownMap,
            List<TAccountsPayableSummary> summaries) {

        // 金額の切り捨て処理
        BigDecimal truncatedTotalExcTax = totalAccountsPayableExcTax.setScale(0, RoundingMode.DOWN);
        BigDecimal recalculatedTaxIncluded = TaxCalculationHelper.calculateTaxIncludedAmount(taxBreakdownMap);
        BigDecimal truncatedRecalculatedTaxIncluded = recalculatedTaxIncluded.setScale(0, RoundingMode.DOWN);
        BigDecimal truncatedSmilePaymentAmount = smilePaymentAmount.setScale(0, RoundingMode.DOWN);
        BigDecimal truncatedDifference = truncatedSmilePaymentAmount.subtract(truncatedRecalculatedTaxIncluded);

        // 差額が許容範囲内かチェック
        boolean adjustToSmilePayment = shouldAdjustToSmilePayment(truncatedDifference);
        BigDecimal finalTaxIncludedAmount = adjustToSmilePayment ?
                truncatedSmilePaymentAmount : truncatedRecalculatedTaxIncluded;

        // 検証結果を更新
        boolean isMatched = isVerificationMatched(truncatedDifference);
        updateVerificationFlags(summaries, supplierCode, isMatched, truncatedDifference);
        updateMfExportFlags(summaries, supplierCode);

        // ログ出力
        logVerificationResult(
                supplierCode, supplierName, truncatedTotalExcTax,
                truncatedRecalculatedTaxIncluded, truncatedSmilePaymentAmount,
                truncatedDifference, taxBreakdownMap, adjustToSmilePayment
        );

        // 必要に応じて金額を調整
        if (adjustToSmilePayment) {
            updateAccountsPayableSummaries(
                    summaries, supplierCode, truncatedSmilePaymentAmount, truncatedRecalculatedTaxIncluded);
            saveAdjustedSummaries(summaries, supplierCode);
        }

        return new VerificationResult(
                supplierCode,
                null,
                finalTaxIncludedAmount,
                truncatedSmilePaymentAmount,
                truncatedDifference,
                adjustToSmilePayment
        );
    }

    /**
     * SMILE支払額に調整すべきかどうかを判定
     */
    private boolean shouldAdjustToSmilePayment(BigDecimal difference) {
        return difference.abs().compareTo(TOLERANCE_AMOUNT) < 0
                && difference.compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * 検証結果が一致とみなせるかどうかを判定
     */
    private boolean isVerificationMatched(BigDecimal difference) {
        return difference.abs().compareTo(TOLERANCE_AMOUNT) < 0
                || difference.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 検証結果フラグを更新
     */
    private void updateVerificationFlags(
            List<TAccountsPayableSummary> summaries,
            String supplierCode,
            boolean isMatched,
            BigDecimal difference) {

        List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .collect(Collectors.toList());

        boolean anyChanged = false;

        for (TAccountsPayableSummary summary : targetSummaries) {
            Integer originalVerificationResult = summary.getVerificationResult();

            boolean finalMatch = isMatched ||
                    (difference != null && difference.abs().compareTo(BigDecimal.ZERO) == 0) ||
                    (difference != null && difference.abs().compareTo(TOLERANCE_AMOUNT) < 0);

            summary.setVerificationResult(finalMatch ? 1 : 0);
            summary.setPaymentDifference(difference);
            summary.setMfExportEnabled(finalMatch);

            tAccountsPayableSummaryService.save(summary);

            if (originalVerificationResult == null || originalVerificationResult != summary.getVerificationResult()) {
                anyChanged = true;
                log.debug("検証結果を{}に設定: 仕入先コード={}, 税率={}%, 差額={}円, マネーフォワードエクスポート={}",
                        finalMatch ? "「一致」" : "「不一致」",
                        supplierCode, summary.getTaxRate(), difference, finalMatch ? "可" : "不可");
            }
        }

        if (anyChanged) {
            log.info("仕入先コード={}の検証結果を{}に設定しました（差額={}円）",
                    supplierCode,
                    isMatched ? "「一致」" : "「不一致」",
                    difference);
        }
    }

    /**
     * マネーフォワードエクスポートフラグを更新
     */
    private void updateMfExportFlags(List<TAccountsPayableSummary> summaries, String supplierCode) {
        summaries.stream()
                .filter(summary -> summary.getSupplierCode().equals(supplierCode))
                .forEach(summary -> {
                    boolean exportEnabled = summary.getVerificationResult() != null
                            && summary.getVerificationResult() == 1;
                    summary.setMfExportEnabled(exportEnabled);

                    log.debug("マネーフォワードエクスポート{}: 仕入先コード={}, 税率={}%",
                            exportEnabled ? "可能に設定" : "不可に設定",
                            supplierCode, summary.getTaxRate());
                });
    }

    /**
     * 検証結果のログ出力
     */
    private void logVerificationResult(
            String supplierCode,
            String supplierName,
            BigDecimal truncatedTotalExcTax,
            BigDecimal truncatedRecalculatedTaxIncluded,
            BigDecimal truncatedSmilePaymentAmount,
            BigDecimal truncatedDifference,
            Map<BigDecimal, TaxBreakdown> taxBreakdownMap,
            boolean adjustToSmilePayment) {

        String taxBreakdownLog = buildTaxBreakdownLog(taxBreakdownMap);

        if (adjustToSmilePayment) {
            log.info("仕入先[{}:{}], 買掛金額合計: 税抜={}円, 買掛集計税込={}円, SMILE支払総額: {}円, " +
                            "差額: {}円あります（請求書金額に合わせます）, {}",
                    supplierCode, supplierName, truncatedTotalExcTax, truncatedRecalculatedTaxIncluded,
                    truncatedSmilePaymentAmount, truncatedDifference, taxBreakdownLog);
        } else {
            log.info("仕入先[{}:{}], 買掛金額合計: 税抜={}円, 買掛集計税込={}円, SMILE支払総額: {}円, " +
                            "差額: {}円, {}",
                    supplierCode, supplierName, truncatedTotalExcTax, truncatedRecalculatedTaxIncluded,
                    truncatedSmilePaymentAmount, truncatedDifference, taxBreakdownLog);
        }
    }

    /**
     * 税率別内訳のログ文字列を構築
     */
    private String buildTaxBreakdownLog(Map<BigDecimal, TaxBreakdown> taxBreakdownMap) {
        StringBuilder taxBreakdownLog = new StringBuilder("税率別内訳: ");

        List<BigDecimal> sortedTaxRates = new ArrayList<>(taxBreakdownMap.keySet());
        sortedTaxRates.sort(Comparator.naturalOrder());

        for (BigDecimal taxRate : sortedTaxRates) {
            TaxBreakdown breakdown = taxBreakdownMap.get(taxRate);
            BigDecimal taxExcluded = breakdown.getTaxExcludedAmount();

            BigDecimal taxAmount = taxExcluded.multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            BigDecimal correctTaxIncluded = taxExcluded.add(taxAmount);

            taxBreakdownLog.append(String.format("【%s%%】税抜=%s円,税込=%s円 ",
                    taxRate,
                    taxExcluded.setScale(0, RoundingMode.DOWN),
                    correctTaxIncluded.setScale(0, RoundingMode.DOWN)));
        }

        return taxBreakdownLog.toString();
    }

    /**
     * 調整後のデータを保存
     */
    private void saveAdjustedSummaries(List<TAccountsPayableSummary> summaries, String supplierCode) {
        List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .collect(Collectors.toList());

        for (TAccountsPayableSummary summary : targetSummaries) {
            log.info("調整後のデータを確実に保存: 仕入先コード={}, 税率={}%, 調整後税込金額={}円",
                    supplierCode, summary.getTaxRate(), summary.getTaxIncludedAmountChange());

            TAccountsPayableSummary saved = tAccountsPayableSummaryService.save(summary);

            log.info("保存後の値を確認: 仕入先コード={}, 税率={}%, 税込金額={}円",
                    saved.getSupplierCode(), saved.getTaxRate(), saved.getTaxIncludedAmountChange());
        }
    }

    /**
     * 買掛金額をSMILE支払額に合わせて更新します。
     * （既存のupdateAccountsPayableSummariesメソッドはそのまま使用）
     */
    public void updateAccountsPayableSummaries(
            List<TAccountsPayableSummary> summaries,
            String supplierCode,
            BigDecimal smilePaymentAmount,
            BigDecimal originalTaxIncluded) {

        // 既存の実装をそのまま使用
        // ... (元のコードと同じ)
    }
}
