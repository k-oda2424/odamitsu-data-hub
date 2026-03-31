package jp.co.oda32.domain.repository.estimate;

import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 見積明細(t_estimate_detail)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2022/10/24
 */
public interface TEstimateDetailRepository extends JpaRepository<TEstimateDetail, Integer>, JpaSpecificationExecutor<TEstimateDetail> {
    List<TEstimateDetail> findByEstimateNo(@Param("estimateNo") Integer estimateNo);

    TEstimateDetail getByEstimateNoAndEstimateDetailNo(@Param("estimateNo") Integer estimateNo, @Param("estimateDetailNo") Integer estimateDetailNo);

    @Modifying
    @Query(value = "update t_estimate_detail " +
            " set (contain_num, specification) = (select mg.case_contain_num, mg.specification" +
            "                                    from m_goods mg" +
            "                                    where t_estimate_detail.goods_no = mg.goods_no)" +
            " where contain_num is null", nativeQuery = true)
    int updateGoodsInfo();

    @Modifying
    @Query(value = "update t_estimate_detail " +
            " set del_flg = '1',modify_date_time=CURRENT_TIMESTAMP" +
            " where estimate_no = :estimateNo", nativeQuery = true)
    int deleteByDelFlg(@Param("estimateNo") int estimateNo);

    /**
     * 引数の見積番号の明細を全て物理削除
     *
     * @param estimateNo 見積番号
     * @return 削除した数
     */
    int deleteByEstimateNo(@Param("estimateNo") int estimateNo);
}
