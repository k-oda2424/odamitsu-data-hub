package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MSmartMat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * スマートマット管理マスタ(m_smart_mat)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2020/01/09
 */
public interface MSmartMatRepository extends JpaRepository<MSmartMat, Integer>, JpaSpecificationExecutor<MSmartMat> {
    List<MSmartMat> findByDelFlg(@Param("delFlg") String delFlg);

    /**
     * PK検索
     *
     * @param matId マットID
     * @return スマートマット管理マスタEntity
     */
    MSmartMat getByMatId(@Param("matId") String matId);
}
