package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 得意先価格変更予定マスタ(m_partner_goods_price_change_plan)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2022/10/13
 */
public interface MPartnerGoodsPriceChangePlanRepository extends JpaRepository<MPartnerGoodsPriceChangePlan, Integer>, JpaSpecificationExecutor<MPartnerGoodsPriceChangePlan> {
    @Modifying
    @Query(value = "update m_partner_goods_price_change_plan " +
            "set goods_no = (select wsg.goods_no " +
            "                from w_sales_goods wsg " +
            "                where m_partner_goods_price_change_plan.shop_no = wsg.shop_no " +
            "                  and m_partner_goods_price_change_plan.goods_code = wsg.goods_code) " +
            "where goods_no is null", nativeQuery = true)
    Integer updateGoodsNo();

    @Modifying
    @Query(value = "update m_partner_goods_price_change_plan set partner_price_reflect = true" +
            " from m_partner_goods mpg" +
            " where after_price = mpg.goods_price" +
            "  and mpg.company_no=m_partner_goods_price_change_plan.company_no" +
            "  and mpg.shop_no=m_partner_goods_price_change_plan.shop_no" +
            "  and mpg.goods_no=m_partner_goods_price_change_plan.goods_no" +
            "  and m_partner_goods_price_change_plan.change_plan_date <= current_date" +
            "  and m_partner_goods_price_change_plan.estimate_created = true" +
            "  and partner_price_reflect = false;", nativeQuery = true)
    int updateReflectComplete();
}
