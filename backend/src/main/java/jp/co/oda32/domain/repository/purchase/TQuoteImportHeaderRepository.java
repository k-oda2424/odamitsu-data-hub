package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.TQuoteImportHeader;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TQuoteImportHeaderRepository extends JpaRepository<TQuoteImportHeader, Integer> {
    List<TQuoteImportHeader> findByDelFlgOrderByAddDateTimeDesc(String delFlg);
}
