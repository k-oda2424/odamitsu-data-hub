package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.PayableBalanceCalculator;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * MF 試算表 (/api/v3/reports/trial_balance_bs) の買掛金 closing_balance と
 * 自社 {@code t_accounts_payable_summary} の累積残 (opening + change - payment_settled) を突合する Service。
 * <p>
 * Phase A の opening_balance + Phase B' の payment_settled T 勘定化を前提とした残高突合。
 * 既存 {@link MfJournalReconcileService} (当月 change 合計での仕訳突合) とは別軸で並行動作する。
 * <p>
 * <b>Phase B'' (2026-04-22)</b>: 期首残 (MF 2025-05-20 買掛金 closing) を MF 側から自動取得し
 * レポートに含める。自社 DB には backfill 起点 2025-06-20 より前の買掛残が入っていないため、
 * 期首残分は常に既知差として残る。UI で明示表示して運用側に誤解を与えないようにする。
 * <p>
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §5,
 *         claudedocs/design-phase-b-prime-payment-settled.md §11
 *
 * @since 2026-04-22 (Phase B)
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfBalanceReconcileService {

    /** MF 勘定科目名: 買掛金 (負債)。 */
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    /**
     * 自社 backfill 起点 (2025-06-20) の前月末日。
     * MF 試算表のこの日の買掛金 closing = 自社 DB に入っていない期首買掛残。
     * Phase B'' (2026-04-22): 自社 DB の期首注入を行わず、突合時に MF 側から差し引く調整で対応。
     */
    private static final LocalDate FISCAL_OPENING_DATE = LocalDate.of(2025, 5, 20);

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final TAccountsPayableSummaryService payableSummaryService;

    public BalanceReconcileReport reconcile(LocalDate period) {
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();
        String endDate = period.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // ---- MF 試算表から買掛金 closing (対象月) を取得 ----
        MfTrialBalanceBsResponse mfBs = mfApiClient.getTrialBalanceBs(client, accessToken, endDate);
        MfTrialBalanceBsResponse.Row mfPayableRow = mfBs.findAccount(MF_ACCOUNT_PAYABLE);
        BigDecimal mfPayableClosing = mfBs.closingOf(mfPayableRow);
        log.info("[balance-reconcile] MF 買掛金 closing @ {} = {}", endDate, mfPayableClosing);

        // ---- MF 試算表から期首残 (自社 backfill 起点の前月) を取得 ----
        //      自社 DB に入っていない期首買掛残 ≒ 突合の既知差
        BigDecimal mfOpeningBalance = BigDecimal.ZERO;
        try {
            String openingDate = FISCAL_OPENING_DATE.format(DateTimeFormatter.ISO_LOCAL_DATE);
            MfTrialBalanceBsResponse mfOpeningBs = mfApiClient.getTrialBalanceBs(client, accessToken, openingDate);
            MfTrialBalanceBsResponse.Row openingRow = mfOpeningBs.findAccount(MF_ACCOUNT_PAYABLE);
            mfOpeningBalance = mfOpeningBs.closingOf(openingRow);
            log.info("[balance-reconcile] MF 期首残 買掛金 closing @ {} = {}", openingDate, mfOpeningBalance);
        } catch (Exception e) {
            log.warn("[balance-reconcile] 期首残取得に失敗、0 で継続: {}", e.getMessage());
        }

        // ---- 自社側 closing 合計 ----
        List<TAccountsPayableSummary> allRows = payableSummaryService.findByTransactionMonth(period);
        BigDecimal selfPayableClosingAll = BigDecimal.ZERO;
        BigDecimal selfPayableClosingForMf = BigDecimal.ZERO;
        int mfExportRowCount = 0;
        for (TAccountsPayableSummary r : allRows) {
            BigDecimal closing = closingOf(r);
            selfPayableClosingAll = selfPayableClosingAll.add(closing);
            if (isMfTarget(r)) {
                selfPayableClosingForMf = selfPayableClosingForMf.add(closing);
                mfExportRowCount++;
            }
        }

        BigDecimal diffAll = mfPayableClosing.subtract(selfPayableClosingAll);
        BigDecimal diffForMf = mfPayableClosing.subtract(selfPayableClosingForMf);
        // 期首残調整後の差 = 対象月 MF - 期首残 - 自社 (純粋に 6/20 以降の累積で比較)
        BigDecimal diffForMfAdjusted = mfPayableClosing.subtract(mfOpeningBalance).subtract(selfPayableClosingForMf);

        PayableBalance payable = new PayableBalance(
                mfPayableClosing,
                selfPayableClosingAll,
                selfPayableClosingForMf,
                diffAll,
                diffForMf,
                allRows.size(),
                mfExportRowCount,
                mfPayableRow != null,
                mfOpeningBalance,
                FISCAL_OPENING_DATE.format(DateTimeFormatter.ISO_LOCAL_DATE),
                diffForMfAdjusted
        );
        return new BalanceReconcileReport(period, Instant.now(), endDate, payable);
    }

    /**
     * closing = opening + effectiveChange - payment_settled (Phase B' 定義)。
     * PayableBalanceCalculator に集約して 4 箇所で同じ式を使う。
     */
    private static BigDecimal closingOf(TAccountsPayableSummary r) {
        return PayableBalanceCalculator.closingTaxIncluded(r);
    }

    /**
     * MF 突合対象かを判定。
     * mf_export_enabled=true または verified_manually=true または is_payment_only=true。
     */
    private static boolean isMfTarget(TAccountsPayableSummary r) {
        return Boolean.TRUE.equals(r.getMfExportEnabled())
                || Boolean.TRUE.equals(r.getVerifiedManually())
                || Boolean.TRUE.equals(r.getIsPaymentOnly());
    }

    // ---- Report types ----

    public record BalanceReconcileReport(
            LocalDate period,
            Instant fetchedAt,
            String mfEndDate,
            PayableBalance payable
    ) {}

    /**
     * 買掛金の残高突合結果。
     * @param mfClosing             MF 試算表の買掛金 closing_balance
     * @param selfClosingAll        自社 summary 全 row の closing 合計 (mf_export_enabled 問わず)
     * @param selfClosingForMf      MF 突合対象 row (mfExportEnabled=true OR verifiedManually=true OR isPaymentOnly=true) のみの closing 合計
     * @param diffAll               mfClosing - selfClosingAll
     * @param diffForMf             mfClosing - selfClosingForMf (メインの突合指標)
     * @param selfRowCount          自社 summary 全 row 数
     * @param selfMfTargetRowCount  MF 突合対象 row 数
     * @param mfAccountFound        MF 試算表に「買掛金」account が存在したか (false なら MF 側異常)
     * @param mfOpeningBalance      MF 側の期首残 (自社 backfill 起点の前月末時点の買掛金 closing)
     *                              自社 DB に入っていない「期首買掛残」で、常に diffForMf に含まれる既知差。
     *                              Phase B'' (2026-04-22) で追加。
     * @param openingReferenceDate  mfOpeningBalance を取得した MF 基準日 (yyyy-MM-dd 文字列)
     * @param diffForMfAdjusted     期首残調整後の差分: mfClosing - mfOpeningBalance - selfClosingForMf。
     *                              純粋に backfill 期間中 (2025-06-20 以降) の累積差を示す。
     *                              この値が残る場合は 2025-06〜2025-12 の verified_amount 欠落 (過去振込明細未取込) が主因。
     */
    public record PayableBalance(
            BigDecimal mfClosing,
            BigDecimal selfClosingAll,
            BigDecimal selfClosingForMf,
            BigDecimal diffAll,
            BigDecimal diffForMf,
            int selfRowCount,
            int selfMfTargetRowCount,
            boolean mfAccountFound,
            BigDecimal mfOpeningBalance,
            String openingReferenceDate,
            BigDecimal diffForMfAdjusted
    ) {}
}
