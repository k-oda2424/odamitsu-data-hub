package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfClientMapping;
import jp.co.oda32.domain.model.finance.MMfJournalRule;
import jp.co.oda32.domain.repository.finance.MMfClientMappingRepository;
import jp.co.oda32.domain.repository.finance.MMfJournalRuleRepository;
import jp.co.oda32.dto.finance.cashbook.CashBookPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Python版CashBookToMoneyForwardConverterが生成したゴールデンCSVと突合する回帰テスト。
 * 意味等価比較（行数・取引日・金額・勘定科目・税区分・摘要の一致）。
 * シード SQL を直接パースしてルール/マッピングを構築し、Mockitoで注入する。
 */
@ExtendWith(MockitoExtension.class)
class CashBookConvertServiceGoldenMasterTest {

    @Mock MMfJournalRuleRepository ruleRepository;
    @Mock MMfClientMappingRepository mappingRepository;
    @InjectMocks CashBookConvertService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/cashbook");
    private static final Path SEED_SQL = Paths.get("src/main/resources/db/migration/V008__create_mf_cashbook_tables.sql");

    private List<MMfJournalRule> rules;
    private List<MMfClientMapping> mappings;

    @BeforeEach
    void loadSeed() throws Exception {
        String sql = Files.readString(SEED_SQL, StandardCharsets.UTF_8);
        rules = parseRules(sql);
        mappings = parseMappings(sql);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "2025現金出納帳2-3_改造.xlsx, 現金出納帳2-3.csv",
            "2025現金出納帳3-4_改造.xlsx, 現金出納帳3-4.csv",
            "2025現金出納帳4-5_改造.xlsx, 現金出納帳4-5.csv",
            "2025現金出納帳5-6_改造.xlsx, 現金出納帳5-6.csv",
            "2025現金出納帳6-7_改造.xlsx, 現金出納帳6-7.csv",
            "2025現金出納帳7-8_改造.xlsx, 現金出納帳7-8.csv",
            "2025現金出納帳8-9_改造.xlsx, 現金出納帳8-9.csv",
            "2025現金出納帳9-10_改造.xlsx, 現金出納帳9-10.csv",
            "2025現金出納帳10-11_改造.xlsx, 現金出納帳10-11.csv",
            "2025現金出納帳11-12_改造.xlsx, 現金出納帳11-12.csv",
            "2025-26現金出納帳12-1_改造.xlsx, 2025-26現金出納帳12-1_改造.csv",
            "2026現金出納帳1-2_改造.xlsx, 2026現金出納帳1-2_改造.csv"
    })
    void 意味等価比較(String xlsx, String goldenCsv) throws Exception {
        when(ruleRepository.findByDelFlgOrderByDescriptionCAscPriorityAsc("0")).thenReturn(rules);
        when(mappingRepository.findByDelFlgOrderByAliasAsc("0")).thenReturn(mappings);

        Path xlsxPath = FIXTURE_DIR.resolve(xlsx);
        Path csvPath = FIXTURE_DIR.resolve(goldenCsv);

        byte[] xlsxBytes = Files.readAllBytes(xlsxPath);
        MockMultipartFile multipart = new MockMultipartFile(
                "file", xlsx,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        CashBookPreviewResponse preview = service.preview(multipart);
        assertEquals(0, preview.getErrorCount(),
                "エラー検出: unmapped=" + preview.getUnmappedClients()
                        + " / unknown=" + preview.getUnknownDescriptions());

        byte[] generated = service.convert(preview.getUploadId());
        List<String[]> actual = parseCsv(generated);
        List<String[]> expected = parseCsv(Files.readAllBytes(csvPath));

        assertEquals(expected.size(), actual.size(), "行数不一致 (" + xlsx + ")");
        assertArrayEquals(expected.get(0), actual.get(0), "ヘッダー不一致");

        for (int i = 1; i < expected.size(); i++) {
            String[] e = expected.get(i);
            String[] a = actual.get(i);
            assertEquals(e.length, a.length, "列数不一致 行" + (i + 1));
            for (int j = 0; j < e.length; j++) {
                assertEquals(e[j], a[j],
                        "不一致 " + xlsx + " 行" + (i + 1) + " 列" + (j + 1)
                                + " (header=" + expected.get(0)[j] + ")");
            }
        }
    }

    // ---- Seed SQL パーサ ----

    private static final Pattern RULE_INSERT = Pattern.compile(
            "INSERT INTO m_mf_journal_rule.*?VALUES\\s*(.*?);",
            Pattern.DOTALL);
    private static final Pattern MAPPING_INSERT = Pattern.compile(
            "INSERT INTO m_mf_client_mapping.*?VALUES\\s*(.*?);",
            Pattern.DOTALL);

    static List<MMfJournalRule> parseRules(String sql) {
        Matcher m = RULE_INSERT.matcher(sql);
        if (!m.find()) return List.of();
        String valuesBlock = m.group(1);
        List<String[]> tuples = splitTuples(valuesBlock);
        List<MMfJournalRule> out = new ArrayList<>();
        int idSeq = 1;
        for (String[] t : tuples) {
            out.add(MMfJournalRule.builder()
                    .id(idSeq++)
                    .descriptionC(t[0])
                    .descriptionDKeyword("NULL".equals(t[1]) ? null : t[1])
                    .priority(Integer.parseInt(t[2]))
                    .amountSource(t[3])
                    .debitAccount(t[4])
                    .debitSubAccount(t[5])
                    .debitDepartment(t[6])
                    .debitTaxResolver(t[7])
                    .creditAccount(t[8])
                    .creditSubAccount(t[9])
                    .creditSubAccountTemplate(t[10])
                    .creditDepartment(t[11])
                    .creditTaxResolver(t[12])
                    .summaryTemplate(t[13])
                    .requiresClientMapping(Boolean.parseBoolean(t[14]))
                    .delFlg("0")
                    .build());
        }
        return out;
    }

    static List<MMfClientMapping> parseMappings(String sql) {
        Matcher m = MAPPING_INSERT.matcher(sql);
        if (!m.find()) return List.of();
        List<String[]> tuples = splitTuples(m.group(1));
        List<MMfClientMapping> out = new ArrayList<>();
        int idSeq = 1;
        for (String[] t : tuples) {
            out.add(MMfClientMapping.builder()
                    .id(idSeq++)
                    .alias(t[0])
                    .mfClientName(t[1])
                    .delFlg("0")
                    .build());
        }
        return out;
    }

    /** "(a,b,c),(d,e,f)" → [["a","b","c"],["d","e","f"]] */
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
                else if (c == ')') { depth--; if (depth == 0) { out.add(splitCsvTuple(cur.toString())); cur.setLength(0); continue; } cur.append(c); }
                else if (depth > 0) cur.append(c);
            }
        }
        return out;
    }

    static String[] splitCsvTuple(String s) {
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

    // ---- CSV パーサ（テスト用）----

    private static List<String[]> parseCsv(byte[] bytes) throws Exception {
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try (InputStream is = new java.io.ByteArrayInputStream(bytes, offset, bytes.length - offset);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                rows.add(splitCsvLine(line));
            }
            return rows;
        }
    }

    private static String[] splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuote = false;
                } else cur.append(c);
            } else {
                if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
                else if (c == '"') inQuote = true;
                else cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
