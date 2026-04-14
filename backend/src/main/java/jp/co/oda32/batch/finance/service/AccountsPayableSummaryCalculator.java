package jp.co.oda32.batch.finance.service;

import jp.co.oda32.batch.finance.model.SummaryKey;
import jp.co.oda32.batch.finance.model.TaxAggregationResult;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.service.purchase.TPurchaseDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 買掛金額の集計を行うサービスクラス
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class AccountsPayableSummaryCalculator {

    private final TPurchaseDetailService tPurchaseDetailService;

    /**
     * 買掛金額の集計を行います。
     *
     * @param startDate 集計する日付from
     * @param endDate   集計する日付to
     * @return 各支払先毎の買掛金集計Entity
     */
    public List<TAccountsPayableSummary> calculatePayableSummaries(LocalDate startDate, LocalDate endDate) {
        // 指定された期間内の注文詳細情報を取得
        List<TPurchaseDetail> tPurchaseDetails = tPurchaseDetailService.find(
                startDate,
                endDate,
                Flag.NO
        );

        // 税率ごとに、取り込んだ明細の税抜金額（baseAmount）を集計し、消費税額は集計後に一括計算する
        Map<SummaryKey, TaxAggregationResult> summaryMap = tPurchaseDetails.stream()
                // 小田光在庫表は棚卸時に手打ち商品について入れているだけなので無視する
                .filter(tPurchaseDetail -> tPurchaseDetail.getTPurchase().getSupplierNo() != FinanceConstants.EXCLUDED_SUPPLIER_NO)
                .collect(Collectors.groupingBy(
                        detail -> {
                            Integer shopNo = detail.getShopNo();
                            MSupplier supplier = detail.getTPurchase().getMSupplier();
                            Integer paymentSupplierNo = supplier.getPaymentSupplierNo();
                            if (paymentSupplierNo == null) {
                                System.out.printf("shop_no:%d, supplier_code:%s%n", supplier.getShopNo(), supplier.getSupplierCode());
                            }
                            String paymentSupplierCode = null;
                            MPaymentSupplier paymentSupplier = supplier.getPaymentSupplier();
                            if (paymentSupplier != null) {
                                paymentSupplierCode = paymentSupplier.getPaymentSupplierCode();
                            }
                            BigDecimal taxRate = detail.getTaxRate();
                            return new SummaryKey(shopNo, paymentSupplierNo, paymentSupplierCode, taxRate);
                        },
                        // 各明細から、税抜金額（baseAmount）のみを集計し、消費税額は後で一括計算する
                        Collectors.reducing(
                                new TaxAggregationResult(BigDecimal.ZERO, BigDecimal.ZERO),
                                detail -> {
                                    // 税抜金額のみを取得（null値の場合はBigDecimal.ZEROを使用）
                                    BigDecimal baseAmount = detail.getSubtotal() != null ? detail.getSubtotal() : BigDecimal.ZERO;

                                    // 消費税額は0で初期化（後で一括計算）
                                    BigDecimal taxAmount = BigDecimal.ZERO;

                                    return new TaxAggregationResult(baseAmount, taxAmount);
                                },
                                TaxAggregationResult::add
                        )
                ));

        // 集計結果を元に、TAccountsPayableSummaryエンティティを生成する
        return summaryMap.entrySet().stream()
                .filter(entry -> entry.getValue().getBaseAmount().compareTo(BigDecimal.ZERO) > 0) // 買掛の無い仕入先を除外
                .map(entry -> {
                    SummaryKey key = entry.getKey();
                    TaxAggregationResult agg = entry.getValue();

                    // 税抜金額
                    BigDecimal taxExcludedAmountChange = agg.getBaseAmount();

                    // 消費税額を新たに計算（仕入先・税率単位で一括計算）
                    BigDecimal taxRate = key.getTaxRate() != null ? key.getTaxRate() : BigDecimal.TEN; // デフォルト10%
                    BigDecimal calculatedTaxAmount = taxExcludedAmountChange.multiply(taxRate)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_DOWN);

                    // 税込金額（税抜金額に計算した消費税額を加算）
                    BigDecimal taxIncludedAmountChange = taxExcludedAmountChange.add(calculatedTaxAmount);

                    return TAccountsPayableSummary.builder()
                            .shopNo(key.getShopNo())
                            .supplierNo(key.getPaymentSupplierNo())
                            .supplierCode(key.getPaymentSupplierCode())
                            .transactionMonth(endDate)
                            .taxRate(key.getTaxRate()) // 消費税率ごとに集計
                            .taxIncludedAmountChange(taxIncludedAmountChange) // 税込買掛合計金額（税抜金額＋計算した消費税額）
                            .taxExcludedAmountChange(taxExcludedAmountChange) // 税抜買掛合計金額
                            .build();
                })
                .collect(Collectors.toList());
    }
}
