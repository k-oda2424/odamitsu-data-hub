package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * applyVerification 時に補助行が正しく {@code t_payment_mf_aux_row} に
 * 洗い替え保存されるかを検証するユニットテスト。
 *
 * <p><b>適用済み仕様 (P1-03 案 D-2 / Codex C2 修正後)</b>:
 * <ul>
 *   <li>SUMMARY 集約 (旧 振込手数料値引/早払収益 合計仕訳 2 行) は撤去。
 *       supplier 別 PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET 副行に展開。</li>
 *   <li>aux テーブル CHECK 制約 (V038) は EXPENSE / SUMMARY / DIRECT_PURCHASE に加え、
 *       PAYABLE_* / DIRECT_PURCHASE_* 副行も保存可能。</li>
 *   <li>{@code saveAuxRowsForVerification} は PAYABLE 主行のみ skip
 *       (= {@code t_accounts_payable_summary} 由来で重複排除)。
 *       PAYABLE_* / DIRECT_PURCHASE_* / EXPENSE / SUMMARY / DIRECT_PURCHASE は全て保存対象。</li>
 *   <li>洗い替えキー = (shop_no, transaction_month, transfer_date) 単位の物理削除→再 saveAll。</li>
 * </ul>
 *
 * <p>fixture 振込み明細08-2-5.xlsx は 5日払いセクションのみで、
 * SUMMARY 行は出ない / DIRECT_PURCHASE 主行も出ない / EXPENSE 主行も出ない構造。
 * 結果 aux に保存されるのは PAYABLE_FEE / PAYABLE_EARLY 副行のみ。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceAuxRowTest {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @Mock TAccountsPayableSummaryService payableService;
    @Mock TPaymentMfAuxRowRepository auxRowRepository;
    @Mock MOffsetJournalRuleRepository offsetJournalRuleRepository;
    @Mock EntityManager entityManager;
    @Mock Query advisoryLockQuery;
    @InjectMocks PaymentMfImportService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");
    private static final Path SEED_SQL = Paths.get("src/main/resources/db/migration/V011__create_payment_mf_tables.sql");

    @BeforeEach
    void setup() throws Exception {
        // 案 D-2 後は supplier 別 PAYABLE_FEE/EARLY を出すために実際のルールが必要
        // (ルール空だと PAYABLE 自体が UNREGISTERED になり aux に何も入らない)。
        String sql = Files.readString(SEED_SQL, java.nio.charset.StandardCharsets.UTF_8);
        List<MPaymentMfRule> rules = PaymentMfImportServiceGoldenMasterTest.parseRules(sql);
        when(ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0")).thenReturn(rules);
        when(payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // G2-M8: OFFSET 副行貸方科目マスタ。V041 seed 同等値を返す。
        when(offsetJournalRuleRepository.findByShopNoAndDelFlg(any(), any()))
                .thenReturn(java.util.Optional.of(MOffsetJournalRule.builder()
                        .id(1)
                        .shopNo(1)
                        .creditAccount("仕入値引・戻し高")
                        .creditDepartment("物販事業部")
                        .creditTaxCategory("課税仕入-返還等 10%")
                        .summaryPrefix("相殺／")
                        .delFlg("0")
                        .build()));
        // EntityManager / advisory lock はテスト対象外。発火したら即 NO-OP で返す。
        // @InjectMocks はコンストラクタ注入で停止するため、@PersistenceContext フィールドは
        // ReflectionTestUtils で明示的にセットする。
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.setParameter(anyString(), any())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.getSingleResult()).thenReturn(1);
    }

    @Test
    void applyVerification後にaux削除と保存が呼ばれる_PAYABLE主行は保存されずSUMMARYは0件() throws Exception {
        List<TPaymentMfAuxRow> saved = applyFixtureAndCaptureSaved("振込み明細08-2-5.xlsx");

        // (shop=1, txMonth=2026-01-20, transferDate=2026-02-05) で delete が呼ばれる
        verify(auxRowRepository).deleteByShopAndTransactionMonthAndTransferDate(
                eq(1), eq(java.time.LocalDate.of(2026, 1, 20)),
                eq(java.time.LocalDate.of(2026, 2, 5)));

        assertFalse(saved.isEmpty(), "aux 行が 1 件以上保存される (PAYABLE_FEE/EARLY 副行)");

        // PAYABLE 主行は aux に保存されない (t_accounts_payable_summary 側で管理)。
        // PAYABLE_FEE / PAYABLE_EARLY などの副行は保存対象。
        for (TPaymentMfAuxRow r : saved) {
            assertFalse("PAYABLE".equals(r.getRuleKind()),
                    "PAYABLE 主行は aux に保存されない: " + r.getRuleKind());
        }

        // P1-03 案 D-2: SUMMARY 集約を撤去したため SUMMARY 行は 0 件
        // (旧仕様の振込手数料値引/早払収益 合計仕訳は出力されない)。
        long summaryCount = saved.stream().filter(r -> "SUMMARY".equals(r.getRuleKind())).count();
        assertEquals(0, summaryCount, "案 D-2 後 SUMMARY 集約撤去のため 0 件");

        // PAYABLE_FEE 副行は supplier 別に存在する (08-2-5.xlsx に値引が記録された supplier 数 ≧ 1)。
        long payableFeeCount = saved.stream().filter(r -> "PAYABLE_FEE".equals(r.getRuleKind())).count();
        assertTrue(payableFeeCount >= 1,
                "PAYABLE_FEE 副行が supplier 別に展開される (実数=" + payableFeeCount + ")");

        // PAYABLE_EARLY 副行も supplier 別に存在する (08-2-5.xlsx は早払収益あり)。
        long payableEarlyCount = saved.stream().filter(r -> "PAYABLE_EARLY".equals(r.getRuleKind())).count();
        assertTrue(payableEarlyCount >= 1,
                "PAYABLE_EARLY 副行が supplier 別に展開される (実数=" + payableEarlyCount + ")");

        // すべての行で transactionMonth と transferDate が正しい
        for (TPaymentMfAuxRow r : saved) {
            assertEquals(java.time.LocalDate.of(2026, 1, 20), r.getTransactionMonth());
            assertEquals(java.time.LocalDate.of(2026, 2, 5), r.getTransferDate());
            assertEquals(Integer.valueOf(1), r.getShopNo());
            assertTrue(r.getSequenceNo() != null && r.getSequenceNo() >= 0);
            assertTrue(r.getAddDateTime() != null);
            assertEquals(Integer.valueOf(1), r.getAddUserNo());
            // V038 で許容される rule_kind 範囲内であること
            String rk = r.getRuleKind();
            assertTrue(
                    "EXPENSE".equals(rk) || "SUMMARY".equals(rk) || "DIRECT_PURCHASE".equals(rk)
                            || "PAYABLE_FEE".equals(rk) || "PAYABLE_DISCOUNT".equals(rk)
                            || "PAYABLE_EARLY".equals(rk) || "PAYABLE_OFFSET".equals(rk)
                            || "DIRECT_PURCHASE_FEE".equals(rk) || "DIRECT_PURCHASE_DISCOUNT".equals(rk)
                            || "DIRECT_PURCHASE_EARLY".equals(rk) || "DIRECT_PURCHASE_OFFSET".equals(rk),
                    "V038 chk_payment_mf_aux_rule_kind 制約範囲内: " + rk);
        }
    }

    @Test
    void 再アップロード時はdelete2回_最終saveAll内容が2回目と一致する() throws Exception {
        // 1 回目: 洗い替え対象のキー (shop=1, 2026-01-20, 2026-02-05) で delete → saveAll
        List<TPaymentMfAuxRow> first = applyFixtureAndCaptureSaved("振込み明細08-2-5.xlsx");
        int firstCount = first.size();
        assertTrue(firstCount > 0, "1回目で補助行が保存される");

        // 2 回目: 同じファイルを再アップロード（洗い替え挙動を検証）
        List<TPaymentMfAuxRow> second = applyFixtureAndCaptureSaved("振込み明細08-2-5.xlsx");

        // delete は計 2 回（各 applyVerification で 1 回ずつ）呼ばれる
        verify(auxRowRepository, org.mockito.Mockito.times(2))
                .deleteByShopAndTransactionMonthAndTransferDate(
                        eq(1), eq(java.time.LocalDate.of(2026, 1, 20)),
                        eq(java.time.LocalDate.of(2026, 2, 5)));

        // 2 回目の saveAll は 1 回目と同件数（同一 Excel なので内容も同等）
        assertEquals(firstCount, second.size(),
                "再アップロードで保存件数が変わらない（洗い替え後の最新状態が同一）");
    }

    /**
     * Fixture を preview → applyVerification し、直近の saveAll() で渡された全 aux 行を返す。
     * 呼び出しの都度 saveAll のキャプチャは累積するため、最後の呼び出し分を返す。
     */
    private List<TPaymentMfAuxRow> applyFixtureAndCaptureSaved(String fixtureName) throws Exception {
        byte[] xlsxBytes = Files.readAllBytes(FIXTURE_DIR.resolve(fixtureName));
        MockMultipartFile multipart = new MockMultipartFile(
                "file", fixtureName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        PaymentMfPreviewResponse preview = service.preview(multipart);
        service.applyVerification(preview.getUploadId(), 1);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<TPaymentMfAuxRow>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(auxRowRepository, atLeastOnce()).saveAll(captor.capture());
        List<List<TPaymentMfAuxRow>> allCalls = captor.getAllValues();
        return allCalls.get(allCalls.size() - 1);
    }

    // Mockito.eq の簡易ショートカット
    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
