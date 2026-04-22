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
 * 自社 {@code t_accounts_payable_summary} の累積残 (opening + effectiveChange) を突合する Service。
 * <p>
 * Phase A で導入された opening_balance 列を活用した残高ベースの整合確認。
 * 既存 {@link MfJournalReconcileService} (当月 change 合計での仕訳突合) とは別軸で並行動作する。
 * <p>
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §5
 *
 * @since 2026-04-22 (Phase B 最小版)
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfBalanceReconcileService {

    /** MF 勘定科目名: 買掛金 (負債)。 */
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final TAccountsPayableSummaryService payableSummaryService;

    public BalanceReconcileReport reconcile(LocalDate period) {
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();
        String endDate = period.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // ---- MF 試算表から買掛金 closing を取得 ----
        MfTrialBalanceBsResponse mfBs = mfApiClient.getTrialBalanceBs(client, accessToken, endDate);
        MfTrialBalanceBsResponse.Row mfPayableRow = mfBs.findAccount(MF_ACCOUNT_PAYABLE);
        BigDecimal mfPayableClosing = mfBs.closingOf(mfPayableRow);
        log.info("[balance-reconcile] MF 買掛金 closing @ {} = {}", endDate, mfPayableClosing);

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

        PayableBalance payable = new PayableBalance(
                mfPayableClosing,
                selfPayableClosingAll,
                selfPayableClosingForMf,
                diffAll,
                diffForMf,
                allRows.size(),
                mfExportRowCount,
                mfPayableRow != null
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
     * mf_export_enabled=true または verified_manually=true なら MF 側に反映されている想定。
     */
    private static boolean isMfTarget(TAccountsPayableSummary r) {
        return Boolean.TRUE.equals(r.getMfExportEnabled())
                || Boolean.TRUE.equals(r.getVerifiedManually())
                || Boolean.TRUE.equals(r.getIsPaymentOnly()); // payment-only も MF 側に journal 化されるため対象
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
     * @param selfClosingForMf      MF 突合対象 row (mfExportEnabled=true OR verifiedManually=true) のみの closing 合計
     * @param diffAll               mfClosing - selfClosingAll
     * @param diffForMf             mfClosing - selfClosingForMf (メインの突合指標)
     * @param selfRowCount          自社 summary 全 row 数
     * @param selfMfTargetRowCount  MF 突合対象 row 数
     * @param mfAccountFound        MF 試算表に「買掛金」account が存在したか (false なら MF 側異常)
     */
    public record PayableBalance(
            BigDecimal mfClosing,
            BigDecimal selfClosingAll,
            BigDecimal selfClosingForMf,
            BigDecimal diffAll,
            BigDecimal diffForMf,
            int selfRowCount,
            int selfMfTargetRowCount,
            boolean mfAccountFound
    ) {}
}
