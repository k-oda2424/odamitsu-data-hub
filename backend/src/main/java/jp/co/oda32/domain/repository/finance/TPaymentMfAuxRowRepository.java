package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 買掛仕入MF 補助行 Repository。
 *
 * <p>物理削除→挿入で (shop_no, transaction_month, transfer_date) 単位の洗い替えを行う。
 * 検証済みCSV出力は取引月単位で全送金日分を一括取得し、transfer_date ASC, sequence_no ASC 順で出力する。
 */
@Repository
public interface TPaymentMfAuxRowRepository extends JpaRepository<TPaymentMfAuxRow, Long> {

    /**
     * 同一 (shop, 取引月, 送金日) の既存行を物理削除する。
     * applyVerification の再アップロード時に洗い替えするために呼ぶ。
     *
     * <p>{@code flushAutomatically}/{@code clearAutomatically} を true にすることで、
     * 直後の {@code saveAll} での insert が delete と同一トランザクション内で
     * 正しい順序（DELETE → INSERT）で flush されることを保証する。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TPaymentMfAuxRow r " +
           "WHERE r.shopNo = :shopNo " +
           "AND r.transactionMonth = :transactionMonth " +
           "AND r.transferDate = :transferDate")
    int deleteByShopAndTransactionMonthAndTransferDate(
            @Param("shopNo") Integer shopNo,
            @Param("transactionMonth") LocalDate transactionMonth,
            @Param("transferDate") LocalDate transferDate);

    /**
     * 取引月の全補助行を CSV 出力順で取得。
     */
    List<TPaymentMfAuxRow> findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
            Integer shopNo, LocalDate transactionMonth);
}
