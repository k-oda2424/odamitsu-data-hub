package jp.co.oda32.domain.service.finance;

import jp.co.oda32.batch.finance.MFJournalCsv;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.util.BigDecimalUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 買掛金から仕入仕訳CSV（マネーフォワードクラウド会計向け）を生成する Service。
 * <p>
 * 元々 {@code AccountsPayableToPurchaseJournalTasklet} に埋め込まれていた CSV 生成ロジックを
 * Controller (ブラウザ DL) からも再利用できるように抽出。売掛側 {@link SalesJournalCsvService} と対称。
 *
 * @since 2026/04/20
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class PurchaseJournalCsvService {

    private static final String CLEAN_LAB_SUPPLIER_CODE = "030302";
    private static final long DEFAULT_INITIAL_TRANSACTION_NO = 1L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MfAccountMasterService mfAccountMasterService;

    /**
     * 指定された買掛金サマリーリストを MF 仕訳CSV形式で writer に書き出す。
     *
     * @param summaries            出力対象（呼び出し側で mf_export_enabled / amount change 等でフィルタ済みであること）
     * @param transactionDate      CSV 取引日列に使う日付（通常は transactionMonth）
     * @param writer               出力先
     * @param initialTransactionNo 取引番号初期値（null なら 1）
     * @return 書き込んだ CSV 明細数（ヘッダ除く）
     * @throws IOException 書き込み失敗時
     */
    public Result writeCsv(List<TAccountsPayableSummary> summaries,
                           LocalDate transactionDate,
                           Writer writer,
                           Long initialTransactionNo) throws IOException {
        writer.write(MFJournalCsv.CSV_HEADER + "\n");
        Result result = new Result();
        if (summaries == null || summaries.isEmpty()) {
            return result;
        }

        Map<String, MfAccountMaster> accountMasterMap = mfAccountMasterService
                .findByFinancialStatementItemAndAccountName("買掛金", "買掛金")
                .stream()
                .filter(m -> m.getSearchKey() != null)
                .collect(Collectors.toMap(MfAccountMaster::getSearchKey, m -> m, (a, b) -> a));

        // supplierNo / taxRate で集約
        Map<AggregationKey, SummedAmounts> aggregated = summaries.stream()
                .collect(Collectors.groupingBy(
                        s -> new AggregationKey(s.getShopNo(), s.getSupplierNo(), s.getSupplierCode(), s.getTaxRate()),
                        Collectors.reducing(
                                new SummedAmounts(BigDecimal.ZERO, BigDecimal.ZERO),
                                s -> new SummedAmounts(
                                        Objects.requireNonNullElse(s.getTaxIncludedAmountChange(), BigDecimal.ZERO),
                                        Objects.requireNonNullElse(s.getTaxExcludedAmountChange(), BigDecimal.ZERO)),
                                SummedAmounts::combine)));

        List<Map.Entry<AggregationKey, SummedAmounts>> sorted = new ArrayList<>(aggregated.entrySet());
        sorted.sort(Comparator
                .<Map.Entry<AggregationKey, SummedAmounts>, String>comparing(e -> Objects.requireNonNullElse(e.getKey().supplierCode(), ""))
                .thenComparing(e -> Objects.requireNonNullElse(e.getKey().taxRate(), BigDecimal.ZERO), Comparator.reverseOrder()));

        String formattedDate = transactionDate.format(DATE_FORMATTER);
        long currentTransactionNo = initialTransactionNo != null ? initialTransactionNo : DEFAULT_INITIAL_TRANSACTION_NO;

        for (Map.Entry<AggregationKey, SummedAmounts> entry : sorted) {
            AggregationKey key = entry.getKey();
            SummedAmounts amounts = entry.getValue();

            validateIntegerAmount(amounts.taxIncludedAmount());
            validateIntegerAmount(amounts.taxExcludedAmount());

            String searchKey = key.supplierCode();
            MfAccountMaster master = accountMasterMap.get(searchKey);
            if (master == null) {
                result.skippedSuppliers.add(key.supplierCode() + " (No." + key.supplierNo() + ")");
                log.warn("MF勘定科目マスタに未登録のためCSVから除外: supplierCode={}, supplierNo={}",
                        key.supplierCode(), key.supplierNo());
                continue;
            }

            String debitDepartment = CLEAN_LAB_SUPPLIER_CODE.equals(key.supplierCode()) ? "クリーンラボ" : "物販事業部";
            BigDecimal includedAmount = truncate(amounts.taxIncludedAmount());

            MFJournalCsv record = MFJournalCsv.builder()
                    .transactionNo(String.valueOf(currentTransactionNo++))
                    .transactionDate(formattedDate)
                    .debitAccount("仕入高")
                    .debitSubAccount("")
                    .debitDepartment(debitDepartment)
                    .debitPartner("")
                    .debitTaxCategory(getTaxType(key.taxRate()))
                    .debitInvoice("")
                    .debitAmount(includedAmount.toPlainString())
                    .creditAccount("買掛金")
                    .creditSubAccount(master.getSubAccountName())
                    .creditDepartment("")
                    .creditPartner("")
                    .creditTaxCategory("対象外")
                    .creditInvoice("")
                    .creditAmount(includedAmount.toPlainString())
                    .summary(master.getSearchKey() + ": " + master.getSubAccountName())
                    .tag("")
                    .memo("")
                    .build();

            writer.write(formatCsvRecord(record));
            result.rowCount++;
            result.totalAmount = result.totalAmount.add(includedAmount);
        }
        return result;
    }

    /**
     * CSV 出力対象の買掛金サマリーに CSV 出力済みマーカーを付ける。
     * {@code tax_included_amount} / {@code tax_excluded_amount} に {@code *_change} の値をコピーする。
     */
    public void markExported(List<TAccountsPayableSummary> summaries) {
        for (TAccountsPayableSummary s : summaries) {
            BigDecimal inc = Objects.requireNonNullElse(s.getTaxIncludedAmountChange(), BigDecimal.ZERO);
            BigDecimal exc = Objects.requireNonNullElse(s.getTaxExcludedAmountChange(), BigDecimal.ZERO);
            s.setTaxIncludedAmount(inc);
            s.setTaxExcludedAmount(exc);
        }
    }

    /**
     * 取引月と forceExport フラグを元に、CSV 出力対象のサマリをフィルタする。
     * 呼び出し側（Tasklet / Controller）で共通化するために Service に置く。
     */
    public FilterResult filter(List<TAccountsPayableSummary> summaries, boolean forceExport) {
        FilterResult r = new FilterResult();
        for (TAccountsPayableSummary s : summaries) {
            BigDecimal change = s.getTaxIncludedAmountChange();
            if (change == null || BigDecimalUtil.isZero(change)) continue;
            boolean enabled = Boolean.TRUE.equals(s.getMfExportEnabled());
            if (!enabled) {
                r.nonExportableCount++;
                if (!forceExport) continue;
            }
            r.exportable.add(s);
        }
        return r;
    }

    private String formatCsvRecord(MFJournalCsv r) {
        return String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                r.getTransactionNo(), r.getTransactionDate(),
                r.getDebitAccount(), r.getDebitSubAccount(), r.getDebitDepartment(), r.getDebitPartner(),
                r.getDebitTaxCategory(), r.getDebitInvoice(), r.getDebitAmount(),
                r.getCreditAccount(), r.getCreditSubAccount(), r.getCreditDepartment(), r.getCreditPartner(),
                r.getCreditTaxCategory(), r.getCreditInvoice(), r.getCreditAmount(),
                r.getSummary(), r.getTag(), r.getMemo());
    }

    private String getTaxType(BigDecimal taxRate) {
        if (taxRate == null) return "";
        if (taxRate.compareTo(BigDecimal.valueOf(10)) == 0) return "課税仕入 10%";
        if (taxRate.compareTo(BigDecimal.valueOf(8)) == 0) return "課仕 (軽)8%";
        if (taxRate.compareTo(BigDecimal.ZERO) == 0) return "非課税";
        return "";
    }

    private BigDecimal truncate(BigDecimal v) {
        return v.setScale(0, RoundingMode.DOWN);
    }

    private void validateIntegerAmount(BigDecimal amount) {
        if (amount != null && amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalStateException("金額に小数点以下が含まれています: " + amount);
        }
    }

    private record AggregationKey(Integer shopNo, Integer supplierNo, String supplierCode, BigDecimal taxRate) {}

    private record SummedAmounts(BigDecimal taxIncludedAmount, BigDecimal taxExcludedAmount) {
        static SummedAmounts combine(SummedAmounts a, SummedAmounts b) {
            return new SummedAmounts(
                    a.taxIncludedAmount.add(b.taxIncludedAmount),
                    a.taxExcludedAmount.add(b.taxExcludedAmount));
        }
    }

    /** CSV 書き込み結果。 */
    public static class Result {
        public int rowCount = 0;
        public BigDecimal totalAmount = BigDecimal.ZERO;
        public final List<String> skippedSuppliers = new ArrayList<>();
    }

    /** {@link #filter(List, boolean)} の結果。 */
    public static class FilterResult {
        public final List<TAccountsPayableSummary> exportable = new ArrayList<>();
        public long nonExportableCount = 0;
    }
}
