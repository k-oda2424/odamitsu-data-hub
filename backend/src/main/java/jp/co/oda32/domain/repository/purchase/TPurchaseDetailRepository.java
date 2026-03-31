package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 仕入(t_purchase_detail)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/06/02
 */
public interface TPurchaseDetailRepository extends JpaRepository<TPurchaseDetail, Integer>, JpaSpecificationExecutor<TPurchaseDetail> {
    List<TPurchaseDetail> findByPurchaseNo(@Param("purchaseNo") Integer purchaseNo);

    List<TPurchaseDetail> findByStockProcessFlg(@Param("stockProcessFlg") String stockProcessFlg);

    TPurchaseDetail getByPurchaseNoAndPurchaseDetailNo(@Param("purchaseNo") Integer purchaseNo, @Param("purchaseDetailNo") Integer purchaseDetailNo);

    @Query("select tpd from TPurchaseDetail tpd" +
            " join TPurchase tp on tpd.purchaseNo = tp.purchaseNo" +
            " where not exists(" +
            "        select 'X' " +
            "        from TPurchaseDetail pd" +
            "                 join TPurchase p on pd.purchaseNo = p.purchaseNo" +
            "        where tpd.goodsCode = pd.goodsCode" +
            "          and tp.purchaseDate < p.purchaseDate" +
            "    )" +
            " and tpd.goodsNo is not null" +
            " and (tpd.purchasePriceReflect = '0' or tpd.purchasePriceReflect is null)" +
            " and (tpd.goodsNum is not null and tpd.goodsNum > 0)" +
            " and (tpd.goodsPrice is not null and tpd.goodsPrice > 0)" +
            " order by tpd.goodsCode")
    List<TPurchaseDetail> findLatestPurchasePrice();

    @Query(value = "SELECT tpd.* FROM t_purchase_detail tpd " +
            "WHERE EXISTS (" +
            "    SELECT 1 FROM w_smile_order_output_file wsoof " +
            "    WHERE wsoof.shori_renban = tpd.ext_purchase_no" +
            "    and wsoof.shop_no = tpd.shop_no) " +
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM w_smile_order_output_file wsoof " +
            "    WHERE wsoof.shori_renban = tpd.ext_purchase_no " +
            "      AND wsoof.gyou = tpd.purchase_detail_no " +
            "      AND wsoof.shop_no = tpd.shop_no" +
            ") " +
            "ORDER BY tpd.ext_purchase_no",
            nativeQuery = true)
    List<TPurchaseDetail> findDeletTPurchaseDetailList();

    @Modifying
    @Query("update TPurchaseDetail set "
            + " purchasePriceReflect = '1'"
            + " where purchasePriceReflect = '0' or purchasePriceReflect is null")
    Integer updateAllPurchasePriceReflect();

    @Transactional
    @Modifying
    @Query("delete from TPurchaseDetail"
            + " where purchaseNo = :purchaseNo")
    Integer deletePurchaseDetailByPurchaseNo(@Param("purchaseNo") Integer purchaseNo);
}
