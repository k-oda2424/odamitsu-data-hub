package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.TQuoteImportDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TQuoteImportDetailRepository extends JpaRepository<TQuoteImportDetail, Integer> {
    List<TQuoteImportDetail> findByQuoteImportIdAndStatusOrderByRowNo(Integer quoteImportId, String status);
    List<TQuoteImportDetail> findByQuoteImportIdAndStatusNotOrderByRowNo(Integer quoteImportId, String status);
    int countByQuoteImportIdAndStatus(Integer quoteImportId, String status);
    List<TQuoteImportDetail> findByQuoteImportIdOrderByRowNo(Integer quoteImportId);
    void deleteByQuoteImportId(Integer quoteImportId);
}
