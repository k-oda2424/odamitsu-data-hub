package jp.co.oda32.domain.repository.smile;

import jp.co.oda32.domain.model.smile.WSmilePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * SMILE支払情報ワークテーブルのリポジトリクラス
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Repository
public interface WSmilePaymentRepository extends JpaRepository<WSmilePayment, WSmilePayment.WSmilePaymentId> {

    /**
     * 伝票日付で絞り込んだSMILE支払情報を取得します
     *
     * @param voucherDate 伝票日付
     * @return 指定された伝票日付に対応するWSmilePaymentのリスト
     */
    List<WSmilePayment> findByVoucherDate(LocalDate voucherDate);

    /**
     * 指定された伝票日付のデータから処理連番のセットを取得します
     *
     * @param voucherDate 伝票日付
     * @return 処理連番のセット
     */
    @Query("SELECT DISTINCT w.processingSerialNumber FROM WSmilePayment w WHERE w.voucherDate = :voucherDate")
    Set<Long> findDistinctProcessingSerialNumbersByVoucherDate(@Param("voucherDate") LocalDate voucherDate);

    /**
     * ワークテーブルをトランケートします
     * トランザクション管理の正しい適用のため、@Transactionalアノテーションを明示的に設定
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRED)
    @Query(value = "TRUNCATE TABLE w_smile_payment", nativeQuery = true)
    void truncateTable();
}