package jp.co.oda32.domain.repository.smile;

import jp.co.oda32.domain.model.smile.TSmilePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * SMILE支払情報テーブルのリポジトリクラス
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Repository
public interface TSmilePaymentRepository extends JpaRepository<TSmilePayment, TSmilePayment.TSmilePaymentId> {
    /**
     * 伝票日付で絞り込んだSMILE支払情報を取得します
     *
     * @param voucherDate 伝票日付
     * @return 指定された伝票日付に対応するTSmilePaymentのリスト
     */
    List<TSmilePayment> findByVoucherDate(LocalDate voucherDate);

    /**
     * 年月度で絞り込んだSMILE支払情報を取得します
     *
     * @param yearMonth 年月度
     * @return 指定された年月度に対応するTSmilePaymentのリスト
     */
    List<TSmilePayment> findByYearMonth(String yearMonth);

    /**
     * 仕入先コードで絞り込んだSMILE支払情報を取得します
     *
     * @param supplierCode 仕入先コード
     * @return 指定された仕入先コードに対応するTSmilePaymentのリスト
     */
    List<TSmilePayment> findBySupplierCode(String supplierCode);

    /**
     * 仕入先コードと年月度で絞り込んだSMILE支払情報を取得します
     *
     * @param supplierCode 仕入先コード
     * @param yearMonth    年月度
     * @return 指定された条件に対応するTSmilePaymentのリスト
     */
    List<TSmilePayment> findBySupplierCodeAndYearMonth(String supplierCode, String yearMonth);

    /**
     * 指定された伝票日付で、処理連番が指定されたセットに含まれないデータを削除します
     *
     * @param voucherDate             伝票日付
     * @param processingSerialNumbers 処理連番のセット
     * @return 削除された行数
     */
    @Modifying
    @Query("DELETE FROM TSmilePayment t WHERE t.voucherDate = :voucherDate AND t.processingSerialNumber NOT IN :processingSerialNumbers")
    int deleteByVoucherDateAndProcessingSerialNumberNotIn(
            @Param("voucherDate") LocalDate voucherDate,
            @Param("processingSerialNumbers") Set<Long> processingSerialNumbers);
}
