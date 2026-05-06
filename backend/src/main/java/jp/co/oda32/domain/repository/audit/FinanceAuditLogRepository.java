package jp.co.oda32.domain.repository.audit;

import jp.co.oda32.domain.model.audit.FinanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * T2: 監査証跡 Repository。
 * <p>
 * 検索系は {@link JpaSpecificationExecutor} で動的フィルタ (actor / operation / target_table / 日付範囲) を組む。
 *
 * @since 2026-05-04 (T2)
 */
@Repository
public interface FinanceAuditLogRepository extends JpaRepository<FinanceAuditLog, Long>,
        JpaSpecificationExecutor<FinanceAuditLog> {

    /**
     * フィルタ UI セレクトボックス用に target_table の distinct 一覧を返す。
     * <p>
     * G3-M5: findAll() による全件 JVM 内 distinct を回避し、DB レベルで DISTINCT する
     * (idx_finance_audit_log_target が効くため audit_log 累積でも線形劣化しない)。
     */
    @Query("SELECT DISTINCT a.targetTable FROM FinanceAuditLog a "
            + "WHERE a.targetTable IS NOT NULL ORDER BY a.targetTable")
    List<String> findDistinctTargetTables();

    /**
     * フィルタ UI セレクトボックス用に operation の distinct 一覧を返す。
     * <p>
     * G3-M5: 同上。DB レベル DISTINCT で効率化。
     */
    @Query("SELECT DISTINCT a.operation FROM FinanceAuditLog a "
            + "WHERE a.operation IS NOT NULL ORDER BY a.operation")
    List<String> findDistinctOperations();
}
