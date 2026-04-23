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

    /**
     * 買掛帳 API 用: 指定 supplier の期間内 summary を取得 (tax_rate 昇順)。
     * 設計書: claudedocs/design-accounts-payable-ledger.md §6
     *
     * @since 2026-04-22 (買掛帳画面)
     */
    List<TAccountsPayableSummary> findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
            Integer shopNo, Integer supplierNo, LocalDate fromMonth, LocalDate toMonth);

    /**
     * 買掛帳 API の期間外前月 continuity 判定用: 指定月の summary を取得。
     */
    List<TAccountsPayableSummary> findByShopNoAndSupplierNoAndTransactionMonth(
            Integer shopNo, Integer supplierNo, LocalDate transactionMonth);

    /**
     * 整合性検出 API 用: supplier 指定無しで期間内の全 row を取得 (R9 反映)。
     * 呼び出し側で supplier × 月 に index 化する。
     */
    List<TAccountsPayableSummary> findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc(
            Integer shopNo, LocalDate fromMonth, LocalDate toMonth);

    /**
     * 軸 D supplier 累積残 / 軸 E ヘルスチェック用: shop の最新 transaction_month を取得。
     */
    @Query("SELECT MAX(s.transactionMonth) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo")
    java.util.Optional<LocalDate> findLatestTransactionMonth(@Param("shopNo") Integer shopNo);

    /**
     * 軸 E ヘルスチェック用: 指定 shop の negative closing 行数。
     */
    @Query("SELECT COUNT(s) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo " +
            "AND (COALESCE(s.openingBalanceTaxIncluded, 0) + " +
            "     CASE WHEN s.verifiedManually = true AND s.verifiedAmount IS NOT NULL " +
            "          THEN s.verifiedAmount ELSE COALESCE(s.taxIncludedAmountChange, 0) END - " +
            "     COALESCE(s.paymentAmountSettledTaxIncluded, 0)) < 0")
    long countNegativeClosings(@Param("shopNo") Integer shopNo);

    /**
     * 軸 E ヘルスチェック用: 指定 shop × 指定月 の未検証行数。
     */
    long countByShopNoAndTransactionMonthAndVerificationResult(Integer shopNo, LocalDate transactionMonth, Integer verificationResult);

    /**
     * 軸 E ヘルスチェック用: 指定 shop × 指定月 の total / mfExportEnabled 行数。
     */
    long countByShopNoAndTransactionMonth(Integer shopNo, LocalDate transactionMonth);

    long countByShopNoAndTransactionMonthAndMfExportEnabled(Integer shopNo, LocalDate transactionMonth, Boolean mfExportEnabled);
}
