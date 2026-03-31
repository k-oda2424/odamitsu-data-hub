package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.MGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 商品マスタ(m_goods)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2017/05/18
 */
public interface GoodsRepository extends JpaRepository<MGoods, Integer>, JpaSpecificationExecutor<MGoods> {
    List<MGoods> findAll();

    List<MGoods> findByGoodsNameAndDelFlg(@Param("goodsName") String goodsName, @Param("delFlg") String delFlg);

    MGoods getByJanCodeAndDelFlg(@Param("janCode") String janCode, @Param("delFlg") String delFlg);
}
