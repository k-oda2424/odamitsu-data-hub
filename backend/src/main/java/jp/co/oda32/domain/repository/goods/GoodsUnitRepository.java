package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.MGoodsUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 商品単位マスタ(m_goods_unit)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/07/25
 */
public interface GoodsUnitRepository extends JpaRepository<MGoodsUnit, Integer>, JpaSpecificationExecutor<MGoodsUnit> {
    List<MGoodsUnit> findByGoodsNo(@Param("goodsNo") Integer goodsNo);

    MGoodsUnit getByGoodsNoAndUnit(@Param("goodsNo") Integer goodsNo, @Param("unit") String unit);
}
