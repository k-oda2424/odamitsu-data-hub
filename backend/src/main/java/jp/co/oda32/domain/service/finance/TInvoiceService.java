package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import jp.co.oda32.domain.specification.finance.TInvoiceSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class TInvoiceService {

    private final TInvoiceRepository tInvoiceRepository;
    private final TInvoiceSpecification tInvoiceSpecification = new TInvoiceSpecification();

    @Autowired
    public TInvoiceService(TInvoiceRepository tInvoiceRepository) {
        this.tInvoiceRepository = tInvoiceRepository;
    }

    /**
     * 請求データを全て取得
     *
     * @return 請求データのリスト
     */
    public List<TInvoice> getAllInvoices() {
        return tInvoiceRepository.findAll();
    }

    /**
     * 特定の請求データを取得
     *
     * @param partnerCode 得意先コード
     * @param closingDate 締め日
     * @return 該当する請求データのリスト
     */
    public List<TInvoice> getInvoicesByPartnerCodeAndClosingDate(String partnerCode, String closingDate) {
        return tInvoiceRepository.findByPartnerCodeAndClosingDate(partnerCode, closingDate);
    }

    /**
     * 新しい請求データを保存
     *
     * @param invoice 保存する請求データ
     * @return 保存された請求データ
     */
    @Transactional
    public TInvoice saveInvoice(TInvoice invoice) {
        return tInvoiceRepository.save(invoice);
    }

    /**
     * 請求データを新規登録または更新
     *
     * @param invoice 保存する請求データ
     * @return 保存された請求データ
     */
    @Transactional
    public TInvoice insert(TInvoice invoice) {
        return tInvoiceRepository.save(invoice);
    }

    /**
     * 請求データを削除
     *
     * @param invoiceId 削除する請求ID
     */
    @Transactional
    public void deleteInvoiceById(Integer invoiceId) {
        tInvoiceRepository.deleteById(invoiceId);
    }

    /**
     * ショップ番号、得意先コード、締め日で請求データを取得
     *
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @param closingDate 締め日（文字列形式 "yyyy/MM/末" または "yyyy/MM/dd"）
     * @return 該当する請求データ（存在しない場合はEmpty）
     */
    public Optional<TInvoice> findByShopNoAndPartnerCodeAndClosingDate(Integer shopNo, String partnerCode, String closingDate) {
        return tInvoiceRepository.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, closingDate);
    }

    public boolean existsByPartnerCodeAndClosingDateAndShopNo(String partnerCode, String closingDate, Integer shopNo) {
        return tInvoiceRepository.existsByPartnerCodeAndClosingDateAndShopNo(partnerCode, closingDate, shopNo);
    }

    /**
     * 得意先コードで請求データを検索
     *
     * @param partnerCode 得意先コード
     * @return 該当する請求データのリスト
     */
    public List<TInvoice> findByPartnerCode(String partnerCode) {
        return tInvoiceRepository.findByPartnerCode(partnerCode);
    }

    /**
     * IDで請求データを1件取得
     *
     * @param invoiceId 請求ID
     * @return 該当する請求データ
     */
    public TInvoice getInvoiceById(Integer invoiceId) {
        return tInvoiceRepository.findById(invoiceId).orElse(null);
    }

    public List<TInvoice> findByIds(List<Integer> invoiceIds) {
        return tInvoiceRepository.findAllById(invoiceIds);
    }

    @Transactional
    public List<TInvoice> saveAll(List<TInvoice> invoices) {
        return tInvoiceRepository.saveAll(invoices);
    }

    /**
     * 締月、ショップ番号、得意先コードで請求データを検索する
     * Specificationの代わりにStartingWithを使用した実装
     *
     * @param closingDate 締月（yyyy/MM形式で検索、例：2025/04）
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @return 検索条件に合致する請求データのリスト
     */
    public List<TInvoice> findBySpecification(String closingDate, Integer shopNo, String partnerCode) {
        return findByDetailedSpecification(closingDate, shopNo, partnerCode, null, null, null);
    }

    /**
     * 詳細検索条件に基づいて請求データを検索する
     *
     * @param closingDate 締月（yyyy/MM形式で検索、例：2025/04）
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @param partnerName 顧客名
     * @param minAmount   最小請求額
     * @param maxAmount   最大請求額
     * @return 検索条件に合致する請求データのリスト（締日降順）
     */
    public List<TInvoice> findByDetailedSpecification(
            String closingDate,
            Integer shopNo,
            String partnerCode,
            String partnerName,
            Double minAmount,
            Double maxAmount) {

        Specification<TInvoice> spec = Specification
                .where(tInvoiceSpecification.closingDateContains(closingDate))
                .and(tInvoiceSpecification.shopNoContains(shopNo))
                .and(tInvoiceSpecification.partnerCodeContains(partnerCode))
                .and(tInvoiceSpecification.partnerNameContains(partnerName))
                .and(tInvoiceSpecification.currentBillingAmountContains(minAmount, maxAmount));

        // 締日降順でソート
        return tInvoiceRepository.findAll(spec, org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "closingDate"
        ));
    }
}