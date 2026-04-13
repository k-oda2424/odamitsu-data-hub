package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AccountingStatusService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        status.put("cashbookHistory", queryCashbookHistory());
        status.put("smilePurchaseLatestDate", querySingle("SELECT MAX(denpyou_hizuke) FROM w_smile_purchase_output_file"));
        status.put("smilePaymentLatestDate", querySingle("SELECT MAX(import_date) FROM t_smile_payment"));
        status.put("invoiceLatest", queryInvoiceLatest());
        status.put("accountsPayableLatestMonth", querySingle("SELECT MAX(transaction_month) FROM t_accounts_payable_summary"));
        status.put("batchJobs", queryBatchJobs());

        return status;
    }

    private List<Map<String, Object>> queryCashbookHistory() {
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT period_label, file_name, processed_at, row_count, total_income, total_payment " +
                    "FROM t_cashbook_import_history ORDER BY processed_at DESC LIMIT 3")
                    .getResultList();
            return rows.stream().map(row -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("periodLabel", toString(row[0]));
                m.put("fileName", toString(row[1]));
                m.put("processedAt", toString(row[2]));
                m.put("rowCount", row[3]);
                m.put("totalIncome", row[4]);
                m.put("totalPayment", row[5]);
                return m;
            }).toList();
        } catch (Exception e) {
            log.warn("現金出納帳履歴の取得に失敗", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> queryInvoiceLatest() {
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT shop_no, closing_date, COUNT(*) as cnt FROM t_invoice " +
                    "WHERE closing_date = (SELECT MAX(closing_date) FROM t_invoice) " +
                    "GROUP BY shop_no, closing_date ORDER BY shop_no")
                    .getResultList();
            return rows.stream().map(row -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("shopNo", row[0]);
                m.put("closingDate", toString(row[1]));
                m.put("count", row[2]);
                return m;
            }).toList();
        } catch (Exception e) {
            log.warn("請求データステータスの取得に失敗", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> queryBatchJobs() {
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT ji.job_name, je.status, je.exit_code, je.start_time, je.end_time " +
                    "FROM batch_job_execution je " +
                    "JOIN batch_job_instance ji ON je.job_instance_id = ji.job_instance_id " +
                    "WHERE ji.job_name IN ('purchaseFileImport','smilePaymentImport'," +
                    "'accountsPayableAggregation','accountsPayableVerification'," +
                    "'accountsPayableSummary','purchaseJournalIntegration','salesJournalIntegration') " +
                    "AND je.create_time = (SELECT MAX(je2.create_time) FROM batch_job_execution je2 " +
                    "JOIN batch_job_instance ji2 ON je2.job_instance_id = ji2.job_instance_id " +
                    "WHERE ji2.job_name = ji.job_name) " +
                    "ORDER BY ji.job_name")
                    .getResultList();
            return rows.stream().map(row -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("jobName", row[0]);
                m.put("status", row[1]);
                m.put("exitCode", row[2]);
                m.put("startTime", toString(row[3]));
                m.put("endTime", toString(row[4]));
                return m;
            }).toList();
        } catch (Exception e) {
            log.warn("バッチ実行履歴の取得に失敗", e);
            return List.of();
        }
    }

    private String querySingle(String sql) {
        try {
            Object result = entityManager.createNativeQuery(sql).getSingleResult();
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
