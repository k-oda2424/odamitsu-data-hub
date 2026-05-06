package jp.co.oda32.domain.service.finance;

import jp.co.oda32.batch.finance.MFJournalCsv;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.exception.FinanceInternalException;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 売掛金から売上仕訳CSV（マネーフォワードクラウド会計向け）を生成する Service。
 * <p>
 * 元々 {@code AccountsReceivableToSalesJournalTasklet} に埋め込まれていた CSV 生成ロジックを
 * Controller (ブラウザ DL) からも再利用できるように抽出。
 *
 * @since 2026/04/17 (設計書 design-accounts-receivable-mf.md §5.3)
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SalesJournalCsvService {

    private static final String CLEAN_LAB_PARTNER_CODE = "301491";
    private static final long DEFAULT_INITIAL_TRANSACTION_NO = 1001L;

    /**
     * MF (マネーフォワード) 仕訳 CSV の文字コード。
     * <p>
     * Tasklet ({@link jp.co.oda32.batch.finance.AccountsReceivableToSalesJournalTasklet})
     * とブラウザ DL Controller ({@code AccountsReceivableController#exportMfCsv}) の双方で
     * 同一エンコーディングを使用するため、本 Service に集約する (SF-E04)。
     * <p>
     * Java の {@code FileWriter} はプラットフォーム既定 charset を使うため Linux/Windows で挙動差が出る。
     * 必ず本定数で {@code OutputStreamWriter} をラップして使用すること。
     */
    public static final Charset CP932 = Charset.forName("windows-31j");

    private final MfAccountMasterService mfAccountMasterService;

    /**
     * 指定された売掛金サマリーリストを MF 仕訳CSV形式で writer に書き出す。
     *
     * @param summaries            出力対象（呼び出し側で mf_export_enabled 等でフィルタ済みであること）
     * @param writer               出力先
     * @param initialTransactionNo 取引番号初期値（null なら 1001）
     * @return 書き込み行数
     * @throws IOException 書き込み失敗時
     */
    public int writeCsv(List<TAccountsReceivableSummary> summaries,
                        Writer writer,
                        Long initialTransactionNo) throws IOException {
        return writeCsv(summaries, writer, initialTransactionNo, new ArrayList<>());
    }

    /**
     * master 未登録行を skipped リストに積みながら CSV を書き出す (B-W13)。
     * 従来は master 未登録で IllegalStateException を投げて fail-fast だったが、買掛側の
     * {@link PurchaseJournalCsvService} と挙動が非対称だった。skipped を列挙して呼び出し側で
     * UI 警告表示する運用に統一する。
     */
    public int writeCsv(List<TAccountsReceivableSummary> summaries,
                        Writer writer,
                        Long initialTransactionNo,
                        List<String> skipped) throws IOException {
        writer.write(MFJournalCsv.CSV_HEADER + "\n");
        if (summaries == null || summaries.isEmpty()) {
            return 0;
        }

        Map<String, MfAccountMaster> accountMasterMap = createAccountMasterMap();
        Map<String, MfAccountMaster> garbageBagAccountMasterMap = createGarbageBagAccountMasterMap();

        List<TAccountsReceivableSummary> sorted = summaries.stream()
                .sorted(Comparator.comparing(TAccountsReceivableSummary::getTransactionMonth)
                        .thenComparing(TAccountsReceivableSummary::getShopNo)
                        .thenComparing(TAccountsReceivableSummary::getPartnerCode)
                        .thenComparing(TAccountsReceivableSummary::getTaxRate, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        long currentTransactionNo = initialTransactionNo != null ? initialTransactionNo : DEFAULT_INITIAL_TRANSACTION_NO;
        int written = 0;

        for (TAccountsReceivableSummary summary : sorted) {
            // CSV出力で使用する金額は tax_included_amount（確定済み）
            // フォールバックとして _change を使う（旧データ互換）
            BigDecimal taxIncluded = summary.getTaxIncludedAmount() != null
                    ? summary.getTaxIncludedAmount()
                    : summary.getTaxIncludedAmountChange();
            if (taxIncluded == null) {
                log.warn("税込金額が null のためスキップ: shopNo={}, partnerNo={}, month={}",
                        summary.getShopNo(), summary.getPartnerNo(), summary.getTransactionMonth());
                continue;
            }
            BigDecimal amount = truncate(taxIncluded);
            String searchKey = summary.getShopNo() + "_" + summary.getPartnerCode();

            MFJournalCsv record;
            if (summary.isOtakeGarbageBag()) {
                String garbageKey = "g_" + searchKey;
                MfAccountMaster master = garbageBagAccountMasterMap.get(garbageKey);
                if (master == null) {
                    String label = String.format("%s (shopNo=%d, partnerCode=%s, month=%s) ゴミ袋マスタ未登録",
                            garbageKey, summary.getShopNo(), summary.getPartnerCode(), summary.getTransactionMonth());
                    log.warn("CSV 出力からスキップ: {}", label);
                    skipped.add(label);
                    continue;
                }
                record = MFJournalCsv.builder()
                        .transactionNo(String.valueOf(currentTransactionNo++))
                        .transactionDate(summary.getTransactionMonth().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                        .debitAccount("未収入金")
                        .debitSubAccount(master.getSubAccountName())
                        .debitDepartment("")
                        .debitPartner("")
                        .debitTaxCategory("対象外")
                        .debitInvoice("")
                        .debitAmount(formatAmount(amount))
                        .creditAccount("仮払金")
                        .creditSubAccount("ゴミ袋／大竹市")
                        .creditDepartment("")
                        .creditPartner("")
                        .creditTaxCategory("対象外")
                        .creditInvoice("")
                        .creditAmount(formatAmount(amount))
                        .summary(master.getSearchKey() + ": " + master.getSubAccountName())
                        .tag("")
                        .memo("")
                        .build();
            } else {
                MfAccountMaster master = accountMasterMap.get(searchKey);
                if (master == null) {
                    String label = String.format("%s (shopNo=%d, partnerCode=%s, month=%s) 売掛金マスタ未登録",
                            searchKey, summary.getShopNo(), summary.getPartnerCode(), summary.getTransactionMonth());
                    log.warn("CSV 出力からスキップ: {}", label);
                    skipped.add(label);
                    continue;
                }
                String creditDepartment = "物販事業部";
                String creditSubAccount = "物販売上高";
                if (StringUtil.isEqual(summary.getPartnerCode(), CLEAN_LAB_PARTNER_CODE)) {
                    creditDepartment = "クリーンラボ";
                    creditSubAccount = "クリーンラボ売上高";
                }
                record = MFJournalCsv.builder()
                        .transactionNo(String.valueOf(currentTransactionNo++))
                        .transactionDate(summary.getTransactionMonth().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                        .debitAccount("売掛金")
                        .debitSubAccount(master.getSubAccountName())
                        .debitDepartment("")
                        .debitPartner("")
                        .debitTaxCategory("対象外")
                        .debitInvoice("")
                        .debitAmount(formatAmount(amount))
                        .creditAccount("売上高")
                        .creditSubAccount(creditSubAccount)
                        .creditDepartment(creditDepartment)
                        .creditPartner("")
                        .creditTaxCategory(getTaxType(summary.getTaxRate()))
                        .creditInvoice("")
                        .creditAmount(formatAmount(amount))
                        .summary(master.getSearchKey() + ": " + master.getSubAccountName())
                        .tag("")
                        .memo("")
                        .build();
            }
            writer.write(formatCsvRecord(record));
            written++;
        }
        return written;
    }

    private Map<String, MfAccountMaster> createAccountMasterMap() {
        List<MfAccountMaster> list = mfAccountMasterService.findByFinancialStatementItemAndAccountName("売掛金", "売掛金");
        return toUniqueMap(list, "売掛金");
    }

    private Map<String, MfAccountMaster> createGarbageBagAccountMasterMap() {
        List<MfAccountMaster> list = mfAccountMasterService.findByFinancialStatementItemAndAccountName("未収入金", "未収入金");
        return toUniqueMap(list, "未収入金");
    }

    private Map<String, MfAccountMaster> toUniqueMap(List<MfAccountMaster> list, String label) {
        Map<String, MfAccountMaster> map = new HashMap<>();
        for (MfAccountMaster master : list) {
            String key = master.getSearchKey();
            if (map.containsKey(key)) {
                // T5: 内部マスタ不整合 (機微情報含む可能性: master の toString に内部 ID 等)。
                throw new FinanceInternalException(String.format(
                        "%sのアカウントマスタで重複キー: %s (%s / %s)", label, key, map.get(key), master));
            }
            map.put(key, master);
        }
        return map;
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
        if (taxRate.compareTo(BigDecimal.valueOf(10)) == 0) return "課税売上 10%";
        if (taxRate.compareTo(BigDecimal.valueOf(8)) == 0) return "課売 (軽)8%";
        if (taxRate.compareTo(BigDecimal.ZERO) == 0) return "非売";
        return "";
    }

    private BigDecimal truncate(BigDecimal v) {
        return v.setScale(0, RoundingMode.DOWN);
    }

    private String formatAmount(BigDecimal v) {
        return truncate(v).toPlainString();
    }

    /**
     * CSV出力対象の売掛金サマリーに CSV出力済みマーカーを付ける。
     * {@code tax_included_amount} / {@code tax_excluded_amount} に {@code *_change} の値をコピーする。
     * （買掛側と同じ仕様: CSV出力済みの確定金額を保持）
     * <p>
     * SF-E02: 旧実装は {@code *_amount == null} のときだけコピーしていたため、再 DL 時に
     * 古い焼付け値がそのまま残る経年バグがあった。null guard は {@code *_change} 側だけに残し、
     * 既存の {@code *_amount} があっても最新の {@code *_change} で必ず上書きする。
     */
    public void markExported(List<TAccountsReceivableSummary> summaries) {
        for (TAccountsReceivableSummary s : summaries) {
            if (s.getTaxIncludedAmountChange() != null) {
                s.setTaxIncludedAmount(s.getTaxIncludedAmountChange());
            }
            if (s.getTaxExcludedAmountChange() != null) {
                s.setTaxExcludedAmount(s.getTaxExcludedAmountChange());
            }
        }
    }
}
