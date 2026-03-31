package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 仕入価格マスタ(m_purchase_price)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2020/01/21
 */
public interface MPurchasePriceRepository extends JpaRepository<MPurchasePrice, Integer>, JpaSpecificationExecutor<MPurchasePrice> {
    MPurchasePrice getBySupplierNoAndShopNoAndGoodsNoAndPartnerNo(@Param("supplierNo") Integer supplierNo, @Param("shopNo") Integer shopNo, @Param("goodsNo") Integer goodsNo, @Param("partnerNo") Integer partnerNo);

    MPurchasePrice getByPurchasePriceNo(@Param("purchasePriceNo") Integer purchasePriceNo);

    @Modifying
    @Query(value = "update m_purchase_price set goods_price = mppcp.after_price" +
            " from m_purchase_price_change_plan mppcp" +
            "    join m_supplier ms on mppcp.supplier_code = ms.supplier_code and mppcp.shop_no=ms.shop_no" +
            "    join w_sales_goods wsg on wsg.shop_no=mppcp.shop_no and wsg.goods_code=mppcp.goods_code" +
            " where mppcp.purchase_price_reflect = false" +
            " and mppcp.shop_no=m_purchase_price.shop_no" +
            " and m_purchase_price.supplier_no = ms.supplier_no" +
            " and m_purchase_price.goods_no=wsg.goods_no" +
            " and mppcp.purchase_price_reflect = false" +
            " and mppcp.partner_price_change_plan_created = true" +
            " and mppcp.change_plan_date <= current_date;", nativeQuery = true)
    int reflectPurchasePriceChange();
}
