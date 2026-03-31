package jp.co.oda32.domain.repository.estimate;

import jp.co.oda32.domain.model.estimate.VEstimateGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 見積商品情報収集Viewリポジトリインターフェース
 *
 * @author k_oda
 * @since 2022/10/28
 */
public interface VEstimateGoodsRepository extends JpaRepository<VEstimateGoods, Integer>, JpaSpecificationExecutor<VEstimateGoods> {

}
