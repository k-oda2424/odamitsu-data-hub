package jp.co.oda32.domain.service.finance;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G2-M3 (2026-05-06) 回帰テスト: {@link PaymentMfExcelParser} の section 別 summary キャプチャ。
 *
 * <p>旧実装は最初の合計行 (= 5日払い summary) しかキャプチャせず、その後の合計行を黙って捨てていた。
 * 結果、5日払い + 20日払い 両セクションがある Excel で 20日払い summary が失われ、
 * 整合性チェック (chk1/chk3) が「5日払い summary vs 5日払い+20日払い 両方の per-supplier sum」を
 * 比較する形で構造的にズレていた。
 *
 * <p>本テストは在庫物 fixture (.xlsx) ではなく POI で in-memory に Excel を構築する
 * (= ステートレス、外部依存なしで section 遷移を直接検証できる)。
 */
class PaymentMfExcelParserSectionTest {

    /**
     * Case 1: 5日払い + 20日払い 両セクションがある Excel。
     * 旧実装は 5日払い summary のみ捕まえて 20日払い summary を取り逃していた。
     */
    @Test
    void parseSheet_両セクション_summary_両方キャプチャ() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("支払い明細");
            writeHeader(sheet, LocalDate.of(2026, 2, 5));

            // 5日払い 明細 (2 行)
            writeEntry(sheet, 2, "100001", "AAA㈱", 100000L, 99000L, 1000L, 0L, 0L, 0L);
            writeEntry(sheet, 3, "100002", "BBB㈱", 50000L,  49500L, 500L,  0L, 0L, 0L);
            // 5日払い 合計 (B 列 "合計"、C=請求, E=振込, F=送料, H=早払)
            writeTotalRow(sheet, 4, 150000L, 148500L, 1500L, 0L);

            // 20日払い 明細 (1 行)
            writeEntry(sheet, 5, "200001", "CCC㈱", 30000L, 29800L, 200L, 0L, 0L, 0L);
            // 20日払い 合計
            writeTotalRow(sheet, 6, 30000L, 29800L, 200L, 0L);

            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);

            // 送金日が読めている
            assertThat(parsed.transferDate).isEqualTo(LocalDate.of(2026, 2, 5));

            // 両 section の summary がキャプチャされている (旧実装は 5日払い のみだった)
            assertThat(parsed.summaries).containsKeys(PaymentMfSection.PAYMENT_5TH, PaymentMfSection.PAYMENT_20TH);

            PaymentMfExcelParser.SectionSummary s5 = parsed.summaries.get(PaymentMfSection.PAYMENT_5TH);
            assertThat(s5.invoiceTotal).isEqualTo(150000L);
            assertThat(s5.transferAmount).isEqualTo(148500L);
            assertThat(s5.sourceFee).isEqualTo(1500L);
            assertThat(s5.earlyPayment).isEqualTo(0L);

            PaymentMfExcelParser.SectionSummary s20 = parsed.summaries.get(PaymentMfSection.PAYMENT_20TH);
            assertThat(s20.invoiceTotal).isEqualTo(30000L);
            assertThat(s20.transferAmount).isEqualTo(29800L);
            assertThat(s20.sourceFee).isEqualTo(200L);
            assertThat(s20.earlyPayment).isEqualTo(0L);

            // entries の section が正しく振られている (合計行で遷移)
            assertThat(parsed.entries).hasSize(3);
            assertThat(parsed.entries.get(0).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(1).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(2).section).isEqualTo(PaymentMfSection.PAYMENT_20TH);
        }
    }

    /**
     * Case 2: 5日払いのみの Excel (合計行 1 個)。
     * 20日払い section は空のまま許容され、summaries には PAYMENT_5TH のみ入る。
     */
    @Test
    void parseSheet_5日払いのみ_PAYMENT20TH_は空セクション() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("支払い明細");
            writeHeader(sheet, LocalDate.of(2026, 2, 5));

            writeEntry(sheet, 2, "100001", "AAA㈱", 100000L, 99000L, 1000L, 0L, 0L, 0L);
            writeEntry(sheet, 3, "100002", "BBB㈱",  50000L, 49500L,  500L, 0L, 0L, 0L);
            writeTotalRow(sheet, 4, 150000L, 148500L, 1500L, 0L);
            // 20日払い 明細・合計なし

            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);

            assertThat(parsed.summaries).containsKey(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.summaries).doesNotContainKey(PaymentMfSection.PAYMENT_20TH);

            assertThat(parsed.entries).hasSize(2);
            assertThat(parsed.entries).allMatch(e -> e.section == PaymentMfSection.PAYMENT_5TH);
        }
    }

    /**
     * Case 3: 5日払い summary が捕まった後、20日払い 明細が PAYMENT_20TH に振られていることを
     * 詳細に検証 (chk3 で section 別 sum を独立比較できる前提を担保)。
     * このテストは旧実装でも entries の数と sourceName は同じだが、
     * section ラベル付与が新規挙動であることを示す。
     */
    @Test
    void parseSheet_合計行を境に_section_遷移する() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("支払い明細");
            writeHeader(sheet, LocalDate.of(2026, 2, 20));

            // 5日払い 3 行 + 合計 + 20日払い 2 行 + 合計
            writeEntry(sheet, 2, "100001", "FIVE-A", 10000L, 10000L, 0L, 0L, 0L, 0L);
            writeEntry(sheet, 3, "100002", "FIVE-B", 20000L, 20000L, 0L, 0L, 0L, 0L);
            writeEntry(sheet, 4, "100003", "FIVE-C", 30000L, 30000L, 0L, 0L, 0L, 0L);
            writeTotalRow(sheet, 5, 60000L, 60000L, 0L, 0L);
            writeEntry(sheet, 6, "200001", "TWENTY-A", 5000L, 5000L, 0L, 0L, 0L, 0L);
            writeEntry(sheet, 7, "200002", "TWENTY-B", 7000L, 7000L, 0L, 0L, 0L, 0L);
            writeTotalRow(sheet, 8, 12000L, 12000L, 0L, 0L);

            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);

            assertThat(parsed.entries).hasSize(5);
            // section ラベルが合計行を境に遷移している
            assertThat(parsed.entries.get(0).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(1).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(2).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(3).section).isEqualTo(PaymentMfSection.PAYMENT_20TH);
            assertThat(parsed.entries.get(4).section).isEqualTo(PaymentMfSection.PAYMENT_20TH);

            // section 別 summary が独立に取れている (chk1/chk3 を section 別判定する基盤)
            assertThat(parsed.summaries.get(PaymentMfSection.PAYMENT_5TH).invoiceTotal).isEqualTo(60000L);
            assertThat(parsed.summaries.get(PaymentMfSection.PAYMENT_20TH).invoiceTotal).isEqualTo(12000L);
        }
    }

    // ===========================================================
    // Helpers: 振込明細 Excel と同形のヘッダ・行を構築する
    // ===========================================================

    /** 行 0 に送金日 (E1)、行 1 に列名行を書き込む。 */
    private static void writeHeader(Sheet sheet, LocalDate transferDate) {
        Workbook wb = sheet.getWorkbook();
        CreationHelper ch = wb.getCreationHelper();
        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(ch.createDataFormat().getFormat("yyyy/m/d"));
        Row r0 = sheet.createRow(0);
        Cell c4 = r0.createCell(4);
        c4.setCellStyle(dateStyle);
        c4.setCellValue(java.sql.Date.valueOf(transferDate));
        // 列名行 (parser は normalize 済 文字列で照合する)
        Row r1 = sheet.createRow(1);
        // A=仕入コード, B=送り先, C=請求額, D=打ち込み額, E=振込金額, F=送料相手, G=値引, H=早払い, I=相殺
        r1.createCell(0).setCellValue("仕入コード");
        r1.createCell(1).setCellValue("送り先");
        r1.createCell(2).setCellValue("請求額");
        r1.createCell(3).setCellValue("打ち込み額");
        r1.createCell(4).setCellValue("振込金額");
        r1.createCell(5).setCellValue("送料相手");
        r1.createCell(6).setCellValue("値引");
        r1.createCell(7).setCellValue("早払い");
        r1.createCell(8).setCellValue("相殺");
    }

    private static void writeEntry(Sheet sheet, int rowIdx, String code, String name,
                                    long invoice, long transfer, long fee,
                                    long discount, long early, long offset) {
        Row r = sheet.createRow(rowIdx);
        r.createCell(0).setCellValue(Long.parseLong(code));
        r.createCell(1).setCellValue(name);
        r.createCell(2).setCellValue(invoice);
        r.createCell(3).setCellValue(invoice - discount - early); // 打ち込み額 (派生)
        r.createCell(4).setCellValue(transfer);
        r.createCell(5).setCellValue(fee);
        r.createCell(6).setCellValue(discount);
        r.createCell(7).setCellValue(early);
        r.createCell(8).setCellValue(offset);
    }

    /** 合計行: B 列に "合計"、C=請求合計, E=振込合計, F=送料合計, H=早払合計 を書く。 */
    private static void writeTotalRow(Sheet sheet, int rowIdx,
                                       long invoice, long transfer, long fee, long early) {
        Row r = sheet.createRow(rowIdx);
        r.createCell(1).setCellValue("合計");
        r.createCell(2).setCellValue(invoice);
        r.createCell(3).setCellValue(invoice - early); // 打ち込み額
        r.createCell(4).setCellValue(transfer);
        r.createCell(5).setCellValue(fee);
        r.createCell(7).setCellValue(early);
    }

}
