package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfJournal;
import jp.co.oda32.domain.service.finance.mf.MfJournal.MfBranch;
import jp.co.oda32.domain.service.finance.mf.MfJournal.MfSide;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService.CachedResult;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.SupplierBalancesResponse;
import jp.co.oda32.dto.finance.SupplierBalancesResponse.SupplierBalanceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SupplierBalancesService} のゴールデンマスタ単体テスト。
 * <p>
 * T3 案 D'' Phase 3 で追加 (2026-05-06)。
 * 改修起因の集計値変動を検知するため、純 Java fixture (builder-based) で代表シナリオを locked-in する。
 * <p>
 * 本テストが fail した時の判断:
 * <ul>
 *   <li><b>意図通りの仕様変更</b> → 期待値を改修目的に合致した値に更新 (PR で根拠を明記)</li>
 *   <li><b>意図外の副作用</b> → 改修見直し (sub_account 解決ロジック、journal フィルタ、closing 算出等)</li>
 * </ul>
 * <p>
 * 詳細手順: {@code claudedocs/runbook-finance-recalc-impact-analysis.md} §3 §4 §6
 * <p>
 * シナリオ:
 * <ul>
 *   <li>全 supplier MATCH (差分 0)</li>
 *   <li>MINOR (差分 ≦ 1000) / MAJOR (差分 > 1000) の閾値判定</li>
 *   <li>MF_MISSING (self あり / MF なし)</li>
 *   <li>SELF_MISSING (MF あり / self なし)</li>
 *   <li>opening 注入 (期首残あり supplier の closing 加算)</li>
 *   <li>journal #1 (期首残仕訳) は accumulation から除外</li>
 * </ul>
 *
 * @since 2026-05-06 (T3 案 D'' Phase 3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierBalancesServiceGoldenTest {

    private static final Integer SHOP = 1;
    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 20);
    private static final LocalDate FETCH_FROM = MfPeriodConstants.MF_JOURNALS_FETCH_FROM; // 2025-05-20

    private MfOauthService mfOauthService;
    private MfJournalCacheService journalCache;
    private MfAccountMasterRepository mfAccountMasterRepository;
    private TAccountsPayableSummaryRepository summaryRepository;
    private MPaymentSupplierService paymentSupplierService;
    private MfOpeningBalanceService openingBalanceService;
    private SupplierBalancesService service;

    @BeforeEach
    void setup() {
        mfOauthService = mock(MfOauthService.class);
        journalCache = mock(MfJournalCacheService.class);
        mfAccountMasterRepository = mock(MfAccountMasterRepository.class);
        summaryRepository = mock(TAccountsPayableSummaryRepository.class);
        paymentSupplierService = mock(MPaymentSupplierService.class);
        openingBalanceService = mock(MfOpeningBalanceService.class);

        service = new SupplierBalancesService(
                mfOauthService, journalCache, mfAccountMasterRepository,
                summaryRepository, paymentSupplierService, openingBalanceService);

        // MF OAuth 系は token 取得まで mock
        MMfOauthClient client = new MMfOauthClient();
        when(mfOauthService.findActiveClient()).thenReturn(Optional.of(client));
        when(mfOauthService.getValidAccessToken()).thenReturn("dummy-token");
    }

    // ============================================================
    // Scenario 1: MATCH (self closing == MF balance, 差分 0)
    // ============================================================

    @Test
    @DisplayName("Scenario 1: 単一 supplier MATCH (self closing == MF balance)")
    void scenario_1_single_supplier_match() {
        // self: supplier 100 / 4/20 / 10% / change 50,000 → closing 50,000 (opening 0, payment 0)
        TAccountsPayableSummary self = row(100, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50000"))
                .taxExcludedAmountChange(new BigDecimal("45455"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(100, "S100", "仕入先A"));
        stubMfAccount("仕入先A_sub", "S100");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先A_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);

        assertThat(resp.getRows()).hasSize(1);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getSupplierNo()).isEqualTo(100);
        assertThat(r.getSelfBalance()).isEqualByComparingTo("50000");
        assertThat(r.getMfBalance()).isEqualByComparingTo("50000");
        assertThat(r.getDiff()).isEqualByComparingTo("0");
        assertThat(r.getStatus()).isEqualTo("MATCH");
        assertThat(r.getMasterRegistered()).isTrue();
    }

    // ============================================================
    // Scenario 2: MINOR / MAJOR 閾値判定
    // ============================================================

    @Test
    @DisplayName("Scenario 2-MINOR: 差分 500 → MINOR 分類")
    void scenario_2_minor_diff() {
        TAccountsPayableSummary self = row(101, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50500"))
                .taxExcludedAmountChange(new BigDecimal("45909"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(101, "S101", "仕入先B"));
        stubMfAccount("仕入先B_sub", "S101");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先B_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // self closing = 50500, MF = 50000, diff = 500
        assertThat(r.getDiff()).isEqualByComparingTo("500");
        assertThat(r.getStatus()).isEqualTo("MINOR");
    }

    @Test
    @DisplayName("Scenario 2-MAJOR: 差分 5000 → MAJOR 分類")
    void scenario_2_major_diff() {
        TAccountsPayableSummary self = row(102, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("55000"))
                .taxExcludedAmountChange(new BigDecimal("50000"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(102, "S102", "仕入先C"));
        stubMfAccount("仕入先C_sub", "S102");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先C_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getDiff()).isEqualByComparingTo("5000");
        assertThat(r.getStatus()).isEqualTo("MAJOR");
    }

    @Test
    @DisplayName("Scenario 2-EDGE: 差分 100 (= MATCH_TOLERANCE) は MATCH 判定")
    void scenario_2_edge_match_tolerance() {
        TAccountsPayableSummary self = row(103, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50100"))
                .taxExcludedAmountChange(new BigDecimal("45545"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(103, "S103", "仕入先D"));
        stubMfAccount("仕入先D_sub", "S103");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先D_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // diff = 100, MATCH_TOLERANCE = 100 → MATCH (≦ で判定)
        assertThat(r.getDiff()).isEqualByComparingTo("100");
        assertThat(r.getStatus()).isEqualTo("MATCH");
    }

    // ============================================================
    // Scenario 3: MF_MISSING (self あり / MF なし)
    // ============================================================

    @Test
    @DisplayName("Scenario 3: MF_MISSING (self closing > 0 / MF activity 0)")
    void scenario_3_mf_missing() {
        TAccountsPayableSummary self = row(200, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("30000"))
                .taxExcludedAmountChange(new BigDecimal("27273"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(200, "S200", "仕入先E"));
        // master 登録あるが activity 0
        stubMfAccount("仕入先E_sub", "S200");
        stubMfJournals(List.of()); // MF journal 無し
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getStatus()).isEqualTo("MF_MISSING");
        assertThat(r.getSelfBalance()).isEqualByComparingTo("30000");
        assertThat(r.getMfBalance()).isEqualByComparingTo("0");
    }

    // ============================================================
    // Scenario 4: SELF_MISSING (MF あり / self なし)
    // ============================================================

    @Test
    @DisplayName("Scenario 4: SELF_MISSING (MF activity あり / self 未登録)")
    void scenario_4_self_missing() {
        // self 行は無し
        stubSelfRows(List.of());
        stubMaster(supplier(300, "S300", "仕入先F"));
        stubMfAccount("仕入先F_sub", "S300");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先F_sub", new BigDecimal("20000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        assertThat(resp.getRows()).hasSize(1);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getStatus()).isEqualTo("SELF_MISSING");
        assertThat(r.getSelfBalance()).isEqualByComparingTo("0");
        assertThat(r.getMfBalance()).isEqualByComparingTo("20000");
    }

    // ============================================================
    // Scenario 5: opening 注入 (期首残あり supplier の closing 加算)
    // ============================================================

    @Test
    @DisplayName("Scenario 5: 期首残あり supplier の self closing に opening を加算")
    void scenario_5_opening_balance_injection() {
        TAccountsPayableSummary self = row(400, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("10000"))
                .taxExcludedAmountChange(new BigDecimal("9091"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(400, "S400", "仕入先G"));
        stubMfAccount("仕入先G_sub", "S400");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先G_sub", new BigDecimal("110000"), null)
        ));
        // 期首残: supplier 400 に 100,000
        when(openingBalanceService.getEffectiveBalanceMap(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
                .thenReturn(Map.of(400, new BigDecimal("100000")));

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // self closing = current_closing(10000) + opening(100000) = 110,000
        assertThat(r.getSelfBalance()).isEqualByComparingTo("110000");
        assertThat(r.getMfBalance()).isEqualByComparingTo("110000");
        assertThat(r.getStatus()).isEqualTo("MATCH");
        assertThat(r.getSelfOpening()).isEqualByComparingTo("100000");
    }

    // ============================================================
    // Scenario 6: journal #1 (期首残仕訳) は accumulation から除外
    // ============================================================

    @Test
    @DisplayName("Scenario 6: 期首残高仕訳 (credit-only / debit に買掛金 無し) は accumulation から除外")
    void scenario_6_opening_journal_excluded() {
        TAccountsPayableSummary self = row(500, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50000"))
                .taxExcludedAmountChange(new BigDecimal("45455"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(500, "S500", "仕入先H"));
        stubMfAccount("仕入先H_sub", "S500");
        // 通常 journal (debit に買掛金あり) と opening journal (credit のみ) を混在
        MfJournal openingJournal = new MfJournal(
                "j-opening", LocalDate.of(2025, 6, 21), 1, true, "opening", "期首残",
                List.of(
                        // credit only (debit は他の科目) → opening 判定対象
                        new MfBranch(
                                new MfSide(null, "買掛金", null, "仕入先H_sub", null, "対象外", null, new BigDecimal("999999")),
                                new MfSide(null, "繰越利益剰余金", null, null, null, "対象外", null, new BigDecimal("999999")),
                                "opening")
                )
        );
        MfJournal regularJournal = payableJournal(LocalDate.of(2026, 4, 1), "仕入先H_sub", new BigDecimal("50000"), null);

        stubMfJournals(List.of(openingJournal, regularJournal));
        // opening は openingBalanceService 経由で別途注入 (今回は 0 に統一して journal #1 除外を verify)
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // opening journal が除外されていれば MF balance = 50000 (regular のみ)
        assertThat(r.getMfBalance()).isEqualByComparingTo("50000");
        assertThat(r.getDiff()).isEqualByComparingTo("0");
        assertThat(r.getStatus()).isEqualTo("MATCH");
    }

    // ============================================================
    // Scenario 7: opening 未投入時の警告 (Codex Major fix P1-02)
    // ============================================================

    @Test
    @DisplayName("Scenario 7-A: opening 未投入時 openingBalanceMissing=true + warning メッセージ")
    void scenario_7_opening_missing_warning() {
        // self / MF は通常通り、ただし m_supplier_opening_balance に row なし (= 初回起動)
        TAccountsPayableSummary self = row(700, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("10000"))
                .taxExcludedAmountChange(new BigDecimal("9091"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(700, "S700", "仕入先X"));
        stubMfAccount("仕入先X_sub", "S700");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先X_sub", new BigDecimal("10000"), null)
        ));
        // opening 行なし: getEffectiveBalanceMap=空 + isOpeningBalanceLoaded=false
        when(openingBalanceService.getEffectiveBalanceMap(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
                .thenReturn(new HashMap<>());
        when(openingBalanceService.isOpeningBalanceLoaded(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
                .thenReturn(false);

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);

        assertThat(resp.getOpeningBalanceMissing()).isTrue();
        assertThat(resp.getOpeningBalanceWarning())
                .isNotNull()
                .contains("MF 期首残")
                .contains("/finance/supplier-opening-balance/mf-fetch");
    }

    @Test
    @DisplayName("Scenario 7-B: opening 投入済 (全 0 含む) なら openingBalanceMissing=false / warning=null")
    void scenario_7_opening_loaded_no_warning() {
        TAccountsPayableSummary self = row(701, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("10000"))
                .taxExcludedAmountChange(new BigDecimal("9091"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(701, "S701", "仕入先Y"));
        stubMfAccount("仕入先Y_sub", "S701");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先Y_sub", new BigDecimal("10000"), null)
        ));
        stubOpeningEmpty();
        // 投入済 (全 0 でも row は存在) は missing=false
        when(openingBalanceService.isOpeningBalanceLoaded(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
                .thenReturn(true);

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);

        assertThat(resp.getOpeningBalanceMissing()).isFalse();
        assertThat(resp.getOpeningBalanceWarning()).isNull();
    }

    // ============================================================
    // 共通 stub helpers
    // ============================================================

    private void stubSelfRows(List<TAccountsPayableSummary> rows) {
        when(summaryRepository
                .findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc(
                        eq(SHOP), eq(FETCH_FROM), eq(AS_OF)))
                .thenReturn(rows);
    }

    private void stubMaster(MPaymentSupplier... suppliers) {
        when(paymentSupplierService.findByShopNo(SHOP)).thenReturn(java.util.Arrays.asList(suppliers));
    }

    private void stubMfAccount(String subAccountName, String supplierCode) {
        // service.buildMfSubToCodes() が findAll() を呼ぶ前提
        MfAccountMaster m = new MfAccountMaster();
        m.setReportName("貸借対照表");
        m.setCategory("負債");
        m.setFinancialStatementItem("買掛金");
        m.setAccountName("買掛金");
        m.setSubAccountName(subAccountName);
        m.setSearchKey(supplierCode);
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(m));
    }

    private void stubMfJournals(List<MfJournal> journals) {
        when(journalCache.getOrFetch(eq(SHOP), any(), any(), eq(FETCH_FROM), eq(AS_OF), anyBoolean()))
                .thenReturn(new CachedResult(journals, java.time.Instant.parse("2026-04-21T00:00:00Z")));
    }

    private void stubOpeningEmpty() {
        when(openingBalanceService.getEffectiveBalanceMap(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
                .thenReturn(new HashMap<>());
    }

    private MPaymentSupplier supplier(Integer no, String code, String name) {
        return MPaymentSupplier.builder()
                .paymentSupplierNo(no)
                .paymentSupplierCode(code)
                .paymentSupplierName(name)
                .shopNo(SHOP)
                .delFlg("0")
                .build();
    }

    /**
     * 通常購入仕訳: credit 買掛金 / debit 仕入高 を 1 branch で構成。
     * <p>
     * C8 fix (2026-05-04) 後の {@link jp.co.oda32.domain.service.finance.mf.MfOpeningJournalDetector#isOpeningCandidate}
     * は number==1 + transactionDate==MF_FISCAL_YEAR_START + credit-only の複合条件で判定するため、
     * 通常仕訳 (number=100, transactionDate != fiscal year start) は何もしなくても
     * isOpeningCandidate=false となる。旧版が必要としていた「dummy debit 買掛金 branch を追加」
     * という workaround は不要になった。
     * <p>
     * 計算: mfBalance = credit - debit。
     *
     * @param credit 通常購入の credit (買掛金)
     * @param debit  支払の debit (買掛金)。null/0 なら debit 買掛金 branch は追加しない。
     */
    private MfJournal payableJournal(LocalDate transactionDate, String subAccountName,
                                      BigDecimal credit, BigDecimal debit) {
        BigDecimal cr = credit != null ? credit : BigDecimal.ZERO;
        // Branch 1: credit 買掛金 (購入)
        MfBranch purchaseBr = new MfBranch(
                new MfSide(null, "買掛金", null, subAccountName, null, "対象外", null, cr),
                new MfSide(null, "仕入高", null, null, null, "課税仕入10%", null, cr),
                "purchase"
        );
        java.util.List<MfBranch> branches = new java.util.ArrayList<>();
        branches.add(purchaseBr);
        if (debit != null && debit.signum() != 0) {
            // 支払 branch (debit 買掛金): debit 金額が指定されたときだけ追加
            branches.add(new MfBranch(
                    new MfSide(null, "普通預金", null, null, null, "対象外", null, debit),
                    new MfSide(null, "買掛金", null, subAccountName, null, "対象外", null, debit),
                    "payment"
            ));
        }
        return new MfJournal(
                "j-" + transactionDate, transactionDate, 100, true, "regular", "",
                branches
        );
    }

    private TAccountsPayableSummary.TAccountsPayableSummaryBuilder row(
            Integer supplierNo, LocalDate month, String taxRate) {
        return TAccountsPayableSummary.builder()
                .shopNo(SHOP)
                .supplierNo(supplierNo)
                .supplierCode("S" + supplierNo)
                .transactionMonth(month)
                .taxRate(new BigDecimal(taxRate))
                .openingBalanceTaxIncluded(BigDecimal.ZERO)
                .openingBalanceTaxExcluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxIncluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxExcluded(BigDecimal.ZERO)
                .autoAdjustedAmount(BigDecimal.ZERO)
                .verifiedManually(false)
                .isPaymentOnly(false);
    }
}
