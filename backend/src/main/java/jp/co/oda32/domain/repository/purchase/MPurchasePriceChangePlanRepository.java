package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

/**
 * 仕入価格変更予定マスタ(m_purchase_price_change_plan)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2022/10/13
 */
public interface MPurchasePriceChangePlanRepository extends JpaRepository<MPurchasePriceChangePlan, Integer>, JpaSpecificationExecutor<MPurchasePriceChangePlan> {

    @Modifying
    @Query(value = "update m_purchase_price_change_plan set purchase_price_reflect = true" +
            " from m_purchase_price mpp" +
            "         join m_supplier ms on mpp.supplier_no = ms.supplier_no" +
            "         join w_sales_goods wsg on wsg.shop_no=mpp.shop_no and wsg.goods_no=mpp.goods_no" +
            " where m_purchase_price_change_plan.purchase_price_reflect = false" +
            "  and m_purchase_price_change_plan.shop_no=mpp.shop_no" +
            "  and mpp.supplier_no = ms.supplier_no" +
            "  and m_purchase_price_change_plan.goods_code=wsg.goods_code" +
            "  and mpp.goods_price=m_purchase_price_change_plan.after_price" +
            "  and m_purchase_price_change_plan.change_plan_date <= current_date;", nativeQuery = true)
    int updateReflectComplete();

    List<MPurchasePriceChangePlan> findByPurchasePriceReflectFalse();

    void deleteByShopNoAndGoodsCodeAndChangePlanDate(Integer shopNo, String goodsCode, LocalDate changePlanDate);
}
