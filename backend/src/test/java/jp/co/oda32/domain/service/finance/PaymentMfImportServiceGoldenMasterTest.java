package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 参照CSV（運用中の買掛仕入MFインポートファイル）と意味等価比較する回帰テスト。
 * シードSQLから m_payment_mf_rule を構築して Mockito 注入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceGoldenMasterTest {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @InjectMocks PaymentMfImportService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");
    private static final Path SEED_SQL = Paths.get("src/main/resources/db/migration/V011__create_payment_mf_tables.sql");
    private static final Charset CP932 = Charset.forName("MS932");

    private List<MPaymentMfRule> rules;

    @BeforeEach
    void loadSeed() throws Exception {
        String sql = Files.readString(SEED_SQL, java.nio.charset.StandardCharsets.UTF_8);
        rules = parseRules(sql);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "振込み明細08-2-5.xlsx,  買掛仕入MFインポートファイル_20260205.csv",
            "振込み明細08-2-20.xlsx, 買掛仕入MFインポートファイル_20260220_v2.csv"
    })
    void 意味等価比較(String xlsx, String goldenCsv) throws Exception {
        when(ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0")).thenReturn(rules);
        when(payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        byte[] xlsxBytes = Files.readAllBytes(FIXTURE_DIR.resolve(xlsx));
        MockMultipartFile multipart = new MockMultipartFile(
                "file", xlsx,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        PaymentMfPreviewResponse preview = service.preview(multipart);
        assertEquals(0, preview.getErrorCount(),
                "未登録送り先あり: " + preview.getUnregisteredSources());

        byte[] actual = service.convert(preview.getUploadId(), null);
        byte[] expected = Files.readAllBytes(FIXTURE_DIR.resolve(goldenCsv));

        List<String[]> a = parseCsv(actual);
        List<String[]> e = parseCsv(expected);
        assertEquals(e.size(), a.size(),
                "行数不一致 (" + xlsx + ") expected=" + e.size() + " actual=" + a.size());
        for (int i = 0; i < e.size(); i++) {
            String[] er = e.get(i);
            String[] ar = a.get(i);
            assertEquals(er.length, ar.length, "列数不一致 行" + (i + 1));
            for (int j = 0; j < er.length; j++) {
                assertEquals(er[j], ar[j],
                        "不一致 " + xlsx + " 行" + (i + 1) + " 列" + (j + 1));
            }
        }
    }

    // ---- Seed SQL パーサ ----
    private static final Pattern RULE_INSERT = Pattern.compile(
            "INSERT INTO m_payment_mf_rule.*?VALUES\\s*(.*?);", Pattern.DOTALL);

    static List<MPaymentMfRule> parseRules(String sql) {
        List<MPaymentMfRule> out = new ArrayList<>();
        Matcher m = RULE_INSERT.matcher(sql);
        int idSeq = 1;
        while (m.find()) {
            for (String[] t : splitTuples(m.group(1))) {
                out.add(MPaymentMfRule.builder()
                        .id(idSeq++)
                        .sourceName(t[0])
                        .paymentSupplierCode(nullable(t[1]))
                        .ruleKind(t[2])
                        .debitAccount(t[3])
                        .debitSubAccount(nullable(t[4]))
                        .debitDepartment(nullable(t[5]))
                        .debitTaxCategory(t[6])
                        .creditAccount(t[7])
                        .creditSubAccount(nullable(t[8]))
                        .creditDepartment(nullable(t[9]))
                        .creditTaxCategory(t[10])
                        .summaryTemplate(t[11])
                        .tag(nullable(t[12]))
                        .priority(Integer.parseInt(t[13].trim()))
                        .delFlg("0")
                        .build());
            }
        }
        return out;
    }

    static String nullable(String v) { return (v == null || "NULL".equals(v)) ? null : v; }

    static List<String[]> splitTuples(String block) {
        List<String[]> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < block.length(); i++) {
            char c = block.charAt(i);
            if (inQuote) {
                if (c == '\'') {
                    if (i + 1 < block.length() && block.charAt(i + 1) == '\'') { cur.append('\''); i++; }
                    else { inQuote = false; cur.append(c); }
                } else cur.append(c);
            } else {
                if (c == '\'') { inQuote = true; cur.append(c); }
                else if (c == '(') { depth++; if (depth == 1) { cur.setLength(0); continue; } cur.append(c); }
                else if (c == ')') { depth--; if (depth == 0) { out.add(splitTuple(cur.toString())); cur.setLength(0); continue; } cur.append(c); }
                else if (depth > 0) cur.append(c);
            }
        }
        return out;
    }

    static String[] splitTuple(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuote) {
                if (c == '\'') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\'') { cur.append('\''); i++; }
                    else inQuote = false;
                } else cur.append(c);
            } else {
                if (c == '\'') inQuote = true;
                else if (c == ',') { out.add(cur.toString().trim()); cur.setLength(0); }
                else cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

    // ---- CSV パーサ (CP932) ----
    private static List<String[]> parseCsv(byte[] bytes) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(bytes), CP932))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }
}
