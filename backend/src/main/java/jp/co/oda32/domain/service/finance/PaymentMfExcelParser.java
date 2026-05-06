package jp.co.oda32.domain.service.finance;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
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
 *   <li>3 行目以降: 5日払い 明細 → 5日払い 合計 → 20日払い 明細 → 20日払い 合計。</li>
 * </ul>
 *
 * <p>ステートレスな純粋関数ユーティリティ。Bean 化せず、呼び出し側は
 * {@link #selectSheet(Workbook)} / {@link #parseSheet(Sheet)} を直接呼ぶ。
 * 数値コードの 6 桁正規化は {@link PaymentMfImportService#normalizePaymentSupplierCode}
 * を利用する（PAYABLE 用のみ、同パッケージ内で共有）。
 *
 * <p>G2-M3 (2026-05-06): 旧 {@code afterTotal} ブール値モデルを {@link PaymentMfSection}
 * 列挙型に置き換え、合計行ごとに section 別 summary をキャプチャする。
 * 旧実装は最初の合計行 (= 5日払い summary) を 1 度だけ捕まえてフラグを立てるだけだったため、
 * 20日払いセクションの合計行が捨てられて整合性チェック (chk1/chk3) が
 * 「5日払い summary vs 5日払い+20日払い 両方の明細合計」を比較する形にズレていた。
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
     *
     * <p>section 遷移: 走査開始時 {@link PaymentMfSection#PAYMENT_5TH}。
     * 合計行を検出するたびに、現在 section の summary を {@link ParsedExcel#summaries} へ格納し、
     * 次の section ({@code PAYMENT_5TH → PAYMENT_20TH}) に切替える。
     * {@code PAYMENT_20TH} 以降にさらに合計行があれば、{@code PAYMENT_20TH} の summary を最新値で上書きする
     * (現実的には 5日払い + 20日払いの 2 セクション以外は出ない)。
     */
    static ParsedExcel parseSheet(Sheet sheet) {
        ParsedExcel out = new ParsedExcel();

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
        // P1-03 案 D: per-supplier 行で値引/相殺/打込金額を読取り、supplier 別 attribute を追跡する。
        // colTransfer (列 E) は合計行サマリーと per-supplier の双方で使う。
        Integer colDiscount = colMap.get("値引");
        Integer colOffset = colMap.get("相殺");
        Integer colPayin = colMap.get("打込金額"); // 整合性検証用 (CSV には出さない派生値)
        if (colSource == null || colAmount == null) {
            List<String> missing = new ArrayList<>();
            if (colSource == null) missing.add("送り先");
            if (colAmount == null) missing.add("請求額");
            throw new IllegalArgumentException(
                    "ヘッダ『" + String.join("』『", missing) + "』が特定できません。"
                    + "2行目で見つかった列: " + colMap.keySet());
        }

        int last = sheet.getLastRowNum();
        // G2-M3: section enum で 5日払い / 20日払い を明示的に区別する。走査開始時は 5日払い。
        PaymentMfSection currentSection = PaymentMfSection.PAYMENT_5TH;
        for (int i = 2; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String sourceName = PaymentMfCellReader.readStringCell(row.getCell(colSource));
            String sourceNorm = PaymentMfCellReader.normalize(sourceName);
            Long amount = PaymentMfCellReader.readLongCell(row.getCell(colAmount));

            // 合計行の処理（section 別 summary 抽出 + 次セクションへ遷移）
            if (sourceNorm != null && ("合計".equals(sourceNorm) || isTotalRow(row, colSource))) {
                SectionSummary summary = new SectionSummary();
                summary.sourceFee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
                summary.earlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
                summary.transferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
                // 合計行の請求額列は colAmount をそのまま読む (旧 summaryInvoiceTotal と同義)。
                summary.invoiceTotal = PaymentMfCellReader.readLongCell(row.getCell(colAmount));
                // 同 section の合計行が複数あれば最後の値で上書き (現実的には起こらない)。
                out.summaries.put(currentSection, summary);

                // 次セクションへ遷移。PAYMENT_20TH の合計行を 2 度目以降に踏んだ場合は
                // 状態は PAYMENT_20TH のまま (= 上書き) で、これ以上の section 拡張はしない。
                if (currentSection == PaymentMfSection.PAYMENT_5TH) {
                    currentSection = PaymentMfSection.PAYMENT_20TH;
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
            // G2-M3: 旧 boolean afterTotal は section enum に置換。
            pe.section = currentSection;
            // P1-03 案 D: per-supplier の値引/早払/送料相手/相殺/振込金額を読取る。
            // 合計行 (= section 別 summary に集約) ではなく、ここで個別 supplier ごとに保持する。
            pe.transferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
            pe.fee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
            pe.discount = colDiscount == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colDiscount));
            pe.earlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
            pe.offset = colOffset == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colOffset));
            pe.payinAmount = colPayin == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colPayin));
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
        /**
         * G2-M3: この明細行が属するセクション (5日払い / 20日払い)。
         * 旧実装の {@code afterTotal} ブール値を置き換える。
         */
        PaymentMfSection section;
        // P1-03 案 D: per-supplier の付帯属性。NULL は該当列がヘッダに存在しないか空欄。
        /** 列 E: 振込金額 (= 打込金額 - 送料相手) */
        Long transferAmount;
        /** 列 F: 送料相手 (仕入先負担の振込手数料) */
        Long fee;
        /** 列 G: 値引 (通常の値引) */
        Long discount;
        /** 列 H: 早払い */
        Long earlyPayment;
        /** 列 I: 相殺 */
        Long offset;
        /** 列 D: 打込金額 (= 請求額 - 値引 - 早払。整合性検証用、CSV 出力には使わない派生値) */
        Long payinAmount;
    }

    /** 1 セクション (5日払い or 20日払い) の合計行 summary。 */
    static class SectionSummary {
        /** 列 F: 送料相手合計 */
        Long sourceFee;
        /** 列 H: 早払合計 */
        Long earlyPayment;
        /** 列 E: 振込金額合計 */
        Long transferAmount;
        /** 列 C: 請求額合計 */
        Long invoiceTotal;
    }

    /**
     * parseSheet の戻り値: 明細エントリ一覧 + section 別合計行サマリ + 送金日。
     *
     * <p>G2-M3 (2026-05-06): 旧 {@code summarySourceFee/EarlyPayment/TransferAmount/InvoiceTotal}
     * のフラットなフィールドを廃止し、{@code Map<PaymentMfSection, SectionSummary>} に格納する。
     * 5日払いのみの Excel では {@code summaries} に {@link PaymentMfSection#PAYMENT_5TH} のみ入り、
     * {@link PaymentMfSection#PAYMENT_20TH} は欠落する (空セクション許容)。
     */
    static class ParsedExcel {
        List<ParsedEntry> entries = new ArrayList<>();
        LocalDate transferDate;
        Map<PaymentMfSection, SectionSummary> summaries = new EnumMap<>(PaymentMfSection.class);
    }
}
