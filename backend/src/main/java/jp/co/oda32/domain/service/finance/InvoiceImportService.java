package jp.co.oda32.domain.service.finance;

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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceImportService {

    private final TInvoiceRepository tInvoiceRepository;

    private static final Pattern CLOSING_DATE_PATTERN =
            Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日締");

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Transactional
    public InvoiceImportResult importFromExcel(MultipartFile file) throws IOException {
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

        int shopNo = originalFilename.contains("松山") ? 2 : 1;
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

            // Row2: 締日を導出
            String closingDate = parseClosingDate(sheet.getRow(1));

            // Phase 1: 全行パース → エンティティリストに蓄積
            List<TInvoice> parsedInvoices = new ArrayList<>();
            int skippedRows = 0;
            int totalRows = 0;

            for (int i = 4; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

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

                String partnerCode = convertPartnerCode(rawCode);

                // 松山の999999はスキップ
                if (isMatsuyama && "999999".equals(partnerCode)) {
                    skippedRows++;
                    log.debug("松山999999スキップ: row={}", i + 1);
                    continue;
                }

                // 得意先名（B列 = index 1）
                String partnerName = getCellStringValue(row.getCell(1));
                if ((partnerName == null || partnerName.isBlank()) && !"999999".equals(partnerCode)) {
                    skippedRows++;
                    log.debug("得意先名なしスキップ: row={}, code={}", i + 1, partnerCode);
                    continue;
                }
                if (partnerName == null || partnerName.isBlank()) {
                    partnerName = "上様";
                }

                TInvoice invoice = TInvoice.builder()
                        .partnerCode(partnerCode)
                        .partnerName(partnerName)
                        .closingDate(closingDate)
                        .previousBalance(getCellBigDecimal(row.getCell(5)))
                        .totalPayment(getCellBigDecimal(row.getCell(6)))
                        .carryOverBalance(getCellBigDecimal(row.getCell(8)))
                        .netSales(getCellBigDecimal(row.getCell(9)))
                        .taxPrice(getCellBigDecimal(row.getCell(10)))
                        .netSalesIncludingTax(getCellBigDecimal(row.getCell(11)))
                        .currentBillingAmount(getCellBigDecimal(row.getCell(12)))
                        .shopNo(shopNo)
                        .build();

                parsedInvoices.add(invoice);
            }

            // Phase 2: 既存レコードを一括取得してUPSERT
            List<TInvoice> existingInvoices = tInvoiceRepository
                    .findAll((root, query, cb) -> cb.and(
                            cb.equal(root.get("shopNo"), shopNo),
                            cb.equal(root.get("closingDate"), closingDate)
                    ));

            Map<String, TInvoice> existingMap = existingInvoices.stream()
                    .collect(Collectors.toMap(TInvoice::getPartnerCode, Function.identity(), (a, b) -> a));

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

            log.info("請求実績インポート完了: closingDate={}, shopNo={}, total={}, inserted={}, updated={}, skipped={}",
                    closingDate, shopNo, totalRows, insertedRows, updatedRows, skippedRows);

            return InvoiceImportResult.builder()
                    .closingDate(closingDate)
                    .shopNo(shopNo)
                    .totalRows(totalRows)
                    .insertedRows(insertedRows)
                    .updatedRows(updatedRows)
                    .skippedRows(skippedRows)
                    .errors(List.of())
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
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    try {
                        double val = cell.getNumericCellValue();
                        if (val == Math.floor(val) && !Double.isInfinite(val)) {
                            yield String.valueOf((long) val);
                        }
                        yield String.valueOf(val);
                    } catch (Exception e2) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield BigDecimal.valueOf(val).setScale(0, RoundingMode.HALF_UP);
            }
            case STRING -> {
                try {
                    yield new BigDecimal(cell.getStringCellValue().trim()).setScale(0, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    yield BigDecimal.ZERO;
                }
            }
            case FORMULA -> {
                try {
                    double val = cell.getNumericCellValue();
                    yield BigDecimal.valueOf(val).setScale(0, RoundingMode.HALF_UP);
                } catch (Exception e) {
                    yield BigDecimal.ZERO;
                }
            }
            default -> BigDecimal.ZERO;
        };
    }
}
