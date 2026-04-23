package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.embeddable.TConsistencyReviewPK;
import jp.co.oda32.domain.model.finance.TConsistencyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 整合性レポート 差分確認履歴 Repository。
 *
 * @since 2026-04-23
 */
@Repository
public interface TConsistencyReviewRepository extends JpaRepository<TConsistencyReview, TConsistencyReviewPK> {

    /**
     * 期間内の全 review を取得し、reviewer 名も同時取得する (N+1 回避)。
     * 返り値配列: [0] TConsistencyReview, [1] reviewer name (String, nullable)
     */
    @Query("SELECT r, u.userName FROM TConsistencyReview r " +
           "LEFT JOIN MLoginUser u ON u.loginUserNo = r.reviewedBy " +
           "WHERE r.pk.shopNo = :shopNo " +
           "  AND r.pk.transactionMonth BETWEEN :fromMonth AND :toMonth")
    List<Object[]> findWithReviewerNameForPeriod(
            @Param("shopNo") Integer shopNo,
            @Param("fromMonth") LocalDate fromMonth,
            @Param("toMonth") LocalDate toMonth);

    Optional<TConsistencyReview> findByPkShopNoAndPkEntryTypeAndPkEntryKeyAndPkTransactionMonth(
            Integer shopNo, String entryType, String entryKey, LocalDate transactionMonth);
}
