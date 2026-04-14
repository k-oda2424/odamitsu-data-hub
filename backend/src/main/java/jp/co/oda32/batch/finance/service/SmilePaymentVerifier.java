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
 * SMILE支払情報との照合を行うサービスクラス
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SmilePaymentVerifier {

    private final MSupplierShopMappingService mSupplierShopMappingService;
    private final MPaymentSupplierService mPaymentSupplierService;
    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;

    /**
     * SMILE支払情報との照合を行い、検証結果を返します。
     * 仕入先ごとに買掛金額の総額とSMILE支払額の総額を比較します。
     * 差額が5円未満の場合はSMILE支払額に合わせます。
     *
     * @param summaries     買掛金集計結果
     * @param smilePayments SMILE支払情報
     * @return 仕入先コードをキーとした検証結果のマップ
     */
    public Map<String, VerificationResult> verifyWithSmilePayment(
            List<TAccountsPayableSummary> summaries,
            List<TSmilePayment> smilePayments) {

        Map<String, VerificationResult> results = new HashMap<>();

        // SMILE支払情報を仕入先コードでグループ化して合計額を算出
        Map<String, BigDecimal> smilePaymentMap = smilePayments.stream()
                .collect(Collectors.groupingBy(
                        TSmilePayment::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TSmilePayment::getPaymentAmount,
                                BigDecimal::add
                        )
                ));

        // 仕入先名情報を保持するマップ
        Map<String, String> supplierNameMap = new HashMap<>();

        // SMILE支払情報から仕入先名を収集
        for (TSmilePayment payment : smilePayments) {
            String supplierCode = payment.getSupplierCode();
            if (!supplierNameMap.containsKey(supplierCode) && payment.getSupplierName1() != null) {
                supplierNameMap.put(supplierCode, payment.getSupplierName1());
            }
        }

        // shop_no:2の買掛データをshop_no:1の仕入先コードにマッピング
        Map<String, List<String>> shop2ToShop1SupplierMap = new HashMap<>();

        for (TAccountsPayableSummary summary : summaries) {
            if (summary.getShopNo() == 2) {
                // shop_no:2の仕入先コードに対応するshop_no:1の仕入先コードを検索
                Optional<MSupplierShopMapping> mapping = mSupplierShopMappingService
                        .findBySourceShopNoAndSupplierCode(2, summary.getSupplierCode());

                if (mapping.isPresent()) {
                    String targetSupplierCode = mapping.get().getTargetSupplierCode();

                    // マッピング情報をマップに追加
                    shop2ToShop1SupplierMap
                            .computeIfAbsent(targetSupplierCode, k -> new ArrayList<>())
                            .add(summary.getSupplierCode());
                }
            }

            // 仕入先名を取得（まだマップに含まれていない場合のみ）
            if (!supplierNameMap.containsKey(summary.getSupplierCode())) {
                // 支払先サービスから仕入先名を取得
                try {
                    MPaymentSupplier paymentSupplier = mPaymentSupplierService.getByPaymentSupplierCode(
                            summary.getShopNo(), summary.getSupplierCode());
                    if (paymentSupplier != null) {
                        supplierNameMap.put(summary.getSupplierCode(), paymentSupplier.getPaymentSupplierName());
                    } else {
                        supplierNameMap.put(summary.getSupplierCode(), "不明");
                    }
                } catch (Exception e) {
                    log.warn("仕入先名の取得に失敗しました: {}", summary.getSupplierCode(), e);
                    supplierNameMap.put(summary.getSupplierCode(), "不明");
                }
            }
        }

        // 仕入先コードごとに買掛金額を合計（全税率分を合算）- 税込金額を使用
        Map<String, BigDecimal> supplierTotalIncTaxMap = summaries.stream()
                .collect(Collectors.groupingBy(
                        TAccountsPayableSummary::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TAccountsPayableSummary::getTaxIncludedAmountChange, // 税込金額を使用して集計
                                BigDecimal::add
                        )
                ));

        // 仕入先コードごとに買掛金額を合計（全税率分を合算）- 税抜金額を使用
        Map<String, BigDecimal> supplierTotalExcTaxMap = summaries.stream()
                .collect(Collectors.groupingBy(
                        TAccountsPayableSummary::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TAccountsPayableSummary::getTaxExcludedAmountChange, // 税抜金額を使用して集計
                                BigDecimal::add
                        )
                ));

        // 支払情報が存在しない場合のログ出力と特別処理
        List<String> supplierCodesWithPayable = new ArrayList<>(supplierTotalIncTaxMap.keySet());
        supplierCodesWithPayable.removeAll(smilePaymentMap.keySet());

        if (!supplierCodesWithPayable.isEmpty()) {
            log.warn("以下の仕入先コードはSMILE支払情報が存在しませんが、買掛金データが存在します: {}",
                    String.join(", ", supplierCodesWithPayable));

            // 支払情報がない仕入先について詳細なログを出力
            for (String supplierCode : supplierCodesWithPayable) {
                BigDecimal payableAmount = supplierTotalIncTaxMap.get(supplierCode);
                String supplierName = supplierNameMap.getOrDefault(supplierCode, "不明");

                log.warn("支払情報なし: 仕入先コード={}, 仕入先名={}, 買掛金額={}円",
                        supplierCode, supplierName, payableAmount);

                // 差額を明示的に設定（支払情報がない場合は差額は買掛金額の負値）
                BigDecimal difference = BigDecimal.ZERO.subtract(payableAmount);

                // 支払情報がないことを明示的に記録するために検証結果を「不一致」と設定
                updateVerificationFlags(summaries, supplierCode, false, difference);

                // マネーフォワードエクスポート不可のフラグを設定
                for (TAccountsPayableSummary summary : summaries) {
                    if (summary.getSupplierCode().equals(supplierCode)) {
                        if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                            log.info("手動確定済みのためMFエクスポートフラグ更新をスキップ: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                            continue;
                        }
                        summary.setMfExportEnabled(false);
                        log.info("仕入先コード={}のマネーフォワードエクスポートを無効に設定しました", supplierCode);
                    }
                }

                // 結果をマップに保存
                VerificationResult result = new VerificationResult(
                        supplierCode,
                        null,
                        payableAmount,
                        BigDecimal.ZERO, // 支払情報なし
                        difference,
                        false
                );
                results.put(supplierCode, result);
            }
        }

        // 仕入先コードと税率ごとの明細を保持するマップ
        Map<String, Map<BigDecimal, TaxBreakdown>> supplierTaxBreakdownMap = new HashMap<>();

        // 仕入先ごとの税率別内訳を計算
        for (TAccountsPayableSummary summary : summaries) {
            String supplierCode = summary.getSupplierCode();
            BigDecimal taxRate = summary.getTaxRate();
            BigDecimal taxIncludedAmount = summary.getTaxIncludedAmountChange();
            BigDecimal taxExcludedAmount = summary.getTaxExcludedAmountChange();

            // 仕入先コードごとのマップを取得または作成
            Map<BigDecimal, TaxBreakdown> taxBreakdownMap = supplierTaxBreakdownMap
                    .computeIfAbsent(supplierCode, k -> new HashMap<>());

            // 税率ごとの内訳を取得または作成
            TaxBreakdown breakdown = taxBreakdownMap
                    .computeIfAbsent(taxRate, k -> new TaxBreakdown(BigDecimal.ZERO, BigDecimal.ZERO));

            // 金額を加算
            breakdown.addTaxExcludedAmount(taxExcludedAmount);
            breakdown.addTaxIncludedAmount(taxIncludedAmount);
        }

        // 各仕入先コードに対する処理
        for (String supplierCode : supplierTotalIncTaxMap.keySet()) {
            BigDecimal totalAccountsPayableIncTax = supplierTotalIncTaxMap.get(supplierCode);
            BigDecimal totalAccountsPayableExcTax = supplierTotalExcTaxMap.getOrDefault(supplierCode, BigDecimal.ZERO);

            // マッピングされている仕入先コードのリストを取得
            List<String> mappedCodes = shop2ToShop1SupplierMap.getOrDefault(supplierCode, new ArrayList<>());

            // SMILE支払情報から支払額の総額を取得
            BigDecimal smilePaymentAmount = smilePaymentMap.getOrDefault(supplierCode, BigDecimal.ZERO);

            // マッピングされた仕入先コードの支払額も合算
            for (String mappedCode : mappedCodes) {
                smilePaymentAmount = smilePaymentAmount.add(
                        smilePaymentMap.getOrDefault(mappedCode, BigDecimal.ZERO));
            }

            // 検証結果を作成（仕入先コードごとの総額比較）
            if (totalAccountsPayableIncTax.compareTo(BigDecimal.ZERO) > 0) {
                // 小数点以下を切り捨て
                BigDecimal truncatedTotalAccountsPayableExcTax = totalAccountsPayableExcTax.setScale(0, RoundingMode.DOWN);
                BigDecimal truncatedTotalAccountsPayableIncTax = totalAccountsPayableIncTax.setScale(0, RoundingMode.DOWN);

                // 税率ごとの内訳を取得
                Map<BigDecimal, TaxBreakdown> taxBreakdownMap = supplierTaxBreakdownMap.getOrDefault(supplierCode, new HashMap<>());

                // 税抜金額から正確に計算した税込金額（比較用）
                BigDecimal recalculatedTaxIncluded = TaxCalculationHelper.calculateTaxIncludedAmount(taxBreakdownMap);
                BigDecimal truncatedRecalculatedTaxIncluded = recalculatedTaxIncluded.setScale(0, RoundingMode.DOWN);

                BigDecimal truncatedSmilePaymentAmount = smilePaymentAmount.setScale(0, RoundingMode.DOWN);
                // 再計算した税込金額とSMILE支払額の差額
                BigDecimal truncatedDifference = truncatedSmilePaymentAmount.subtract(truncatedRecalculatedTaxIncluded);

                // 仕入先名を取得
                String supplierName = supplierNameMap.getOrDefault(supplierCode, "不明");

                // 重要：差額が5円未満の場合、SMILE支払額に合わせる
                boolean adjustToSmilePayment = truncatedDifference.abs().compareTo(new BigDecimal(5)) < 0
                        && truncatedDifference.compareTo(BigDecimal.ZERO) != 0;

                // 税込金額を指定（差額が5円未満ならSMILE支払額に合わせる）
                BigDecimal finalTaxIncludedAmount = adjustToSmilePayment ?
                        truncatedSmilePaymentAmount : truncatedRecalculatedTaxIncluded;

                // 検証結果を生成（adjustToSmilePaymentフラグも設定）
                VerificationResult result = new VerificationResult(
                        supplierCode,
                        null,  // 税率はnull（全税率の合計を比較するため）
                        finalTaxIncludedAmount, // 調整後の買掛金額（税込）
                        truncatedSmilePaymentAmount, // SMILE支払額（税込）
                        truncatedDifference,     // 差額
                        adjustToSmilePayment     // SMILE支払額に調整したかどうか
                );

                // 差額が5円未満または0円なら常に「一致」のフラグを設定
                boolean isMatched = truncatedDifference.abs().compareTo(new BigDecimal(5)) < 0
                        || truncatedDifference.compareTo(BigDecimal.ZERO) == 0;

                // 対象のサマリーデータの検証結果フラグを更新
                updateVerificationFlags(summaries, supplierCode, isMatched, truncatedDifference);

                // 検証結果が「一致」の場合のみマネーフォワードエクスポート可能に設定
                for (TAccountsPayableSummary summary : summaries) {
                    if (summary.getSupplierCode().equals(supplierCode)) {
                        if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                            log.info("手動確定済みのためMFエクスポートフラグ更新をスキップ: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                            continue;
                        }
                        if (summary.getVerificationResult() != null && summary.getVerificationResult() == 1) {
                            summary.setMfExportEnabled(true);
                            log.debug("検証結果「一致」のためマネーフォワードエクスポート可能に設定: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                        } else {
                            summary.setMfExportEnabled(false);
                            log.debug("検証結果「不一致」のためマネーフォワードエクスポート不可に設定: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                        }
                    }
                }

                // 結果をマップに保存
                results.put(supplierCode, result);

                // 税率ごとの内訳をログ出力
                StringBuilder taxBreakdownLog = new StringBuilder();
                taxBreakdownLog.append("税率別内訳: ");

                List<BigDecimal> sortedTaxRates = new ArrayList<>(taxBreakdownMap.keySet());
                sortedTaxRates.sort(Comparator.naturalOrder());

                for (BigDecimal taxRate : sortedTaxRates) {
                    TaxBreakdown breakdown = taxBreakdownMap.get(taxRate);
                    BigDecimal taxExcluded = breakdown.getTaxExcludedAmount();

                    // 正確に計算した税込金額
                    BigDecimal taxAmount = taxExcluded.multiply(taxRate)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
                    BigDecimal correctTaxIncluded = taxExcluded.add(taxAmount);

                    taxBreakdownLog.append(String.format("【%s%%】税抜=%s円,税込=%s円 ",
                            taxRate,
                            taxExcluded.setScale(0, RoundingMode.DOWN),
                            correctTaxIncluded.setScale(0, RoundingMode.DOWN)));
                }

                // メインログ出力
                if (adjustToSmilePayment) {
                    // 差額が5円未満の場合、特別なログを出力
                    log.info("仕入先[{}:{}], 買掛金額合計: 税抜={}円, 買掛集計税込={}円, SMILE支払総額: {}円, 差額: {}円あります（請求書金額に合わせます）, {}",
                            supplierCode,
                            supplierName,
                            truncatedTotalAccountsPayableExcTax,
                            truncatedRecalculatedTaxIncluded,
                            truncatedSmilePaymentAmount,
                            truncatedDifference,
                            taxBreakdownLog.toString());

                    // 仕入明細のtaxIncludedAmountChangeを更新（差額が5円未満の場合）
                    updateAccountsPayableSummaries(summaries, supplierCode, truncatedSmilePaymentAmount, truncatedRecalculatedTaxIncluded);

                    // 調整後のデータをデータベースに確実に保存
                    List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                            .filter(s -> s.getSupplierCode().equals(supplierCode))
                            .collect(Collectors.toList());

                    for (TAccountsPayableSummary summary : targetSummaries) {
                        log.info("調整後のデータを確実に保存: 仕入先コード={}, 税率={}%, 調整後税込金額={}円",
                                supplierCode, summary.getTaxRate(), summary.getTaxIncludedAmountChange());
                        TAccountsPayableSummary saved = tAccountsPayableSummaryService.save(summary);
                        // 保存後の値も確認
                        log.info("保存後の値を確認: 仕入先コード={}, 税率={}%, 税込金額={}円",
                                saved.getSupplierCode(), saved.getTaxRate(), saved.getTaxIncludedAmountChange());
                    }
                } else {
                    // 通常のログ出力
                    log.info("仕入先[{}:{}], 買掛金額合計: 税抜={}円, 買掛集計税込={}円, SMILE支払総額: {}円, 差額: {}円, {}",
                            supplierCode,
                            supplierName,
                            truncatedTotalAccountsPayableExcTax,
                            truncatedRecalculatedTaxIncluded,
                            truncatedSmilePaymentAmount,
                            truncatedDifference,
                            taxBreakdownLog.toString());
                }
            }
        }

        return results;
    }

    /**
     * 対象の仕入先の検証結果フラグを更新します。
     *
     * @param summaries    対象の買掛金集計リスト
     * @param supplierCode 仕入先コード
     * @param isMatched    一致しているかどうか（true: 一致, false: 不一致）
     * @param difference   差額
     */
    private void updateVerificationFlags(List<TAccountsPayableSummary> summaries,
                                         String supplierCode,
                                         boolean isMatched,
                                         BigDecimal difference) {
        // 対象の仕入先のサマリーデータを抽出
        List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .collect(Collectors.toList());

        // ロギング用に元の状態を確認
        boolean anyChanged = false;

        for (TAccountsPayableSummary summary : targetSummaries) {
            // 手動確定済み行はSMILE再検証で上書きしない
            if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                log.info("手動確定済みのため検証結果更新をスキップ: 仕入先コード={}, 税率={}%",
                        supplierCode, summary.getTaxRate());
                continue;
            }

            // 元の値を保存
            Integer originalVerificationResult = summary.getVerificationResult();

            // 検証結果フラグを設定（1: 一致, 0: 不一致）
            // 差額が0円または絶対値が5円未満の場合は常に「一致」とする
            boolean finalMatch = isMatched ||
                    (difference != null && difference.abs().compareTo(BigDecimal.ZERO) == 0) ||
                    (difference != null && difference.abs().compareTo(new BigDecimal(5)) < 0);

            summary.setVerificationResult(finalMatch ? 1 : 0);
            // 差額も設定
            summary.setPaymentDifference(difference);

            // マネーフォワードエクスポートフラグを設定（一致時のみtrue）
            summary.setMfExportEnabled(finalMatch);

            // 変更を保存
            tAccountsPayableSummaryService.save(summary);

            // 値が変更された場合にログ出力
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
     * 買掛金額をSMILE支払額に合わせて更新します。
     * 最も金額の大きい税率の金額を優先的に調整します。
     *
     * @param summaries           更新対象の買掛金集計リスト
     * @param supplierCode        仕入先コード
     * @param smilePaymentAmount  SMILE支払額
     * @param originalTaxIncluded 元の買掛金額合計
     */
    public void updateAccountsPayableSummaries(List<TAccountsPayableSummary> summaries,
                                               String supplierCode,
                                               BigDecimal smilePaymentAmount,
                                               BigDecimal originalTaxIncluded) {

        // 対象の仕入先の買掛データを抽出（手動確定済みは除外）
        List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .filter(s -> !Boolean.TRUE.equals(s.getVerifiedManually()))
                .collect(Collectors.toList());

        long manualCount = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .filter(s -> Boolean.TRUE.equals(s.getVerifiedManually()))
                .count();
        if (manualCount > 0) {
            log.info("手動確定済み {}件 を買掛金額調整から除外: 仕入先コード={}", manualCount, supplierCode);
        }

        if (targetSummaries.isEmpty() || originalTaxIncluded.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // 差額を計算
        BigDecimal difference = smilePaymentAmount.subtract(originalTaxIncluded);
        log.info("買掛金額調整: 仕入先コード={}, 買掛金額={}, SMILE支払額={}, 差額={}円",
                supplierCode, originalTaxIncluded, smilePaymentAmount, difference);

        // 税込金額の大きい順にソート
        targetSummaries.sort(Comparator.comparing(s ->
                        s.getTaxIncludedAmountChange() != null ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO,
                Comparator.reverseOrder()));

        // 最大金額のサマリーを選択
        TAccountsPayableSummary largestSummary = targetSummaries.get(0);

        // 元の税込金額
        BigDecimal originalAmount = largestSummary.getTaxIncludedAmountChange();

        // 差額を加算して調整（SMILE支払額に合わせる）
        // ここで、合計金額に対する差額を適用するのではなく、
        // SMILE支払額から他のレコードの合計を引いて算出する
        BigDecimal otherSummariesTotal = targetSummaries.stream()
                .filter(s -> !s.equals(largestSummary))
                .map(TAccountsPayableSummary::getTaxIncludedAmountChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 最大レコードの金額は、SMILE支払額から他のレコード合計を引いた値にする
        BigDecimal adjustedAmount = smilePaymentAmount.subtract(otherSummariesTotal).setScale(0, RoundingMode.DOWN);

        // 金額がマイナスになる場合は調整方法を変更
        if (adjustedAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("最大金額への差額調整でマイナスになるため、差額のみ記録し、金額は調整しません");
            // 差額を記録するだけで、金額は調整しない
            for (TAccountsPayableSummary summary : targetSummaries) {
                // 差額を設定するだけ
                summary.setPaymentDifference(difference);
                // 変更を保存
                tAccountsPayableSummaryService.save(summary);
            }
        } else {
            // 最大金額のレコードのみで調整
            log.info("最大金額のレコードのみで調整: 仕入先コード={}, 税率={}%, 元の金額={}円, 調整後金額={}円",
                    supplierCode, largestSummary.getTaxRate(), originalAmount, adjustedAmount);

            // 金額を更新
            largestSummary.setTaxIncludedAmountChange(adjustedAmount);

            // オリジナルのsummariesリスト内の対応するオブジェクトも直接更新
            for (TAccountsPayableSummary originalSummary : summaries) {
                if (originalSummary.getShopNo().equals(largestSummary.getShopNo()) &&
                        originalSummary.getSupplierNo().equals(largestSummary.getSupplierNo()) &&
                        originalSummary.getTransactionMonth().equals(largestSummary.getTransactionMonth()) &&
                        originalSummary.getTaxRate().equals(largestSummary.getTaxRate())) {
                    originalSummary.setTaxIncludedAmountChange(adjustedAmount);
                    break;
                }
            }

            // 変更を保存
            TAccountsPayableSummary saved = tAccountsPayableSummaryService.save(largestSummary);
            log.info("調整後のデータを保存しました: 仕入先コード={}, 税率={}%, 税込金額={}円",
                    saved.getSupplierCode(), saved.getTaxRate(), saved.getTaxIncludedAmountChange());
        }

        // 最終確認：合計金額がSMILE支払額と一致することを確認
        BigDecimal finalTotal = targetSummaries.stream()
                .map(TAccountsPayableSummary::getTaxIncludedAmountChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalDifference = smilePaymentAmount.subtract(finalTotal);

        // 最終差額が0でない場合は警告ログを出力
        if (finalDifference.compareTo(BigDecimal.ZERO) != 0) {
            log.warn("調整後も差額が残っています: {}円", finalDifference);
        }

        log.info("買掛金額調整結果: 仕入先コード={}, 調整前合計={}円, 調整後合計={}円, SMILE支払額={}円, 残差額={}円",
                supplierCode,
                originalTaxIncluded,
                finalTotal,
                smilePaymentAmount,
                smilePaymentAmount.subtract(finalTotal));
    }
}
