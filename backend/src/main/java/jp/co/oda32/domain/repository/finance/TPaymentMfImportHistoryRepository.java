package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TPaymentMfImportHistoryRepository extends JpaRepository<TPaymentMfImportHistory, Integer> {

    List<TPaymentMfImportHistory> findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(Integer shopNo, String delFlg);

    /**
     * P1-08 L1: 同一ハッシュの過去取込検知。最新取込が先頭に来る。
     */
    List<TPaymentMfImportHistory> findBySourceFileHashAndDelFlgOrderByAddDateTimeDesc(
            String sourceFileHash, String delFlg);

    /**
     * P1-08 L2: 同 (shop, transfer_date) で applyVerification 実行済の最新行を取得。
     * non-empty なら preview で「再確定で確定値上書き」警告対象。
     */
    Optional<TPaymentMfImportHistory> findFirstByShopNoAndTransferDateAndAppliedAtNotNullAndDelFlgOrderByAppliedAtDesc(
            Integer shopNo, LocalDate transferDate, String delFlg);
}
