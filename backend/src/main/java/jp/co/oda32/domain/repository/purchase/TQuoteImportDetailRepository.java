package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.TQuoteImportDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface TQuoteImportDetailRepository extends JpaRepository<TQuoteImportDetail, Integer> {
    List<TQuoteImportDetail> findByQuoteImportIdAndStatusOrderByRowNo(Integer quoteImportId, String status);
    List<TQuoteImportDetail> findByQuoteImportIdAndStatusNotOrderByRowNo(Integer quoteImportId, String status);
    int countByQuoteImportIdAndStatus(Integer quoteImportId, String status);
    List<TQuoteImportDetail> findByQuoteImportIdOrderByRowNo(Integer quoteImportId);
    @Modifying
    void deleteByQuoteImportId(Integer quoteImportId);

    @Query("SELECT d.quoteImportId AS importId, COUNT(d) AS cnt " +
           "FROM TQuoteImportDetail d WHERE d.status = :status AND d.quoteImportId IN :importIds " +
           "GROUP BY d.quoteImportId")
    List<Object[]> countByImportIdsAndStatus(@Param("importIds") List<Integer> importIds, @Param("status") String status);
}
