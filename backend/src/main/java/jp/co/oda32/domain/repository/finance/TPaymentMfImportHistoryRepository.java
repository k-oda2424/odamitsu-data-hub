package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TPaymentMfImportHistoryRepository extends JpaRepository<TPaymentMfImportHistory, Integer> {

    List<TPaymentMfImportHistory> findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(Integer shopNo, String delFlg);
}
