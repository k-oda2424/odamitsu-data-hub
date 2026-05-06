package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jp.co.oda32.audit.FinanceAuditWriter;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.exception.FinanceBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G2-M2 (2026-05-06): {@code applyVerification(uploadId, userNo, force)} の per-supplier 1 円不一致
 * ブロックロジックを検証する。
 *
 * <p>Excel fixture からは mismatch を意図的に作りにくいため、{@link Mockito#spy} で
 * {@link PaymentMfImportService#perSupplierMismatchesOf} を差し替えて mismatch あり/なしの
 * 4 ケースを網羅する。
 *
 * <ol>
 *   <li>mismatch 空 + force=false → 通常成功</li>
 *   <li>mismatch 空 + force=true → 通常成功 (force は副作用なし、補足 audit 行も出ない)</li>
 *   <li>mismatch 非空 + force=false → {@link FinanceBusinessException}
 *       ({@code code=PER_SUPPLIER_MISMATCH}) で 422 ブロック</li>
 *   <li>mismatch 非空 + force=true → 成功 + {@link FinanceAuditWriter#write} に
 *       {@code reason=FORCE_APPLIED: per-supplier mismatches=...} の補足行が記録される</li>
 * </ol>
 *
 * <p>{@code buildForceAppliedReason} の整形ロジック (件数 / 切り詰め / 件数 0) も同クラスで網羅。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceForceApplyTest {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @Mock TAccountsPayableSummaryService payableService;
    @Mock TPaymentMfAuxRowRepository auxRowRepository;
    @Mock MOffsetJournalRuleRepository offsetJournalRuleRepository;
    @Mock EntityManager entityManager;
    @Mock Query advisoryLockQuery;
    @Mock FinanceAuditWriter financeAuditWriter;

    private PaymentMfImportService service;
    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");
    private static final Path SEED_SQL = Paths.get("src/main/resources/db/migration/V011__create_payment_mf_tables.sql");

    @BeforeEach
    void setup() throws Exception {
        // ベース service を Spy 化することで perSupplierMismatchesOf を doReturn で差し替え可能にする。
        // @InjectMocks では spy にできないため手動で組み立て。
        PaymentMfImportService base = new PaymentMfImportService(
                ruleRepository, historyRepository, payableRepository, payableService, auxRowRepository,
                offsetJournalRuleRepository);
        service = spy(base);

        // ルールを seed SQL から取得 (PAYABLE 主行ヒット用)
        String sql = Files.readString(SEED_SQL, java.nio.charset.StandardCharsets.UTF_8);
        List<MPaymentMfRule> rules = PaymentMfImportServiceGoldenMasterTest.parseRules(sql);
        when(ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0")).thenReturn(rules);

        // 買掛 t_accounts_payable_summary は空 (notFound 扱いにして書込みパスを単純化)
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

        // EntityManager / advisory lock は no-op
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.setParameter(anyString(), any())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.getSingleResult()).thenReturn(1);

        // FinanceAuditWriter を field 注入 (force=true の補足 audit 行で使う)
        ReflectionTestUtils.setField(service, "financeAuditWriter", financeAuditWriter);
    }

    @Test
    void mismatch空_force_false_は通常成功() throws Exception {
        // 通常 fixture (mismatch なし) を preview → applyVerification
        String uploadId = uploadFixture("振込み明細08-2-5.xlsx");

        PaymentMfImportService.VerifyResult result =
                service.applyVerification(uploadId, 1, false);

        assertThat(result).isNotNull();
        assertThat(result.getTransferDate()).isNotNull();
        // 補足 audit 行は書かれない (mismatch 空のため)
        verify(financeAuditWriter, never()).write(
                anyString(), anyString(), any(), anyString(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void mismatch空_force_true_でも通常成功_補足audit行は書かれない() throws Exception {
        String uploadId = uploadFixture("振込み明細08-2-5.xlsx");

        PaymentMfImportService.VerifyResult result =
                service.applyVerification(uploadId, 1, true);

        assertThat(result).isNotNull();
        // mismatch が無いので force=true でも補足 audit 行は書かれない
        verify(financeAuditWriter, never()).write(
                anyString(), anyString(), any(), anyString(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void mismatch非空_force_false_は422ブロック() throws Exception {
        String uploadId = uploadFixture("振込み明細08-2-5.xlsx");

        // Spy で perSupplierMismatchesOf を mismatch あり に差し替え
        List<String> fakeMismatches = List.of(
                "[5日払い] supplier=10001 transferAmount=100000 expected=99999 diff=1",
                "[5日払い] supplier=10002 transferAmount=50000 expected=49998 diff=2"
        );
        doReturn(fakeMismatches).when(service).perSupplierMismatchesOf(any());

        assertThatThrownBy(() -> service.applyVerification(uploadId, 1, false))
                .isInstanceOf(FinanceBusinessException.class)
                .hasMessageContaining("per-supplier 1 円整合性違反 2 件")
                .satisfies(ex -> {
                    FinanceBusinessException fbe = (FinanceBusinessException) ex;
                    assertEquals(FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH, fbe.getErrorCode());
                });

        // ブロック時は payableService.save も auxRowRepository.saveAll も呼ばれない
        verify(payableService, never()).save(any());
        verify(auxRowRepository, never()).saveAll(any());
        // 補足 audit も書かれない (force=false でブロックしたため)
        verify(financeAuditWriter, never()).write(
                anyString(), anyString(), any(), anyString(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void mismatch非空_force_true_は成功_補足audit行に違反詳細が記録される() throws Exception {
        String uploadId = uploadFixture("振込み明細08-2-5.xlsx");

        List<String> fakeMismatches = List.of(
                "[5日払い] supplier=10001 transferAmount=100000 expected=99999 diff=1",
                "[5日払い] supplier=10002 transferAmount=50000 expected=49998 diff=2",
                "[20日払い] supplier=20003 transferAmount=80000 expected=79997 diff=3"
        );
        doReturn(fakeMismatches).when(service).perSupplierMismatchesOf(any());

        PaymentMfImportService.VerifyResult result =
                service.applyVerification(uploadId, 42, true);

        assertThat(result).isNotNull();

        // 補足 audit 行が書かれることを検証
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> opCaptor = ArgumentCaptor.forClass(String.class);
        verify(financeAuditWriter, atLeastOnce()).write(
                tableCaptor.capture(),
                opCaptor.capture(),
                eq(42),
                eq("USER"),
                any(),
                isNull(),
                isNull(),
                reasonCaptor.capture(),
                isNull(),
                isNull());

        // ターゲットテーブルと operation
        assertThat(tableCaptor.getValue()).isEqualTo("t_accounts_payable_summary");
        assertThat(opCaptor.getValue()).isEqualTo("payment_mf_apply_force");

        // reason に違反件数と詳細が含まれること
        String reason = reasonCaptor.getValue();
        assertThat(reason)
                .contains("FORCE_APPLIED: per-supplier mismatches count=3")
                .contains("supplier=10001")
                .contains("supplier=10002")
                .contains("supplier=20003");
    }

    // ============================================================
    // buildForceAppliedReason 単体テスト (整形ロジック)
    // ============================================================

    @Test
    void buildForceAppliedReason_空リストはcount0を返す() {
        String reason = PaymentMfImportService.buildForceAppliedReason(List.of());
        assertEquals("FORCE_APPLIED: per-supplier mismatches count=0", reason);
    }

    @Test
    void buildForceAppliedReason_nullはcount0を返す() {
        String reason = PaymentMfImportService.buildForceAppliedReason(null);
        assertEquals("FORCE_APPLIED: per-supplier mismatches count=0", reason);
    }

    @Test
    void buildForceAppliedReason_少件数はそのまま含む() {
        List<String> mm = List.of("a", "b", "c");
        String reason = PaymentMfImportService.buildForceAppliedReason(mm);
        assertEquals("FORCE_APPLIED: per-supplier mismatches count=3, details=[a, b, c]", reason);
    }

    @Test
    void buildForceAppliedReason_50件超は先頭50件_残数表示() {
        // 60 件 → 先頭 50 + "...(+10 more)"
        List<String> mm = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) mm.add("item" + i);
        String reason = PaymentMfImportService.buildForceAppliedReason(mm);

        assertThat(reason)
                .startsWith("FORCE_APPLIED: per-supplier mismatches count=60, details=[item0,")
                .contains("item49")
                .contains("...(+10 more)")
                .doesNotContain("item50")
                .doesNotContain("item59");
    }

    /**
     * 指定 fixture を service.preview() でアップロードし、uploadId を返す。
     */
    private String uploadFixture(String fixtureName) throws Exception {
        byte[] xlsxBytes = Files.readAllBytes(FIXTURE_DIR.resolve(fixtureName));
        MockMultipartFile multipart = new MockMultipartFile(
                "file", fixtureName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        PaymentMfPreviewResponse preview = service.preview(multipart);
        return preview.getUploadId();
    }
}
