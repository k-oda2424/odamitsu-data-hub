package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * SF-12: ショップ番号と締日で一括取得 (Excel UPSERT 用、既存無名 Specification の置換)。
     */
    List<TInvoice> findByShopNoAndClosingDate(Integer shopNo, String closingDate);

    /**
     * SF-21: 四半期合算用に 1 クエリで複数 closing_date を取得。
     */
    List<TInvoice> findByShopNoAndPartnerCodeAndClosingDateIn(
            Integer shopNo, String partnerCode, List<String> closingDates);

    /**
     * MJ-H01 (SF-H08 follow-up): 経理ワークフロー画面の請求データステータス用。
     * <p>ショップ別に「最新の closing_date」を取得し、その日の件数を返す。
     * 旧実装 (AccountingStatusService#queryInvoiceLatest) の NativeQuery を Repository 経由化。
     * SF-H01 で適用したショップ別 MAX (相関サブクエリ) と完全等価。
     *
     * @return Object[3]: [shop_no (Integer), closing_date (String), cnt (Long)] のリスト
     */
    @Query(nativeQuery = true, value = """
            SELECT i.shop_no, i.closing_date, COUNT(*) AS cnt
            FROM t_invoice i
            WHERE i.closing_date = (SELECT MAX(i2.closing_date) FROM t_invoice i2 WHERE i2.shop_no = i.shop_no)
            GROUP BY i.shop_no, i.closing_date
            ORDER BY i.shop_no
            """)
    List<Object[]> findLatestClosingDatePerShop();
}