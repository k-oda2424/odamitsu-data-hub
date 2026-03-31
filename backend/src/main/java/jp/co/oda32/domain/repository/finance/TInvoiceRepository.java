package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TInvoiceRepository extends JpaRepository<TInvoice, Integer>, JpaSpecificationExecutor<TInvoice> {

    // 特定の得意先コードで締め日を指定して検索
    List<TInvoice> findByPartnerCodeAndClosingDate(String partnerCode, String closingDate);

    // ショップ番号、得意先コード、締め日で検索
    Optional<TInvoice> findByShopNoAndPartnerCodeAndClosingDate(Integer shopNo, String partnerCode, String closingDate);

    // 締め日で請求データを検索
    List<TInvoice> findByClosingDate(String closingDate);

    // 得意先コードで検索するメソッドを追加
    List<TInvoice> findByPartnerCode(String partnerCode);

    boolean existsByPartnerCodeAndClosingDateAndShopNo(String partnerCode, String closingDate, Integer shopNo);
}