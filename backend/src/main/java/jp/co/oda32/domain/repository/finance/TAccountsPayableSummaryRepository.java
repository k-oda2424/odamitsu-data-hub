package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsPayableSummaryPK;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 20日締め買掛金テーブルのリポジトリクラス
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Repository
public interface TAccountsPayableSummaryRepository extends JpaRepository<TAccountsPayableSummary, TAccountsPayableSummaryPK>, JpaSpecificationExecutor<TAccountsPayableSummary> {
    /**
     * 指定された取引月に対応するTAccountsPayableSummaryのリストを取得します。
     *
     * @param transactionMonth 取引月
     * @return 取引月に対応するTAccountsPayableSummaryのリスト
     */
    List<TAccountsPayableSummary> findByTransactionMonth(LocalDate transactionMonth);

    TAccountsPayableSummary getByShopNoAndSupplierNoAndTransactionMonthAndTaxRate(int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate);

    List<TAccountsPayableSummary> findByShopNoAndSupplierCodeAndTransactionMonth(Integer shopNo, String supplierCode, LocalDate transactionMonth);

    /**
     * 一括検証用: 複数 supplierCode を 1 クエリで取得し、applyVerification の N+1 を解消する。
     * 呼び出し側で supplierCode ごとに groupingBy する想定。
     */
    List<TAccountsPayableSummary> findByShopNoAndSupplierCodeInAndTransactionMonth(
            Integer shopNo, Collection<String> supplierCodes, LocalDate transactionMonth);

    /**
     * MF仕訳CSV出力対象となる「検証結果=一致 かつ MF出力=ON」の行を取得。
     * 税率別に複数行あるため、呼び出し側で supplier_no 単位に集約する必要がある。
     */
    @Query("SELECT s FROM TAccountsPayableSummary s " +
            "WHERE s.shopNo = :shopNo AND s.transactionMonth = :transactionMonth " +
            "AND s.verificationResult = 1 AND s.mfExportEnabled = true " +
            "ORDER BY s.supplierNo, s.taxRate")
    List<TAccountsPayableSummary> findVerifiedForMfExport(
            @Param("shopNo") Integer shopNo,
            @Param("transactionMonth") LocalDate transactionMonth);
}
