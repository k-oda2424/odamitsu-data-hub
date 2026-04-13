package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.WSalesGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 販売商品ワーク(w_sales_goods)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/07/20
 */
public interface WSalesGoodsRepository extends JpaRepository<WSalesGoods, Integer>, JpaSpecificationExecutor<WSalesGoods> {
    List<WSalesGoods> findAll();

//    @Query("select t from WSalesGoods t where t.goodsNo in :goodsNoList")
//    List<WSalesGoods> findByGoodsNoList(@Param("goodsNoList") List<Integer> goodsNoList);

    List<WSalesGoods> findByGoodsName(@Param("goodsName") String goodsName);

    WSalesGoods getByShopNoAndGoodsCode(@Param("shopNo") Integer shopNo, @Param("goodsCode") String goodsCode);

    WSalesGoods getByShopNoAndGoodsNo(@Param("shopNo") Integer shopNo, @Param("goodsNo") Integer goodsNo);

    /**
     * 指定店舗の goods_code のみを射影で取得します（重複除外用、軽量）。
     * shopNo が null の場合は全店舗対象。
     */
    @Query("SELECT DISTINCT w.goodsCode FROM WSalesGoods w " +
            "WHERE (:shopNo IS NULL OR w.shopNo = :shopNo) " +
            "AND w.delFlg = '0' AND w.goodsCode IS NOT NULL")
    List<String> findDistinctGoodsCodesByShopNo(@Param("shopNo") Integer shopNo);
}
