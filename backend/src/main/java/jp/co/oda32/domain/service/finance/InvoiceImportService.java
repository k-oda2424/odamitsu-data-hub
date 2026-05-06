package jp.co.oda32.domain.service.finance;

import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import jp.co.oda32.dto.finance.InvoiceImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMILE 請求実績 Excel 取込サービス。
 *
 * <ul>
 *   <li>SF-07: Excel 内同一 partnerCode の重複を後勝ちで dedup (warn ログ + errors 集約)</li>
 *   <li>SF-08: DB 既存重複 (UNIQUE 制約違反含む) も WARN ログで可視化</li>
 *   <li>SF-12: 既存 invoice 取得を Repository derived query 化 (無名 Specification 撤去)</li>
 *   <li>SF-13: パーサー異常を {@link InvoiceImportResult#getErrors()} に行単位集約</li>
 *   <li>SF-14: 数値 → 文字列変換は {@link #formatNumeric(double)} に共通化 (BigDecimal#toPlainString)</li>
 *   <li>SF-15: {@code getCellBigDecimal} の文字列セル変換失敗時は silent zero ではなく null + errors 記録</li>
 *   <li>SF-15: {@code closing_date} の SF-01 CHECK 制約と同じ正規表現で fail-fast 検証</li>
 *   <li>SF-18: クラスレベル {@code @Transactional(readOnly=true)} + 書込みは個別 override</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InvoiceImportService {

    /** SF-15: SF-01 V031 の CHECK 制約と同じパターン (closing_date format)。 */
    static final Pattern CLOSING_DATE_OUTPUT_FORMAT =
            Pattern.compile("^\\d{4}/\\d{2}/(末|\\d{2})$");

    private final TInvoiceRepository tInvoiceRepository;

    private static final Pattern CLOSING_DATE_PATTERN =
            Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日締");

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Transactional
    @AuditLog(table = "t_invoice", operation = "import",
            pkExpression = "{'fileName': #a0?.originalFilename, 'shopNo': #a1}",
            captureArgsAsAfter = false, captureReturnAsAfter = true)
    public InvoiceImportResult importFromExcel(MultipartFile file, Integer shopNoParam) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("ファイル名が取得できません");
        }
        if (!originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Excelファイル（.xlsx）のみ対応しています");
        }
        String contentType = file.getContentType();
        if (contentType != null && !XLSX_CONTENT_TYPE.equals(contentType)) {
            throw new IllegalArgumentException("Excelファイル（.xlsx）のみ対応しています");
        }

        // shopNoが指定されればそれを使用、未指定ならファイル名から推定
        int shopNo = shopNoParam != null ? shopNoParam
                : (originalFilename.contains("松山") ? 2 : 1);
        boolean isMatsuyama = shopNo == 2;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Sheet1");
            if (sheet == null) {
                log.warn("Sheet1が見つかりません。先頭シートを使用します: file={}", originalFilename);
                sheet = workbook.getSheetAt(0);
            }
            if (sheet == null) {
                throw new IllegalArgumentException("シートが見つかりません");
            }

            // SF-13: 行単位エラーを集約
            List<String> errors = new ArrayList<>();

            // Row2: 締日を導出
            String closingDate = parseClosingDate(sheet.getRow(1));
            // SF-15: SF-01 CHECK 制約と整合する正規表現で fail-fast 検証
            if (!CLOSING_DATE_OUTPUT_FORMAT.matcher(closingDate).matches()) {
                throw new IllegalArgumentException(
                        "締日のフォーマットが不正です (期待: YYYY/MM/末 or YYYY/MM/DD): '" + closingDate + "'");
            }

            // Phase 1: 全行パース → SF-07 dedup 用に LinkedHashMap で蓄積
            LinkedHashMap<String, TInvoice> parsedByPartnerCode = new LinkedHashMap<>();
            int skippedRows = 0;
            int totalRows = 0;

            for (int i = 4; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int rowNum1 = i + 1; // 1-origin row number for error messages

                // 総合計行チェック（E列 = index 4）
                String colE = getCellStringValue(row.getCell(4));
                if (colE != null && colE.contains("総合計")) {
                    break;
                }

                // 得意先コード（A列 = index 0）
                Cell codeCell = row.getCell(0);
                if (codeCell == null) {
                    continue;
                }
                String rawCode = getCellStringValue(codeCell);
                if (rawCode == null || rawCode.isBlank()) {
                    continue;
                }

                totalRows++;

                String partnerCode;
                try {
                    partnerCode = convertPartnerCode(rawCode);
                } catch (IllegalArgumentException ex) {
                    // SF-13: パース不能行は errors に集約して continue
                    skippedRows++;
                    errors.add(String.format("row=%d: 得意先コード変換失敗 (value='%s')", rowNum1, rawCode));
                    log.warn("得意先コード変換失敗: row={}, value='{}'", rowNum1, rawCode);
                    continue;
                }

                // 松山の999999はスキップ
                if (isMatsuyama && "999999".equals(partnerCode)) {
                    skippedRows++;
                    log.debug("松山999999スキップ: row={}", rowNum1);
                    continue;
                }

                // 得意先名（B列 = index 1）
                String partnerName = getCellStringValue(row.getCell(1));
                if ((partnerName == null || partnerName.isBlank()) && !"999999".equals(partnerCode)) {
                    skippedRows++;
                    log.debug("得意先名なしスキップ: row={}, code={}", rowNum1, partnerCode);
                    continue;
                }
                if (partnerName == null || partnerName.isBlank()) {
                    partnerName = "上様";
                }

                // SF-15: 金額セルの変換失敗は errors に集約
                BigDecimal previousBalance = getCellBigDecimalOrError(row.getCell(5), rowNum1, "F", errors);
                BigDecimal totalPayment = getCellBigDecimalOrError(row.getCell(6), rowNum1, "G", errors);
                BigDecimal carryOverBalance = getCellBigDecimalOrError(row.getCell(8), rowNum1, "I", errors);
                BigDecimal netSales = getCellBigDecimalOrError(row.getCell(9), rowNum1, "J", errors);
                BigDecimal taxPrice = getCellBigDecimalOrError(row.getCell(10), rowNum1, "K", errors);
                BigDecimal netSalesIncludingTax = getCellBigDecimalOrError(row.getCell(11), rowNum1, "L", errors);
                BigDecimal currentBillingAmount = getCellBigDecimalOrError(row.getCell(12), rowNum1, "M", errors);

                TInvoice invoice = TInvoice.builder()
                        .partnerCode(partnerCode)
                        .partnerName(partnerName)
                        .closingDate(closingDate)
                        .previousBalance(previousBalance)
                        .totalPayment(totalPayment)
                        .carryOverBalance(carryOverBalance)
                        .netSales(netSales)
                        .taxPrice(taxPrice)
                        .netSalesIncludingTax(netSalesIncludingTax)
                        .currentBillingAmount(currentBillingAmount)
                        .shopNo(shopNo)
                        .build();

                // SF-07: Excel 内同一 partnerCode の重複を後勝ちで dedup + warn
                TInvoice prev = parsedByPartnerCode.put(partnerCode, invoice);
                if (prev != null) {
                    skippedRows++;
                    String msg = String.format(
                            "row=%d: Excel内で partnerCode=%s が重複しています (後勝ちで採用)",
                            rowNum1, partnerCode);
                    errors.add(msg);
                    log.warn("Excel 内 partnerCode 重複: row={}, partnerCode={}, prevName={}, newName={}",
                            rowNum1, partnerCode, prev.getPartnerName(), partnerName);
                }
            }

            List<TInvoice> parsedInvoices = new ArrayList<>(parsedByPartnerCode.values());

            // Phase 2: 既存レコードを一括取得してUPSERT
            // SF-12: Repository derived query で無名 Specification を撤去
            List<TInvoice> existingInvoices =
                    tInvoiceRepository.findByShopNoAndClosingDate(shopNo, closingDate);

            // SF-08: DB 既存重複 (本来 UNIQUE 制約で防がれるが念のため) を WARN
            Map<String, TInvoice> existingMap = new java.util.HashMap<>();
            for (TInvoice ex : existingInvoices) {
                TInvoice clash = existingMap.put(ex.getPartnerCode(), ex);
                if (clash != null) {
                    log.warn("DB に partnerCode={} の重複あり (closingDate={}, shopNo={}): keep id={}, drop id={}",
                            ex.getPartnerCode(), closingDate, shopNo,
                            clash.getInvoiceId(), ex.getInvoiceId());
                }
            }

            int insertedRows = 0;
            int updatedRows = 0;
            List<TInvoice> toSave = new ArrayList<>();

            for (TInvoice parsed : parsedInvoices) {
                TInvoice existing = existingMap.get(parsed.getPartnerCode());
                if (existing != null) {
                    existing.setPartnerName(parsed.getPartnerName());
                    existing.setPreviousBalance(parsed.getPreviousBalance());
                    existing.setTotalPayment(parsed.getTotalPayment());
                    existing.setCarryOverBalance(parsed.getCarryOverBalance());
                    existing.setNetSales(parsed.getNetSales());
                    existing.setTaxPrice(parsed.getTaxPrice());
                    existing.setNetSalesIncludingTax(parsed.getNetSalesIncludingTax());
                    existing.setCurrentBillingAmount(parsed.getCurrentBillingAmount());
                    // payment_date は保持（更新しない）
                    toSave.add(existing);
                    updatedRows++;
                } else {
                    toSave.add(parsed);
                    insertedRows++;
                }
            }

            // 一括永続化
            tInvoiceRepository.saveAll(toSave);

            log.info("請求実績インポート完了: closingDate={}, shopNo={}, total={}, inserted={}, updated={}, skipped={}, errors={}",
                    closingDate, shopNo, totalRows, insertedRows, updatedRows, skippedRows, errors.size());

            return InvoiceImportResult.builder()
                    .closingDate(closingDate)
                    .shopNo(shopNo)
                    .totalRows(totalRows)
                    .insertedRows(insertedRows)
                    .updatedRows(updatedRows)
                    .skippedRows(skippedRows)
                    .errors(errors)
                    .build();
        }
    }

    /**
     * Row2から締日を導出する
     * 例: "2025年11月30日締 今回請求分" → "2025/11/末"
     * 例: "2025年 7月20日締 今回請求分" → "2025/07/20"
     */
    String parseClosingDate(Row row) {
        if (row == null) {
            throw new IllegalArgumentException("Row2が空です。締日を解析できません");
        }
        Cell cell = row.getCell(0);
        if (cell == null) {
            throw new IllegalArgumentException("Row2のA列が空です。締日を解析できません");
        }

        String raw = getCellStringValue(cell);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Row2のA列が空です。締日を解析できません");
        }

        // NFKC正規化（全角数字・全角スペース対策）
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC);

        Matcher matcher = CLOSING_DATE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Row2から締日を解析できません: '" + raw + "'");
        }

        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));

        int lastDayOfMonth = YearMonth.of(year, month).lengthOfMonth();

        String monthStr = String.format("%02d", month);
        if (day == lastDayOfMonth) {
            return year + "/" + monthStr + "/末";
        } else {
            return year + "/" + monthStr + "/" + String.format("%02d", day);
        }
    }

    /**
     * 得意先コードを6桁0埋めに変換する
     * - "<009896>" → "009896"
     * - 29 → "000029"
     * - "181" → "000181"
     */
    String convertPartnerCode(String rawCode) {
        String code = rawCode.replace("<", "").replace(">", "").trim();

        try {
            long numericCode = Long.parseLong(code);
            return String.format("%06d", numericCode);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("得意先コードが数値ではありません: '" + rawCode + "'");
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatNumeric(cell.getNumericCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    try {
                        yield formatNumeric(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    /**
     * SF-14: 数値 (double) → 文字列の共通ヘルパ。
     * - 整数値: 後ろ桁無し ("123")
     * - 小数値: BigDecimal#toPlainString で "1.23" ("1.0E2" 表記回避)
     */
    static String formatNumeric(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return null;
        }
        if (val == Math.floor(val)) {
            return String.valueOf((long) val);
        }
        return BigDecimal.valueOf(val).toPlainString();
    }

    /**
     * SF-15: 文字列セルが数値変換失敗時、silent zero ではなく null を返す。
     * 数値変換失敗時は呼び出し側でログ + errors 記録すること。
     */
    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).setScale(0, RoundingMode.HALF_UP);
            case STRING -> {
                String raw = cell.getStringCellValue();
                if (raw == null || raw.trim().isEmpty()) {
                    yield BigDecimal.ZERO;
                }
                try {
                    yield new BigDecimal(raw.trim()).setScale(0, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    // SF-15: silent zero フォールバック撤去 → null で異常を伝播
                    yield null;
                }
            }
            case FORMULA -> {
                try {
                    yield BigDecimal.valueOf(cell.getNumericCellValue()).setScale(0, RoundingMode.HALF_UP);
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * SF-15: {@link #getCellBigDecimal(Cell)} のラッパ。
     * null (= 数値変換失敗) なら errors に記録してから {@link BigDecimal#ZERO} を返す
     * (Entity の column が NULL 不可のケースに備えて 0 補正)。
     */
    private BigDecimal getCellBigDecimalOrError(
            Cell cell, int rowNum, String colLabel, List<String> errors) {
        BigDecimal v = getCellBigDecimal(cell);
        if (v == null) {
            String raw = getCellStringValue(cell);
            String msg = String.format(
                    "row=%d, col=%s: 金額として解釈できません (value='%s') → 0 円扱い",
                    rowNum, colLabel, raw);
            errors.add(msg);
            log.warn(msg);
            return BigDecimal.ZERO;
        }
        return v;
    }
}
