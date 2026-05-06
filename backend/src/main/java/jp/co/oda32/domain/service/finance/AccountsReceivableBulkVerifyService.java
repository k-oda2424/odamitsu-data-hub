package jp.co.oda32.domain.service.finance;

import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.batch.finance.service.AccountsReceivableCutoffReconciler;
import jp.co.oda32.batch.finance.service.InvoiceVerifier;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 売掛金の一括検証 (bulkVerify) を 1 トランザクションに閉じ込める Service。
 * <p>
 * 旧実装は Controller が以下を直接呼び出しており、{@code Reconciler} 内部の {@code @Transactional} と
 * {@code summaryService.saveAll} の {@code @Transactional} が独立 tx で動いていたため、
 * 検証保存中に例外が起きると Reconciler 結果のみコミット済みという「部分コミット破綻」が発生していた。
 * <p>
 * 本 Service の {@code execute} は親 {@code @Transactional} を持つため、Reconciler / saveAll の
 * 内側 tx (REQUIRED) は親 tx に乗り、全工程のロールバック整合性を保つ (SF-E05)。
 *
 * @since 2026-05-04 (SF-E05)
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class AccountsReceivableBulkVerifyService {

    private final TAccountsReceivableSummaryService summaryService;
    private final AccountsReceivableCutoffReconciler cutoffReconciler;
    private final InvoiceVerifier invoiceVerifier;

    /**
     * 1 tx で reconcile + reload + verify + saveAll を実行する。
     *
     * @param effectiveShopNo 検索対象 shop_no (admin で全店舗の場合は null)
     * @param fromDate        検索期間 (含む)
     * @param toDate          検索期間 (含む)
     * @return 検証結果と reconcile 件数を保持する {@link Result}
     */
    @Transactional
    @AuditLog(table = "t_accounts_receivable_summary", operation = "bulk_verify",
            pkExpression = "{'shopNo': #a0, 'fromDate': #a1, 'toDate': #a2}",
            captureArgsAsAfter = true)
    public Result execute(Integer effectiveShopNo, LocalDate fromDate, LocalDate toDate) {
        List<TAccountsReceivableSummary> summaries = summaryService
                .findByShopAndDateRange(effectiveShopNo, fromDate, toDate);

        // 事前パス: 請求書 closing_date と AR transaction_month が食い違う得意先を再集計。
        AccountsReceivableCutoffReconciler.ReconcileResult reconcile =
                cutoffReconciler.reconcile(summaries, fromDate, toDate);
        if (reconcile.getReconciledPartners() > 0) {
            // 再集計で AR が変わっているので再ロード
            summaries = summaryService.findByShopAndDateRange(effectiveShopNo, fromDate, toDate);
            log.info("bulkVerify: 自動再集計 partners={} inserted={} deleted={}",
                    reconcile.getReconciledPartners(), reconcile.getInsertedRows(), reconcile.getDeletedRows());
        }

        InvoiceVerificationSummary result = invoiceVerifier.verify(summaries, toDate);

        // 1 tx で saveAll: 親 @Transactional 配下のため部分コミットにならない。
        summaryService.saveAll(summaries);

        return new Result(result, reconcile);
    }

    @Getter
    public static class Result {
        private final InvoiceVerificationSummary verification;
        private final AccountsReceivableCutoffReconciler.ReconcileResult reconcile;

        public Result(InvoiceVerificationSummary verification,
                      AccountsReceivableCutoffReconciler.ReconcileResult reconcile) {
            this.verification = verification;
            this.reconcile = reconcile;
        }
    }
}
