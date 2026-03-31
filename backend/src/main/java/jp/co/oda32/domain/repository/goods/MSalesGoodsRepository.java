package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.MSalesGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 販売商品マスタ(m_sales_goods)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/07/20
 */
public interface MSalesGoodsRepository extends JpaRepository<MSalesGoods, Integer>, JpaSpecificationExecutor<MSalesGoods> {
    List<MSalesGoods> findAll();

    List<MSalesGoods> findByGoodsName(@Param("goodsName") String goodsName);

//    @Query("select t from MSalesGoods t where t.goodsNo in :goodsNoList")
//    List<MSalesGoods> findByGoodsNoList(@Param("goodsNoList") List<Integer> goodsNoList);

    MSalesGoods getByShopNoAndGoodsCode(@Param("shopNo") Integer shopNo, @Param("goodsCode") String goodsCode);

    MSalesGoods getByShopNoAndGoodsNo(@Param("shopNo") Integer shopNo, @Param("goodsNo") Integer goodsNo);
}
