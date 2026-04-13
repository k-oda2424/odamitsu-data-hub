package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TCashbookImportHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TCashbookImportHistoryRepository extends JpaRepository<TCashbookImportHistory, Integer> {
    Optional<TCashbookImportHistory> findByPeriodLabel(String periodLabel);
    Optional<TCashbookImportHistory> findFirstByOrderByProcessedAtDesc();
}
