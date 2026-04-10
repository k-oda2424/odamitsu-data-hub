package jp.co.oda32.domain.repository.goods;

import jp.co.oda32.domain.model.goods.MGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * jan_code リストから一括取得（N+1 回避用）。
     * 同一 jan_code が複数件あっても全件返す。
     * COALESCE で modify_date_time が null なら add_date_time でソート（標準JPQL内）。
     */
    @Query("SELECT g FROM MGoods g WHERE g.janCode IN :janCodes AND g.delFlg = '0' " +
            "ORDER BY COALESCE(g.modifyDateTime, g.addDateTime) DESC")
    List<MGoods> findByJanCodeInAndDelFlgNo(@Param("janCodes") Collection<String> janCodes);
}
