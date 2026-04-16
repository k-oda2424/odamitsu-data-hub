package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
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
 * applyVerification 時に補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) が正しく aux テーブルに
 * 洗い替え保存されるかを検証するユニットテスト。
 * ルールマスタは最小限のダミー (SUMMARY はルール不要で自動生成される) を渡すだけで良く、
 * 振込手数料値引 / 早払収益の SUMMARY 2件が確実に保存されることを検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceAuxRowTest {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @Mock TAccountsPayableSummaryService payableService;
    @Mock TPaymentMfAuxRowRepository auxRowRepository;
    @Mock EntityManager entityManager;
    @Mock Query advisoryLockQuery;
    @InjectMocks PaymentMfImportService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");

    @BeforeEach
    void setup() {
        // ルールマスタは空 (= すべて UNREGISTERED 扱い) でも SUMMARY 2 件は自動生成される
        when(ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0"))
                .thenReturn(Collections.<MPaymentMfRule>emptyList());
        when(payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // EntityManager / advisory lock はテスト対象外。発火したら即 NO-OP で返す。
        // @InjectMocks はコンストラクタ注入で停止するため、@PersistenceContext フィールドは
        // ReflectionTestUtils で明示的にセットする。
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.setParameter(anyString(), any())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.getSingleResult()).thenReturn(1);
    }

    @Test
    void applyVerification後にaux削除と保存が呼ばれる_SUMMARY2件は必ず保存される() throws Exception {
        List<TPaymentMfAuxRow> saved = applyFixtureAndCaptureSaved("振込み明細08-2-5.xlsx");

        // (shop=1, txMonth=2026-01-20, transferDate=2026-02-05) で delete が呼ばれる
        verify(auxRowRepository).deleteByShopAndTransactionMonthAndTransferDate(
                eq(1), eq(java.time.LocalDate.of(2026, 1, 20)),
                eq(java.time.LocalDate.of(2026, 2, 5)));

        assertFalse(saved.isEmpty(), "aux 行が 1 件以上保存される");

        // PAYABLE は aux に入らない
        for (TPaymentMfAuxRow r : saved) {
            assertFalse("PAYABLE".equals(r.getRuleKind()),
                    "PAYABLE 行は aux に保存されない: " + r.getRuleKind());
        }

        // SUMMARY 2 件（振込手数料値引 + 早払収益）が確実に含まれる
        long summaryCount = saved.stream().filter(r -> "SUMMARY".equals(r.getRuleKind())).count();
        assertEquals(2, summaryCount, "SUMMARY 行は必ず 2 件 (振込手数料値引 + 早払収益)");

        // すべて transactionMonth と transferDate が正しい
        for (TPaymentMfAuxRow r : saved) {
            assertEquals(java.time.LocalDate.of(2026, 1, 20), r.getTransactionMonth());
            assertEquals(java.time.LocalDate.of(2026, 2, 5), r.getTransferDate());
            assertEquals(Integer.valueOf(1), r.getShopNo());
            assertTrue(r.getSequenceNo() != null && r.getSequenceNo() >= 0);
            assertTrue(r.getAddDateTime() != null);
            assertEquals(Integer.valueOf(1), r.getAddUserNo());
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
