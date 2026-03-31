package jp.co.oda32.domain.repository.estimate;

import jp.co.oda32.domain.model.estimate.VEstimateGoodsSpecial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 特値見積商品情報収集Viewリポジトリインターフェース
 *
 * @author k_oda
 * @since 2022/12/21
 */
public interface VEstimateGoodsSpecialRepository extends JpaRepository<VEstimateGoodsSpecial, Integer>, JpaSpecificationExecutor<VEstimateGoodsSpecial> {

}
