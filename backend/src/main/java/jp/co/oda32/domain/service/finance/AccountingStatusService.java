package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.batch.BatchJobCatalog;
import jp.co.oda32.domain.repository.finance.TCashbookImportHistoryRepository;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import jp.co.oda32.dto.finance.AccountingStatusResponse;
import jp.co.oda32.dto.finance.AccountingStatusResponse.BatchJobStatus;
import jp.co.oda32.dto.finance.AccountingStatusResponse.CashbookHistoryRow;
import jp.co.oda32.dto.finance.AccountingStatusResponse.InvoiceLatestRow;
import jp.co.oda32.exception.FinanceInternalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 経理ワークフロー画面のステータス取得サービス。
 * <p>
 * SF-H02: ワークフローステップ 4 (売掛集計) の {@code accountsReceivableSummary} を監視対象に追加。
 * SF-H05: バッチ実行履歴の取得を {@link JobExplorer} 経由に統一 (BatchController と同一機構)。
 * SF-H06: 戻り型を型安全な {@link AccountingStatusResponse} record に置換。
 * SF-H07: silent swallow を廃止し SQL grammar 例外は上位へ伝播。
 * SF-H08: 既存 Repository ({@link TCashbookImportHistoryRepository}) 経由化で NativeQuery を削減。
 *
 * @since 2026-04-13 (initial)
 * @since 2026-05-04 (SF-H02 / SF-H05 / SF-H06 / SF-H07 / SF-H08)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountingStatusService {

    private final TCashbookImportHistoryRepository cashbookImportHistoryRepository;
    private final TInvoiceRepository tInvoiceRepository;
    private final JobExplorer jobExplorer;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public AccountingStatusResponse getStatus() {
        return new AccountingStatusResponse(
                queryCashbookHistory(),
                querySingle("SELECT MAX(denpyou_hizuke) FROM w_smile_purchase_output_file"),
                querySingle("SELECT MAX(import_date) FROM t_smile_payment"),
                queryInvoiceLatest(),
                querySingle("SELECT MAX(transaction_month) FROM t_accounts_payable_summary"),
                queryBatchJobs()
        );
    }

    private List<CashbookHistoryRow> queryCashbookHistory() {
        // SF-H08: NativeQuery 直叩きを廃止し Repository 経由で最新 3 件取得。
        try {
            return cashbookImportHistoryRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(0, 3,
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC, "processedAt")))
                    .stream()
                    .map(h -> new CashbookHistoryRow(
                            h.getPeriodLabel(),
                            h.getFileName(),
                            h.getProcessedAt() == null ? null : h.getProcessedAt().toString(),
                            h.getRowCount(),
                            BigDecimal.valueOf(h.getTotalIncome()),
                            BigDecimal.valueOf(h.getTotalPayment())
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("現金出納帳履歴の取得に失敗", e);
            return List.of();
        }
    }

    private List<InvoiceLatestRow> queryInvoiceLatest() {
        try {
            // SF-H01 + MJ-H01: ショップ別最新締日 (相関サブクエリ) を Repository 経由で取得。
            // NativeQuery 直叩きは TInvoiceRepository#findLatestClosingDatePerShop() に移管済。
            // 旧 SF-H08 実装が cashbook 側のみ Repository 化していた残課題を解消し、SF-H01 と完全等価。
            List<Object[]> rows = tInvoiceRepository.findLatestClosingDatePerShop();
            return rows.stream().map(row -> new InvoiceLatestRow(
                    toInteger(row[0]),
                    toString(row[1]),
                    toLong(row[2])
            )).toList();
        } catch (Exception e) {
            log.warn("請求データステータスの取得に失敗", e);
            return List.of();
        }
    }

    private List<BatchJobStatus> queryBatchJobs() {
        // SF-H05: NativeQuery + IN リストを廃止し、JobExplorer + BatchJobCatalog に統一。
        // BatchController#getJobStatus と同じパターン (findJobInstancesByJobName + 最新取得) を使用。
        // SF-H02: monitored=true に accountsReceivableSummary を含めることで売掛側の監視抜けを解消。
        List<BatchJobStatus> result = new ArrayList<>();
        for (String jobName : BatchJobCatalog.monitoredJobNames()) {
            try {
                List<JobInstance> instances = jobExplorer.findJobInstancesByJobName(jobName, 0, 1);
                if (instances.isEmpty()) continue;
                List<JobExecution> executions = jobExplorer.getJobExecutions(instances.get(0));
                if (executions.isEmpty()) continue;
                JobExecution latest = executions.stream()
                        .max(Comparator.comparing(
                                (JobExecution e) -> e.getCreateTime(),
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElse(executions.get(0));
                result.add(new BatchJobStatus(
                        jobName,
                        latest.getStatus() == null ? null : latest.getStatus().toString(),
                        latest.getExitStatus() == null ? null : latest.getExitStatus().getExitCode(),
                        toIsoString(latest.getStartTime()),
                        toIsoString(latest.getEndTime())
                ));
            } catch (Exception e) {
                log.warn("バッチ実行履歴の取得に失敗: jobName={}", jobName, e);
            }
        }
        result.sort(Comparator.comparing(BatchJobStatus::jobName));
        return result;
    }

    private String querySingle(String sql) {
        // SF-H07: silent swallow を廃止。SQLGrammarException (テーブル定義変更等の構造異常) は
        // 再 throw して上位へ伝播。SQLException (接続障害) や NoResultException は warn ログを残して null を返す。
        try {
            Object result = entityManager.createNativeQuery(sql).getSingleResult();
            return result != null ? result.toString() : null;
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        } catch (jakarta.persistence.PersistenceException e) {
            Throwable cause = e.getCause();
            if (cause instanceof org.hibernate.exception.SQLGrammarException) {
                log.warn("AccountingStatus SQL grammar error: sql={}", sql, e);
                // T5: 内部 SQL エラー (機微情報なしだが内部実装詳細)。
                throw new FinanceInternalException("経理ステータス SQL の実行に失敗しました", e);
            }
            log.warn("AccountingStatus SQL persistence error: sql={}", sql, e);
            return null;
        } catch (Exception e) {
            log.warn("AccountingStatus SQL unexpected error: sql={}", sql, e);
            return null;
        }
    }

    private static String toIsoString(LocalDateTime dt) {
        return dt == null ? null : dt.toString();
    }

    private static String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private static Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return null; }
    }
}
