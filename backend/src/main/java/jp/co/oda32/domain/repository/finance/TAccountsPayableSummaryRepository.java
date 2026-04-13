package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsPayableSummaryPK;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}
