package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import jp.co.oda32.domain.specification.finance.TInvoiceSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 請求 (TInvoice) ドメインサービス。
 *
 * <ul>
 *   <li>SF-09: {@link #getInvoiceById(Integer)} を Optional 化</li>
 *   <li>SF-10: 未使用メソッド (getAllInvoices / findBySpecification / insert / deleteInvoiceById) を削除</li>
 *   <li>SF-11: TInvoiceSpecification を {@code private static final} 化 (field-level new 撤去)</li>
 *   <li>SF-18: クラスレベル {@code @Transactional(readOnly=true)} + 書込みメソッドは override</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TInvoiceService {

    private static final TInvoiceSpecification SPEC = new TInvoiceSpecification();

    private final TInvoiceRepository tInvoiceRepository;

    /**
     * 特定の請求データを取得 (得意先コード + 締め日)。
     *
     * @return 該当する請求データのリスト (空可)
     */
    public List<TInvoice> getInvoicesByPartnerCodeAndClosingDate(String partnerCode, String closingDate) {
        return tInvoiceRepository.findByPartnerCodeAndClosingDate(partnerCode, closingDate);
    }

    /**
     * SF-09: 請求データを 1 件 Optional で取得。
     */
    public Optional<TInvoice> getInvoiceById(Integer invoiceId) {
        return tInvoiceRepository.findById(invoiceId);
    }

    @Transactional
    public TInvoice saveInvoice(TInvoice invoice) {
        return tInvoiceRepository.save(invoice);
    }

    /**
     * ショップ番号、得意先コード、締め日で請求データを取得。
     *
     * @return 該当する請求データ (Optional)
     */
    public Optional<TInvoice> findByShopNoAndPartnerCodeAndClosingDate(
            Integer shopNo, String partnerCode, String closingDate) {
        return tInvoiceRepository.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, closingDate);
    }

    public boolean existsByPartnerCodeAndClosingDateAndShopNo(
            String partnerCode, String closingDate, Integer shopNo) {
        return tInvoiceRepository.existsByPartnerCodeAndClosingDateAndShopNo(partnerCode, closingDate, shopNo);
    }

    /**
     * 得意先コードで請求データを検索。
     */
    public List<TInvoice> findByPartnerCode(String partnerCode) {
        return tInvoiceRepository.findByPartnerCode(partnerCode);
    }

    public List<TInvoice> findByIds(List<Integer> invoiceIds) {
        return tInvoiceRepository.findAllById(invoiceIds);
    }

    @Transactional
    public List<TInvoice> saveAll(List<TInvoice> invoices) {
        return tInvoiceRepository.saveAll(invoices);
    }

    /**
     * SF-21: 四半期合算用に 1 クエリで複数 closing_date を一括取得。
     */
    public List<TInvoice> findByShopNoAndPartnerCodeAndClosingDateIn(
            Integer shopNo, String partnerCode, List<String> closingDates) {
        if (closingDates == null || closingDates.isEmpty()) {
            return List.of();
        }
        return tInvoiceRepository.findByShopNoAndPartnerCodeAndClosingDateIn(shopNo, partnerCode, closingDates);
    }

    /**
     * 詳細検索条件に基づいて請求データを検索する (締日降順)。
     */
    public List<TInvoice> findByDetailedSpecification(
            String closingDate,
            Integer shopNo,
            String partnerCode,
            String partnerName,
            Double minAmount,
            Double maxAmount) {

        Specification<TInvoice> spec = Specification
                .where(SPEC.closingDateContains(closingDate))
                .and(SPEC.shopNoContains(shopNo))
                .and(SPEC.partnerCodeContains(partnerCode))
                .and(SPEC.partnerNameContains(partnerName))
                .and(SPEC.currentBillingAmountContains(minAmount, maxAmount));

        return tInvoiceRepository.findAll(spec, org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "closingDate"
        ));
    }
}
