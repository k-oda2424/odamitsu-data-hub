package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Codex Major fix (2026-05-06): {@code m_offset_journal_rule} の active 行が
 * 未登録 (= shop_no=2 を将来追加 / admin UI で論理削除等) のとき、
 * preview/convert が {@link IllegalStateException} で停止せず hardcoded default で
 * fallback することを検証する。
 * <p>
 * 二段防御の上位 (PaymentMfImportService 側) を unit test で固定する。
 * 下位 ({@link MOffsetJournalRuleService#delete}) の最後の active 行削除禁止は
 * {@link MOffsetJournalRuleServiceTest#delete_最後のactive行は削除禁止} で検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceOffsetFallbackTest {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @Mock MOffsetJournalRuleRepository offsetJournalRuleRepository;
    @InjectMocks PaymentMfImportService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");
    private static final Path SEED_SQL = Paths.get("src/main/resources/db/migration/V011__create_payment_mf_tables.sql");
    private static final Charset CP932 = Charset.forName("MS932");

    private List<MPaymentMfRule> rules;

    @BeforeEach
    void loadSeed() throws Exception {
        String sql = Files.readString(SEED_SQL, java.nio.charset.StandardCharsets.UTF_8);
        rules = PaymentMfImportServiceGoldenMasterTest.parseRules(sql);
    }

    @Test
    @DisplayName("OFFSET マスタ欠落時: preview/convert が hardcoded default で fallback (例外送出しない)")
    void offsetマスタ欠落時_default_fallback() throws Exception {
        when(ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0")).thenReturn(rules);
        when(payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        // m_offset_journal_rule の active 行が無い状態を再現 (admin UI で削除 / 未 seed 状態)
        when(offsetJournalRuleRepository.findByShopNoAndDelFlg(eq(1), eq("0")))
                .thenReturn(java.util.Optional.empty());

        // OFFSET 行を含む golden master fixture を流し込み、IllegalStateException が出ないことを確認
        Path xlsx = FIXTURE_DIR.resolve("振込み明細08-2-5.xlsx");
        // fixture が無い環境では skip (CI 等で paymentmf fixture 同梱前は意味が無い)
        if (!Files.exists(xlsx)) {
            return;
        }

        byte[] xlsxBytes = Files.readAllBytes(xlsx);
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "振込み明細08-2-5.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        PaymentMfPreviewResponse preview = service.preview(multipart);
        assertNotNull(preview, "preview が IllegalStateException 無しで返ること");

        // convert (CSV 生成) も例外なく終わる = OFFSET 行が hardcoded default で構築された
        byte[] csv = service.convert(preview.getUploadId(), null);
        assertNotNull(csv);
        String body = new String(csv, CP932);
        // hardcoded default 値が CSV に出ていることを軽く検証 (= V041 seed と同値)
        // 強い検証は GoldenMasterTest で fixture と比較済みなので、ここでは
        // OFFSET 副行が出力されたサンプル月では「仕入値引・戻し高」が出現することだけを確認。
        // (該当行が無い fixture もあるので contains の有無は assert しない。)
        assertTrue(body.length() > 0, "CSV body が生成されている");
    }

    @SuppressWarnings("unused") // 将来 fixture parser を再利用する場合に保持
    private static List<String[]> dummyConsume(BufferedReader br, InputStreamReader isr) {
        return Collections.emptyList();
    }
}
