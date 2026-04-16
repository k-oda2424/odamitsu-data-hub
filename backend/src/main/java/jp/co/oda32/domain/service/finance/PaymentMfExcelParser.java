package jp.co.oda32.domain.service.finance;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 振込明細 Excel（支払い明細 / 振込明細シート）から明細行と合計行サマリを抽出する。
 *
 * <p>Excel 構造:
 * <ul>
 *   <li>1 行目: ヘッダ情報。E1 付近に送金日（LocalDate）が入る。</li>
 *   <li>2 行目: カラム名行（送り先 / 請求額 / 振込金額 / 送料相手 / 早払い 等）。</li>
 *   <li>3 行目以降: 明細行 → 合計行 → 明細行（20日払いセクション）。</li>
 * </ul>
 *
 * <p>ステートレスな純粋関数ユーティリティ。Bean 化せず、呼び出し側は
 * {@link #selectSheet(Workbook)} / {@link #parseSheet(Sheet)} を直接呼ぶ。
 * 数値コードの 6 桁正規化は {@link PaymentMfImportService#normalizePaymentSupplierCode}
 * を利用する（PAYABLE 用のみ、同パッケージ内で共有）。
 */
final class PaymentMfExcelParser {

    private PaymentMfExcelParser() {}

    /** 合計/メタ行のB列判定（正規化後の完全一致） */
    private static final Set<String> META_EXACT = Set.of(
            "合計", "小計", "その他計", "本社仕入 合計", "請求額", "打ち込み額", "打込額",
            "本社仕入合計"
    );
    /** 前方一致でメタ行判定 */
    private static final List<String> META_PREFIX = List.of(
            "20日払い振込手数料", "5日払い振込手数料", "送金日"
    );

    /**
     * Workbook から対象シートを選択する。優先順位: "支払い明細" &gt; "振込明細" &gt; 部分一致フォールバック。
     * 変換MAP・MF 用シート・福通シートは除外する。
     *
     * @return 対象シート。見つからない場合は null。
     */
    static Sheet selectSheet(Workbook workbook) {
        Sheet byExactPayment = null;
        Sheet byExactTransfer = null;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String n = workbook.getSheetName(i);
            if (n == null) continue;
            if (n.contains("MF") || n.contains("変換MAP") || n.contains("福通")) continue;
            if ("支払い明細".equals(n)) byExactPayment = workbook.getSheetAt(i);
            else if ("振込明細".equals(n)) byExactTransfer = workbook.getSheetAt(i);
        }
        if (byExactPayment != null) return byExactPayment;
        if (byExactTransfer != null) return byExactTransfer;
        // フォールバック
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String n = workbook.getSheetName(i);
            if (n != null && (n.contains("支払") || n.contains("振込"))
                    && !n.contains("MF") && !n.contains("変換") && !n.contains("福通")) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    /**
     * シートを走査し、送金日・合計行サマリ・明細エントリを抽出する。
     * 合計行の前は {@code afterTotal=false}、合計行以降は {@code afterTotal=true}（= 20日払いセクション）。
     */
    static ParsedWorkbook parseSheet(Sheet sheet) {
        ParsedWorkbook out = new ParsedWorkbook();
        out.entries = new ArrayList<>();

        // 送金日: 1行目の日付セルをスキャン（通常はE1）
        Row r1 = sheet.getRow(0);
        if (r1 != null) {
            for (int c = 0; c <= 10; c++) {
                LocalDate d = PaymentMfCellReader.readDateCell(r1.getCell(c));
                if (d != null) { out.transferDate = d; break; }
            }
        }

        // ヘッダ行（2行目）から列マップ構築
        Row header = sheet.getRow(1);
        if (header == null) throw new IllegalArgumentException("ヘッダ行（2行目）が見つかりません");
        Map<String, Integer> colMap = buildColumnMap(header);
        // 「仕入コード」ヘッダが無い振込明細（20日払いなど）では、送り先列の直前（通常は A列）に数値コードが入る。
        Integer colCode = colMap.getOrDefault("仕入コード", null);
        Integer colSource = colMap.get("送り先");
        if (colCode == null && colSource != null && colSource > 0) {
            colCode = colSource - 1;
        }
        Integer colAmount = colMap.get("請求額");
        Integer colFee = colMap.get("送料相手");
        Integer colEarly = colMap.get("早払い");
        // 合計行の「振込金額」列を読み取り、請求額総計 - 値引 - 早払 との整合性チェックに使う
        Integer colTransfer = colMap.get("振込金額");
        if (colSource == null || colAmount == null) {
            List<String> missing = new ArrayList<>();
            if (colSource == null) missing.add("送り先");
            if (colAmount == null) missing.add("請求額");
            throw new IllegalArgumentException(
                    "ヘッダ『" + String.join("』『", missing) + "』が特定できません。"
                    + "2行目で見つかった列: " + colMap.keySet());
        }

        int last = sheet.getLastRowNum();
        boolean summaryCaptured = false;
        boolean afterTotal = false;
        for (int i = 2; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String sourceName = PaymentMfCellReader.readStringCell(row.getCell(colSource));
            String sourceNorm = PaymentMfCellReader.normalize(sourceName);
            Long amount = PaymentMfCellReader.readLongCell(row.getCell(colAmount));

            // 合計行の処理（サマリー抽出 + セクション切替）
            if (sourceNorm != null && ("合計".equals(sourceNorm) || isTotalRow(row, colSource))) {
                if (!summaryCaptured) {
                    out.summarySourceFee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
                    out.summaryEarlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
                    out.summaryTransferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
                    out.summaryInvoiceTotal = colAmount == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colAmount));
                    summaryCaptured = true;
                    afterTotal = true;
                }
                continue;
            }
            // メタ行スキップ
            if (isMetaRow(sourceNorm)) continue;
            // 金額ゼロ/未入力はスキップ
            if (amount == null || amount == 0L) continue;
            if (sourceName == null || sourceName.isBlank()) continue;

            String supplierCode = null;
            if (colCode != null && colCode >= 0) {
                supplierCode = PaymentMfCellReader.readStringCell(row.getCell(colCode));
                if (supplierCode != null) {
                    supplierCode = supplierCode.trim();
                    if (supplierCode.isEmpty() || !supplierCode.chars().allMatch(Character::isDigit)) {
                        supplierCode = null;
                    } else {
                        supplierCode = PaymentMfImportService.normalizePaymentSupplierCode(supplierCode);
                    }
                }
            }

            ParsedEntry pe = new ParsedEntry();
            pe.rowIndex = i + 1;
            pe.supplierCode = supplierCode;
            pe.sourceName = sourceName.trim();
            pe.amount = amount;
            pe.afterTotal = afterTotal;
            out.entries.add(pe);
        }
        return out;
    }

    private static Map<String, Integer> buildColumnMap(Row header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String v = PaymentMfCellReader.readStringCell(header.getCell(c));
            if (v == null) continue;
            String n = PaymentMfCellReader.normalize(v);
            if (n.isEmpty()) continue;
            map.putIfAbsent(n, c);
        }
        return map;
    }

    private static boolean isTotalRow(Row row, int colSource) {
        // A列やB列が「合計」という明示
        for (int c = 0; c < Math.min(row.getLastCellNum(), colSource + 2); c++) {
            String v = PaymentMfCellReader.normalize(PaymentMfCellReader.readStringCell(row.getCell(c)));
            if ("合計".equals(v)) return true;
        }
        return false;
    }

    private static boolean isMetaRow(String normalized) {
        if (normalized == null || normalized.isEmpty()) return true;
        if (META_EXACT.contains(normalized)) return true;
        for (String p : META_PREFIX) if (normalized.startsWith(p)) return true;
        return false;
    }

    /** Excel 明細1行の抽出結果（内部用 POJO）。 */
    static class ParsedEntry {
        int rowIndex;
        String supplierCode;
        String sourceName;
        Long amount;
        /** 合計行より後の行 (= 20日払いセクション) に含まれる明細か */
        boolean afterTotal;
    }

    /** parseSheet の戻り値: 明細エントリ一覧 + 合計行サマリ値 + 送金日。 */
    static class ParsedWorkbook {
        List<ParsedEntry> entries;
        LocalDate transferDate;
        Long summarySourceFee;
        Long summaryEarlyPayment;
        Long summaryTransferAmount;
        Long summaryInvoiceTotal;
    }
}
