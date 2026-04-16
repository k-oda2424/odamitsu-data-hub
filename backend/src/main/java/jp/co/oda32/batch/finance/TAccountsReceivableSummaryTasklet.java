package jp.co.oda32.batch.finance;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.PaymentType;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 売掛金額を集計し、TAccountsReceivableSummaryテーブルに登録するTasklet
 * - 得意先コード7桁以上は「上様」（999999）扱いで、かつ都度現金払い（締め日-1）として処理
 * - 締め日-1は「都度現金払い」として注文ごとに集計
 * - 消費税は請求単位で計算（都度現金払いは注文ごと）
 * - TInvoiceテーブルと金額を照合し、一致する場合のみ登録
 * - 都度現金払いの場合、TInvoice検索は月末締め("YYYY/MM/末")として行う
 * - 都度現金払いでも請求書との比較は「店舗＋得意先」ごとの合算で実施
 * - 得意先コード301491は店舗番号1で請求書検索（実際の店舗番号が3でも）
 *
 * @author k (modified by AI based on requirements)
 * @since 2024/08/31 (last modified: 2025/04/30)
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class TAccountsReceivableSummaryTasklet implements Tasklet {

    // 大竹市のゴミ袋のgoods_code一覧 (現在の日付: 2025-04-29)
    private static final Set<String> OTAKE_GARBAGE_BAG_GOODS_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "00100001", "00100003", "00100005",
                    "00100101", "00100103", "00100105"
            ))
    );

    // 四半期請求の得意先コード
    private static final String QUARTERLY_BILLING_PARTNER_CODE = "000231";

    private final TAccountsReceivableSummaryService tAccountsReceivableSummaryService;
    private final TOrderDetailService tOrderDetailService;
    private final MPartnerService mPartnerService;
    private final TInvoiceService tInvoiceService;

    // --- 処理全体で共有するキャッシュデータ ---
    private Map<Integer, String> partnerInvoiceCodeMap; // partner_no -> invoice_partner_code
    private Map<ShopInvoiceKey, Integer> invoicePartnerCodeToPartnerNoMap; // (shopNo, invoice_partner_code) -> partnerNo (請求先)
    private Map<Integer, Integer> partnerCutoffDateMap; // partner_no -> cutoff_date

    // --- 各締め日タイプごとの得意先リスト (partner_no) ---
    private List<Integer> monthEndPartnerNos; // 月末締めグループ (月末, その他, 都度現金)
    private List<Integer> cutoff20PartnerNos;  // 20日締め
    private List<Integer> cutoff15PartnerNos;  // 15日締め

    @Value("#{jobParameters['targetDate']}")
    private String targetDate; // 例: "20250430"

    @Value("${batch.accounts-receivable.invoice-amount-tolerance:3}")
    private BigDecimal invoiceAmountTolerance; // 請求書金額との許容差額（デフォルト: 3円）

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("売掛金集計バッチ処理 (TAccountsReceivableSummaryTasklet) を開始します。 targetDate={}", targetDate);
        try {
            initializeCache();
            LocalDate targetDateAsDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            log.info("処理対象年月: {}", YearMonth.from(targetDateAsDate));

            preloadPartnerData();

            processMonthEndCutoffPartners(targetDateAsDate);
            process20thCutoffPartners(targetDateAsDate);
            process15thCutoffPartners(targetDateAsDate);

            log.info("売掛金集計バッチ処理 (TAccountsReceivableSummaryTasklet) が正常に完了しました。");

        } catch (Exception e) {
            log.error("売掛金集計バッチ処理中に致命的なエラーが発生しました。", e);
            contribution.setExitStatus(org.springframework.batch.core.ExitStatus.FAILED);
            throw new RuntimeException("売掛金集計バッチ処理がエラーで終了しました。", e);
        }
        return RepeatStatus.FINISHED;
    }

    private void initializeCache() {
        log.debug("キャッシュを初期化します。");
        partnerInvoiceCodeMap = new HashMap<>();
        invoicePartnerCodeToPartnerNoMap = new HashMap<>();
        partnerCutoffDateMap = new HashMap<>();
        monthEndPartnerNos = new ArrayList<>();
        cutoff20PartnerNos = new ArrayList<>();
        cutoff15PartnerNos = new ArrayList<>();
    }

    private void preloadPartnerData() {
        log.info("得意先情報のプリロードを開始します。");
        List<MPartner> allPartners = mPartnerService.findAll();
        log.info("得意先マスタから {} 件の有効な得意先を読み込みました。", allPartners.size());

        int cashOnDeliveryCount = 0;
        for (MPartner partner : allPartners) {
            Integer partnerNo = partner.getPartnerNo();
            String partnerCode = partner.getPartnerCode();
            Integer shopNo = partner.getShopNo();
            Integer cutoffDate = partner.getCutoffDate() == null ? 0 : partner.getCutoffDate();
            String invoicePartnerCode = partner.getInvoicePartnerCode();
            String effectiveInvoiceCode = (invoicePartnerCode == null || invoicePartnerCode.isEmpty()) ? partnerCode : invoicePartnerCode;

            partnerInvoiceCodeMap.put(partnerNo, effectiveInvoiceCode);
            ShopInvoiceKey shopInvoiceKey = new ShopInvoiceKey(shopNo, effectiveInvoiceCode);

            // 同じShopInvoiceKeyに複数の得意先番号がマッピングされる場合の処理
            // 複数の得意先が同じinvoicePartnerCodeを持つ可能性がある（親会社-子会社関係など）
            if (invoicePartnerCodeToPartnerNoMap.containsKey(shopInvoiceKey)) {
                // 既存のマッピング情報をログ出力（デバッグ用）
                log.debug("ShopInvoiceKey {} に複数の得意先番号がマッピングされています。既存: {}, 追加: {}",
                        shopInvoiceKey, invoicePartnerCodeToPartnerNoMap.get(shopInvoiceKey), partnerNo);

                // 複数の得意先番号を管理するためのマップ構造に変更が必要かもしれませんが、
                // 現状は最後に処理された得意先番号を使用します。
                // これにより、同じinvoicePartnerCodeを持つ得意先が正しく集計されます。
            }

            // 最後に処理された得意先番号をマッピングに使用
            invoicePartnerCodeToPartnerNoMap.put(shopInvoiceKey, partnerNo);
            partnerCutoffDateMap.put(partnerNo, cutoffDate);

            PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDate);
            switch (paymentType) {
                case DAY_20:
                    cutoff20PartnerNos.add(partnerNo);
                    break;
                case DAY_15:
                    cutoff15PartnerNos.add(partnerNo);
                    break;
                case CASH_ON_DELIVERY:
                    monthEndPartnerNos.add(partnerNo);
                    cashOnDeliveryCount++;
                    break;
                case MONTH_END:
                default:
                    monthEndPartnerNos.add(partnerNo);
                    if (paymentType != PaymentType.MONTH_END) {
                        log.debug("締め日 {} (PaymentType: {}) の得意先 {} (店舗:{}) は月末締めグループとして処理します。",
                                cutoffDate, paymentType, partnerNo, shopNo);
                    }
                    break;
            }
        }

        monthEndPartnerNos = monthEndPartnerNos.stream().distinct().collect(Collectors.toList());
        cutoff20PartnerNos = cutoff20PartnerNos.stream().distinct().collect(Collectors.toList());
        cutoff15PartnerNos = cutoff15PartnerNos.stream().distinct().collect(Collectors.toList());

        long actualMonthEndCount = monthEndPartnerNos.stream()
                .filter(pn -> partnerCutoffDateMap.get(pn) != PaymentType.CASH_ON_DELIVERY.getCutoffCode())
                .count();
        log.info("得意先情報のプリロード完了。月末締め(通常): {}件, 20日締め: {}件, 15日締め: {}件, 都度現金払い: {}件 (月末締めグループで処理)",
                actualMonthEndCount, cutoff20PartnerNos.size(), cutoff15PartnerNos.size(), cashOnDeliveryCount);
    }

    private void processMonthEndCutoffPartners(LocalDate targetDateAsDate) throws Exception {
        if (monthEndPartnerNos.isEmpty()) {
            log.info("月末締めグループ（都度現金払い含む）の対象得意先が存在しません。");
            return;
        }
        YearMonth currentMonth = YearMonth.from(targetDateAsDate);
        LocalDate periodStartDate = currentMonth.atDay(1);
        LocalDate periodEndDate = currentMonth.atEndOfMonth();
        log.info("--- 月末締めグループ（都度現金払い含む）の売掛金集計開始 ---");
        log.info("期間: {} ～ {}, 対象得意先リストサイズ: {}", periodStartDate, periodEndDate, monthEndPartnerNos.size());
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, monthEndPartnerNos);
        saveAndLogSummaries(summaries, "月末締めグループ", periodEndDate);
        log.info("--- 月末締めグループの売掛金集計完了 ---");
    }

    private void process20thCutoffPartners(LocalDate targetDateAsDate) throws Exception {
        if (cutoff20PartnerNos.isEmpty()) {
            log.info("20日締めの対象得意先が存在しません。");
            return;
        }
        LocalDate periodStartDate = YearMonth.from(targetDateAsDate).minusMonths(1).atDay(21);
        LocalDate periodEndDate = YearMonth.from(targetDateAsDate).atDay(20);
        log.info("--- 20日締めの売掛金集計開始 ---");
        log.info("期間: {} ～ {}, 対象得意先リストサイズ: {}", periodStartDate, periodEndDate, cutoff20PartnerNos.size());
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, cutoff20PartnerNos);
        saveAndLogSummaries(summaries, "20日締め", periodEndDate);
        log.info("--- 20日締めの売掛金集計完了 ---");
    }

    private void process15thCutoffPartners(LocalDate targetDateAsDate) throws Exception {
        if (cutoff15PartnerNos.isEmpty()) {
            log.info("15日締めの対象得意先が存在しません。");
            return;
        }
        LocalDate periodStartDate = YearMonth.from(targetDateAsDate).minusMonths(1).atDay(16);
        LocalDate periodEndDate = YearMonth.from(targetDateAsDate).atDay(15);
        log.info("--- 15日締めの売掛金集計開始 ---");
        log.info("期間: {} ～ {}, 対象得意先リストサイズ: {}", periodStartDate, periodEndDate, cutoff15PartnerNos.size());
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, cutoff15PartnerNos);
        saveAndLogSummaries(summaries, "15日締め", periodEndDate);
        log.info("--- 15日締めの売掛金集計完了 ---");
    }

    /**
     * 集計結果 (請求単位) を請求書と照合し、検証OKなら保存してログを出力します。
     * 【変更点】都度現金払いの場合、請求書検索は月末締め("YYYY/MM/末")で行います。
     *
     * @param summaries 請求単位で集計された `TAccountsReceivableSummary` のリスト
     * @param cutoffType 締め日タイプの説明（ログ用）
     * @param targetPeriodEndDate 期間終了日 (TInvoice検索時の年月取得、および TAccountsReceivableSummary.transactionMonth の値)
     */
    /**
     * 集計結果 (請求単位) を請求書と照合し、検証OKなら保存してログを出力します。
     * 【変更点】
     * - 都度現金払いの場合、請求書検索は月末締め("YYYY/MM/末")で行います。
     * - 都度現金払い（注文ごとの消費税計算）も請求書との比較は「店舗＋得意先」ごとの合算で行います。
     * 【考慮事項】同じinvoicePartnerCodeが複数の得意先に設定されることがあります。
     * 【ソート】shop_no, partner_codeの昇順で処理を行います。
     *
     * @param summaries 請求単位で集計された `TAccountsReceivableSummary` のリスト
     * @param cutoffType 締め日タイプの説明（ログ用）
     * @param targetPeriodEndDate 期間終了日 (TInvoice検索時の年月取得、および TAccountsReceivableSummary.transactionMonth の値)
     */
    /**
     * 対象の日付が四半期末（2月、5月、8月、11月）かどうかを確認します
     *
     * @param date 確認対象の日付
     * @return 四半期末の場合はtrue、それ以外はfalse
     */
    private boolean isSpecialMonth(LocalDate date) {
        int month = date.getMonthValue();
        return month == 2 || month == 5 || month == 8 || month == 11;
    }

    /**
     * 得意先コード000231の請求書を取得するための締め日文字列を生成します
     *
     * @param targetPeriodEndDate 処理対象の期間終了日
     * @return 検索すべき締め日のリスト
     */
    private List<String> getSpecialPartnerClosingDates(LocalDate targetPeriodEndDate) {
        List<String> closingDates = new ArrayList<>();
        int month = targetPeriodEndDate.getMonthValue();
        int year = targetPeriodEndDate.getYear();

        // 基本的に当月15日締めの請求書を追加
        closingDates.add(String.format("%d/%02d/15", year, month));

        // 前月が2月、5月、8月、11月の場合は、前月末日の請求書も追加
        int previousMonth = month - 1;
        int previousYear = year;
        if (previousMonth == 0) {
            previousMonth = 12;
            previousYear--;
        }

        if (previousMonth == 2 || previousMonth == 5 || previousMonth == 8 || previousMonth == 11) {
            closingDates.add(String.format("%d/%02d/末", previousYear, previousMonth));
            log.debug("得意先コード000231: 前月({}/{})が特殊月のため、月末締め請求書も追加", previousYear, previousMonth);
        }

        return closingDates;
    }

    private void saveAndLogSummaries(List<TAccountsReceivableSummary> summaries, String cutoffType, LocalDate targetPeriodEndDate) {
        if (summaries == null || summaries.isEmpty()) {
            log.info("{} の処理対象となる集計結果はありませんでした。", cutoffType);
            return;
        }

        log.info("{} の集計結果を請求書と照合します。集計件数(請求単位): {}", cutoffType, summaries.size());

        // ★請求書との照合のために、請求書キーでグループ化
        // 都度現金払いの場合も「店舗＋得意先」単位でグループ化（注文番号は含めない）
        // キー: InvoiceValidationKey (店舗、請求先コード、"請求書検索用"締め日文字列)
        Map<InvoiceValidationKey, List<TAccountsReceivableSummary>> summariesByInvoiceKey = new HashMap<>();

        // 同じinvoicePartnerCodeを持つ複数の得意先をグループ化（都度現金払いも合算）
        for (TAccountsReceivableSummary summary : summaries) {
            // --- 請求書検索用の締め日文字列を決定 ---
            String closingDateStrForSearch = formatClosingDateForSearch(
                    targetPeriodEndDate, // 検索用の年月として期間終了日を使用
                    summary.getCutoffDate() // Summary に格納された実際の締め日コード
            );

            String searchPartnerCode = summary.getPartnerCode(); // 請求単位のコード ("999999" or 請求コード)

            // 得意先コード301491の場合、クリーンラボのため、一旦店舗番号を1に変更して請求書検索を行う
            Integer shopNoForSearch = summary.getShopNo();
            if ("301491".equals(searchPartnerCode)) {
                shopNoForSearch = 1; // 店舗番号を1に変更
                log.debug("得意先コード301491のため、請求書検索用の店舗番号を {} から 1 に変更します。", summary.getShopNo());
            }

            // 請求書比較用のキー - 都度現金払いでも注文番号は含めない（合算するため）
            InvoiceValidationKey invoiceKey = new InvoiceValidationKey(
                    shopNoForSearch,
                    searchPartnerCode,
                    closingDateStrForSearch,
                    null  // 都度現金払いでも注文番号は含めない
            );

            // マップにグループ化して追加
            if (!summariesByInvoiceKey.containsKey(invoiceKey)) {
                summariesByInvoiceKey.put(invoiceKey, new ArrayList<>());
            }
            summariesByInvoiceKey.get(invoiceKey).add(summary);
        }

        List<TAccountsReceivableSummary> validatedSummaries = new ArrayList<>();
        int validationOkCount = 0;
        int validationNgCount = 0;
        int invoiceNotFoundCount = 0;

        // shop_no, partner_codeの昇順でソートするためのキーのリストを作成
        List<InvoiceValidationKey> sortedKeys = new ArrayList<>(summariesByInvoiceKey.keySet());
        // shop_noの昇順、次にpartner_codeの昇順でソート
        // shop_noが異なる場合はその順序を使用
        // shop_noが同じ場合はpartner_codeでソート
        sortedKeys.sort(Comparator.comparingInt(InvoiceValidationKey::getShopNo).thenComparing(InvoiceValidationKey::getSearchPartnerCode));

        // ソートされた順序でエントリを処理
        for (InvoiceValidationKey invoiceKey : sortedKeys) {
            List<TAccountsReceivableSummary> invoiceGroupSummaries = summariesByInvoiceKey.get(invoiceKey);

            if (invoiceGroupSummaries.isEmpty()) {
                log.warn("請求キー {} に対応する集計結果が空です。スキップします。", invoiceKey);
                continue;
            }

            Integer shopNo = invoiceKey.getShopNo();
            String searchPartnerCode = invoiceKey.getSearchPartnerCode();
            String closingDateStrForSearch = invoiceKey.getClosingDateStr();

            // 代表的なサマリーから得意先情報を取得（ログ出力用）
            TAccountsReceivableSummary representativeSummary = invoiceGroupSummaries.get(0);
            String partnerCodeForLog = representativeSummary.getPartnerCode();
            Integer partnerNoForLog = representativeSummary.getPartnerNo();
            Integer cutoffDateForLog = representativeSummary.getCutoffDate();

            // 支払いタイプの判定（ログ出力用）
            PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDateForLog);
            boolean isCashOnDelivery = paymentType == PaymentType.CASH_ON_DELIVERY;

            // 注文番号一覧を作成（都度現金払いの場合のみ使用、ログ出力用）
            List<Integer> orderNos = new ArrayList<>();
            if (isCashOnDelivery) {
                for (TAccountsReceivableSummary summary : invoiceGroupSummaries) {
                    Integer orderNo = summary.getOrderNo();
                    if (orderNo != null && !orderNos.contains(orderNo)) {
                        orderNos.add(orderNo);
                    }
                }
                // 注文番号でソート
                Collections.sort(orderNos);
            }

            // 金額集計 - 各サマリーの税込金額を合計
            BigDecimal totalTaxIncludedAmount = BigDecimal.ZERO;
            for (TAccountsReceivableSummary summary : invoiceGroupSummaries) {
                BigDecimal amount = summary.getTaxIncludedAmountChange();
                if (amount != null) {
                    totalTaxIncludedAmount = totalTaxIncludedAmount.add(amount);
                }
            }
            // 比較時も切り捨て（RoundingMode.DOWN）を使用して統一感を持たせる
            BigDecimal roundedTotalAmount = totalTaxIncludedAmount.setScale(0, RoundingMode.DOWN);

            // ★ TInvoice 検索 (検索用締め日文字列を使用)
            // 得意先コード301491の場合、店舗番号を1で検索
            Integer invoiceSearchShopNo = shopNo;
            if ("301491".equals(searchPartnerCode)) {
                invoiceSearchShopNo = 1;
                log.debug("得意先コード301491のため、TInvoice検索用の店舗番号を {} から 1 に変更します。", shopNo);
            }

            Optional<TInvoice> invoiceOpt;

            // 上様分（得意先コード：999999）の場合、請求書金額で常に上書き
            boolean isSpecialJosamaSummary = "999999".equals(searchPartnerCode);

            // 得意先コード000231の場合、特殊な請求書処理
            if (QUARTERLY_BILLING_PARTNER_CODE.equals(searchPartnerCode)) {
                // 当月15日締め + 前月が特殊月(2,5,8,11)の場合は前月末締めも追加
                List<String> specialPartnerClosingDates = getSpecialPartnerClosingDates(targetPeriodEndDate);

                log.info("得意先コード000231の特殊請求処理: 対象締め日 = {}", specialPartnerClosingDates);

                // すべての該当締め日の請求書を取得
                List<TInvoice> combinedInvoices = new ArrayList<>();
                for (String closingDateStr : specialPartnerClosingDates) {
                    Optional<TInvoice> invoice = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(
                            invoiceSearchShopNo, searchPartnerCode, closingDateStr);
                    invoice.ifPresent(combinedInvoices::add);
                }

                if (!combinedInvoices.isEmpty()) {
                    // 全ての請求書の合計金額を計算
                    BigDecimal totalInvoiceAmount = BigDecimal.ZERO;
                    for (TInvoice invoice : combinedInvoices) {
                        if (invoice.getNetSalesIncludingTax() != null) {
                            totalInvoiceAmount = totalInvoiceAmount.add(invoice.getNetSalesIncludingTax());
                        }
                    }

                    // 合計金額で仮想的な請求書を作成
                    TInvoice combinedInvoice = new TInvoice();
                    combinedInvoice.setNetSalesIncludingTax(totalInvoiceAmount);
                    combinedInvoice.setShopNo(invoiceSearchShopNo);
                    combinedInvoice.setPartnerCode(searchPartnerCode);
                    combinedInvoice.setClosingDate(closingDateStrForSearch);

                    invoiceOpt = Optional.of(combinedInvoice);
                    log.info("得意先コード000231: 対象締め日{}の請求書{}件を合算し、合計金額{}円の仮想請求書を作成しました",
                            specialPartnerClosingDates, combinedInvoices.size(), totalInvoiceAmount);
                } else {
                    invoiceOpt = Optional.empty();
                    log.warn("得意先コード000231: 対象締め日{}の請求書が見つかりませんでした",
                            specialPartnerClosingDates);
                }
            } else {
                // 通常の請求書検索
                invoiceOpt = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(
                        invoiceSearchShopNo, searchPartnerCode, closingDateStrForSearch);
            }

            if (invoiceOpt.isPresent()) {
                TInvoice invoice = invoiceOpt.get();
                BigDecimal invoiceAmount = invoice.getNetSalesIncludingTax() != null ? invoice.getNetSalesIncludingTax() : BigDecimal.ZERO;
                // 請求書金額も切り捨て（RoundingMode.DOWN）を使用
                BigDecimal roundedInvoiceAmount = invoiceAmount.setScale(0, RoundingMode.DOWN);

                // 上様分（得意先コード：999999）の場合は常に請求書の金額で上書き
                if (isSpecialJosamaSummary) {
                    // 上様分の場合、請求書金額で上書きして保存
                    validatedSummaries.addAll(invoiceGroupSummaries);
                    validationOkCount++;

                    // 請求書金額と集計金額の差分を記録
                    BigDecimal diffAmount = roundedInvoiceAmount.subtract(roundedTotalAmount);

                    // ログ出力
                    if (isCashOnDelivery && !orderNos.isEmpty()) {
                        log.info("上様分: 請求書金額で上書き - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 注文番号: {}, 集計金額: {}, 請求書金額: {}, 差額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                orderNos, // 注文番号リスト
                                roundedTotalAmount, roundedInvoiceAmount, diffAmount);
                    } else {
                        log.info("上様分: 請求書金額で上書き - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 集計金額: {}, 請求書金額: {}, 差額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                roundedTotalAmount, roundedInvoiceAmount, diffAmount);
                    }

                    // 請求書金額で上書き（比率按分 + 残差は最大金額行で吸収）
                    if (roundedTotalAmount.compareTo(BigDecimal.ZERO) != 0) {
                        allocateProportionallyWithRemainder(invoiceGroupSummaries, roundedInvoiceAmount, roundedTotalAmount);
                    } else {
                        // 集計金額が 0 の場合: 税率別按分の根拠がないため上書きをスキップし ERROR ログ
                        // （従来は 1 件目に全額・2 件目以降 0 でデータ破損していた）
                        log.error("上様分の集計金額が 0 のため、請求書金額({})を按分できません。該当 summary は変更しません。shop={}, partnerCode={}, partnerNo={}",
                                roundedInvoiceAmount, shopNo, partnerCodeForLog, partnerNoForLog);
                    }
                } else if (roundedTotalAmount.compareTo(roundedInvoiceAmount) == 0) {
                    // 元の注文ごとのサマリーを保持
                    validatedSummaries.addAll(invoiceGroupSummaries);
                    validationOkCount++;

                    // ログ出力 - 都度現金払いの場合は注文番号リストを表示
                    if (isCashOnDelivery && !orderNos.isEmpty()) {
                        log.info("請求書との検証OK - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 注文番号: {}, 金額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                orderNos, // 注文番号リスト
                                roundedTotalAmount);
                    } else {
                        log.info("請求書との検証OK - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 金額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                roundedTotalAmount);
                    }
                } else if (roundedTotalAmount.subtract(roundedInvoiceAmount).abs().compareTo(invoiceAmountTolerance) <= 0) {
                    // 差額の絶対値が許容差額以内の場合は警告ログを出力し、請求書の金額に合わせる
                    validatedSummaries.addAll(invoiceGroupSummaries);
                    validationOkCount++;

                    // 警告ログ出力 - 都度現金払いの場合は注文番号リストを表示
                    if (isCashOnDelivery && !orderNos.isEmpty()) {
                        log.warn("請求書との金額が{}円違いますが、請求書の金額に合わせます - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 注文番号: {}, 集計金額: {}, 請求書金額: {}, 差額: {}",
                                roundedTotalAmount.subtract(roundedInvoiceAmount).abs(),
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                orderNos, // 注文番号リスト
                                roundedTotalAmount, roundedInvoiceAmount, roundedTotalAmount.subtract(roundedInvoiceAmount));
                    } else {
                        log.warn("請求書との金額が{}円違いますが、請求書の金額に合わせます - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 集計金額: {}, 請求書金額: {}, 差額: {}",
                                roundedTotalAmount.subtract(roundedInvoiceAmount).abs(),
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                roundedTotalAmount, roundedInvoiceAmount, roundedTotalAmount.subtract(roundedInvoiceAmount));
                    }

                    // 得意先ごとの請求金額を調整（比率按分 + 残差は最大金額行で吸収）
                    allocateProportionallyWithRemainder(invoiceGroupSummaries, roundedInvoiceAmount, roundedTotalAmount);

                } else {
                    validationNgCount++;
                    // ログ出力 - 都度現金払いの場合は注文番号リストを表示
                    if (isCashOnDelivery && !orderNos.isEmpty()) {
                        log.error("請求書との金額不一致 - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 注文番号: {}, " +
                                        "集計金額: {}, 請求書金額: {}, 差額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                orderNos, // 注文番号リスト
                                roundedTotalAmount, roundedInvoiceAmount, roundedTotalAmount.subtract(roundedInvoiceAmount));
                    } else {
                        log.error("請求書との金額不一致 - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, " +
                                        "集計金額: {}, 請求書金額: {}, 差額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                roundedTotalAmount, roundedInvoiceAmount, roundedTotalAmount.subtract(roundedInvoiceAmount));
                    }
                }
            } else {
                invoiceNotFoundCount++;

                // 注文金額が0円以上の場合のみwarnログを出力（問題があるケース）
                if (roundedTotalAmount.compareTo(BigDecimal.ZERO) > 0) {
                    // ログ出力 - 都度現金払いの場合は注文番号リストを表示
                    if (isCashOnDelivery && !orderNos.isEmpty()) {
                        log.warn("該当する請求書が見つかりません - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 注文番号: {}, 金額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                orderNos, // 注文番号リスト
                                roundedTotalAmount);
                    } else {
                        log.warn("該当する請求書が見つかりません - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}, 金額: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType,
                                roundedTotalAmount);
                    }
                } else {
                    // 注文金額が0円の場合はinfo（正常）ログを出力
                    if (isCashOnDelivery && !orderNos.isEmpty()) {
                        log.info("請求書なし（集計金額0円） - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType);
                    } else {
                        log.info("請求書なし（集計金額0円） - ショップ: {}, 得意先コード: {} (検索用: {}), 得意先番号: {}, 締め日: {}, 支払タイプ: {}",
                                shopNo, partnerCodeForLog, searchPartnerCode, partnerNoForLog,
                                closingDateStrForSearch, paymentType);
                    }
                }
            }
        }

        log.info("{} の請求書照合結果 - OK: {}件, 金額不一致: {}件, 請求書なし: {}件 (件数は請求書単位)",
                cutoffType, validationOkCount, validationNgCount, invoiceNotFoundCount);

        if (!validatedSummaries.isEmpty()) {
            log.info("{} の検証済み売掛金集計結果をデータベースに保存します。保存対象Summary件数: {}", cutoffType, validatedSummaries.size());
            int savedCount = 0;
            for (TAccountsReceivableSummary summary : validatedSummaries) {
                try {
                    tAccountsReceivableSummaryService.save(summary);
                    savedCount++;
                } catch (Exception e) {
                    log.error("売掛金集計結果(Summary)の保存中にエラーが発生しました。ShopNo: {}, PartnerNo: {}, PartnerCode: {}, OrderNo: {}",
                            summary.getShopNo(), summary.getPartnerNo(), summary.getPartnerCode(), summary.getOrderNo(), e);
                }
            }
            log.info("{} の検証済み売掛金集計結果 {} 件をデータベースに保存しました。", cutoffType, savedCount);
        } else {
            log.info("{} の保存対象となる検証済み売掛金集計結果はありませんでした。", cutoffType);
        }
    }

    /**
     * ★請求書検索に使用する★ 締め日文字列を生成します。
     * 都度現金払いは月末扱い ("YYYY/MM/末") になります。
     *
     * @param targetPeriodEndDate 期間終了日 (年月取得用)
     * @param cutoffDate          締め日コード (得意先マスタの値, null の場合は 0 扱い)
     * @return フォーマットされた締め日文字列（例: "2025/04/末", "2025/04/20"）
     */
    private String formatClosingDateForSearch(LocalDate targetPeriodEndDate, Integer cutoffDate) {
        PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDate);

        // 都度現金払い、または月末締めの場合 -> "YYYY/MM/末"
        if (paymentType == PaymentType.CASH_ON_DELIVERY || paymentType == PaymentType.MONTH_END) {
            return String.format("%d/%02d/末", targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue());
        }
        // 特定日締めの場合 -> "YYYY/MM/DD"
        else {
            return String.format("%d/%02d/%02d", targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue(), cutoffDate);
        }
    }

    /**
     * 【主にログ出力やデバッグ用】締め日を詳細な文字列フォーマットに変換します。
     * 都度現金払いは注文番号を含みます ("YYYY/MM/現金/OrderNo")。
     * ★請求書検索には `formatClosingDateForSearch` を使用してください★
     *
     * @param targetPeriodEndDate 期間終了日 (年月取得用)
     * @param cutoffDate 締め日コード (得意先マスタの値, null の場合は 0 扱い)
     * @param orderNo 注文番号（都度現金払いの場合のみ使用、null でない場合）
     * @return フォーマットされた締め日文字列（例: "2025/04/末", "2025/04/20", "2025/04/現金/12345"）
     */
    private String formatClosingDateForLog(LocalDate targetPeriodEndDate, Integer cutoffDate, Integer orderNo) {
        PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDate);

        if (paymentType == PaymentType.CASH_ON_DELIVERY && orderNo != null) {
            return String.format("%d/%02d/現金/%d", targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue(), orderNo);
        } else if (paymentType == PaymentType.MONTH_END) {
            return String.format("%d/%02d/末", targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue());
        } else {
            return String.format("%d/%02d/%02d", targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue(), cutoffDate);
        }
    }


    private List<TAccountsReceivableSummary> calculateReceivableSummaries(LocalDate startDate, LocalDate endDate, List<Integer> targetPartnerNos) {
        log.info("売掛金集計計算を開始します。期間: {} ～ {}, 対象得意先数: {}", startDate, endDate, targetPartnerNos.size());
        List<TOrderDetail> orderDetails;
        if (!targetPartnerNos.isEmpty()) {
            orderDetails = tOrderDetailService.findByPartnerNosAndDateRange(targetPartnerNos, startDate, endDate, Flag.NO);
            log.info("期間内の注文詳細を取得しました。件数: {}", orderDetails.size());
        } else {
            log.warn("対象得意先リストが空のため、注文詳細を取得せずにスキップします。");
            return new ArrayList<>();
        }
        if (orderDetails.isEmpty()) {
            log.info("期間内に該当する注文詳細が存在しませんでした。");
            return new ArrayList<>();
        }

        Map<SummaryKey, List<TOrderDetail>> detailsBySummaryKey = new HashMap<>();
        Map<SummaryKey, PartnerBillingInfo> keyToBillingInfoMap = new HashMap<>();
        int skippedDetailCount = 0;

        for (TOrderDetail detail : orderDetails) {
            Integer shopNo = detail.getShopNo();
            Integer orderPartnerNo = detail.getTOrder().getPartnerNo();
            String invoicePartnerCode = this.partnerInvoiceCodeMap.get(orderPartnerNo);
            if (invoicePartnerCode == null) {
                log.error("注文詳細(ID:{}, OrderNo:{})の得意先番号 {} に対応する請求コードがキャッシュに見つかりません。スキップします。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), orderPartnerNo);
                skippedDetailCount++;
                continue;
            }
            ShopInvoiceKey shopInvoiceKey = new ShopInvoiceKey(shopNo, invoicePartnerCode);
            Integer billingPartnerNo = this.invoicePartnerCodeToPartnerNoMap.get(shopInvoiceKey);
            if (billingPartnerNo == null) {
                log.error("注文詳細(ID:{}, OrderNo:{})の店舗:{}、請求コード:{} に対応する請求先得意先番号がキャッシュに見つかりません。スキップします。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), shopNo, invoicePartnerCode);
                skippedDetailCount++;
                continue;
            }
            Integer billingCutoffDate = this.partnerCutoffDateMap.get(billingPartnerNo);
            if (billingCutoffDate == null) {
                log.error("注文詳細(ID:{}, OrderNo:{})の請求先得意先番号 {} に対応する締め日情報がキャッシュに見つかりません。スキップします。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), billingPartnerNo);
                skippedDetailCount++;
                continue;
            }

            PaymentType paymentType = PaymentType.fromCutoffCode(billingCutoffDate);
            boolean isCashOnDelivery = paymentType == PaymentType.CASH_ON_DELIVERY;
            boolean isLongPartnerCode = invoicePartnerCode.length() >= 7;
            int summaryPartnerNo;
            Integer summaryOrderNo = null;
            String summaryPartnerCode;

            if (isCashOnDelivery) {
                summaryPartnerNo = billingPartnerNo;
                summaryOrderNo = detail.getOrderNo();
                summaryPartnerCode = invoicePartnerCode;
                if (summaryOrderNo == null) {
                    log.error("都度現金払いの注文詳細(ID:{})に注文番号が設定されていません。この明細はスキップされる可能性があります。", detail.getOrderDetailNo());
                    skippedDetailCount++;
                    continue;
                }
            } else if (isLongPartnerCode) {
                // 得意先コードが7桁以上の場合は「999999」（上様）として処理し、
                // かつ締め日を-1（都度現金払い）として処理
                summaryPartnerNo = -999999;
                summaryPartnerCode = "999999";
                // 注文番号を設定（都度現金払いと同様に処理）
                summaryOrderNo = detail.getOrderNo();
                if (summaryOrderNo == null) {
                    log.error("上様扱い(7桁以上)の注文詳細(ID:{})に注文番号が設定されていません。この明細はスキップされる可能性があります。", detail.getOrderDetailNo());
                    skippedDetailCount++;
                    continue;
                }
                // PaymentTypeを都度現金払いに変更
                // 新しい締め日を一時変数に格納
                Integer cashOnDeliveryCutoffDate = PaymentType.CASH_ON_DELIVERY.getCutoffCode(); // -1に設定
                log.debug("得意先コードが7桁以上の注文詳細(ID:{}, OrderNo:{}, 得意先コード:{})を上様（都度現金払い）として処理します。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), invoicePartnerCode);

                // 一時変数の締め日を使用して処理を続ける
                // SummaryKeyの生成
                SummaryKey key = new SummaryKey(
                        shopNo, summaryPartnerNo,
                        detail.getTaxRate() != null ? detail.getTaxRate() : BigDecimal.ZERO,
                        OTAKE_GARBAGE_BAG_GOODS_CODES.contains(detail.getGoodsCode()),
                        summaryOrderNo
                );
                // 新しい締め日を使用してPartnerBillingInfoを生成
                keyToBillingInfoMap.computeIfAbsent(key, k -> new PartnerBillingInfo(summaryPartnerCode, cashOnDeliveryCutoffDate));
                detailsBySummaryKey.computeIfAbsent(key, k -> new ArrayList<>()).add(detail);
                // このケースでは既に全ての処理を完了したので、下の共通処理をスキップ
                continue;
            } else {
                summaryPartnerNo = billingPartnerNo;
                summaryPartnerCode = invoicePartnerCode;
            }

            SummaryKey key = new SummaryKey(
                    shopNo, summaryPartnerNo,
                    detail.getTaxRate() != null ? detail.getTaxRate() : BigDecimal.ZERO,
                    OTAKE_GARBAGE_BAG_GOODS_CODES.contains(detail.getGoodsCode()),
                    summaryOrderNo
            );
            keyToBillingInfoMap.computeIfAbsent(key, k -> new PartnerBillingInfo(summaryPartnerCode, billingCutoffDate));
            detailsBySummaryKey.computeIfAbsent(key, k -> new ArrayList<>()).add(detail);
        }

        if (skippedDetailCount > 0) {
            log.warn("請求情報の解決に失敗し、{} 件の注文詳細がスキップされました。詳細はエラーログを確認してください。", skippedDetailCount);
        }
        log.info("注文詳細のグループ化完了。グループ数: {}", detailsBySummaryKey.size());

        List<TAccountsReceivableSummary> summaries = new ArrayList<>();
        for (Map.Entry<SummaryKey, List<TOrderDetail>> entry : detailsBySummaryKey.entrySet()) {
            SummaryKey key = entry.getKey();
            List<TOrderDetail> detailsInGroup = entry.getValue();
            PartnerBillingInfo billingInfo = keyToBillingInfoMap.get(key);
            if (billingInfo == null) {
                log.error("請求単位キーに対応する請求情報が見つかりません: {}. このグループはスキップします。", key);
                continue;
            }

            BigDecimal totalAmountExcludingTax = BigDecimal.ZERO;
            BigDecimal totalOriginalAmount = BigDecimal.ZERO; // 元の税込金額合計を追跡

            for (TOrderDetail detail : detailsInGroup) {
                // 税抜金額の計算
                totalAmountExcludingTax = totalAmountExcludingTax.add(calculateAmountExcludingTax(detail));

                // 元の金額（税込または税抜）を合計に追加
                BigDecimal totalAmount = detail.getTotalAmount();
                if (totalAmount != null) {
                    totalOriginalAmount = totalOriginalAmount.add(totalAmount);
                }
            }

            BigDecimal calculatedTaxAmount = BigDecimal.ZERO;
            BigDecimal taxRate = key.getTaxRate(); // key生成時にnullは0に変換済み
            if (taxRate.compareTo(BigDecimal.ZERO) > 0) {
                calculatedTaxAmount = totalAmountExcludingTax
                        .multiply(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN))
                        .setScale(0, RoundingMode.DOWN);
            }

            // 税込金額の決定：
            // TAXABLE_INCLUDEの場合は元の税込金額をそのまま使用、それ以外は計算値を使用
            BigDecimal totalAmountIncludingTax;

            // 全ての明細がTAXABLE_INCLUDEか確認
            boolean allTaxableInclude = detailsInGroup.stream()
                    .allMatch(detail -> TaxType.TAXABLE_INCLUDE.equals(TaxType.purse(detail.getTaxType())));

            if (allTaxableInclude) {
                // 税込販売の場合は元の税込金額を使用
                totalAmountIncludingTax = totalOriginalAmount;
                // デバッグ用（得意先番号104019の場合は詳細ログ出力）
                if (key.getPartnerNo() != null && key.getPartnerNo().equals(104019)) {
                    log.debug("得意先番号104019の計算詳細: 元の税込金額={}, 計算した税抜金額={}, 計算した税額={}, 計算した税込金額={}, 差額={}",
                            totalOriginalAmount, totalAmountExcludingTax, calculatedTaxAmount,
                            totalAmountExcludingTax.add(calculatedTaxAmount),
                            totalOriginalAmount.subtract(totalAmountExcludingTax.add(calculatedTaxAmount)));
                }
            } else {
                // 税抜販売の場合は計算値を使用
                totalAmountIncludingTax = totalAmountExcludingTax.add(calculatedTaxAmount);
            }

            // 金額の端数処理（小数点以下切り捨て）
            BigDecimal taxExcludedAmountTruncated = totalAmountExcludingTax.setScale(0, RoundingMode.DOWN);

            // 税額の計算（税込金額 - 税抜金額）
            BigDecimal taxAmount;
            if (allTaxableInclude) {
                // 税込販売の場合、元の税込金額から税抜金額を引いて計算
                taxAmount = totalOriginalAmount.subtract(totalAmountExcludingTax);
            } else {
                // 税抜販売の場合、計算済みの税額を使用
                taxAmount = calculatedTaxAmount;
            }
            BigDecimal taxAmountTruncated = taxAmount.setScale(0, RoundingMode.DOWN);

            // tax_excluded_amount_change + tax_amount = tax_included_amount_change となるように調整
            BigDecimal originalTaxIncludedAmount = allTaxableInclude ? totalOriginalAmount : totalAmountIncludingTax;
            BigDecimal originalTaxIncludedAmountTruncated = originalTaxIncludedAmount.setScale(0, RoundingMode.DOWN);
            BigDecimal taxIncludedAmountTruncated = taxExcludedAmountTruncated.add(taxAmountTruncated);

            // 消費税計算の誤差チェック（1円の誤差なら請求書金額に合わせる）
            BigDecimal diff = originalTaxIncludedAmountTruncated.subtract(taxIncludedAmountTruncated);
            if (diff.abs().compareTo(BigDecimal.ONE) == 0) {
                // 1円の誤差がある場合
                log.warn("消費税計算に1円の誤差を検出しました。請求書の金額に合わせます。得意先No={}, 元の計算値: 税抜={}, 税額={}, 税込={}, 調整後税込金額={}",
                        key.getPartnerNo(), taxExcludedAmountTruncated, taxAmountTruncated, taxIncludedAmountTruncated, originalTaxIncludedAmountTruncated);
                // 請求書の金額（元の税込金額の切り捨て値）に合わせる
                taxIncludedAmountTruncated = originalTaxIncludedAmountTruncated;
            }

            // デバッグ用ログ出力
            if (key.getPartnerNo() != null && key.getPartnerNo().equals(104019)) {
                log.debug("得意先番号104019の端数処理: 税抜={} → {}, 税額={} → {}, 税込={} → {}, 関係式={}",
                        totalAmountExcludingTax, taxExcludedAmountTruncated,
                        taxAmount, taxAmountTruncated,
                        originalTaxIncludedAmount, taxIncludedAmountTruncated,
                        (taxExcludedAmountTruncated.add(taxAmountTruncated).compareTo(taxIncludedAmountTruncated) == 0 ? "OK" : "NG"));
            }

            TAccountsReceivableSummary summary = TAccountsReceivableSummary.builder()
                    .shopNo(key.getShopNo())
                    .partnerNo(key.getPartnerNo())
                    .partnerCode(billingInfo.getPartnerCode())
                    .transactionMonth(endDate) // ★ 集計期間の終了日
                    .taxRate(key.getTaxRate())
                    .taxIncludedAmountChange(taxIncludedAmountTruncated) // 端数切捨て済みの税込金額
                    .taxExcludedAmountChange(taxExcludedAmountTruncated) // 端数切捨て済みの税抜金額
                    .isOtakeGarbageBag(key.isOtakeGarbageBag())
                    .cutoffDate(billingInfo.getCutoffDate()) // ★ 実際の締め日
                    .orderNo(key.getOrderNo()) // ★ 都度現金の場合のみ
                    .build();
            summaries.add(summary);
        }

        log.info("売掛金集計計算完了。生成されたSummary件数: {}", summaries.size());
        return summaries;
    }

    /**
     * 集計サマリー群を請求書金額に合わせて按分する。
     * <p>単純に比率を掛けて DOWN で丸めると合計が請求書金額に届かない（最大で行数円ぶん下振れ）ため、
     * 最大金額の行で残差を吸収する。税抜も同様に按分して調整。
     *
     * @param summaries        調整対象の summary リスト（順序は呼出元で維持）
     * @param targetIncTotal   目標税込合計（請求書金額）
     * @param originalIncTotal 現在の税込合計（按分根拠）
     */
    private void allocateProportionallyWithRemainder(
            List<TAccountsReceivableSummary> summaries,
            BigDecimal targetIncTotal,
            BigDecimal originalIncTotal) {
        if (summaries == null || summaries.isEmpty()) return;
        if (originalIncTotal == null || originalIncTotal.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal ratio = targetIncTotal.divide(originalIncTotal, 10, RoundingMode.HALF_UP);

        // 最大金額行（残差を吸収する行）を特定
        TAccountsReceivableSummary largest = summaries.stream()
                .filter(s -> s.getTaxIncludedAmountChange() != null)
                .max(Comparator.comparing(TAccountsReceivableSummary::getTaxIncludedAmountChange))
                .orElse(null);

        BigDecimal incAllocated = BigDecimal.ZERO;
        BigDecimal excAllocated = BigDecimal.ZERO;
        BigDecimal targetExcTotal = summaries.stream()
                .filter(s -> s.getTaxExcludedAmountChange() != null)
                .map(TAccountsReceivableSummary::getTaxExcludedAmountChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(ratio)
                .setScale(0, RoundingMode.DOWN);

        // 最大行以外を先に按分
        for (TAccountsReceivableSummary s : summaries) {
            if (s == largest) continue;
            if (s.getTaxIncludedAmountChange() != null) {
                BigDecimal adjIncluded = s.getTaxIncludedAmountChange().multiply(ratio).setScale(0, RoundingMode.DOWN);
                s.setTaxIncludedAmountChange(adjIncluded);
                incAllocated = incAllocated.add(adjIncluded);
            }
            if (s.getTaxExcludedAmountChange() != null) {
                BigDecimal adjExcluded = s.getTaxExcludedAmountChange().multiply(ratio).setScale(0, RoundingMode.DOWN);
                s.setTaxExcludedAmountChange(adjExcluded);
                excAllocated = excAllocated.add(adjExcluded);
            }
        }

        // 最大行に残差を割り当て（これにより合計が targetIncTotal にピタリ一致する）
        if (largest != null) {
            largest.setTaxIncludedAmountChange(targetIncTotal.subtract(incAllocated));
            if (largest.getTaxExcludedAmountChange() != null) {
                largest.setTaxExcludedAmountChange(targetExcTotal.subtract(excAllocated));
            }
        }
    }

    private BigDecimal calculateAmountExcludingTax(TOrderDetail detail) {
        BigDecimal totalAmount = detail.getTotalAmount();
        TaxType taxType = TaxType.purse(detail.getTaxType());
        BigDecimal taxRate = detail.getTaxRate();
        if (totalAmount == null) {
            log.trace("OrderDetail ID: {} の totalAmount が null のため、0として扱います。", detail.getOrderDetailNo());
            return BigDecimal.ZERO;
        }
        if (taxRate == null) {
            log.trace("OrderDetail ID: {} の taxRate が null のため、0として扱います。", detail.getOrderDetailNo());
            taxRate = BigDecimal.ZERO;
        }

        switch (Objects.requireNonNull(taxType, "TaxType が決定できません: " + detail.getTaxType())) {
            case TAX_EXCLUDE:
                return totalAmount;
            case TAXABLE_INCLUDE:
                if (taxRate.compareTo(BigDecimal.ZERO) == 0) return totalAmount;
                BigDecimal divider = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN));
                return totalAmount.divide(divider, 10, RoundingMode.DOWN);
            case TAX_FREE:
                return totalAmount;
            default:
                log.error("未知または処理不能な税区分です: OrderDetail ID={}, TaxType={}", detail.getOrderDetailNo(), taxType);
                return totalAmount;
        }
    }

    // --- Helper Classes ---
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    private static class SummaryKey {
        private final Integer shopNo;
        private final Integer partnerNo;
        private final BigDecimal taxRate;
        private final boolean isOtakeGarbageBag;
        private final Integer orderNo;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    private static class PartnerBillingInfo {
        private final String partnerCode;
        private final Integer cutoffDate;
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    private static class ShopInvoiceKey {
        private final Integer shopNo;
        private final String invoicePartnerCode;
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    private static class InvoiceValidationKey {
        private final Integer shopNo;
        private final String searchPartnerCode;
        /**
         * ★請求書検索用★ 締め日文字列 ("YYYY/MM/末" or "YYYY/MM/DD")
         */
        private final String closingDateStr;
        /**
         * ★都度現金払いの場合に使用★ 注文番号
         */
        private final Integer orderNo;
    }
}
