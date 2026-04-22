package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * MF /api/v3/reports/trial_balance_bs のレスポンス。
 * <p>
 * Phase 0 実測で確定した shape (設計書 §1):
 * <pre>
 * {
 *   "columns": ["opening_balance","debit_amount","credit_amount","closing_balance","ratio"],
 *   "end_date": "2026-03-20",
 *   "report_type": "trial_balance_bs",
 *   "rows": [ ... ネスト構造、type=account が leaf ... ]
 * }
 * </pre>
 * sub_account 粒度は含まれない (全 account が leaf)。仕入先別突合は /journals 累積で fallback。
 *
 * @since 2026-04-22 (Phase B)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfTrialBalanceBsResponse(
        List<String> columns,
        @JsonProperty("end_date") String endDate,
        @JsonProperty("report_type") String reportType,
        List<Row> rows
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            String name,
            String type,
            List<BigDecimal> values,
            List<Row> rows
    ) {}

    /** 指定した account 名に一致する leaf (type=account) の Row を深さ優先で探す。見つからなければ null。 */
    public Row findAccount(String accountName) {
        return findAccount(this.rows, accountName);
    }

    private static Row findAccount(List<Row> rows, String name) {
        if (rows == null) return null;
        for (Row r : rows) {
            if ("account".equals(r.type()) && name.equals(r.name())) return r;
            Row nested = findAccount(r.rows(), name);
            if (nested != null) return nested;
        }
        return null;
    }

    /** 全 account leaf をフラット化して返す。 */
    public List<Row> flattenAccounts() {
        List<Row> out = new ArrayList<>();
        collectAccounts(this.rows, out);
        return out;
    }

    private static void collectAccounts(List<Row> rows, List<Row> out) {
        if (rows == null) return;
        for (Row r : rows) {
            if ("account".equals(r.type())) out.add(r);
            collectAccounts(r.rows(), out);
        }
    }

    /**
     * columns 配列のインデックスで closing_balance を取り出す。
     * Phase 0 実測では columns = [opening, debit, credit, closing, ratio] 固定だったが、
     * 将来変わっても安全に取れるよう columns から index を解決。
     */
    public BigDecimal closingOf(Row row) {
        if (row == null || row.values() == null) return BigDecimal.ZERO;
        int idx = columns == null ? 3 : columns.indexOf("closing_balance");
        if (idx < 0) idx = 3;
        if (idx >= row.values().size()) return BigDecimal.ZERO;
        BigDecimal v = row.values().get(idx);
        return v != null ? v : BigDecimal.ZERO;
    }
}
