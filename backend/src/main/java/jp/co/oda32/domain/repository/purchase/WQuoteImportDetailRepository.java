package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.WQuoteImportDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WQuoteImportDetailRepository extends JpaRepository<WQuoteImportDetail, Integer> {
    List<WQuoteImportDetail> findByQuoteImportIdOrderByRowNo(Integer quoteImportId);
    int countByQuoteImportId(Integer quoteImportId);
    void deleteByQuoteImportId(Integer quoteImportId);
}
