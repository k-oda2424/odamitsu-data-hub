package jp.co.oda32.batch.finance.service;

import jp.co.oda32.batch.finance.service.PayableMonthlyAggregator.PrevMonthData;
import jp.co.oda32.batch.finance.service.PayableMonthlyAggregator.SupplierAgg;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PayableMonthlyAggregator} のゴールデンマスタ単体テスト。
 * <p>
 * T3 案 D'' Phase 3 で追加 (2026-05-06)。
 * 改修起因の集計値変動を検知するため、純 Java fixture (builder-based) で代表シナリオを locked-in する。
 * <p>
 * 本テストが fail した時の判断:
 * <ul>
 *   <li><b>意図通りの仕様変更</b> → 期待値を改修目的に合致した値に更新 (PR で根拠を明記)</li>
 *   <li><b>意図外の副作用</b> → 改修見直し (過去確定月への影響、按分式バグ、手動確定行保護漏れ等)</li>
 * </ul>
 * <p>
 * 詳細手順: {@code claudedocs/runbook-finance-recalc-impact-analysis.md} §3 §4 §6
 * <p>
 * シナリオ:
 * <ul>
 *   <li>{@link Scenario1_SingleSupplierSingleMonth}: 単一 supplier × 単月 (10% 税率のみ) の按分基本</li>
 *   <li>{@link Scenario2_MultipleSuppliersMultipleTaxRates}: 複数 supplier × 税率混在 (8% + 10%) の按分</li>
 *   <li>{@link Scenario3_PaymentOnlyRowGeneration}: 前月 paid>0 / 当月 change=0 の payment-only 行生成</li>
 *   <li>{@link Scenario4_MfDebitOverrideAutoOnly}: MF debit 上書き (案 A) — 全行 auto</li>
 *   <li>{@link Scenario5_MfDebitOverrideManualProtect}: MF debit 上書き — 手動確定行 protect (案 A)</li>
 * </ul>
 *
 * @since 2026-05-06 (T3 案 D'' Phase 3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayableMonthlyAggregatorGoldenTest {

    private static final Integer SHOP = 1;
    private static final LocalDate APRIL_20 = LocalDate.of(2026, 4, 20);
    private static final LocalDate MARCH_20 = LocalDate.of(2026, 3, 20);
    private static final BigDecimal RATE_8 = new BigDecimal("8");
    private static final BigDecimal RATE_10 = new BigDecimal("10");

    private TAccountsPayableSummaryService summaryService;
    private PayableMonthlyAggregator aggregator;

    @BeforeEach
    void setup() {
        summaryService = mock(TAccountsPayableSummaryService.class);
        aggregator = new PayableMonthlyAggregator(summaryService);
    }

    // ============================================================
    // Scenario 1: 単一 supplier × 単月 (10% 税率のみ)
    // ============================================================

    @Nested
    @DisplayName("Scenario 1: 単一 supplier × 単月 (10% のみ)")
    class Scenario1_SingleSupplierSingleMonth {

        @Test
        @DisplayName("前月 closing → 当月 opening 繰越 + 当月 change → closing")
        void single_supplier_single_month() {
            // 前月 (3/20): change 100,000 / payment 0 → closing 100,000
            TAccountsPayableSummary prev = row(101, MARCH_20, RATE_10)
                    .openingBalanceTaxIncluded(BigDecimal.ZERO)
                    .openingBalanceTaxExcluded(BigDecimal.ZERO)
                    .taxIncludedAmountChange(new BigDecimal("100000"))
                    .taxExcludedAmountChange(new BigDecimal("90909"))
                    .paymentAmountSettledTaxIncluded(BigDecimal.ZERO)
                    .paymentAmountSettledTaxExcluded(BigDecimal.ZERO)
                    .build();

            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月 (4/20): change 50,000 (新規仕入)
            TAccountsPayableSummary curr = row(101, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyOpenings(List.of(curr), prevData);

            // 当月 opening = 前月 closing = 100,000 (繰越)
            assertThat(curr.getOpeningBalanceTaxIncluded()).isEqualByComparingTo("100000");
            assertThat(curr.getOpeningBalanceTaxExcluded()).isEqualByComparingTo("90909");
        }

        @Test
        @DisplayName("前月 paid>0 → 当月 payment_settled に按分 (単一行は全額)")
        void payment_settled_single_row() {
            // 前月: change 100,000 / verified 100,000 (paid)
            TAccountsPayableSummary prev = row(102, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("100000"))
                    .taxExcludedAmountChange(new BigDecimal("90909"))
                    .verifiedAmount(new BigDecimal("100000"))
                    .verifiedManually(true)
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月 (4/20): change 30,000
            TAccountsPayableSummary curr = row(102, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27273"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyPaymentSettled(List.of(curr), prevData);

            // 単一行のため前月 paid 全額が当月行に乗る
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("100000");
            // 税抜は paidIncl × changeExcl / changeIncl = 100000 × 27273 / 30000 = 90910 (DOWN)
            // ただし単一行 = 最終行のため paidExcl 直接代入。SupplierAgg.paidExcl = 100000 × 90909 / 100000 = 90909
            assertThat(curr.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("90909");
        }
    }

    // ============================================================
    // Scenario 2: 複数 supplier × 税率混在
    // ============================================================

    @Nested
    @DisplayName("Scenario 2: 複数 supplier × 税率混在 (8% + 10%)")
    class Scenario2_MultipleSuppliersMultipleTaxRates {

        @Test
        @DisplayName("supplier 内税率別 change 比で payment 按分 + 最終行端数吸収")
        void multi_tax_rate_apportionment() {
            // 前月: supplier 201, 8% change 30,000 + 10% change 70,000 → paid 集計 95,000 (全行同値)
            TAccountsPayableSummary prev8 = row(201, MARCH_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27778"))
                    .verifiedAmount(new BigDecimal("95000"))
                    .build();
            TAccountsPayableSummary prev10 = row(201, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("70000"))
                    .taxExcludedAmountChange(new BigDecimal("63636"))
                    .verifiedAmount(new BigDecimal("95000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev8, prev10));

            // 当月: 同じ supplier に 8% / 10% の change あり
            TAccountsPayableSummary curr8 = row(201, APRIL_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("20000"))
                    .taxExcludedAmountChange(new BigDecimal("18519"))
                    .build();
            TAccountsPayableSummary curr10 = row(201, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("80000"))
                    .taxExcludedAmountChange(new BigDecimal("72727"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyPaymentSettled(new ArrayList<>(List.of(curr8, curr10)), prevData);

            // 全行 paid 合計 = 95,000 (全行同値ロジックで代表値、SUM 190,000 にしない)
            BigDecimal totalPaidIncl = curr8.getPaymentAmountSettledTaxIncluded()
                    .add(curr10.getPaymentAmountSettledTaxIncluded());
            assertThat(totalPaidIncl).isEqualByComparingTo("95000");

            // 8% 行 = 95000 × 20000 / 100000 = 19000 (DOWN)
            assertThat(curr8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("19000");
            // 10% (最終行) = 95000 - 19000 = 76000
            assertThat(curr10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("76000");
        }

        @Test
        @DisplayName("change=0 supplier は applyPaymentSettled で skip (payment-only に委ねる)")
        void change_zero_supplier_skipped() {
            // 前月: supplier 202 に paid 50,000 / change 50,000
            TAccountsPayableSummary prev = row(202, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .verifiedAmount(new BigDecimal("50000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月: 同 supplier に change 0 (=支払のみ完了で当月仕入無し)
            TAccountsPayableSummary curr = row(202, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(BigDecimal.ZERO)
                    .taxExcludedAmountChange(BigDecimal.ZERO)
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyPaymentSettled(new ArrayList<>(List.of(curr)), prevData);

            // applyPaymentSettled は change=0 で skip → payment は 0 のまま (payment-only 行で別処理)
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("0");
        }
    }

    // ============================================================
    // Scenario 3: payment-only 行生成 (R2 反映)
    // ============================================================

    @Nested
    @DisplayName("Scenario 3: payment-only 行生成")
    class Scenario3_PaymentOnlyRowGeneration {

        @Test
        @DisplayName("前月 paid>0 / 当月 supplier 不在 → payment-only 行生成")
        void payment_only_row_for_absent_supplier() {
            // 前月: supplier 301 に paid 80,000
            TAccountsPayableSummary prev = row(301, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("80000"))
                    .taxExcludedAmountChange(new BigDecimal("72727"))
                    .verifiedAmount(new BigDecimal("80000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月: 別 supplier (302) しかいない → 301 は payment-only 対象
            TAccountsPayableSummary other = row(302, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("10000"))
                    .taxExcludedAmountChange(new BigDecimal("9091"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            Map<String, TAccountsPayableSummary> currMap = new HashMap<>();
            currMap.put(PayableMonthlyAggregator.rowKey(other), other);

            List<TAccountsPayableSummary> generated = aggregator.generatePaymentOnlyRows(
                    prevData, List.of(other), APRIL_20, currMap);

            assertThat(generated).hasSize(1);
            TAccountsPayableSummary po = generated.get(0);
            assertThat(po.getSupplierNo()).isEqualTo(301);
            assertThat(po.getTransactionMonth()).isEqualTo(APRIL_20);
            assertThat(po.getIsPaymentOnly()).isTrue();
            assertThat(po.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("80000");
            // opening = 前月 closing 合計 = 80000 - 0 = 80000 (前月 payment_settled = 0)
            assertThat(po.getOpeningBalanceTaxIncluded()).isEqualByComparingTo("80000");
            assertThat(po.getVerifiedAmount()).isNull();
            assertThat(po.getVerifiedManually()).isFalse();
            assertThat(po.getVerificationNote()).isEqualTo("[payment-only] " + APRIL_20);
        }

        @Test
        @DisplayName("手動確定行は payment-only で上書きされない (検証済み振込明細を保護)")
        void manual_verified_row_protected_from_payment_only() {
            // 前月: supplier 303 に paid 60,000
            TAccountsPayableSummary prev = row(303, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("60000"))
                    .taxExcludedAmountChange(new BigDecimal("54545"))
                    .verifiedAmount(new BigDecimal("60000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月: 同 supplier に change=0 だが手動確定済み行が既に存在
            TAccountsPayableSummary manual = row(303, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(BigDecimal.ZERO)
                    .taxExcludedAmountChange(BigDecimal.ZERO)
                    .verifiedAmount(new BigDecimal("70000"))
                    .verifiedManually(true)
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            Map<String, TAccountsPayableSummary> currMap = new HashMap<>();
            currMap.put(PayableMonthlyAggregator.rowKey(manual), manual);

            List<TAccountsPayableSummary> generated = aggregator.generatePaymentOnlyRows(
                    prevData, List.of(manual), APRIL_20, currMap);

            // 手動確定行は payment-only で上書きされない
            assertThat(generated).isEmpty();
            assertThat(manual.getVerifiedAmount()).isEqualByComparingTo("70000");
            assertThat(manual.getVerifiedManually()).isTrue();
            // payment-only マーカーも付かない
            assertThat(manual.getIsPaymentOnly()).isNotEqualTo(Boolean.TRUE);
        }
    }

    // ============================================================
    // Scenario 4: MF debit 上書き (案 A) — 全行 auto
    // ============================================================

    @Nested
    @DisplayName("Scenario 4: MF debit 上書き (案 A) — 全行 auto")
    class Scenario4_MfDebitOverrideAutoOnly {

        @Test
        @DisplayName("MF debit が verified より大きい時は MF debit 値で上書き")
        void mf_debit_overrides_verified_amount() {
            // 当月: supplier 401 に 8% / 10% の change あり (改修後 paymentSettled は 0 想定)
            TAccountsPayableSummary curr8 = row(401, APRIL_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27778"))
                    .build();
            TAccountsPayableSummary curr10 = row(401, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("70000"))
                    .taxExcludedAmountChange(new BigDecimal("63636"))
                    .build();

            // MF debit = 100,000 (= 当月買掛金 debit 合計)
            Map<Integer, BigDecimal> mfDebit = Map.of(401, new BigDecimal("100000"));

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr8, curr10)), mfDebit, APRIL_20);

            // 8% 行 = 100000 × 30000 / 100000 = 30000 (DOWN)
            assertThat(curr8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("30000");
            // 10% (最終行) = 100000 - 30000 = 70000
            assertThat(curr10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("70000");

            // 合計 = 100,000
            BigDecimal sum = curr8.getPaymentAmountSettledTaxIncluded()
                    .add(curr10.getPaymentAmountSettledTaxIncluded());
            assertThat(sum).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("MF debit map に supplier が無い時は何もしない")
        void no_mf_debit_supplier_no_op() {
            TAccountsPayableSummary curr = row(402, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .paymentAmountSettledTaxIncluded(new BigDecimal("12345"))
                    .build();

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr)),
                    Map.of(999, new BigDecimal("99999")), APRIL_20);

            // 既存値が残る (改修されない)
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("12345");
        }

        @Test
        @DisplayName("空 / null map は no-op")
        void null_or_empty_map_no_op() {
            TAccountsPayableSummary curr = row(403, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .paymentAmountSettledTaxIncluded(new BigDecimal("11111"))
                    .build();

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr)), null, APRIL_20);
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("11111");

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr)), Map.of(), APRIL_20);
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("11111");
        }
    }

    // ============================================================
    // Scenario 5: MF debit 上書き — 手動確定行 protect
    // ============================================================

    @Nested
    @DisplayName("Scenario 5: MF debit 上書き — 手動確定行 protect")
    class Scenario5_MfDebitOverrideManualProtect {

        @Test
        @DisplayName("手動確定行 paymentSettled は MF debit で上書きされず、残額のみ auto 行に按分")
        void manual_row_payment_settled_protected() {
            // supplier 501: 手動確定行 (8%) と 自動行 (10%) が混在
            TAccountsPayableSummary manual8 = row(501, APRIL_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27778"))
                    .verifiedManually(true)
                    .paymentAmountSettledTaxIncluded(new BigDecimal("28000"))
                    .paymentAmountSettledTaxExcluded(new BigDecimal("25926"))
                    .build();
            TAccountsPayableSummary auto10 = row(501, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("70000"))
                    .taxExcludedAmountChange(new BigDecimal("63636"))
                    .build();

            // MF debit = 98,000 → 手動 28,000 を引いた 70,000 を auto 行に上書き
            Map<Integer, BigDecimal> mfDebit = Map.of(501, new BigDecimal("98000"));

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(manual8, auto10)), mfDebit, APRIL_20);

            // 手動確定行は値が変わらない (保護)
            assertThat(manual8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("28000");
            assertThat(manual8.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("25926");
            // 自動行 = 70,000 (残額) — 単独 auto 行のため最終行 = 全額
            assertThat(auto10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("70000");
        }

        @Test
        @DisplayName("全行手動確定の supplier は overrideWithMfDebit で skip")
        void all_manual_rows_skipped() {
            TAccountsPayableSummary manual = row(502, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .verifiedManually(true)
                    .paymentAmountSettledTaxIncluded(new BigDecimal("47000"))
                    .paymentAmountSettledTaxExcluded(new BigDecimal("42727"))
                    .build();

            Map<Integer, BigDecimal> mfDebit = Map.of(502, new BigDecimal("60000"));
            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(manual)), mfDebit, APRIL_20);

            // 手動確定行は値が変わらない (skip)
            assertThat(manual.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("47000");
            assertThat(manual.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("42727");
        }
    }

    // ============================================================
    // Scenario 6: SupplierAgg 計算 (verified 全行同値 → 代表値、不一致 → SUM)
    // ============================================================

    @Nested
    @DisplayName("Scenario 6: SupplierAgg 計算")
    class Scenario6_SupplierAgg {

        @Test
        @DisplayName("verified 全行同値 → 代表値 (二重計上回避)")
        void all_same_verified_uses_representative() {
            List<TAccountsPayableSummary> group = List.of(
                    row(601, MARCH_20, RATE_8)
                            .taxIncludedAmountChange(new BigDecimal("30000"))
                            .taxExcludedAmountChange(new BigDecimal("27778"))
                            .verifiedAmount(new BigDecimal("100000"))
                            .build(),
                    row(601, MARCH_20, RATE_10)
                            .taxIncludedAmountChange(new BigDecimal("70000"))
                            .taxExcludedAmountChange(new BigDecimal("63636"))
                            .verifiedAmount(new BigDecimal("100000"))
                            .build()
            );

            SupplierAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
            assertThat(agg.paidIncl()).isEqualByComparingTo("100000");
            assertThat(agg.changeInclTotal()).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("verified 不一致 → SUM (DB 異常検知の安全側挙動)")
        void mismatched_verified_falls_back_to_sum() {
            List<TAccountsPayableSummary> group = List.of(
                    row(602, MARCH_20, RATE_8)
                            .taxIncludedAmountChange(new BigDecimal("30000"))
                            .taxExcludedAmountChange(new BigDecimal("27778"))
                            .verifiedAmount(new BigDecimal("100000"))
                            .build(),
                    row(602, MARCH_20, RATE_10)
                            .taxIncludedAmountChange(new BigDecimal("70000"))
                            .taxExcludedAmountChange(new BigDecimal("63636"))
                            .verifiedAmount(new BigDecimal("50000"))   // ← 不一致
                            .build()
            );

            SupplierAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
            // 不一致 → SUM = 100000 + 50000 = 150000
            assertThat(agg.paidIncl()).isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("payment-only 行は paid 算出から除外 (R4)")
        void payment_only_row_excluded_from_paid() {
            List<TAccountsPayableSummary> group = List.of(
                    row(603, MARCH_20, RATE_10)
                            .taxIncludedAmountChange(new BigDecimal("50000"))
                            .taxExcludedAmountChange(new BigDecimal("45455"))
                            .verifiedAmount(new BigDecimal("50000"))
                            .build(),
                    row(603, MARCH_20, RATE_10)
                            .isPaymentOnly(true)
                            .verifiedAmount(new BigDecimal("99999"))   // payment-only は無視
                            .build()
            );

            SupplierAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
            assertThat(agg.paidIncl()).isEqualByComparingTo("50000");
        }
    }

    // ============================================================
    // 共通 builder
    // ============================================================

    private TAccountsPayableSummary.TAccountsPayableSummaryBuilder row(
            Integer supplierNo, LocalDate month, BigDecimal taxRate) {
        return TAccountsPayableSummary.builder()
                .shopNo(SHOP)
                .supplierNo(supplierNo)
                .supplierCode("S" + supplierNo)
                .transactionMonth(month)
                .taxRate(taxRate)
                .openingBalanceTaxIncluded(BigDecimal.ZERO)
                .openingBalanceTaxExcluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxIncluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxExcluded(BigDecimal.ZERO)
                .autoAdjustedAmount(BigDecimal.ZERO)
                .verifiedManually(false)
                .isPaymentOnly(false);
    }

}
