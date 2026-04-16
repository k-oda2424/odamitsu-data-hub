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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 買掛金額の集計を行うサービスクラス。
 *
 * <h2>集計仕様（重要）</h2>
 * <ul>
 *   <li><b>集計元</b>: {@code t_purchase_detail}（仕入明細）</li>
 *   <li><b>集計単位</b>: (買掛管理ショップ=1, 支払先No, 支払先コード, 税率) の4キー</li>
 *   <li><b>買掛管理ショップ</b>: {@link FinanceConstants#ACCOUNTS_PAYABLE_SHOP_NO}
 *       (=1, 第1事業部) に固定。仕入は shop_no=1(第1事業部) と shop_no=2(第2事業部)
 *       の両方で発生するが、買掛管理は第1事業部が一元管理する運用のため、
 *       shop_no=2 の仕入も含めて shop_no=1 の買掛集計に合算する。
 *       <p>なお、第1事業部 SMILE には第2事業部の月次集約を `goods_code=00000021/00000023`
 *       で手入力する運用があるが、それはワーク→本テーブル取込時点
 *       ({@code WSmilePurchaseOutputFileRepository.findNewPurchases} の NOT IN 条件)
 *       で除外されるため、本テーブルには入らず shop_no=2 の個別仕入と二重計上にはならない。
 *       設計思想: 買掛集計は shop_no=2 個別仕入（生データ）を源泉とし、経理の手入力集約
 *       (00000021/00000023) には依存しない。経理手入力漏れでも集計が自動計算される運用。</li>
 *   <li><b>除外</b>:
 *     <ul>
 *       <li>{@code supplier_no = 303}（小田光在庫表・手打ち用の特殊仕入先）</li>
 *       <li>{@code goods_code IN ('00000021', '00000023')}
 *           （第2事業部の月次集約仕入。第2事業部の個別仕入と重複するため、
 *           詳細は {@link FinanceConstants#DIVISION2_AGGREGATE_GOODS_CODES}）</li>
 *       <li>{@code t_purchase.del_flg='1'} / {@code m_supplier.del_flg='1'} /
 *           {@code m_payment_supplier.del_flg='1'}（論理削除済み）</li>
 *       <li>{@code m_supplier.payment_supplier_no IS NULL}（親支払先未設定）</li>
 *     </ul>
 *   </li>
 *   <li><b>税額計算</b>: 支払先×税率単位で税抜を合算してから一度だけ税率を乗じる
 *       （{@link RoundingMode#DOWN} で円未満切り捨て、TaxCalculationHelper と統一）</li>
 * </ul>
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class AccountsPayableSummaryCalculator {

    private final TPurchaseDetailService tPurchaseDetailService;

    /**
     * 買掛金額の集計を行います。
     * <p>仕様詳細はクラス Javadoc 参照。
     *
     * @param startDate 集計対象の仕入日 from（通常 前月21日）
     * @param endDate   集計対象の仕入日 to（通常 当月20日。`transaction_month` にも使用）
     * @return 支払先ショップ×支払先×税率 単位の買掛金集計 Entity
     */
    public List<TAccountsPayableSummary> calculatePayableSummaries(LocalDate startDate, LocalDate endDate) {
        // 指定された期間の仕入明細を取得（pd.del_flg='0' のみ）
        List<TPurchaseDetail> tPurchaseDetails = tPurchaseDetailService.find(
                startDate,
                endDate,
                Flag.NO
        );

        // 集計除外の警告は集約してから出す（大量の明細で重複ログを出さないため）
        Set<String> missingPaymentSupplier = new LinkedHashSet<>();
        Set<String> logicallyDeletedMaster = new LinkedHashSet<>();
        Set<String> missingPaymentSupplierMaster = new LinkedHashSet<>();
        Set<String> missingTaxRate = new LinkedHashSet<>();

        Map<SummaryKey, TaxAggregationResult> summaryMap = tPurchaseDetails.stream()
                // 除外1: 小田光在庫表(supplier_no=303) の手打ち商品は買掛対象外
                .filter(d -> d.getTPurchase().getSupplierNo() != FinanceConstants.EXCLUDED_SUPPLIER_NO)
                // 除外2: 第2事業部の月次集約商品コード(00000021/00000023)は、shop_no=2 の
                // 個別仕入と重複する事務処理行のため除外（通常はワーク→本テーブル取込時点で
                // 既に除外されているが、過去データ用の防御フィルタとして残す。詳細は
                // {@link FinanceConstants#DIVISION2_AGGREGATE_GOODS_CODES}）
                .filter(d -> d.getGoodsCode() == null
                        || !FinanceConstants.DIVISION2_AGGREGATE_GOODS_CODES.contains(d.getGoodsCode()))
                // 除外3: 論理削除済みマスタ/伝票はスキップ（pd.del_flg は find() で既にフィルタ済）
                .filter(d -> isActive(d, logicallyDeletedMaster))
                // 除外4: 親支払先未設定の仕入先は買掛金の突合キーが作れないためスキップ
                .filter(d -> {
                    MSupplier supplier = d.getTPurchase().getMSupplier();
                    if (supplier == null || supplier.getPaymentSupplierNo() == null) {
                        String key = (supplier != null ? supplier.getShopNo() + "/" + supplier.getSupplierCode() : "null-supplier");
                        missingPaymentSupplier.add(key);
                        return false;
                    }
                    return true;
                })
                // 除外5: m_payment_supplier 実体がロードできない (= 親支払先マスタ未登録 or JOIN 不整合)
                .filter(d -> {
                    MPaymentSupplier ps = d.getTPurchase().getMSupplier().getPaymentSupplier();
                    if (ps == null) {
                        missingPaymentSupplierMaster.add(
                                "supplier_code=" + d.getTPurchase().getMSupplier().getSupplierCode()
                                        + " payment_supplier_no=" + d.getTPurchase().getMSupplier().getPaymentSupplierNo());
                        return false;
                    }
                    return true;
                })
                // 除外6: 税率 null は後段で税額が計算できない → 明示的に除外してログ
                .filter(d -> {
                    if (d.getTaxRate() == null) {
                        missingTaxRate.add("purchase_no=" + d.getPurchaseNo() + " detail_no=" + d.getPurchaseDetailNo());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.groupingBy(
                        detail -> {
                            MSupplier supplier = detail.getTPurchase().getMSupplier();
                            MPaymentSupplier paymentSupplier = supplier.getPaymentSupplier();
                            // 買掛管理は第1事業部に集約する運用のため、shop_no=2 の仕入も
                            // 強制的に shop_no=1 のキーに寄せて合算する（クラス Javadoc 参照）。
                            Integer shopNo = FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO;
                            Integer paymentSupplierNo = supplier.getPaymentSupplierNo();
                            // 除外5 フィルタで paymentSupplier non-null を保証済み
                            String paymentSupplierCode = paymentSupplier.getPaymentSupplierCode();
                            BigDecimal taxRate = detail.getTaxRate(); // 除外6 で non-null 保証
                            return new SummaryKey(shopNo, paymentSupplierNo, paymentSupplierCode, taxRate);
                        },
                        // 各明細の税抜金額(subtotal)のみ合算。消費税は集計後に税率を乗じて一括算出する。
                        Collectors.reducing(
                                new TaxAggregationResult(BigDecimal.ZERO, BigDecimal.ZERO),
                                detail -> {
                                    BigDecimal baseAmount = detail.getSubtotal() != null
                                            ? detail.getSubtotal() : BigDecimal.ZERO;
                                    return new TaxAggregationResult(baseAmount, BigDecimal.ZERO);
                                },
                                TaxAggregationResult::add
                        )
                ));

        if (!missingPaymentSupplier.isEmpty()) {
            log.warn("親支払先(payment_supplier_no)未設定のため集計対象外とした仕入先: {}", missingPaymentSupplier);
        }
        if (!logicallyDeletedMaster.isEmpty()) {
            log.warn("関連マスタ/伝票が論理削除済のため集計対象外とした仕入先: {}", logicallyDeletedMaster);
        }
        if (!missingPaymentSupplierMaster.isEmpty()) {
            log.warn("m_payment_supplier マスタがロードできず集計対象外とした仕入先: {}", missingPaymentSupplierMaster);
        }
        if (!missingTaxRate.isEmpty()) {
            log.warn("tax_rate 未設定のため集計対象外とした仕入明細: {}", missingTaxRate);
        }

        // 集計結果を元に、TAccountsPayableSummaryエンティティを生成する
        return summaryMap.entrySet().stream()
                .filter(entry -> entry.getValue().getBaseAmount().compareTo(BigDecimal.ZERO) > 0) // 買掛の無い仕入先を除外
                .map(entry -> {
                    SummaryKey key = entry.getKey();
                    TaxAggregationResult agg = entry.getValue();

                    // 税抜金額
                    BigDecimal taxExcludedAmountChange = agg.getBaseAmount();

                    // 消費税額を新たに計算（仕入先・税率単位で一括計算）
                    // 税率 null は L120-126 のフィルタで除外済みのため、ここでは常に non-null
                    // 丸めは TaxCalculationHelper と統一するため RoundingMode.DOWN（切り捨て）
                    BigDecimal taxRate = key.getTaxRate();
                    BigDecimal calculatedTaxAmount = taxExcludedAmountChange.multiply(taxRate)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);

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

    /**
     * 仕入明細と関連マスタの論理削除フラグをチェックし、集計対象かを判定する。
     * 対象外の場合は {@code report} に識別情報を追加し、呼び出し側で警告ログに出す。
     *
     * @return すべて del_flg='0' なら true
     */
    private static boolean isActive(TPurchaseDetail detail, Set<String> report) {
        // 該当する全理由を「;」区切りで1レコードに集約して記録する。
        // purchase_no + purchase_detail_no で一意特定できるキーを先頭に付与。
        String recordKey = "purchase_no=" + detail.getPurchaseNo()
                + "/detail_no=" + detail.getPurchaseDetailNo();
        List<String> reasons = new ArrayList<>();
        if (detail.getTPurchase() != null && Flag.YES.getValue().equals(detail.getTPurchase().getDelFlg())) {
            reasons.add("t_purchase.del_flg=1");
        }
        MSupplier supplier = detail.getTPurchase() != null ? detail.getTPurchase().getMSupplier() : null;
        if (supplier != null && Flag.YES.getValue().equals(supplier.getDelFlg())) {
            reasons.add("m_supplier.del_flg=1 supplier_code=" + supplier.getSupplierCode());
        }
        MPaymentSupplier paymentSupplier = supplier != null ? supplier.getPaymentSupplier() : null;
        if (paymentSupplier != null && Flag.YES.getValue().equals(paymentSupplier.getDelFlg())) {
            reasons.add("m_payment_supplier.del_flg=1 payment_supplier_code=" + paymentSupplier.getPaymentSupplierCode());
        }
        if (reasons.isEmpty()) return true;
        report.add(recordKey + " [" + String.join("; ", reasons) + "]");
        return false;
    }
}
