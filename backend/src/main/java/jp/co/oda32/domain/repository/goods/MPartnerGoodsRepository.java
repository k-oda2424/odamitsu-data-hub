package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.MPartnerGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 得意先商品マスタ(m_partner_goods)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/23
 */
public interface MPartnerGoodsRepository extends JpaRepository<MPartnerGoods, Integer>, JpaSpecificationExecutor<MPartnerGoods> {
    List<MPartnerGoods> findByGoodsName(@Param("goodsName") String goodsName);

    MPartnerGoods getByPartnerNoAndGoodsNoAndDestinationNo(@Param("partnerNo") Integer partnerNo, @Param("goodsNo") Integer goodsNo, @Param("destinationNo") Integer destinationNo);

    @Modifying
    @Query("update MPartnerGoods set "
            + " orderNumPerYear = 0"
            + " where orderNumPerYear > 0")
    Integer updateAllClearOrderNumPerYear();

    @Modifying
    @Query(value = "update m_partner_goods set goods_price = mpgpcp.after_price" +
            " from m_partner_goods_price_change_plan mpgpcp" +
            " where mpgpcp.partner_price_reflect = false" +
            "  and mpgpcp.estimate_created = true" +
            "  and mpgpcp.change_plan_date <= current_date" +
            "  and m_partner_goods.company_no=mpgpcp.company_no" +
            "  and m_partner_goods.shop_no=mpgpcp.shop_no" +
            "  and m_partner_goods.goods_no=mpgpcp.goods_no", nativeQuery = true)
    int reflectPartnerGoodsPriceChange();
}
