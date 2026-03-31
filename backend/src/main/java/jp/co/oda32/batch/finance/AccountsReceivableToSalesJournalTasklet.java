package jp.co.oda32.batch.finance;

import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.service.finance.MfAccountMasterService;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 売掛金を売上に変換する仕訳帳をCSVファイルに出力するTasklet
 *
 * @author k_oda
 * @modified 2025/04/30 - targetDateの形式をyyyyMMに変更し、指定年月の締め月データを対象とするよう修正
 * @modified 2025/12/30 - fromDate/toDateの期間指定に変更し、小田光の20日締めに対応
 * @since 2024/08/31
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsReceivableToSalesJournalTasklet implements Tasklet {

    private final TAccountsReceivableSummaryService tAccountsReceivableSummaryService;
    private final MfAccountMasterService mfAccountMasterService;

    // 起動引数で設定する取引番号の初期値
    @Value("#{jobParameters['initialTransactionNo']}")
    private Long initialTransactionNo;

    @Value("#{jobParameters['fromDate']}")
    private String fromDate;

    @Value("#{jobParameters['toDate']}")
    private String toDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("売掛金を売上に変換する仕訳帳をCSVファイルに出力するTaskletを開始します。fromDate={}, toDate={}", fromDate, toDate);

        // 指定された日付を解析（yyyyMMdd形式）
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate fromLocalDate = LocalDate.parse(fromDate, dateFormatter);
        LocalDate toLocalDate = LocalDate.parse(toDate, dateFormatter);
        log.info("処理対象期間: {} ～ {}", fromLocalDate, toLocalDate);

        // 日付範囲のバリデーション
        if (fromLocalDate.isAfter(toLocalDate)) {
            throw new IllegalArgumentException("fromDateはtoDateより前の日付を指定してください。fromDate=" + fromDate + ", toDate=" + toDate);
        }

        // 指定期間内のデータを取得
        List<TAccountsReceivableSummary> summaries = tAccountsReceivableSummaryService.findByDateRange(fromLocalDate, toLocalDate);
        log.info("対象データ件数: {}", summaries.size());

        // データが0件の場合は処理をスキップ
        if (summaries.isEmpty()) {
            log.info("対象データが存在しないため、ファイル出力をスキップします。");
            return RepeatStatus.FINISHED;
        }

        // アカウントマスターマップの作成
        Map<String, MfAccountMaster> accountMasterMap = createAccountMasterMap();
        Map<String, MfAccountMaster> garbageBagAccountMasterMap = createGarbageBagAccountMasterMap();

        // 日付範囲に応じたファイル名を生成する
        // 同一日の場合: accounts_receivable_to_sales_journal_20251220.csv
        // 異なる日の場合: accounts_receivable_to_sales_journal_20251121_20251220.csv
        String fileName = generateFileName(fromLocalDate, toLocalDate);

        // 生成したファイル名をログに出力
        log.info("生成されたファイル名: {}", fileName);

        boolean success = false;
        try {
            // CSVファイルに出力
            writeToFile(summaries, accountMasterMap, garbageBagAccountMasterMap, fileName);
            success = true;
            log.info("売掛金を売上に変換する仕訳帳をCSVファイルに出力するTaskletが完了しました。出力ファイル: {}", fileName);
        } catch (Exception e) {
            log.error("出力処理中にエラーが発生しました", e);
            // 不完全なファイルが作成されているため、完全成功しなかった場合はファイルを削除
            if (!success) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(fileName));
                    log.info("処理失敗のため不完全なファイルを削除しました: {}", fileName);
                } catch (Exception ex) {
                    log.error("不完全なファイルの削除に失敗しました: {}", fileName, ex);
                }
            }
            // 元の例外を再スロー
            throw e;
        }

        return RepeatStatus.FINISHED;
    }

    /**
     * 売掛金用のアカウントマスターマップを作成します。
     * 重複キーがある場合はエラーとして処理を終了します。
     *
     * @return 売掛金用のアカウントマスターマップ
     */
    private Map<String, MfAccountMaster> createAccountMasterMap() {
        // 重複キーをチェックするための一時マップ
        Map<String, MfAccountMaster> duplicateKeyMap = new HashMap<>();
        List<MfAccountMaster> accountMasters = mfAccountMasterService.findByFinancialStatementItemAndAccountName("売掛金", "売掛金");

        // 重複キーのチェック
        for (MfAccountMaster master : accountMasters) {
            String key = master.getSearchKey();
            if (duplicateKeyMap.containsKey(key)) {
                String errorMessage = String.format("売掛金用のアカウントマスターに重複した検索キーが見つかりました: %s\n" +
                                "重複アカウント1: %s\n" +
                                "重複アカウント2: %s",
                        key, duplicateKeyMap.get(key), master);
                log.error(errorMessage);
                throw new IllegalStateException(errorMessage);
            }
            duplicateKeyMap.put(key, master);
        }

        // 重複がなければMapに変換して返す
        Map<String, MfAccountMaster> accountMasterMap = accountMasters.stream()
                .collect(Collectors.toMap(MfAccountMaster::getSearchKey, accountMaster -> accountMaster));

        log.info("売掛金用アカウントマスターマップを作成しました。件数: {}", accountMasterMap.size());
        if (log.isDebugEnabled()) {
            // デバッグ用にキー一覧を出力
            log.debug("accountMasterMapのキー一覧: {}", String.join(", ", accountMasterMap.keySet()));
        }

        return accountMasterMap;
    }

    /**
     * ゴミ袋用のアカウントマスターマップを作成します。
     * 重複キーがある場合はエラーとして処理を終了します。
     *
     * @return ゴミ袋用のアカウントマスターマップ
     */
    private Map<String, MfAccountMaster> createGarbageBagAccountMasterMap() {
        // 重複キーをチェックするための一時マップ
        Map<String, MfAccountMaster> duplicateKeyMap = new HashMap<>();
        List<MfAccountMaster> accountMasters = mfAccountMasterService.findByFinancialStatementItemAndAccountName("未収入金", "未収入金");

        // 重複キーのチェック
        for (MfAccountMaster master : accountMasters) {
            String key = master.getSearchKey();
            if (duplicateKeyMap.containsKey(key)) {
                String errorMessage = String.format("ゴミ袋用のアカウントマスターに重複した検索キーが見つかりました: %s\n" +
                                "重複アカウント1: %s\n" +
                                "重複アカウント2: %s",
                        key, duplicateKeyMap.get(key), master);
                log.error(errorMessage);
                throw new IllegalStateException(errorMessage);
            }
            duplicateKeyMap.put(key, master);
        }

        // 重複がなければMapに変換して返す
        Map<String, MfAccountMaster> garbageBagAccountMasterMap = accountMasters.stream()
                .collect(Collectors.toMap(MfAccountMaster::getSearchKey, accountMaster -> accountMaster));

        log.info("ゴミ袋用アカウントマスターマップを作成しました。件数: {}", garbageBagAccountMasterMap.size());
        if (log.isDebugEnabled()) {
            // デバッグ用にキー一覧を出力
            log.debug("garbageBagAccountMasterMapのキー一覧: {}", String.join(", ", garbageBagAccountMasterMap.keySet()));
        }

        return garbageBagAccountMasterMap;
    }

    /**
     * ファイル名を生成します。
     * 日付範囲に応じたファイル名を生成します。
     * 同一日の場合: accounts_receivable_to_sales_journal_20251220.csv
     * 異なる日の場合: accounts_receivable_to_sales_journal_20251121_20251220.csv
     *
     * @param fromDate 開始日
     * @param toDate 終了日
     * @return 生成されたファイル名
     */
    private String generateFileName(LocalDate fromDate, LocalDate toDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        if (fromDate.equals(toDate)) {
            // 同一日の場合は1つの日付のみ
            return "accounts_receivable_to_sales_journal_" + fromDate.format(formatter) + ".csv";
        } else {
            // 異なる日の場合は開始日_終了日
            return "accounts_receivable_to_sales_journal_" + fromDate.format(formatter) + "_" + toDate.format(formatter) + ".csv";
        }
    }

    /**
     * データをCSVファイルに書き込みます。
     *
     * @param summaries                  売掛金のサマリーデータ
     * @param accountMasterMap           検索キーごとのMfAccountMasterのマップ
     * @param garbageBagAccountMasterMap ゴミ袋用のMfAccountMasterのマップ
     * @param fileName                   出力するファイル名
     */
    private void writeToFile(List<TAccountsReceivableSummary> summaries, Map<String, MfAccountMaster> accountMasterMap, Map<String, MfAccountMaster> garbageBagAccountMasterMap, String fileName) throws Exception {
        // 空リストのチェック
        if (summaries == null || summaries.isEmpty()) {
            log.info("出力対象データが空のため、CSV出力をスキップします。");
            // 空のファイルだけは作成する
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(MFJournalCsv.CSV_HEADER + "\n");
            } catch (IOException e) {
                log.error("空のCSVファイル作成に失敗しました: {}", fileName, e);
                throw new IOException("空のCSVファイル作成に失敗しました: " + fileName, e);
            }
            return;
        }
        try (FileWriter writer = new FileWriter(fileName)) {
            // ヘッダーを書き込む
            writer.write(MFJournalCsv.CSV_HEADER + "\n");

            summaries = summaries.stream()
                    .sorted(Comparator.comparing(TAccountsReceivableSummary::getTransactionMonth) // 取引日で昇順ソート
                            .thenComparing(TAccountsReceivableSummary::getShopNo)
                            .thenComparing(TAccountsReceivableSummary::getPartnerCode) // 検索キー（店舗番号+得意先コード）で昇順ソート
                            .thenComparing(TAccountsReceivableSummary::getTaxRate, Comparator.reverseOrder())) // 消費税率を高い順にソート（元の動作を維持）
                    .collect(Collectors.toList());

            long currentTransactionNo = (initialTransactionNo != null) ? initialTransactionNo : 1001L; // 起動引数の初期取引番号を使用、nullの場合は1001

            for (TAccountsReceivableSummary summary : summaries) {
                // 小数点以下をチェックし、エラーを発生させる代わりに切り捨てる
                BigDecimal taxIncludedAmount = validateAmount(summary.getTaxIncludedAmountChange(), summary);
                BigDecimal taxExcludedAmount = validateAmount(summary.getTaxExcludedAmountChange(), summary);

                // 切り捨てた値をセットし直す
                summary.setTaxIncludedAmountChange(taxIncludedAmount);
                summary.setTaxExcludedAmountChange(taxExcludedAmount);

                String searchKey = summary.getShopNo() + "_" + summary.getPartnerCode();
                // Mapに存在する検索キーのリストを取得しておく（デバッグ用）
                if (log.isDebugEnabled()) {
                    log.debug("検索キー: {}", searchKey);
                    log.debug("accountMasterMapのキー一覧: {}", accountMasterMap.keySet());
                    log.debug("garbageBagAccountMasterMapのキー一覧: {}", garbageBagAccountMasterMap.keySet());
                }

                if (summary.isOtakeGarbageBag()) {
                    String garbageKey = "g_" + searchKey;
                    MfAccountMaster accountMaster = garbageBagAccountMasterMap.get(garbageKey);

                    // accountMasterがnullの場合でも処理を継続できるようにする
                    String subAccountName = "";
                    String summaryText = "";

                    if (accountMaster != null) {
                        subAccountName = accountMaster.getSubAccountName();
                        summaryText = accountMaster.getSearchKey() + ": " + subAccountName;
                    } else {
                        String errorMessage = String.format("ゴミ袋用のアカウントマスターが見つかりません。検索キー: %s, 得意先情報: shopNo=%d, partnerNo=%d, partnerCode=%s",
                                garbageKey, summary.getShopNo(), summary.getPartnerNo(), summary.getPartnerCode());
                        log.error(errorMessage);
                        throw new RuntimeException(errorMessage);
                    }

                    MFJournalCsv csvRecord = MFJournalCsv.builder()
                            .transactionNo(String.valueOf(currentTransactionNo++)) // 連番の取引番号を設定
                            .transactionDate(summary.getTransactionMonth().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                            .debitAccount("未収入金")
                            .debitSubAccount(subAccountName)
                            .debitDepartment("")
                            .debitPartner("")
                            .debitTaxCategory("対象外")
                            .debitInvoice("")
                            .debitAmount(formatAmount(summary.getTaxIncludedAmountChange())) // 小数点を省く処理
                            .creditAccount("仮払金")
                            .creditSubAccount("ゴミ袋／大竹市")
                            .creditDepartment("")
                            .creditPartner("")
                            .creditTaxCategory("対象外")
                            .creditInvoice("")
                            .creditAmount(formatAmount(summary.getTaxIncludedAmountChange())) // 小数点を省く処理
                            .summary(summaryText)
                            .tag("")
                            .memo("")
                            .build();

                    writer.write(formatCsvRecord(csvRecord));
                } else {
                    MfAccountMaster accountMaster = accountMasterMap.get(searchKey);

                    // accountMasterがnullの場合はエラーとして処理を停止
                    if (accountMaster == null) {
                        String errorMessage = String.format("売掛金用のアカウントマスターが見つかりません。検索キー: %s, 得意先情報: shopNo=%d, partnerNo=%d, partnerCode=%s",
                                searchKey, summary.getShopNo(), summary.getPartnerNo(), summary.getPartnerCode());
                        log.error(errorMessage);
                        throw new RuntimeException(errorMessage);
                    }
                    String creditDepartment = "物販事業部";
                    String creditSubAccount = "物販売上高";
                    // クリーンラボの得意先コード　301491
                    if (StringUtil.isEqual(summary.getPartnerCode(), "301491")) {
                        creditDepartment = "クリーンラボ";
                        creditSubAccount = "クリーンラボ売上高";
                    }

                    MFJournalCsv csvRecord = MFJournalCsv.builder()
                            .transactionNo(String.valueOf(currentTransactionNo++)) // 連番の取引番号を設定
                            .transactionDate(summary.getTransactionMonth().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                            .debitAccount("売掛金")
                            .debitSubAccount(accountMaster.getSubAccountName())
                            .debitDepartment("")
                            .debitPartner("")
                            .debitTaxCategory("対象外")
                            .debitInvoice("")
                            .debitAmount(formatAmount(summary.getTaxIncludedAmountChange())) // 小数点を省く処理
                            .creditAccount("売上高")
                            .creditSubAccount(creditSubAccount)
                            .creditDepartment(creditDepartment)
                            .creditPartner("")
                            .creditTaxCategory(getTaxType(summary.getTaxRate()))
                            .creditInvoice("")
                            .creditAmount(formatAmount(summary.getTaxIncludedAmountChange())) // 小数点を省く処理
                            .summary(accountMaster.getSearchKey() + ": " + accountMaster.getSubAccountName())
                            .tag("")
                            .memo("")
                            .build();

                    writer.write(formatCsvRecord(csvRecord));
                }
            }
            // 最後にデータを更新
            log.info("データ更新を開始します: {} 件", summaries.size());

            // tmpカラムから連携金額カラムへコピーして更新
            for (TAccountsReceivableSummary summary : summaries) {
                summary.setTaxIncludedAmount(summary.getTaxIncludedAmountChange());
                summary.setTaxExcludedAmount(summary.getTaxExcludedAmountChange());
                this.tAccountsReceivableSummaryService.save(summary);
            }

            log.info("データ更新が完了しました: {} 件", summaries.size());
        } catch (IOException e) {
            log.error("Failed to write CSV file", e);
            throw new IOException("CSVファイルの書き込みに失敗しました。");
        }
    }

    /**
     * CSVレコードをフォーマットします。
     *
     * @param record 書き込むCSVレコード
     * @return フォーマットされたCSVレコードの文字列
     */
    private String formatCsvRecord(MFJournalCsv record) {
        return String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                record.getTransactionNo(),
                record.getTransactionDate(),
                record.getDebitAccount(),
                record.getDebitSubAccount(),
                record.getDebitDepartment(),
                record.getDebitPartner(),
                record.getDebitTaxCategory(),
                record.getDebitInvoice(),
                record.getDebitAmount(),
                record.getCreditAccount(),
                record.getCreditSubAccount(),
                record.getCreditDepartment(),
                record.getCreditPartner(),
                record.getCreditTaxCategory(),
                record.getCreditInvoice(),
                record.getCreditAmount(),
                record.getSummary(),
                record.getTag(),
                record.getMemo()
        );
    }

    /**
     * 消費税率に応じた税区分を取得します。
     *
     * @param taxRate 消費税率
     * @return 税区分
     */
    private String getTaxType(BigDecimal taxRate) {
        if (taxRate.compareTo(BigDecimal.valueOf(10)) == 0) {
            return "課税売上 10%";
        } else if (taxRate.compareTo(BigDecimal.valueOf(8)) == 0) {
            return "課売 (軽)8%";
        } else if (taxRate.compareTo(BigDecimal.ZERO) == 0) {
            return "非売";
        } else {
            return "";
        }
    }

    /**
     * 金額の小数点以下を切り捨て整数に変換します。
     * 小数点以下がある場合は警告ログを出力します。
     *
     * @param amount  チェックして変換する金額
     * @param summary ログメッセージに含める得意先情報
     * @return 小数点以下を切り捨てた整数金額
     */
    private BigDecimal validateAmount(BigDecimal amount, TAccountsReceivableSummary summary) {
        if (amount.stripTrailingZeros().scale() > 0) {
            // 小数点以下を切り捨てる
            BigDecimal truncatedAmount = amount.setScale(0, RoundingMode.DOWN);

            // 警告ログ出力
            String warningMessage = String.format(
                    "金額に小数点以下があるため切り捨てます: %s->%s\n" +
                            "店舗番号: %d, 得意先番号: %d, 得意先コード: %s, 注文番号: %s, 税率: %s, 取引日: %s",
                    amount, truncatedAmount,
                    summary.getShopNo(),
                    summary.getPartnerNo(),
                    summary.getPartnerCode(),
                    summary.getOrderNo() != null ? summary.getOrderNo().toString() : "null",
                    summary.getTaxRate(),
                    summary.getTransactionMonth()
            );
            log.warn(warningMessage);

            return truncatedAmount;
        }
        return amount;
    }

    /**
     * 金額をフォーマットし、小数点以下があれば切り捨てます。
     *
     * @param amount フォーマットする金額
     * @return フォーマットされた金額文字列
     */
    private String formatAmount(BigDecimal amount) {
        // 小数点以下を切り捨てて整数化
        BigDecimal truncatedAmount = amount.setScale(0, RoundingMode.DOWN);
        // 単純な整数文字列として返す
        return truncatedAmount.toPlainString();
    }
}
