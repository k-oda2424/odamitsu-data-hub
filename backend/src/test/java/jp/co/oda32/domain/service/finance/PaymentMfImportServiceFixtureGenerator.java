package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * P1-03 案 D-2 / Codex C2 適用後の PaymentMfImportService.convert() 出力を
 * src/test/resources/paymentmf/ 配下に新ゴールデンマスタ CSV として書き出す
 * ワンショット生成テスト。
 *
 * <p>通常は {@link Disabled} だが、以下手順で fixture 再生成のときだけ一時的に
 * @Disabled を外して実行する:
 * <ol>
 *   <li>本クラスから {@code @Disabled} を一時削除</li>
 *   <li>{@code ./gradlew test --tests '*PaymentMfImportServiceFixtureGenerator*'} 実行</li>
 *   <li>{@code 買掛仕入MFインポートファイル_*_v3.csv} が出力される</li>
 *   <li>{@code @Disabled} を戻し、{@link PaymentMfImportServiceGoldenMasterTest} の
 *       {@code @CsvSource} を {@code _v3} 名に更新</li>
 *   <li>GoldenMasterTest の {@code @Disabled} を外して PASS 確認</li>
 * </ol>
 */
@Disabled("Fixture 再生成専用 (一時的に外して実行する。通常 CI では skip)")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceFixtureGenerator {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @Mock MOffsetJournalRuleRepository offsetJournalRuleRepository;
    @InjectMocks PaymentMfImportService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");
    private static final Path SEED_SQL = Paths.get("src/main/resources/db/migration/V011__create_payment_mf_tables.sql");

    @Test
    void generate_v3_fixtures() throws Exception {
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

        generateOne("振込み明細08-2-5.xlsx",  "買掛仕入MFインポートファイル_20260205_v3.csv");
        generateOne("振込み明細08-2-20.xlsx", "買掛仕入MFインポートファイル_20260220_v3.csv");
    }

    private void generateOne(String xlsx, String outCsv) throws Exception {
        byte[] xlsxBytes = Files.readAllBytes(FIXTURE_DIR.resolve(xlsx));
        MockMultipartFile multipart = new MockMultipartFile(
                "file", xlsx,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        PaymentMfPreviewResponse preview = service.preview(multipart);
        if (preview.getErrorCount() > 0) {
            throw new IllegalStateException(
                    "未登録送り先あり (" + xlsx + "): " + preview.getUnregisteredSources());
        }
        byte[] csv = service.convert(preview.getUploadId(), null);
        Path out = FIXTURE_DIR.resolve(outCsv);
        Files.write(out, csv);
        System.out.println("[FixtureGenerator] wrote " + out.toAbsolutePath() + " (" + csv.length + " bytes)");
    }
}
