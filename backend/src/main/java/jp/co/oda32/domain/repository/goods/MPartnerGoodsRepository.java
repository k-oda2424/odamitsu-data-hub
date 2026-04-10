package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.MPartnerGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
    @Query(nativeQuery = true, value = """
            INSERT INTO m_partner_goods (
                partner_no, goods_no, destination_no, shop_no, company_no,
                goods_code, goods_name, goods_price,
                order_num_per_year, last_sales_date,
                del_flg, add_date_time
            )
            SELECT
                o.partner_no, od.goods_no, o.destination_no,
                o.shop_no, o.company_no,
                MAX(od.goods_code), MAX(od.goods_name), MAX(od.goods_price),
                SUM(od.order_num - od.cancel_num - od.return_num),
                MAX(o.order_date_time::date),
                '0', NOW()
            FROM t_order_detail od
            JOIN t_order o ON od.order_no = o.order_no
            WHERE o.order_date_time >= :oneYearAgo
              AND o.del_flg = '0' AND od.del_flg = '0'
              AND o.destination_no IS NOT NULL
            GROUP BY o.partner_no, od.goods_no, o.destination_no, o.shop_no, o.company_no
            ON CONFLICT (partner_no, goods_no, destination_no)
            DO UPDATE SET
                order_num_per_year = EXCLUDED.order_num_per_year,
                last_sales_date = GREATEST(m_partner_goods.last_sales_date, EXCLUDED.last_sales_date),
                modify_date_time = NOW()
            """)
    int bulkUpsertFromOrderDetails(@Param("oneYearAgo") LocalDateTime oneYearAgo);

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
